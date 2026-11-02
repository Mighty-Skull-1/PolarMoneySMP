# 🏆 Willy Money SMP — Official Tournament Guide & Rulebook

Welcome to **Willy Money SMP**, a high-stakes, economy-driven Minecraft tournament engine built for Paper 1.20.4.

---

## ❄️ Season 2 changes (Polar Money SMP)
- KOTH removed, airdrops replaced by **Capture Points** (`/cp`), trackers removed.
- **4 teams** (`max-teams` in config.yml).
- Tiers **S (captains) / A / B / C / D**: kill rewards 30 / 25 / 20 / 15 / 10.
- **Draft**: no timer. Captains right-click bid items in their hotbar (+5 / +10 / +25); the admin who nominated right-clicks **SOLD** (or `/bid sold`).
- **Bingo**: `/bingo` is locked until the event starts, then the board opens for everyone at once.

## 📌 Table of Contents
1. [Tournament Overview](#-tournament-overview)
2. [The Two Currencies](#-the-two-currencies)
3. [Tournament Phases](#-tournament-phases)
4. [Player Tiers & Drafting](#-player-tiers--drafting)
5. [Teams, Elimination & Buyback](#-teams-elimination--buyback)
6. [Live Tournament Events](#-live-tournament-events)
8. [Admin Control Panel & System Reset](#-admin-control-panel--system-reset)
9. [Complete Commands Reference](#-complete-commands-reference)

---

## 🎮 Tournament Overview
In **Willy Money SMP**, players compete in teams led by designated Captains. The game flows through four distinct phases:
- **Drafting**: Captains spend Draft Credits in live auctions to buy players.
- **Resource Gathering**: A Grace Period where PvP is disabled to allow teams to prepare.
- **Combat & Eliminations**: PvP is activated. Death triggers permadeath into Spectator mode.
- **Finale**: The endgame showdown where surviving teams fight for the championship.

---

## 🪙 The Two Currencies

| Currency | Purpose | How to Earn / Obtain |
| :--- | :--- | :--- |
| **Draft Credits** | Used **strictly by Team Captains** during the Draft Phase auction to bid on players. | Set at the start (`300` default) or granted by Admins via `/credits give <player> <amt> draft`. |
| **Game Credits** | Used to rank teams on the leaderboard (`/leaderboard`). | Earned from **Player Kills**, capturing **Capture Points**, and placing in **Bingo**. |

---

## ⏳ Tournament Phases

The global game phase is controlled by server admins via `/smp phase <phase>` or via the interactive GUI (`/smp`).

### 1. Draft Phase (`/smp phase draft`)
- **PvP Status**: Disabled.
- **Gameplay**: Captains participate in live 30-second player auctions. Admins nominate players, and captains bid using Draft Credits.

### 2. Grace Period (`/smp phase grace`)
- **PvP Status**: Strict block on all PvP damage and friendly fire.
- **Gameplay**: Teams explore the world, mine ores, gather food, enchant armor, craft tools, and establish bases.

### 3. PvP Enabled (`/smp phase pvp`)
- **PvP Status**: Full player combat enabled. Friendly fire remains blocked between teammates.
- **Respawn Mechanics**: Normal respawning is active (no permadeath). Players keep fighting in survival upon death.
- **Kill Credit Rewards**:
  - **Captain Kill**: `+25 Game Credits`
  - **Tier A Kill**: `+15 Game Credits`
  - **Tier B Kill**: `+10 Game Credits`
  - **Tier C Kill**: `+10 Game Credits`

### 4. Final Fight: Team Deathmatch (`/tdm start` or `/ffa start`)
- This is the finale. `/tdm start` switches to the Finale phase. When it ends (last team standing, or `/tdm end`), placings are paid and the team with the most Game Credits wins the tournament.
- PvP event during the PvP phase. Everyone on a team who is online when it starts takes part.
- Die (or log out) and you're out of the FFA only: you spectate until it ends, then you're put back in Survival at your respawn point. Nothing in the tournament is reset.
- When nobody on a team is left standing, that team is out. First team out = 5th, next = 4th... last team standing = 1st.
- Rewards (`rewards.ffa` in config.yml, paid to the captain): 125 / 100 / 75 / 50 / 25.
- Kills are worth +20 more than usual (`ffa.kill-bonus`). Every 4 minutes a bounty tier (Captains → C → A → B, `ffa.bounty-order`) glows and is worth another +20 (`ffa.bounty-bonus`).
- Rule: fight whoever you want, but no focusing one team for the whole deathmatch (fine).
- Admin: `/ffa status`, `/ffa out <player>` (AFK players), `/ffa end` (end now and pay; teams still in ranked by credits), `/ffa stop` (cancel, no rewards).

### Optional: shrinking border (`/freefall start`)
- **PvP Status**: High-stakes endgame combat.
- **Free Fall**: The world border shrinks in (default 1000 → 50 blocks over 20 minutes), forcing every team into the middle. Being outside the border hurts. Use it during the final fight to push teams together if you want.

---

## 🎖️ Player Tiers & Drafting

Players are classified into four skill tiers:
- **Captain**: Team leaders who hold draft funds and lead their squad.
- **Tier A**: Elite PvP and tactical players.
- **Tier B**: Balanced combat and resource gatherers.
- **Tier C**: Support, miners, and builders.

### Running a Draft Auction:
1. Admin nominates an unassigned player:
   ```
   /bid nominate <player>
   ```
2. A 30-second live auction begins with action-bar countdowns.
3. Captains bid by typing:
   ```
   /bid
   ```
   Every bid is exactly 5 more than the last (5, 10, 15, 20...), so nobody can blow their whole purse
   on one player. The 30-second clock counts down without resetting; the top bid at 0 wins.
4. When the timer expires, the highest bidder automatically wins the player, adds them to their team roster, and deducts the Draft Credits.

---

## 🛡️ Teams & Combat

- **Friendly Fire**: Players on the same team cannot hurt each other.
- **Team Chat**: Send private team-only messages using `/tc <message>`.
- **Respawn System**: No permadeath. Players respawn normally at their bed or spawn upon death.

---

## 🎯 Live Tournament Events

### Capture Points (Season 2 - replaces KOTH and airdrops)
- **Set up**: stand on each spot and run `/cp set <name>` (8 points around the map).
- **Each day**: `/cp startday` (or the banner in `/smp`) picks 5 of the 8 at random. Only 2 are open at once; when one is captured the next opens.
- **Majority rule**: the team with the most players inside the zone (radius 6) makes progress. A tie for most = contested (frozen). Extra players don't speed it up.
- **Two phases**: hold it for 4 minutes (control), then 8 more minutes (capture). A boss bar shows who's holding each point and the time left.
- **Stealing**: if another team gets the majority, the old team's progress drains (2x speed), then the new team starts from zero.
- **Reward**: +75 credits to the capturing team's captain. `/cp list`, `/cp open <name>`, `/cp stopday`, `/cp remove <name>`.

### Lockout Bingo (5x5 Board)
- **Start**: `/bingo start` (or click Map in `/smp` GUI).
- **View Board**: `/bingo` opens a 54-slot GUI showing the 25 tasks.
- **Lockout Rule**: The first team to complete a task claims it permanently in their team color—other teams are locked out from that task.
- **Rewards** (paid when an admin ends bingo with `/bingo stop`, ranked by **most squares claimed**): **1st** `+150` · **2nd** `+120` · **3rd** `+100` · **4th** `+50` · **5th** `+25` Game Credits to the Captain.

---

## 🛠️ Admin Control Panel & System Reset

Type **`/smp`** to open the interactive Admin Control Panel:

```
[Row 1 - Tournament Phases & Stats]
  Slot 10: [Gold Ingot]       Draft Phase (Glows when active)
  Slot 11: [Shield]           Grace Period (Glows when active)
  Slot 12: [Diamond Sword]    PvP Phase (Glows when active)
  Slot 13: [Netherite Sword]  Finale Showdown (Glows when active)
  Slot 16: [Nether Star]      Tournament Live Stats Overview

[Row 2 - Events & System Controls]
  Slot 18: [White Banner]     Capture Points (Left: start today, Right: close all)
  Slot 22: [Filled Map]       Lockout Bingo Toggle
  Slot 23: [Eye of Ender]     Save Data to data.yml
  Slot 24: [Redstone Torch]   Reload Configs
  Slot 25: [TNT]              RESET TOURNAMENT (Shift + Click to Confirm)
  Slot 26: [Barrier]          Close Menu
```

### Full Tournament Reset
After running tests, you can completely reset everything to the beginning:
- **Command**: `/smp reset confirm`
- **GUI**: **Shift + Click** the TNT (Slot 25) in `/smp`.
- **What gets reset**:
  - All players restored to Survival mode.
  - Health and hunger filled to maximum, potion effects cleared, XP reset to 0.
  - Test inventories and ender chests emptied.
  - All players teleported to world spawn.
  - Eliminations cleared and spectators revived.
  - All teams disbanded and player tiers cleared.
  - Credits reset to starting defaults (`game: 100`, `draft: 300`).
  - Tournament phase reset to `Draft`.
  - All ongoing events stopped.
  - `data.yml` wiped fresh.

---

## ⌨️ Complete Commands Reference

### Player Commands
| Command | Usage |
| :--- | :--- |
| `/team list` | View all active teams and rosters. |
| `/team info [team]` | Inspect a team's members, captain, and elimination status. |
| `/tc <message>` | Send a private message to your teammates. |
| `/bingo` | Open the interactive 5x5 Lockout Bingo GUI. |
| `/leaderboard` | View top teams ranked by credit balances. |
| `/website` | Clickable link to the live standings website. |

### Admin Commands
| Command | Usage |
| :--- | :--- |
| `/smp` | Open the Admin Control Panel GUI. |
| `/smp phase <draft\|grace\|pvp\|finale>` | Set the tournament phase. |
| `/smp reset confirm` | Perform a complete tournament wipe & reset. |
| `/smp reload` | Reload config.yml. |
| `/smp save` | Force an immediate write of all data to `data.yml`. |
| `/tier set <player> <A\|B\|C\|CAPTAIN>` | Assign a player's tier. |
| `/tier remove <player>` | Remove a player's assigned tier. |
| `/team create <name> <captain>` | Create a team with a captain. |
| `/team remove <player>` | Remove a player from their team. |
| `/team disband <team>` | Disband an entire team. |
| `/bid nominate <player>` | Put a player up for auction (captains get bid items, you get SOLD/CANCEL). |
| `/bid cancel` | Cancel the active auction. |
| `/bid sold` | Sell the player to the top bidder (same as the SOLD item). |
| `/cp startday` | Open today's capture points (also `set`, `list`, `open`, `stopday`, `remove`). |
| `/credits give <player> <amt> [game\|draft]` | Give credits to a player. |
| `/credits take <player> <amt> [game\|draft]` | Deduct credits from a player. |
| `/credits set <player> <amt> [game\|draft]` | Set a player's credit balance. |
| `/credits balance <player>` | Check credit balances of a player. |
| `/team sub <old> <new>` | Sub a player out (works while they're offline). The new player gets their team, tier, credits, kills and deaths. Saves instantly. |
| `/ffa start` | Team Deathmatch: last team standing wins, bounty tier glows (also `status`, `out <player>`, `end`, `stop`). |
| `/freefall start [minutes] [size]` | Finale: shrink the world border (also `center`, `status`, `stop`, `end`, `reset`). |
| `/bingo start` / `/bingo stop` | Start or stop the Lockout Bingo event. |
| `/announce <message>` | Global server announcement with sound. |
| `/titleannounce <title>` | Fullscreen screen title alert for all players. |
