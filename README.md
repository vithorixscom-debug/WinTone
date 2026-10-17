# WinTone

Fabric client pathfinder for Minecraft 1.21.11 — a [Baritone](https://github.com/cabaletta/baritone) fork with a clean in-game menu (EN/RU), smooth look by default, a MiniHUD overlay, and light client toggles. Path, mine, build, elytra, waypoints — one mod, mod id `winton` (provides `baritone`).

Клиентский pathfinder для Minecraft 1.21.11 на Fabric. Форк Baritone с аккуратным меню (EN/RU), плавным взглядом по умолчанию, MiniHUD и лёгкими клиентскими переключателями. Всё в одном моде.

[Modrinth](https://modrinth.com/mod/winton) · [Release 1.17.32](https://github.com/vithorixscom-debug/WinTone/releases/tag/v1.17.32)

WinTone is not affiliated with the Baritone authors. MiniHUD and Tweakeroo ports are LGPL-3.0-only, so the combined mod is **LGPL-3.0-only**. See `NOTICE`.

## What it does

- Pathing: `#goto`, `#goal`, `#path`, `#come`, `#thisway`, `#axis`, `#surface`
- Mining and exploring: `#mine`, `#explore`, `#farm`, `#tunnel`
- Building: `#build`, `#litematica`, `#schematica`, `#sel`
- Elytra: `#elytra`
- Waypoints, blacklist, follow, chest sort, render toggle
- In-game menu for the same actions, with English and Russian labels

The command prefix is `#` unless you changed it. `#help` lists commands.

`provides: ["baritone"]` is intentional. Mods that ask Fabric for `baritone` receive WinTone, and Fabric will not load this jar next to the real Baritone mod.

## Requirements

- Minecraft 1.21.11
- Fabric Loader
- JDK 21 to build
- Optional: [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) for schematic build

## Build

```bash
./gradlew :fabric:remapJar
```

The jar is `fabric/build/libs/winton-fabric-<version>.jar`. Copy it into `mods/`. A file named `*.jar.disabled` is not loaded.

## License

| Part | License | Notice |
|---|---|---|
| Baritone pathing and WinTone changes to it | LGPL-3.0-or-later | `LICENSE`, `NOTICE` |
| MiniHUD port | LGPL-3.0-only | `LICENSE_MINIHUD.txt`, `NOTICE_MINIHUD.txt` |
| Tweakeroo port | LGPL-3.0-only | `LICENSE_TWEAKEROO.txt`, `NOTICE_TWEAKEROO.txt` |
| Combined program | LGPL-3.0-only | `NOTICE` |

Keep the file headers. Corresponding Source has to ship with every binary. File WinTone bugs on this repository, not on cabaletta/baritone.

## Contributing

- Keep LGPL headers. New Tweakeroo or MiniHUD code stays LGPL-3.0-only.
- Do not add obfuscated jars.
- Say what the change does and how you checked it.
