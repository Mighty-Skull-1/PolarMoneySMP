# Changelog

Version numbers are **big.medium.small** (for example 3.4.0):

- **Small** fix or tweak: the last number goes up (3.4.0 → 3.4.1).
- **New feature**: the middle number goes up and the last goes back to 0 (3.4.1 → 3.5.0).
- **Big change** that could break things or change saved data: the first number goes up and the others go to 0 (3.5.0 → 4.0.0).

Check which version a server is running with `/smp version` (it's also shown in the console when the plugin starts, and in the jar name, e.g. `WillyMoneySMP-3.4.0.jar`).

---

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
