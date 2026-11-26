package me.moneysmp.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a username into a UUID, even for players who have never joined this server.
 *
 * Order: online player -> name saved in data.yml -> the server's own player cache -> Mojang API.
 * The Mojang call runs off the main thread so the server never lags; the answer always comes back
 * on the main thread.
 */
public final class PlayerLookup {
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{1,16})\"");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private PlayerLookup() {}

    public enum Status { FOUND, NOT_FOUND, ERROR }

    /** What the lookup found. {@code name} has the player's real capitalisation. */
    public record Result(Status status, UUID uuid, String name) {
        public boolean found() { return status == Status.FOUND; }
    }

    /**
     * Looks the player up and calls {@code callback} on the main thread.
     * Found players get their name saved in {@link PlayerNames}.
     */
    public static void resolve(JavaPlugin plugin, String input, Consumer<Result> callback) {
        if (input == null || !VALID_NAME.matcher(input).matches()) {
            callback.accept(new Result(Status.NOT_FOUND, null, null));
            return;
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            callback.accept(found(online.getUniqueId(), online.getName()));
            return;
        }
        UUID saved = PlayerNames.findSaved(input);
        if (saved != null) {
            callback.accept(found(saved, PlayerNames.get(saved)));
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached != null && cached.getName() != null) {
            callback.accept(found(cached.getUniqueId(), cached.getName()));
            return;
        }

        // Offline-mode server (no proxy): UUIDs come from the name, not from Mojang.
        if (!usesMojangUuids()) {
            UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + input).getBytes(StandardCharsets.UTF_8));
            callback.accept(found(offline, input));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Result r = askMojang(input);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (r.found()) PlayerNames.remember(r.uuid(), r.name());
                callback.accept(r);
            });
        });
    }

    private static Result found(UUID uuid, String name) {
        PlayerNames.remember(uuid, name);
        return new Result(Status.FOUND, uuid, name);
    }

    /** Runs off the main thread. Tries the main Mojang API, then the newer one if the first is down. */
    private static Result askMojang(String name) {
        Result first = fetch("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (first.status() != Status.ERROR) return first;
        return fetch("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name);
    }

    private static Result fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "WillyMoneySMP-Plugin")
                    .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 204 || code == 404) return new Result(Status.NOT_FOUND, null, null);
            if (code != 200) return new Result(Status.ERROR, null, null);
            Matcher id = ID.matcher(res.body());
            Matcher nm = NAME.matcher(res.body());
            if (!id.find()) return new Result(Status.NOT_FOUND, null, null);
            String hex = id.group(1);
            UUID uuid = UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
            return new Result(Status.FOUND, uuid, nm.find() ? nm.group(1) : null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Status.ERROR, null, null);
        } catch (Exception e) {
            return new Result(Status.ERROR, null, null);
        }
    }

    /** True when players get their real Mojang UUIDs: online mode, or behind a BungeeCord/Velocity proxy. */
    @SuppressWarnings("deprecation")
    private static boolean usesMojangUuids() {
        if (Bukkit.getOnlineMode()) return true;
        try {
            if (Bukkit.spigot().getSpigotConfig().getBoolean("settings.bungeecord", false)) return true;
        } catch (Throwable ignored) {}
        try {
            if (Bukkit.spigot().getPaperConfig().getBoolean("proxies.velocity.enabled", false)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /** Message for an admin when the lookup didn't find anyone. */
    public static String failMessage(Result r, String input) {
        if (r.status() == Status.ERROR) return "§cCouldn't reach Mojang to look up " + input + ". Try again in a moment.";
        return "§cPlayer not found";
    }
}
