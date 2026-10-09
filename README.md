# Alloy

A Java agent that loads **unmodified Forge 1.8.9 mods on Lunar Client 1.8.9** and chain-loads
[Weave](https://github.com/Weave-MC/Weave-Loader), so Forge mods and Weave mods run together with a
single JVM argument.

> **Status: 0.1, early.** Everything builds and passes its tests, and the reference mod initialises
> fully in a headless rehearsal. A launch inside the real Lunar Client has **not been verified yet**.

## Usage

1. Build and install once:
   ```bat
   scripts\setup-workspace.cmd
   scripts\build.cmd
   scripts\install.cmd
   ```
2. Drop Forge 1.8.9 mods into `%USERPROFILE%\.alloy\mods`.
3. In the Lunar launcher, profile **1.8** → advanced settings → *JVM Arguments*:
   ```
   -javaagent:C:\Users\<you>\.alloy\agents\Alloy-Agent.jar
   ```
4. Launch. The log is `%USERPROFILE%\.alloy\logs\latest.log`.

**Weave:** use only Alloy's `-javaagent`. Alloy starts the Weave agent found in `~/.weave/agents`
when a Weave mod exists for the launched version (`weave.enabled` in `~/.alloy/alloy.properties`).
Never put Forge mods in Weave's `mods` folder.

## Limits

Alloy does not run real Forge: it gives mods the Forge classes and fires Forge events from hooks
injected into the game.

- Not supported: coremods, tweakers, Mixin mods, block/item registries, Forge network channels,
  `EnumHelper`, `@Optional`.
- Events fired: ticks, keyboard/mouse input, screen open/init/draw/buttons, chat received, client
  commands, connect/disconnect, world load/unload, entity join/update/attack, HUD overlay
  (`ALL`, `CHAT`, `HOTBAR`, `EXPERIENCE`, `JUMPBAR`, `BOSSHEALTH`, `HELMET`, `PORTAL`, `PLAYER_LIST`,
  `CROSSHAIRS`, `TEXT`), entity/player rendering, `RenderWorldLastEvent`, texture stitch, FOV,
  tooltips, sounds, block highlight.
- Forge-added Minecraft members are replaced one by one, see
  `alloy-forge/src/main/resources/META-INF/alloy/member-shims.tsv`.
- Nothing a server can see is changed, and the agent does not hide itself.

## How it works

```
system loader    Lunar jars + Alloy-Agent.jar   (premain, class transformer, dev.alloy.bridge)
game loader      Minecraft, Lunar, OptiFine, Weave mods
  ^ parent
Alloy loader     Alloy's Forge runtime + Forge (remapped) + Forge mods (remapped)
```

1. `premain` registers a class transformer and chain-loads Weave.
2. The transformer patches Lunar's class loader so the game can reach `dev.alloy.bridge`, and hooks
   `Main.main`.
3. At game start Alloy downloads the official Forge jar once (SHA-256 checked), remaps Forge and each
   mod to the game's names using Lunar's own mapping files, caches them in `~/.alloy/cache`, and
   starts its runtime in a child class loader.
4. Hooks injected into Minecraft classes call `GameHooks`, which posts the matching Forge event.

| Module | Role |
|---|---|
| `alloy-bridge` | Contract between the game and the runtime (`GameHooks`, `GameEventSink`). No dependencies. |
| `alloy-remap` | Reads Lunar's `.kin` mappings, remaps jars, applies Forge's load-time transforms. |
| `alloy-hooks` | Class transformer: game-loader bridge and the hook catalog. |
| `alloy-agent` | `-javaagent` entry point, config, log, Weave, cache, mod class loader. |
| `alloy-forge` | Runs inside the game: mod lifecycle and Forge events. |
| `alloy-devtools` | Command-line tool (`setup-workspace`). |
| `alloy-dist` | Builds the final agent jar. |

## Development

Java 17, Maven (`mvnw.cmd` included), JUnit 5. Open the folder in IntelliJ.

`alloy-forge` and `alloy-dist` compile against renamed Minecraft and Forge jars that cannot be
redistributed. `scripts\setup-workspace.cmd` generates them locally in `workspace/`; reload the Maven
project afterwards.

Some tests need files from a local Lunar install and skip themselves when those are missing
(`-Dalloy.test.*` properties).

## License

GPL-3.0, like Weave, whose class-loader technique Alloy ports. Forge and Minecraft are never copied
or redistributed.
