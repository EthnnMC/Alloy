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

- Not supported: coremods, tweakers, block/item registries, Forge network channels, `EnumHelper`,
  `@Optional`.
- Mixin mods: their mixins are applied by the real Mixin library (0.8.5), run by Alloy on the
  classes Lunar loads. A mixin that aims at code Lunar or OptiFine already rewrote may not find
  it: it is then logged and skipped instead of stopping the game. Mixins registered from code (a
  tweaker or coremod calling `Mixins.addConfiguration`) are not seen; only the `MixinConfigs`
  manifest attribute is.
- Events published: see `alloy-forge/src/main/resources/META-INF/alloy/fired-events.txt`. Fog,
  camera, hand, first-person overlay and item-frame events come from OptiFine's own Forge call
  sites, which Alloy connects to its Forge classes.
- HUD: health, armor, food and air are drawn by one game method, so they can only be hidden
  together; `HEALTHMOUNT` is not published.
- Forge-added Minecraft members are replaced one by one, see
  `alloy-forge/src/main/resources/META-INF/alloy/member-shims.tsv`.
- Nothing a server can see is changed, and the agent does not hide itself.

At every launch the log lists, per mod, what it needs that Alloy lacks: Mixin, a coremod or a
tweaker, a Forge event that is never published, a Forge-added member that is not replaced.

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
5. Mods with mixins: the Mixin library rewrites each targeted game class as it is defined, and
   Lunar's class loader is taught to find the mod classes that code refers to.

| Module | Role |
|---|---|
| `alloy-bridge` | Contract between the game and the runtime (`GameHooks`, `GameEventSink`). No dependencies. |
| `alloy-remap` | Reads Lunar's `.kin` mappings, remaps jars, applies Forge's load-time transforms. |
| `alloy-hooks` | Class transformer: game-loader bridge and the hook catalog. |
| `alloy-mixin` | Runs the Mixin library for the mods (its own class loader, apart from Lunar's copy). |
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
or redistributed. The agent jar embeds SpongePowered Mixin (MIT), ASM (BSD-3-Clause), Guava and
Gson (Apache-2.0).
