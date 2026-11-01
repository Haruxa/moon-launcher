<p align="center">
  <img src="docs/logo.png" width="112" alt="Moon">
</p>

<h1 align="center">Moon</h1>

<p align="center"><em>Material 3 Expressive launcher for <a href="https://github.com/Haruxa/moonmc-apk">MoonMC</a></em></p>

<p align="center">
  <a href="https://github.com/Haruxa/moon-launcher/actions/workflows/gradle.yml"><img src="https://github.com/Haruxa/moon-launcher/actions/workflows/gradle.yml/badge.svg" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-9BE8BE?style=flat-square" alt="GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 10+">
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-M3%20Expressive-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/package-dev.moonmc.moon-1F6FEB?style=flat-square" alt="package">
  <a href="https://github.com/Haruxa/moon-launcher/stargazers"><img src="https://img.shields.io/github/stars/Haruxa/moon-launcher?style=flat-square&color=9BE8BE" alt="stars"></a>
</p>

Moon installs mods, packs and on‑screen controls for **MoonMC** — the rebranded Horizon / MargyMC
Android client — and builds the game APK for you on GitHub Actions. Nothing is compiled on the
phone: the launcher dispatches a workflow, waits for the run, downloads the finished APK and hands
it to the installer.

## Why

- **No toolchain on the phone.** Building the client needs Rust, the NDK, a JDK and ~40 minutes.
  The launcher only talks to GitHub.
- **Mods that must be compiled in.** Native mods (`.hnmod`) are mixins into the engine — they cannot
  be dropped into a folder at runtime. Moon keeps them in a list and bakes the ones you enable.
- **Everything visible.** The game folder lives at `/sdcard/MoonMC/horizon`: worlds, mods, packs,
  logs and control profiles are ordinary files.

## What it does

| Tab | What's there |
| --- | --- |
| **Mods** | The official Horizon catalogue: data mods (`.hmod`) install straight into the game folder; native mods go to the build list |
| **Modrinth** | Resource packs, shaders, worlds; Java mods are analysed and converted into data mods (`.hmod`) when the mapping is known — otherwise you get an honest verdict instead of a broken install |
| **Controls** | On‑screen gamepad editor: JSON profiles in `touch/profiles/`, buttons as key / hold / macro / command / switch with separate aim and tap modes |
| **Build** | The build list: toggle, reorder, remove, clear. Bundles from `ci/bundles` and catalogue mods by URL live in one list; the engine pin follows the selected mods |
| **More** | GitHub device‑flow sign‑in, game folder, language (English by default, Russian), storage permissions |

### Build list

1. **Mods → Native → “Add to build list”** (or tick a bundle under **Build → Bundles in the repository**).
2. **Build** — flip entries on/off, reorder, delete, clear. The counter shows `enabled / total`.
3. Pick the engine pin (filled in automatically when the selected mods agree), press
   **Build APK (N)** → workflow `apk.yml` → artifact `horizon-apk` → installer.

You can also drop your own `.hnmod` from the phone: it is uploaded to `ci/bundles` and appears in
the list like the others.

## Install

Grab the `launcher-apk` artifact from the latest
[workflow run](https://github.com/Haruxa/moon-launcher/actions/workflows/gradle.yml), or build it
yourself:

```
gradle assembleDebug
```

Requirements: JDK 17+, Android SDK 37, AGP 9.1, Gradle 9.8. The launcher is landscape‑only by
design; the language defaults to English.

## Icons

`tools/gen_icons.py` regenerates the adaptive icons from one PNG — pixel moon, monochrome layer for
Material You, legacy and round variants:

```
python3 tools/gen_icons.py --src icon.png --out app/src/main/res --layout gradle
```

## Project layout

```
app/src/main/java/dev/moonmc/moon/
  Builder.kt BuildList.kt Github.kt GithubAuth.kt   GitHub Actions, build list, device flow
  Catalog.kt HorizonIndex.kt Modrinth.kt Analyze.kt catalogues, mod conversion and analysis
  TouchData.kt TouchEditorActivity.kt               on‑screen controls editor
  I18n.kt Prefs.kt                                  translations and settings
  ui/                                               Compose screens, Material 3 Expressive
tools/gen_icons.py                                  icon generator (pure Python)
```

## Ecosystem

- **[moonmc-apk](https://github.com/Haruxa/moonmc-apk)** — the build pipeline for the game itself.

## Credits

Moon is a rebrand of the MargyMC launcher by the same author; the engine is
[Horizon](https://github.com/horizon-rust/horizon) by Ludovic Marie and the Horizon contributors.
Icons follow [Material Design Icons](https://github.com/google/material-design-icons) (Apache‑2.0).
Full third‑party list: [NOTICE.md](NOTICE.md).

## License

[GPL-3.0](LICENSE). Not affiliated with, endorsed by or sponsored by Mojang Studios or Microsoft.
Minecraft is a trademark of Mojang Studios; no Minecraft files are distributed here.
