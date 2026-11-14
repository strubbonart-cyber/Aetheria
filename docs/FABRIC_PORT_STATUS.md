# Fabric 1.21 Port Status

This branch is an unofficial, incomplete Fabric port based on the Aetheria source. It is not an official Aetheria release.

## In-game settings GUI

- Open the dedicated Mining/Dungeon settings screen with **Right Shift** or the client command `/skyblockqol`.
- Mining and Dungeon tabs expose individual feature toggles. Mining includes a separate Powder Chat Filter options page.
- Mining HUD panels share an adjustable saved position and scale; tracker pause and powder/essence thresholds are configurable in the GUI. Dungeon map, timer, splits, breaker, and room-status overlays each have independent saved layout controls.
- Changes are wired to the live feature settings and saved where the feature has persistent settings.

## Target

- Minecraft 1.21.0
- Fabric Loader and Fabric API
- Java 21-compatible mod bytecode
- Gradle/Loom runs on Java 25 in CI because the configured Loom snapshot requires it

## Implemented in the Fabric source set

- Mining first slice: daily Fetchur item HUD; Fabric system-chat listener for powder, chest, ordinary gemstone, essence, pristine, hard-stone compact, gold compact, and named Crystal Hollows reward counters; simple powder reward chat filter; mining HUD; counters persisted in `config/skyblockqol-mining.properties`.
- Mining controls: `/mining status`, `/mining filter`, `/mining reset`, and independently toggleable HUD panels with `/mining hud fetchur|powder|pristine|gold`. Settings can also be changed in the dedicated GUI.
- Ghost Tracker first slice: manual session toggle via `/ghosttracker toggle`, status/reset commands, combat-XP-based kill estimate, Sorrow/Volta/Plasma/Ghostly Boots/Bag of Cash drop counts, coin-drop count, average/highest Magic Find, HUD, and persisted counters.
- Dungeon timer, split HUD, and Dungeon Breaker HUD now each have saved, independent position and scale values. Configure them with `/dungeon layout timer <x> <y> <scale>`, `/dungeon layout splits <x> <y> <scale>`, or `/dungeon layout breaker <x> <y> <scale>`; reset all three with `/dungeon layout reset`. Existing `/dungeon timer`, `/dungeonsplits`, and `/dungeon breaker` toggles remain separate.
- Dungeon first slice: Hide Blessing Messages (`/dungeon blessing`); Dungeon Breaker charge HUD (`/dungeon breaker`); scoreboard floor/run timer (`/dungeon timer`); Blood Door, clear percentage, Scarf/Professor/Sadan/F7/M7 phase splits, score/XP capture and run-end summaries; toggleable split HUD (`/dungeonsplits`). Boss and Blood Room mob highlights now render colour-coded entity boxes, toggled with `/dungeon bosses` and `/dungeon blood`. Detection follows the original armor-stand-name association rules. These are world-space boxes, not the original model outlines. Settings persist in `config/skyblockqol-dungeon.properties`.

## Important limitations of the first Mining slice

- Powder/Pristine/chest/gemstone/essence/special-drop counters now only collect when the sidebar identifies Crystal Hollows. Gold counters collect in Dwarven Mines or Crystal Hollows.
- The filter currently suppresses common reward line types. The original per-category thresholds, full wrapper formatting behavior, and all configurable options are not yet migrated.
- Powder, Pristine, Gold and Fetchur HUD panels can be toggled independently and share a configurable position and scale. Powder and Pristine now store separate per-gem, per-quality counters and can show compact per-gem conversions (toggle each breakdown independently in the Mining GUI). The panels show totals plus active mining time and powder/Pristine per-hour rates. `/mining pause` and `/mining resume` control active-time accumulation. The Powder Chat Filter now has independent persistent toggles for chest messages, already-looted warnings, breaking-power warnings, compact messages, reward wrappers, powder, essence, gemstones, Goblin Eggs, and special drops; powder/essence thresholds and the original four gemstone tier modes are configurable with `/mining filter ...` and `/mining threshold ...`. Chat filtering is gated on a Crystal Hollows sidebar match. Per-gem quality breakdown widgets, exact tablist/scoreboard sync, profile-specific storage, tracker keybinds, and full draggable overlays remain incomplete.
- Gold Tracker now gates pickup counts to Dwarven Mines/Crystal Hollows, supports optional mining-only filtering, tracks a separate 10-second inactivity-based active time and rate, converts ingots to enchanted-gold equivalents (160:1), and lets the user select ingot or enchanted display units in the Mining GUI. Gold compact counts are gated to the same areas. Live Bazaar profit values and full tablist mining-stat parsing remain unported.
- HOTM tooltip migration: shows each recognized perk's powder invested against its max cost and, while Shift is held, the cost for the next up to 10 levels. Uses the original Aetheria perk cost formulas and activates in the Heart of the Mountain screen. The original blue-level adjustment and all config GUI format toggles are not yet ported.
- Powder Mining Chat Filter full settings and thresholds, plus its Crystal Hollows location gate, remain incomplete.
- Commission Highlight is migrated as a green translucent slot overlay on handled screens titled Commissions. Toggle with `/commissionhighlight`; it currently identifies completion from an exact `COMPLETED` lore line.
- Pickobulus Preview is migrated: while holding an item with `Ability: Pickobulus` in its lore, it raycasts up to 30 blocks and renders a 7×7×7 pink wireframe/fill cube centred on the targeted block. Toggle with `/pickobuluspreview`. It follows the original preview geometry; the original commented-out cooldown handling remains intentionally absent.
- Ghost Tracker limitations: start/stop is manual so enable it only in The Mist; the original location validation, scavenger/purse tracker, drop-value/profit calculation, all original overlay options, and full profile/session behavior are not yet migrated.

## Dungeon data foundation

- Runtime downloader/cache for the original Aetheria dungeon room and secret-location JSON. Files are stored in `config/skyblockqol/dungeon-data/`, fetched asynchronously, and retained with their GPL-3.0 attribution metadata. Check using `/dungeon data`; refresh using `/dungeon refresh-data`.
- This data foundation does not yet identify the player's room or render secrets. The original detector hashes legacy block-state strings, so the 1.21 port still needs a verified legacy-state normalization layer before its hashes can match the source dataset.

- Rectangular and L-shaped Dungeon room scanner and Secret Finder: supports the original four-side width test, the special L-shape row traversal and its floor-hash discriminator, as well as exact four-neighbour origin-corner detection. Unknown hashes remain unmatched rather than guessed. The scanner uses the original sorted block-frequency MD5 lookup against the downloaded data, but live hash compatibility still needs validation in Fakepixel 1.21. Secret markers render coloured block boxes and track chest/lever interactions, nearby item/entrance collection, block disappearance, and the Secrets Found action bar. Toggle room overlay with `/dungeon room` and secret finder with `/dungeon secrets` or in the Dungeon GUI. uses the original sorted block-frequency MD5 plus floor-hash disambiguation against the downloaded GPL-3.0 room/secret datasets. Shows matched room name/category/alias/relative coordinates in the HUD. Secret markers render coloured block boxes, track chest/lever interactions, nearby item pickups, entrance proximity, block disappearance, and the server Secrets Found action bar. Toggle with `/dungeon room` and `/dungeon secrets`, or in the Dungeon GUI. Full secret collection edge cases, tracers, custom labels and actual 1.8-to-1.21 hash compatibility remain to be verified in-game. Unknown hashes are shown as unmatched rather than guessed.

- Spirit Leap / InfiniLeap menu first slice: automatically replaces the matching chest screen while in a dungeon, shows the live map and party markers, and provides clickable teammate buttons. A button or clickable player map marker selects the matching player item in the original container using a normal vanilla slot click. Returning to the original chest is protected from automatically re-opening the overlay. Enable/disable with `/dungeon leap` or the Dungeon settings GUI. In-game server verification is still needed for both server menu title variants and item-name matching.

- Dungeon chest reveal animation first slice: on Obsidian/Bedrock reward chests during a run, scans the server-populated reward slots after a short delay and selects the actual shown reward using the original Aetheria floor/material drop table. It displays a short visual carousel and the actual reward stack; it does not generate or change the reward. Toggle with `/dungeon chestanimation` or the Dungeon GUI. Croesus chest-list integration and original custom animated textures are not yet ported.

## Dungeon features not yet ported

- L-shaped room detection, visited-room map labels/custom rank badges and advanced secret labels/tracers
- Dungeon room detection/overlay, secret finder, dungeon map/player markers, and Spirit Leap/InfiniLeap map menu
- Dungeon chest price estimator
- `/report-secret` workflow

## Build verification

A successful GitHub Actions build confirms that Gradle compilation and remapping pass. It does not by itself prove that the mod has been launched or exercised in a Minecraft client. Manual in-game verification remains required.

## Other feature families intentionally excluded from this port

- All non-Mining and non-Dungeon Aetheria feature families are out of scope for this Fabric build.
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
