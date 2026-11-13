# Fabric 1.21 port

This branch is a separate Fabric 1.21.0 implementation track. The original Forge 1.8.9 source remains in the repository for reference and is intentionally not included in the Fabric compilation source set.

## Current status

### Ported in the first foundation pass

- Fabric client entry point and metadata.
- Fabric 1.21.0 / Java 21 Gradle toolchain.
- Toggleable performance HUD: FPS, current server ping when available, player coordinates, yaw, and pitch.
- Right Shift keybind to toggle the HUD.
- Existing compatible `assets/` and `data/` resources are included from the original project.

### Not yet ported

The rest of the original features remain outstanding. In particular, the following require dedicated rewrites against Minecraft 1.21 and Fabric APIs rather than copying the Forge 1.8.9 classes:

- Configuration, storage, profile persistence, and autosave.
- Item database, item list, recipe viewer, search, and tooltip enhancements.
- Storage overlay and container GUI.
- Scoreboard, chat filters, compacting, timestamps, chat heads, emojis, and global chat.
- Dungeon timers, map, secret tools, highlights, and chest animation.
- Mining, powder, pristine, farming, fishing, Diana, and visitor overlays/trackers.
- Waypoints, capes, profile viewer, API/network integrations, commands, and remaining QoL mixins.

See the original feature inventory in `docs/FEATURES.md`.

## Build

Requirements: JDK 21 and an internet connection for Gradle/Minecraft dependencies.

```bash
./gradlew build
```

The remapped Fabric JAR is produced in `build/libs/`. Install Fabric Loader for Minecraft 1.21.0 and Fabric API, then put the JAR in the instance's `mods` directory.

## Important licensing note

This is a source-only development branch. The original source is governed by the Aetheria Mod License (AML-1.0), included as `LICENSE.txt`. That license permits source forks and modifications but restricts public distribution of compiled derivative JARs without written permission from the original author. Do not publish a release or mirror a compiled JAR without obtaining the required written permission. CI uploads are temporary build artifacts, not public releases.

This is an unofficial porting effort and is not endorsed by the original authors. Original copyright notices and attribution remain in the repository.
