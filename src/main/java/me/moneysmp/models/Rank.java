package me.moneysmp.models;

/** Ranks, set with /rank (ops and console only). Shown before the name in tab and chat. Sub is a label only, with no admin powers. */
public enum Rank {
    OWNER("§4§l[Owner]"),
    ADMIN("§c§l[Admin]"),
    SUB("§9§l[Sub]"),
    NONE("");

    private final String prefix;

    Rank(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() { return prefix; }

    /** Owners and Admins are staff (admin permissions, shown on the website). Subs are not. */
    public boolean isStaff() { return this == OWNER || this == ADMIN; }

    /** "Owner" / "Admin" / "Sub" / "None". */
    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
