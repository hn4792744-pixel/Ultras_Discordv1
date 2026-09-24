# Ultras_discord  v1.0.0 — by UC_Hussein

Paper plugin (Minecraft **1.21 – 1.21.x**, Java **21**) : X-Ray / Fly / AutoClicker **alerts** (never auto-ban),
statistics GUI, per-player storage and Discord logging (Bot Token and/or Webhook). Arabic + English.

> ⚠️ **حالة البناء / Build status:** the source was written and checked for syntax/consistency in a sandbox
> **without internet and without Maven / the Paper API**, so `mvn clean package` could **not** be executed there.
> Run the build once on your machine (below). If Maven reports a compile error, send me the message and I will fix it.

## 1) Build / البناء
```bash
# requirements: JDK 21 + Maven 3.9+ (internet needed once to download paper-api from repo.papermc.io)
mvn clean package
# output:
target/Ultras_discord-1.0.0.jar
```
Only dependency: `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` (scope *provided*). Nothing is shaded, so there are
no library conflicts; Gson / Adventure / MiniMessage / Java's `HttpClient` are provided by Paper / the JDK.
No NMS, no reflection on server internals (only on the public `Sound` fields, to support every 1.21.x).

## 2) Installation / التثبيت
1. Put `Ultras_discord-1.0.0.jar` in `plugins/` of a **Paper** 1.21+ server (Java 21). Spigot/Folia are not supported.
2. Start the server once → `plugins/Ultras_discord/` is created (`config.yml`, `messages_ar.yml`, `messages_en.yml`, `data/`).
3. Edit `config.yml` (language, staff, Discord…) then `/ucsecurity reload`.
Java + Bedrock players (Geyser/Floodgate) are supported because everything is server-side; Bedrock players get a small
leniency in the Fly checks (detected through the Floodgate-style zero UUID).

## 3) Language / اللغة
`config.yml` → `language: ar` or `language: en`, then `/ucsecurity reload`. Both files are editable (MiniMessage format).

## 4) Discord setup
**Webhook (easiest):** Channel → Edit → Integrations → Webhooks → New Webhook → Copy URL →
`webhook.enabled: true`, `webhook.url: "<url>"`.
**Bot:** discord.com/developers → New Application → Bot → Reset Token (copy) → invite the bot to your server with
*View Channel, Send Messages, Embed Links* → enable Developer Mode → right-click the channel → Copy ID →
`discord.enabled: true`, `discord.bot-token`, `discord.channel-id`. (No privileged intents are required – the bot only posts.)
If both are enabled the webhook is used and the bot is the automatic fallback.
Per-event switches: `discord.events.*` (xray, fly, autoclicker, combat, kills, warnings, staff-alerts, security, errors).
Embeds are fully editable in `discord.embeds.*`. Test with `/ucsecurity discordtest`.
**Secrets:** the token/webhook are never printed in the console/logs; treat `config.yml` as confidential.
A wrong token, a deleted webhook, rate limits (429) or Discord being offline never crash the plugin: messages are queued
and retried with back-off.

## 5) Commands
| Command | Permission | Description |
|---|---|---|
| `/xry_list` | `ultras.gui` (or staff) | Main GUI: Sword (kills), Ores, Players, Warnings |
| `/xry_list refresh` | `ultras.gui` | Instant refresh |
| `/xry_list open|stats|warnings|blocks|punish <player\|uuid>` | `ultras.gui` | Direct sections (used by the chat buttons) |
| `/ucsecurity reload` | `ultras.reload` | Reload config, messages, Discord |
| `/ucsecurity refresh` | `ultras.logs` | Refresh statistics/GUI |
| `/ucsecurity resetlogs all` (+ `confirm`) | `ultras.resetlogs` | Delete ALL logs (asks confirmation, 30 s) |
| `/ucsecurity resetlogs <player>` | `ultras.resetlogs` | Delete one player's logs |
| `/ucsecurity alerts` | staff | Toggle your own alerts |
| `/ucsecurity status` / `discordtest` | staff / `ultras.admin` | Status / Discord test |

## 6) Permissions
`ultras.admin` (all below) · `ultras.xray` · `ultras.fly` · `ultras.autoclicker` · `ultras.logs` · `ultras.gui` ·
`ultras.resetlogs` · `ultras.reload` · `ultras.punish` · `ultras.fly.bypass` (default false) · `ultras.autoclicker.bypass` (default false).
Staff = OP and/or any node in `staff.staff-permissions` (default `ultras.admin`, `ultras.sradmin`, `ultras.mod`).
Staff are **never exempt** from X-Ray/AutoClicker; Fly exemption is configurable (`fly.exempt-op`, `ultras.fly.bypass`).

## 7) How detection works (no auto-ban, ever)
* **X-Ray** – rolling window (10 min): rare-ores/min, ore ratio, rare share, "digging straight into hidden ores" ratio,
  time between finds, vein hopping → weighted 0-100 %. Needs ≥3 strong indicators, minimum sample sizes, ignores creative
  and self-placed ores. Levels 0-29 / 30 / 50 / 70 / 85 (editable). Every alert lists the *Detection Reasons*.
* **Fly** (survival/adventure): hover, impossible ascent, air speed – ignoring allow-flight, elytra, riptide, vehicles,
  water, ladders/vines/scaffolding, cobweb, slime/honey/beds, levitation, slow-falling, jump-boost, knockback/explosions/
  wind charges, teleports, low TPS and high ping.
* **AutoClicker** – CPS + variation + repeating pattern + same-tick bursts; several must agree; needs ≥ 9 CPS.
* Alerts go to staff chat with clickable **[OPEN PLAYER] [PUNISHMENTS] [WARNINGS]**. PUNISHMENTS runs
  `punishment-command` (default `uc gui`, placeholders `{player}` `{uuid}`) as the clicking staff member.

## 8) Data
`storage.per-player-files: true` → `data/players/<uuid>.yml` (profile, mining, kills, suspicion) and
`data/warnings/<uuid>.yml`; `false` → `data/players.yml` + `data/warnings.yml`. Saves are batched, serialised on the
main thread and written on a dedicated IO thread using atomic writes + `.bak`. A corrupt file is restored from `.bak`
or quarantined (`.corrupt-<time>`) and never crashes the server. Warnings older than `storage.warning-expire-days`
(default 3, `0` = never) are removed individually.

## 9) Testing checklist (per system)
* **Startup:** no errors; console shows modules + "Loaded data of N players".
* **GUI:** `/xry_list` → each button; open a profile; click a warning book; PUNISHMENTS.
* **X-Ray:** in survival dig tunnels straight to ores (or lower thresholds in config) → alert with reasons.
* **Fly:** survival + a fly hack/`allow-flight` off; then check elytra/ladders/water/slime do **not** alert.
* **AutoClicker:** use an auto-click tool at >15 CPS steady → alert; normal fast clicking should not.
* **Kills:** kill a player → Sword section + Discord `kills` embed.
* **Discord:** `/ucsecurity discordtest`; try a wrong token → error logged once, server unaffected.
* **Storage:** stop/start → data persists; `/ucsecurity resetlogs <player>` and `all` (+confirm).
* **Language:** `language: ar`, `/ucsecurity reload`.

## 10) Technical limitations (honest notes)
* Paper 1.21+ only (uses Paper API: Adventure/MiniMessage, `Bukkit.getCurrentTick()`, `getBlockKey()`). Not Folia-safe.
* Click timing has the server's tick resolution (50 ms), so AutoClicker analysis is statistical, never proof.
* Anti-Fly is server-side heuristics without NMS; rare edge cases (custom blocks/plugins that move players) may need
  `ultras.fly.bypass` or larger leniency values. Detection pauses under lag (`fly.min-tps`).
* X-Ray analysis is behavioural: a very lucky legit player can raise a *low* score, which is why it only alerts.
* Alerts toggled with `/ucsecurity alerts` reset on restart. In central-file mode the file is rewritten as a whole.
* Not compiled in the authoring sandbox (see top) – build it and report any compile message.

## Project tree
```
Ultras_discord/
├─ pom.xml
├─ README.md
└─ src/main/
   ├─ resources/ plugin.yml  config.yml  messages_ar.yml  messages_en.yml
   └─ java/me/uc/hussein/ultrasdiscord/
      ├─ UltrasDiscord.java
      ├─ command/    XryListCommand, UcSecurityCommand
      ├─ config/     ConfigManager, Messages
      ├─ detection/  SuspicionLevel, CombatTracker, xray/XrayDetector, fly/FlyDetector, autoclicker/AutoClickerDetector
      ├─ discord/    DiscordManager
      ├─ gui/        Menu, PagedMenu, GuiManager, MainMenu, KillsMenu, PlayersMenu, OresMenu, WarningsMenu, ProfileMenu, StatsMenu
      ├─ listener/   Connection, Mining, Movement, Click, Combat, Gui listeners
      ├─ manager/    StatsManager, StaffManager, AlertManager
      ├─ model/      PlayerData, WarningRecord, Detection, DetectionType
      ├─ storage/    StorageManager, PlayerCodec, SafeFiles
      └─ utility/    Text, TimeUtil, ItemBuilder, SoundUtil
```
