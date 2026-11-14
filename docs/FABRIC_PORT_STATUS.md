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
- Dungeon first slice: Hide Blessing Messages filter, controlled by `/dungeon blessing`; Dungeon Breaker charge HUD from hotbar item lore, toggled with `/dungeon breaker`; `/dungeon status` reports both toggles. Settings are persisted in `config/skyblockqol-dungeon.properties`.

## Important limitations of the first Mining slice

- Mining message parsing is currently based on recognized system-chat text and is not yet gated by a verified Crystal Hollows/Dwarven Mines location.
- The filter currently suppresses common reward line types. The original per-category thresholds, full wrapper formatting behavior, and all configurable options are not yet migrated.
- Powder and Pristine counters currently show lifetime totals. Rate/hour calculations, active-time/session behavior, profile-specific stats, tracker keybinds, and full draggable overlays are not yet migrated.
- Initial Gold Tracker pickup counting is implemented by comparing inventory totals after a recently broken gold block/ore; ingot, enchanted-gold, and compact counts appear in the Mining HUD. Profit calculation, unit conversion, tablist mining stats, pause/hide settings, profile persistence, and exact internal SkyBlock item-ID detection remain unported.
- HOTM tooltip powder cost, Commission Highlight, and Pickobulus preview are not yet migrated.
- Ghost Tracker limitations: start/stop is manual so enable it only in The Mist; the original location validation, scavenger/purse tracker, drop-value/profit calculation, all original overlay options, and full profile/session behavior are not yet migrated.

## Dungeon features not yet ported

- Dungeon overlay/splits and end-of-run stats
- Dungeon Room Overlay
- Secret Finder, dungeon map/player markers, and Spirit Leap/InfiniLeap map menu
- Boss and Blood Mob highlights
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
