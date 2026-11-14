# Fabric 1.21 Port Status

This branch is an unofficial, incomplete Fabric port based on the Aetheria source. It is not an official Aetheria release.

## Target

- Minecraft 1.21.0
- Fabric Loader and Fabric API
- Java 21-compatible mod bytecode
- Gradle/Loom runs on Java 25 in CI because the configured Loom snapshot requires it

## Implemented in the Fabric source set

- Performance HUD: FPS, ping, XYZ coordinates, yaw and pitch. Toggle with Right Shift.
- Countdown timer: `/timer <duration>` or `/asmtimer <duration>`; accepts inputs such as `30s`, `5m`, and `1h30m`.
- Timer controls: `/timer show`, `pause`, `resume`, `cancel`, and `add <duration>`.
- Timer HUD: remaining duration and pause state; expiration produces a chat notification.
- Roman numeral conversion: `/roman <numeral>`, accepting canonical numerals from I through MMMCMXCIX.
- Advanced calculator: `/calc <expression>` and `/athrcalc <expression>`, using the original expression evaluator with arithmetic, functions, constants, postfix multipliers, and factorial.
- Mining first slice: daily Fetchur item HUD; Fabric system-chat listener for powder, chest, ordinary gemstone, essence, pristine, hard-stone compact, gold compact, and named Crystal Hollows reward counters; simple powder reward chat filter; mining HUD; counters persisted in `config/skyblockqol-mining.properties`.
- Mining controls: `/mining status`, `/mining filter`, `/mining reset`, and `/mining hud`.
- Ghost Tracker first slice: manual session toggle via `/ghosttracker toggle`, status/reset commands, combat-XP-based kill estimate, Sorrow/Volta/Plasma/Ghostly Boots/Bag of Cash drop counts, coin-drop count, average/highest Magic Find, HUD, and persisted counters.
- Dungeon first slice: Hide Blessing Messages (`/dungeon blessing`); Dungeon Breaker charge HUD (`/dungeon breaker`); scoreboard floor/run timer (`/dungeon timer`); Blood Door, clear percentage, Scarf/Professor/Sadan/F7/M7 phase splits, score/XP capture and run-end summaries; toggleable split HUD (`/dungeonsplits`). Boss and Blood Room mob highlights now render colour-coded entity boxes, toggled with `/dungeon bosses` and `/dungeon blood`. Detection follows the original armor-stand-name association rules. These are world-space boxes, not the original model outlines. Settings persist in `config/skyblockqol-dungeon.properties`.

## Important limitations of the first Mining slice

- Mining message parsing is currently based on recognized system-chat text and is not yet gated by a verified Crystal Hollows/Dwarven Mines location.
- The filter currently suppresses common reward line types. The original per-category thresholds, full wrapper formatting behavior, and all configurable options are not yet migrated.
- Powder and Pristine counters show totals plus active mining time and powder/Pristine per-hour rates. `/mining pause` and `/mining resume` control active-time accumulation. The Powder Chat Filter now has independent persistent toggles for chest messages, already-looted warnings, breaking-power warnings, compact messages, reward wrappers, powder, essence, gemstones, Goblin Eggs, and special drops; powder/essence thresholds and the original four gemstone tier modes are configurable with `/mining filter ...` and `/mining threshold ...`. Chat filtering is gated on a Crystal Hollows sidebar match. Per-gem quality breakdown widgets, exact tablist/scoreboard sync, profile-specific storage, tracker keybinds, and full draggable overlays remain incomplete.
- Initial Gold Tracker pickup counting is implemented by comparing inventory totals after a recently broken gold block/ore; ingot, enchanted-gold, and compact counts appear in the Mining HUD. Profit calculation, unit conversion, tablist mining stats, pause/hide settings, profile persistence, and exact internal SkyBlock item-ID detection remain unported.
- HOTM tooltip migration: shows each recognized perk's powder invested against its max cost and, while Shift is held, the cost for the next up to 10 levels. Uses the original Aetheria perk cost formulas and activates in the Heart of the Mountain screen. The original blue-level adjustment and all config GUI format toggles are not yet ported.
- Powder Mining Chat Filter full settings and thresholds, plus its Crystal Hollows location gate, remain incomplete.
- Commission Highlight is migrated as a green translucent slot overlay on handled screens titled Commissions. Toggle with `/commissionhighlight`; it currently identifies completion from an exact `COMPLETED` lore line.
- Pickobulus Preview is migrated: while holding an item with `Ability: Pickobulus` in its lore, it raycasts up to 30 blocks and renders a 7×7×7 pink wireframe/fill cube centred on the targeted block. Toggle with `/pickobuluspreview`. It follows the original preview geometry; the original commented-out cooldown handling remains intentionally absent.
- Ghost Tracker limitations: start/stop is manual so enable it only in The Mist; the original location validation, scavenger/purse tracker, drop-value/profit calculation, all original overlay options, and full profile/session behavior are not yet migrated.

## Dungeon data foundation

- Runtime downloader/cache for the original Aetheria dungeon room and secret-location JSON. Files are stored in `config/skyblockqol/dungeon-data/`, fetched asynchronously, and retained with their GPL-3.0 attribution metadata. Check using `/dungeon data`; refresh using `/dungeon refresh-data`.
- This data foundation does not yet identify the player's room or render secrets. The original detector hashes legacy block-state strings, so the 1.21 port still needs a verified legacy-state normalization layer before its hashes can match the source dataset.

## Dungeon features not yet ported

- Dungeon room detection/overlay, secret finder, dungeon map/player markers, and Spirit Leap/InfiniLeap map menu
- Dungeon room detection/overlay, secret finder, dungeon map/player markers, and Spirit Leap/InfiniLeap map menu
- CSGO chest opening animation
- Dungeon chest price estimator
- `/report-secret` workflow

## Build verification

A successful GitHub Actions build confirms that Gradle compilation and remapping pass. It does not by itself prove that the mod has been launched or exercised in a Minecraft client. Manual in-game verification remains required.

## Other feature families not yet ported

- Configuration GUI and persistent feature settings beyond the simple Mining/Dungeon properties files
- Original item database, recipe viewer, tooltip changes, storage overlay and inventory features
- Chat filters, compacting, timestamps, chat heads, copy helpers, emoji rendering and global chat
- Farming trackers, visitor tools, pest tools, mouse lock and Trevor solver
- Fishing trackers and timers
- Diana tracker, loot overlays, HP overlays and party finder
- Waypoint manager, profile viewer, capes, network and privacy systems

## Porting rules

- Keep legacy Forge sources as reference only; compile only `src/fabric/java`.
- Replace Forge events, commands, GUI hooks and Mixins with Fabric APIs or updated Mixins as needed.
- Port features incrementally and update this status file only after the implementation exists.
- Do not claim a feature is complete because a placeholder class or metadata entry exists.
- Preserve original notices and AML-1.0 terms. Public distribution of compiled derivative builds requires written permission from the Original Author.
