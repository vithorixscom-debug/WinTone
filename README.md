# WinTone

WinTone (mod id `winton`) is a Fabric client fork of [Baritone](https://github.com/cabaletta/baritone) for **Minecraft 1.21.11**.

Page: https://modrinth.com/mod/winton

It pathfinds, mines, explores, and builds. A small MiniHUD overlay and a few Tweakeroo-style client toggles are included. Those two ports are **LGPL-3.0-only**, so the combined mod is **LGPL-3.0-only** even though Baritone itself is LGPL-3.0-or-later. See `NOTICE`.

WinTone is not affiliated with the Baritone authors.

## What it does

- Pathing: `#goto`, `#goal`, `#path`, `#come`, `#thisway`, `#axis`, `#surface`
- Mining and exploring: `#mine`, `#explore`, `#farm`, `#tunnel`
- Building: `#build`, `#litematica`, `#schematica`, `#sel`
- Elytra: `#elytra`
- Waypoints, blacklist, follow, chest sort, render toggle
- In-game menu for the same actions (English and Russian labels)

Prefix is `#` unless you changed it. `#help` lists commands.

## Requirements

- Minecraft 1.21.11
- Fabric Loader
- JDK 21 to build
- Optional: [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) for schematic build

## Build

```bash
./gradlew :fabric:remapJar
```

Jar: `fabric/build/libs/winton-fabric-<version>.jar`

Copy that jar into `mods/`. A file named `*.jar.disabled` is not loaded.

## License

| Part | License | Notice |
|---|---|---|
| Baritone pathing and Winton changes to it | LGPL-3.0-or-later | `LICENSE`, `NOTICE` |
| MiniHUD port | LGPL-3.0-only | `LICENSE_MINIHUD.txt`, `NOTICE_MINIHUD.txt` |
| Tweakeroo port | LGPL-3.0-only | `LICENSE_TWEAKEROO.txt`, `NOTICE_TWEAKEROO.txt` |
| Combined program | LGPL-3.0-only | `NOTICE` |

Keep the headers. Corresponding Source has to ship with every binary.

## Publishing this fork

`contact.sources` and `contact.issues` point at this fork, https://github.com/vithorixscom-debug/WinTone. They must not point at cabaletta/baritone, or WinTone bugs will be filed on the upstream tracker.

`provides: ["baritone"]` is intentional. Mods that ask Fabric for the `baritone` mod id get WinTone, and Fabric will refuse to load this jar next to the real Baritone mod. The `baritone.api.BaritoneAPI` class is the matching compatibility entry.

The source archive includes a `.git` directory whose history starts at https://github.com/cabaletta/baritone. Do not delete it and do not replace it with a single new commit.

Historical links such as `github.com/cabaletta/baritone/issues/107` are real upstream tickets. `baritone.leijurv.com` is the upstream documentation site.

There are no screenshots in this repository.

## Changelog

See `CHANGELOG.md`.

## Contributing

Open issues and pull requests on this fork, not on cabaletta/baritone.

- Keep LGPL headers. New Tweakeroo or MiniHUD code stays LGPL-3.0-only.
- Do not add obfuscated jars.
- Say what the change does and how you checked it.
