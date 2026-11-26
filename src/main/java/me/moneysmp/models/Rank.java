package me.moneysmp.models;

/** Staff ranks, set with /rank (ops and console only). Shown before the name in tab and chat. */
public enum Rank {
    OWNER("§4§l[Owner]"),
    ADMIN("§c§l[Admin]"),
    NONE("");

    private final String prefix;

    Rank(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() { return prefix; }

    /** "Owner" / "Admin" / "None". */
    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
