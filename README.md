# Moon — launcher for MoonMC

An Android launcher for **MoonMC** (the rebranded Horizon / MargyMC Android port), built with
Jetpack Compose and Material 3 Expressive. Nothing is compiled on the phone: the launcher drives
GitHub Actions, downloads the finished APK and hands it to the installer.

## What it does

- **Mods** — the official Horizon catalogue: data mods (`.hmod`) install straight into the game
  folder, native mods (`.hnmod`) need a rebuild and the launcher starts it for you.
- **Modrinth** — search resource packs, shaders and worlds; Java mods are analysed and converted
  into data mods (`.hmod`) when the mapping is known, otherwise you get an honest compatibility
  verdict instead of a broken install.
- **Controls** — on-screen gamepad editor: profiles as JSON in
  `/sdcard/MoonMC/horizon/touch/profiles/`, any button can be a key, a hold, a macro, a command
  or a switch, with separate aim/tap modes.
- **Build** — the list of `.hnmod` bundles from `ci/bundles` of the build repository, tick the
  ones you want, set the engine pin and press *Build APK with selected mods*: the launcher
  dispatches `apk.yml`, waits for the run, downloads `horizon-apk` and installs it. You can also
  add your own `.hnmod` from the phone — it is uploaded to the repository and appears in the list.
- **More** — GitHub device-flow sign-in (no token typing), game folder, language (English by
  default, Russian on request), storage permissions.

## Build

```
gradle assembleDebug      # or run the "gradle" workflow; artifact: launcher-apk
```

Requirements: JDK 17+, Android SDK 37, AGP 9.1, Gradle 9.8.

`tools/gen_icons.py` regenerates the adaptive icons (pixel moon, monochrome layer for Material You):

```
python3 tools/gen_icons.py --src icon.png --out app/src/main/res --layout gradle
```

## Layout

```
app/src/main/java/dev/moonmc/moon/
  Builder.kt Github.kt GithubAuth.kt    GitHub Actions and device-flow
  Catalog.kt HorizonIndex.kt Modrinth.kt Analyze.kt   catalogues and mod analysis
  TouchData.kt TouchEditorActivity.kt   on-screen controls editor
  ui/                                   Compose screens (Material 3 Expressive)
```

## License

MIT — see [LICENSE](LICENSE). Credits and third-party licenses: [NOTICE.md](NOTICE.md).
Not affiliated with Mojang Studios or Microsoft.
