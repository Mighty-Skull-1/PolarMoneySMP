# Changelog

Version numbers are **big.medium.small** (for example 3.4.0):

- **Small** fix or tweak: the last number goes up (3.4.0 → 3.4.1).
- **New feature**: the middle number goes up and the last goes back to 0 (3.4.1 → 3.5.0).
- **Big change** that could break things or change saved data: the first number goes up and the others go to 0 (3.5.0 → 4.0.0).

Check which version a server is running with `/smp version` (it's also shown in the console when the plugin starts, and in the jar name, e.g. `PolarMoneySMP-3.5.1.jar`).

---

## 3.7.1 (2026-11-26)

- **Your data is kept safe on every update.** When a new version starts for the first time, it copies `data.yml`, `fines.yml`, `events-state.yml`, `banned-items.yml` and `config.yml` into `backups/update_<date>_from-<old>_to-<new>/` before loading anything.
- If any of those files can't be read, the plugin no longer saves over it. Saving to that file stops, and admins are warned in the console and in chat. Before this, a broken `fines.yml`, `events-state.yml`, `banned-items.yml` or `config.yml` could be wiped. While `fines.yml` is unreadable, `/fine` is paused.
- Saving `data.yml` now keeps any fields this version doesn't know about, instead of dropping them.
- New options in `config.yml` are added to your server's `config.yml` automatically, without changing the values you've already set.
- `/team create <team> <captain>` works for players who have never joined. The name is looked up from Mojang (off the main thread), and the captain is saved by UUID with their name. They show in `/team list` and on the website straight away, and are captain with the right team colour and tier when they first join. A name that doesn't exist says "Player not found".
- `/credits` now works for offline and never-joined players too. `/ffa out` no longer risks freezing the server while it looks up a name.
- New "Updating safely" section in the README with these rules.
- No existing data is changed or reset.

## 3.7.0 (2026-11-26)

- New **Sub** rank for substitute players: `/rank set <player> sub`. They get a blue `[Sub]` tag before their name in tab and chat, and show up in `/rank list` under the Owners and Admins.
- Subs get no admin powers. It's only a tag.
- Subs are not staff, so they don't appear in the website's "Run by" section.
- Remove it like any other rank with `/rank set <player> none`.

## 3.6.3 (2026-11-26)

- On startup, the plugin now fixes the old website link by itself. If the server's `config.yml` still has `website-url: https://willy-money-smp-web.vercel.app/` (that address no longer works), it is changed to `https://polar-money-smp-web.vercel.app/` and the console says so. That way `/website`, the tab list and the scoreboard show the right link.
- Only that one line is changed, and only if it is exactly the old address. Other settings and all saved data are left alone.

## 3.6.2 (2026-11-26)

- The plugin's author is now listed as **Mighty**.
- The website link now points to the new address, `polar-money-smp-web.vercel.app` (the old willy address no longer works). This is the default for new servers.
- The README and tournament guides now say Polar Money SMP and link to the renamed website repo, `PolarMoneySMPWeb`.
- Nothing else changed: the data folder is still `plugins/WillyMoneySMP`, and saved data and website signing are untouched.

## 3.6.1 (2026-11-26)

- Everything players see in game now says **Polar Money SMP** instead of Willy Money SMP: the chat prefix, titles, the scoreboard, menus, the tab list, command help and the command descriptions.
- Nothing else changed. The plugin's internal name is still WillyMoneySMP, so its data folder (`plugins/WillyMoneySMP`), `data.yml`, fines, teams and credits stay exactly where they are. The website link and website signing work the same as before.

## 3.6.0 (2026-11-26)

- The website now shows the server staff. Everyone with a `/rank` (Owner or Admin) is sent to the site with the rest of the live data, and the Season 2 page shows them in a "Run by" section.
- Changing someone's rank with `/rank set` updates the website within a few seconds. If nobody has a rank, the section is hidden.

## 3.5.1 (2026-11-26)

- The jar is now called `PolarMoneySMP-<version>.jar` (for example `PolarMoneySMP-3.5.1.jar`), to match the GitHub repo's new name.
- The GitHub download is now `PolarMoneySMP.jar`. `WillyMoneySMP.jar` is still there as the same file, so old links keep working.
- When updating a server, delete the old `WillyMoneySMP-...jar` from the plugins folder first, so you don't have two copies.
- Nothing else changed: the plugin is still called WillyMoneySMP in game, and its data folder and `data.yml` stay where they are.

## 3.5.0 (2026-11-26)

**Owner and Admin staff ranks.**

- New `/rank set <player> owner|admin|none` and `/rank list`. Only server operators and the console can use `/rank` - not Admins, and not Owners either.
- `[Owner]` or `[Admin]` shows before the player's name in the tab list and in chat.
- **Owners** can use every plugin command (except `/rank`).
- **Admins** can run events, the draft, fines, subs, teams, tiers and credits, but can't reset the tournament (`/smp reset`), reload the config (`/smp reload`), change the server address (`/smp setip`), or use the Reload and Reset buttons in the `/smp` menu.
- Ranks are saved by UUID in a new `ranks:` section in `data.yml`. They work for offline players and players who have never joined, and they're kept when the tournament is reset.
- Server operators keep full access, like before.

## 3.4.0 (2026-11-26)

**Draft players who have never joined the server.**

- `/bid nominate <name>` now works for players who are offline or have never joined. If the server doesn't know them, the plugin asks Mojang for their UUID in the background, so the server doesn't lag.
- If the name doesn't exist, admins see "Player not found".
- When a player is sold, they're added to their team and their name is saved, so they show on the website and in `/team list` and `/team info` straight away.
- When they first join, they're already on their team with the right tier and team colour, and they get a short welcome message.
- `/tier set`, `/tier remove` and `/team sub` also work for players who have never joined.
- `/team list` now shows each team's players, not just the captain.
- Player names are saved in a new `names:` section in `data.yml`. Nothing already in `data.yml` is changed or reset.
- New `/smp version` command. The jar is now named with its version (`WillyMoneySMP-3.4.0.jar`).

## 3.3-SNAPSHOT and earlier

Older versions didn't use this numbering. See the git history for what changed.
