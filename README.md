# Skyfront

Modern drone warfare for Minecraft, built as an add-on for [Superb Warfare](https://modrinth.com/mod/superb-warfare).

Recon and thermal quadcopters, FPV kamikaze drones carrying RPG rounds and mortar shells, the P1-SUN interceptor, the Molniya fixed-wing strike drone, Shahed-136 launches with route planning, radar and air-raid sirens. Written for multiplayer servers: client-side prediction with an authoritative server, long-range drone links with terrain streaming, and client-side effects.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1+
- Superb Warfare 0.8+
- GeckoLib 4.6+

The mod is needed on both the server and the client.

## Building

1. Download the Superb Warfare jar for Minecraft 1.21.1 (0.8.9 or newer) and put it into a `libs/` folder in the project root. It is only used to compile against and is not part of this repository.
2. Build:

   ```
   ./gradlew build
   ```

   The mod jar is written to `build/libs/`.

In-game tests run with `./gradlew runGameTestServer`.

## Project layout

- `src/main/java/dev/krutlexik/skyfront` — the mod
  - `drone/` — the flyable drones, their flight models, OSDs, controls and networking
  - `entity/` — Shahed, launch rail, debris
  - `block/`, `client/gui/` — terminal, radar, siren
  - `config/SkyfrontConfig.java` — the config, written to `config/skyfront-common.toml`
- `tools/` — Python scripts that convert the Blockbench models into the `.glb` files the mod renders

## License

The code is GPL-3.0-or-later, see [LICENSE](LICENSE). Third-party assets keep their own licenses, listed below.

## Third-party assets

- Mavic 3 Pro / Mavic 3T model (`models/entity/drone.glb`): ["DJI Mavic 3"](https://sketchfab.com/3d-models/dji-mavic-3-c5a5abae1dea468ab73b1bdc7d616fa6) by [llirikslon](https://sketchfab.com/llirikslon), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Reduced to a tenth of its polygons and converted for Minecraft.

## Authors

Lexik and Krutnet.
