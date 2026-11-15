# Fabric 1.21 Port Status

This branch is an unofficial, incomplete Fabric port based on the Aetheria source. It is not an official Aetheria release.

## In-game settings GUI

- Open the dedicated Mining/Dungeon settings screen with **Right Shift** or the client command `/skyblockqol`.
- Mining and Dungeon tabs expose individual feature toggles. Mining includes a separate Powder Chat Filter options page.
- Fetchur, Powder, Pristine, and Gold HUD panels each have an independent saved position and scale; Ghost Tracker also has its own saved position and scale. Tracker pause, powder/essence thresholds, panel visibility, and filter categories are configurable in the GUI. Dungeon map, timer, splits, breaker, and room-status overlays each have independent saved position/scale controls.
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
- Dungeon timer, split HUD, Dungeon Breaker HUD, and room-status HUD now each have saved, independent position and scale values; the settings screen edits the same values used by the renderers. Configure them with `/dungeon layout timer <x> <y> <scale>`, `/dungeon layout splits <x> <y> <scale>`, or `/dungeon layout breaker <x> <y> <scale>`; reset all three with `/dungeon layout reset`. Existing `/dungeon timer`, `/dungeonsplits`, and `/dungeon breaker` toggles remain separate.
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
- The Fabric room scanner supports rectangular and L-shaped room traversal, floor-hash disambiguation, and exact four-neighbour room-origin detection. It uses the original sorted block-frequency MD5 lookup against the downloaded data. The Secret Finder renders collected/remaining secret markers and tracks chest/lever interactions, nearby item pickups, entrance proximity, block disappearance, and the Secrets Found action bar. Unknown hashes stay unmatched rather than being guessed. Live 1.8-to-1.21 block-state hash compatibility and edge cases still require in-game validation.
- Spirit Leap / InfiniLeap replaces the matching server chest screen while in a dungeon, displays the party map/markers, and supports normal server-validated slot clicks for teammates. Menu title variants and item matching still need in-game testing.

## Dungeon features not yet ported

- Dungeon Chest Price Estimator and its best-value chest analyzer. The original relies on Aetheria's shared item-price cache, which is not part of this Fabric slice yet.
- `/report-secret` submission GUI. The original endpoint expects an Aetheria mod-secret header, so it must not be copied into this public client port as a hard-coded credential.
- Visited-room map labels/custom rank badges, advanced secret tracers/custom labels, and exact original detector compatibility remain incomplete or unverified.
- The cosmetic CSGO-style chest-opening animation has been removed from this port's feature scope.

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

## QoL and Pets migration (first slice)

- SkyBlock item ID tooltip: reads the public `ExtraAttributes.id` entry from the item's custom-data component and appends `skyblock:<id>`. It does not alter item data. Toggle in the Quality of Life settings page.
- Custom block selection highlight: renders a cyan filled/outline overlay around the block currently targeted by the crosshair. It is an additive highlight in this first slice; the vanilla outline remains visible. Toggle in the Quality of Life settings page.
- Current Pet HUD: learns the active pet from the server's Autopet chat message or from the active pet item in the Pets menu (the item whose lore contains `Click to despawn`). The text HUD and its enabled state persist in the config folder. It does not automate pet selection or inventory actions.

## QoL features still to port

Enchant Parser and its layouts/chroma, Gyro Wand ring/cooldown helper, cursor reset prevention, disable enchant glint, Brewing Helper, Missing Enchants, Confirm Disconnect, Chat State Restore, Anvil Combine Helper, Slot Binds, Better Containers, Damage Formatter, Profile Parser, and Rare Drop Tracker remain unported or incomplete.

## Pets, Wardrobe, and Garden remaining

The Current Pet text HUD is only the first pet feature. Pet textures/cache, pet collection/profile data, and Wardrobe profile/slot data are not yet ported. Garden work has not started on this branch. Planned Garden order: plot numbers, farming/organic-matter trackers, BPS and precision overlays, visitor shopping list, then pest finder/tracker/alerts and Trevor tools.

- Mixin-backed QoL options: Disable Enchant Glint, Show Own Nametag, and Confirm Disconnect (first click asks for a second click within three seconds). All three settings are saved and individually configurable in the Quality of Life page.

- Enchant tooltip first slice: uses the repository's bundled enchant metadata to color recognized enchant names by level tier (including separate ultimate-enchant coloring). The same data drives a Shift-held missing-enchant list, respecting item categories and mutually exclusive enchant pools. Toggle each behavior from Quality of Life settings. Advanced chroma animation, two-column/compressed layouts, exact legacy ordering, and every special-case lore format remain incomplete.

## Shared HUD position editor

- A common drag-and-drop editor is now used for every active HUD: Fetchur, Powder, Pristine, Gold, Ghost Tracker, Dungeon Map, Dungeon Timer, Dungeon Splits, Dungeon Breaker, Dungeon Room, and Current Pet.
- Select an overlay in its feature page and choose **Edit Position**. Drag the preview with the left mouse button. The editor shows X/Y and scale, supports SIZE - / SIZE +, RESET, and DONE, and saves the position in the same configuration used by the live HUD.
- Garden and future QoL overlays should register with this shared editor instead of implementing separate position controls.


## Garden first Fabric slice

- Garden detection reads the visible sidebar and activates the Garden HUDs only when a sidebar line contains "Garden".
- Blocks-per-second HUD counts client-side block-break events in a rolling one-second window.
- Farming Tracker counts mature crop-block breaks in the Garden and saves the lifetime total. It does not claim to count exact item stacks or calculate Bazaar profit.
- Precise Yaw/Pitch HUD displays the player rotation to two decimal places.
- The three HUDs each have independent saved X/Y/scale values and use the shared drag editor. Configure them from the new **Garden** settings page.
- Plot-number overlays, visitor shopping list, pest finder/tracker/cooldown alert, Trevor Solver, mouse lock/sensitivity reducer, Organic Matter Tracker and the remaining Garden features are not yet ported.


## Garden pest features

- Pest Tracker parses the server kill message format `You received Nx <drop> for killing a/an <pest>!`, counts kills and reported drop quantities, saves them locally, and provides a HUD plus `/pesttracker reset|show|hide|toggle|status`.
- Pest Cooldown Alert reads the tab-list `Cooldown:` entry while in the Garden. It is off by default, supports a 5-300 second threshold, fires once when the cooldown crosses below the threshold, and can be configured in the Garden page or with `/asmpest <seconds>|off|status`.
- Pest Tracker uses the server-reported drop quantities. It does not estimate item prices or profit without the shared price service.
