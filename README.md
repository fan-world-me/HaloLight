# HyperOS ColorLightManager Research

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10.2-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)

Research into Xiaomi HyperOS's undocumented `miui.lights.ILightsManager` Binder API, plus **HaloLite**, an Android app for controlling the RGB camera ring on supported POCO/Redmi devices.

> This is an independent community project, not affiliated with Xiaomi. Compatibility depends on the device and HyperOS build; undocumented system APIs can change without notice.

## What is in this repository?

- **HaloLite Android app** — Kotlin app with a Quick Settings tile, a short rainbow animation, and an animation-duration selector.
- **Binder/API research** — findings from inspecting HyperOS framework code and testing calls on-device.
- **Reference examples** — a minimal Shizuku Binder bridge and interface definitions for developers.
- **Build & release automation** — GitHub Actions builds the APK and publishes a versioned GitHub Release.

## HaloLite features

- Toggle the system camera-ring/strip lighting setting from a Quick Settings tile.
- Run a rainbow animation on the RGB camera ring.
- Choose animation duration: **3, 5, 10, 15, or 30 seconds** (default: 5 seconds).
- Open the source repository from the app's settings.
- Uses Shizuku as a shell/Binder bridge; **root is not required**.

The app currently uses the `miui.lights.ILightsManager` Binder service and the `setColorCommon` transaction with `styleType = 3` for its animation. The system lighting toggle and the animated color call are separate operations.

## Download

Get the latest APK from **[GitHub Releases](https://github.com/fan-world-me/HyperOS-ColorLightManager-Research/releases)**.

The automated release workflow names APKs like `HaloLite-v1.0.<run-number>.apk`. If the app is distributed with a debug signing key, Android will not treat it as an update to a build signed with a different key; uninstalling the old app may be necessary before installing a differently signed build.

## Requirements and setup

- An Android device with the relevant Xiaomi/HyperOS lighting service and compatible RGB camera-ring hardware. Research was conducted on a **POCO X8 Pro (`klee`) running HyperOS 3 / Android 16**; other devices and firmware versions are not guaranteed.
- [Shizuku](https://shizuku.rikka.app/) installed and running. Start it using wireless debugging or ADB; no root access is needed.
- Grant HaloLite permission when prompted by Shizuku.

### First launch

1. Install Shizuku and start its service.
2. Install HaloLite from Releases.
3. Open HaloLite once and tap **Request Shizuku access**; approve the request in Shizuku.
4. Add **HaloLite** to Quick Settings (edit tiles in the notification shade).
5. Tap the tile to toggle the system lighting setting. Long-press the tile to open duration settings.
6. To run the rainbow animation, open HaloLite and choose **Start animation**. The app uses a foreground service while the animation runs.

If the tile or animation does not work, confirm that Shizuku is running, HaloLite has permission, and your firmware exposes the expected system service. HyperOS may restrict or change this API on other builds.

## Build from source

### Requirements

- JDK 17
- Android SDK with Android SDK Platform 34 installed
- Internet access for Gradle dependencies

### Build a release APK

Linux/macOS/Termux:
```bash
chmod +x gradlew
./gradlew assembleRelease
```

Windows:
```bat
gradlew.bat assembleRelease
```

Output:
```
app/build/outputs/apk/release/app-release.apk
```

Without a configured release keystore, the project signs the release variant with the local debug key. For a custom signing key, set these environment variables before building:

- `KEYSTORE_PATH` — path to the keystore file
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Do not commit real signing keys or passwords to the repository.

## Automated builds and releases

Workflow: [Build & Release](.github/workflows/release.yml)

- Runs on pushes to `main` that are not Markdown-only changes, and can be started manually from **Actions → Build & Release → Run workflow**.
- Uses JDK 17 and the Gradle Wrapper.
- If `KEYSTORE_BASE64` is configured in repository Actions secrets, the workflow decodes it and uses `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` for signing.
- If no keystore secret is configured, the APK is debug-signed.
- Publishes the generated APK as a GitHub Release.

## Repository layout

```text
.
├── app/                         # HaloLite Android application
├── docs/
│   ├── API.md                   # ILightsManager methods and parameters
│   ├── BINDER.md                # Binder service access and details
│   ├── FINDINGS.md              # Tested behavior and limitations
│   ├── RESEARCH.md              # Research tools and methodology
│   └── TIMELINE.md              # Research timeline
├── examples/
│   ├── lightsapi/ILightsManager.java
│   ├── LightsBridge.kt
│   └── HaloLightService.kt
├── src/main/java/miui/lights/   # Reference interface, not used directly by the app
├── .github/workflows/release.yml
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

## Research notes

The system API is undocumented and its behavior is firmware-specific. The research documentation describes observed Binder transactions, style types, and known limitations; treat each result as tied to the tested build rather than a universal guarantee.

- [API reference](docs/API.md)
- [Binder internals](docs/BINDER.md)
- [Findings and known limitations](docs/FINDINGS.md)
- [Research methodology](docs/RESEARCH.md)
- [Timeline](docs/TIMELINE.md)

## Disclaimer

This project is provided **as is**, without warranty. It is intended for research and interoperability. It does not include proprietary Xiaomi firmware binaries and is not endorsed by Xiaomi. See [DISCLAIMER.md](DISCLAIMER.md).

## License

[GNU General Public License v3.0](LICENSE).

---

Made in Ukraine 🇺🇦 · [GitHub profile](https://github.com/fan-world-me) · [Telegram](https://t.me/fan_world_me) · [Portfolio](https://fan-world-me.github.io/)
