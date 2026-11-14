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

## Build verification

GitHub Actions build runs compile and remap the Fabric JAR. A successful CI build confirms that Gradle compilation and remapping pass. It does not by itself prove that the mod has been launched or exercised in a Minecraft client. Manual in-game verification remains required.

## Not yet ported

The following feature families are still outstanding and should not be represented as available:

- Configuration GUI and persistent feature settings
- Original item database, recipe viewer, tooltip changes, storage overlay and inventory features
- Chat filters, compacting, timestamps, chat heads, copy helpers, emoji rendering and global chat
- Dungeon map, secrets, timers, room overlays, boss and blood mob highlights, leap menu and chest estimator
- Mining overlays and trackers
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
