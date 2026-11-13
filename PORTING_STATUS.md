# Porting status: Minecraft 1.21.0 / Fabric

Last updated: 2026-11-13

## Foundation

- [x] Separate Fabric branch.
- [x] Fabric Loader entry point.
- [x] Fabric metadata and Java 21 toolchain.
- [x] Fabric API dependency.
- [x] First HUD feature: FPS, ping, coordinates, yaw, and pitch.
- [x] Configurable keybind registration (Right Shift default).
- [ ] First CI build verification.
- [ ] In-game smoke test.

## Feature migration order

1. Core configuration and storage.
2. Common event abstractions and command registry.
3. HUD/overlay framework.
4. Item data, item list, and recipe viewer.
5. Chat modules and scoreboard.
6. Mining, farming, fishing, and Diana trackers.
7. Dungeons, waypoints, profile viewer, capes, and network integrations.
8. Remaining GUI changes and mixins.

A checked item means the code exists in this branch. It does not imply the in-game behavior has been tested until the smoke-test item is checked.
