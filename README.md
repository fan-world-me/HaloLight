[![Telegram](https://img.shields.io/badge/Telegram-@fan__world__me-2CA5E0?style=flat-square&logo=telegram)](https://t.me/fan_world_me)   [![Discord](https://img.shields.io/badge/Discord-fan__world__me-5865F2?style=flat-square&logo=discord)](https://discord.com/users/fan_world_me)   [![GitHub](https://img.shields.io/badge/GitHub-fan--world--me-181717?style=flat-square&logo=github)](https://github.com/fan-world-me)   [![Portfolio](https://img.shields.io/badge/Portfolio-fan--world--me.github.io-00e5ff?style=flat-square&logo=githubpages&logoColor=white)](https://fan-world-me.github.io/)

```
 ██████╗ ██████╗  ██████╗     ██████╗ ██╗███╗   ██╗ ██████╗
██╔══██╗██╔════╝ ██╔══██╗    ██╔══██╗██║████╗  ██║██╔════╝
██████╔╝██║  ███╗██████╔╝    ██████╔╝██║██╔██╗ ██║██║  ███╗
██╔══██╗██║   ██║██╔══██╗    ██╔══██╗██║██║╚██╗██║██║   ██║
██║  ██║╚██████╔╝██████╔╝    ██║  ██║██║██║ ╚████║╚██████╔╝
╚═╝  ╚═╝ ╚═════╝ ╚═════╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝
  HyperOS ColorLightManager Research — RGB camera ring API, no root
```

Reverse-engineering the hidden Xiaomi/HyperOS API that controls the RGB ring around the rear camera on **POCO X8 Pro** (and the same hardware on Redmi Turbo 5 / other `klee`-family devices).

**Status: working, flicker-free.** A confirmed, end-to-end, on-device tested method for smoothly animating the ring in custom colors exists. As far as we know this is the first publicly documented working method for this specific hardware.

### 💭 More about this research

- 🔍 **Reverse-engineered** `miui-services.jar`, `miui-framework.jar`, `framework.jar` — decompiled via jadx, entirely on-device via Termux
- 🔌 **No root required** — uses [Shizuku](https://shizuku.rikka.app/) (uid 2000 shell) as the Binder bridge
- 🎨 **Confirmed flicker-free animation** — full rainbow sweep at 25ms update rate without gaps
- 📡 **Blind Binder fuzzer** — independently verified the method table by brute-forcing transaction codes 1–20
- 📱 **HaloLite PoC** — working Android app (Kotlin, foreground service) that drives a rainbow animation
- 📚 **Full documentation** — API reference, Binder internals, findings, methodology, and a step-by-step research timeline

---

### ⚡ TL;DR — The Working Call

```kotlin
// via Shizuku, no root required — confirmed flicker-free at 25ms update rate
val binder = ShizukuBinderWrapper(
    SystemServiceHelper.getSystemService("miui.lights.ILightsManager")
)
val lights = ILightsManager.Stub.asInterface(binder)

lights.setColorCommon(
    0xFFFF0000.toInt(),    // ARGB color, e.g. red
    "com.android.camera",  // pkg — no real caller check on this method
    3,                     // styleType — "music rhythm" zone = same physical camera ring
    0                      // userId
)
```

Call this every ~25ms in a loop for smooth animation — no flicker, no auto-off timer.

---

### 🧰 Tech Stack & Tools

**Target device**
![POCO X8 Pro](https://img.shields.io/badge/POCO_X8_Pro-klee-FF6900?style=flat-square)
![HyperOS 3](https://img.shields.io/badge/HyperOS_3-Android_16_SDK_36-FF6900?style=flat-square)
![AW21024](https://img.shields.io/badge/Awinic_AW21024-24ch_I²C_RGB-00e5ff?style=flat-square)

**Research toolchain**
![jadx](https://img.shields.io/badge/jadx-decompiler-181717?style=flat-square)
![Shizuku](https://img.shields.io/badge/Shizuku-Binder_bridge-7C3AED?style=flat-square)
![Termux](https://img.shields.io/badge/Termux-on--device_build-1D1D1D?style=flat-square)

**HaloLite PoC**
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-foreground_service-3DDC84?style=flat-square&logo=android&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-KTS-02303A?style=flat-square&logo=gradle&logoColor=white)

---

### 🗂️ Repo Layout

```
hyperos-colorlightmanager-research/
├── docs/
│   ├── API.md         — full method table, styleType values, timing notes
│   ├── BINDER.md      — service registration, permission-check quirk, 3 ways to get IBinder
│   ├── FINDINGS.md    — hardware summary, confirmed working calls, known dead ends
│   ├── RESEARCH.md    — tools/methodology, Termux build gotchas, classloader collision warning
│   └── TIMELINE.md    — condensed step-by-step history of the investigation
├── src/
│   └── main/java/miui/lights/ILightsManager.java
│                      — the real interface, annotated (reference only — do NOT use directly)
├── examples/
│   ├── lightsapi/ILightsManager.java
│                      — same interface under your own package (safe to use — no classloader collision)
│   ├── LightsBridge.kt— Shizuku binder bridge, minimal
│   └── HaloLightService.kt
│                      — working foreground-service example: full rainbow sweep
├── app/               — integrated HaloLite Android application
├── DISCLAIMER.md
└── LICENSE
```

---

### 📡 API Reference — `miui.lights.ILightsManager`

| Code | Method | Signature | Works? |
|---|---|---|---|
| 1 | `setColorfulLight` | `(String pkg, int styleType, int userId)` | ⚠️ pre-baked XML animations only |
| 2 | `setColorCommon` | `(int color, String pkg, int styleType, int userId)` | ✅ **use this** — no timer, flicker-free |
| 3 | `setColorLed` | `(int color, String pkg, int styleType, int userId, int category)` | ❌ empty body, no-op |
| 4 | `setCustomLight` | `(int color, int flashMode, int onMs, int offMs, int brightNessMode, String pkg, int styleType, int userId)` | ⚠️ works, but has auto-off timer causing flicker at high update rates |

**Key `styleType` values:**

| styleType | Meaning |
|---|---|
| 3 | music rhythm — same physical camera ring, no auto-off timer |
| 12 | camera ring — LIGHTSTYLE_CAMERA |
| 1 | incoming call |
| 9 | notifications |
| -1 | reset / clear |

---

### 🔬 How it works

```
[On-device research]
        │
        ├── pull system jars via Shizuku shell ────► jadx decompile
        │   (miui-services.jar, framework.jar)
        │
        ├── service list | grep light ─────────────► find miui.lights.ILightsManager
        │
        ├── blind Binder fuzzer ────────────────────► confirm 4 methods on codes 1-4
        │
        └── test each method on-device ─────────────► setColorCommon(color, pkg, 3, 0)
                                                               │
                                                               ▼
                                                   [camera ring lights up ✅]
                                                   [25ms loop = smooth rainbow ✅]
```

---

### 🚀 Quick Start

1. Install [Shizuku](https://shizuku.rikka.app/) and start it via ADB / wireless debugging (no root needed)
2. Use `examples/lightsapi/ILightsManager.java` + `examples/LightsBridge.kt` as your starting point
3. Grant the app Shizuku permission at runtime
4. Call `LightsBridge.setCameraRing(color, onMs)` from anywhere

The integrated HaloLite Android app lives in `app/`; the older duplicate project copy has been removed.

```kotlin
// minimal usage — rainbow sweep
scope.launch {
    var hue = 0f
    while (true) {
        lights.setColorCommon(Color.HSVToColor(floatArrayOf(hue, 1f, 1f)), "com.android.camera", 3, 0)
        hue = (hue + 1.5f) % 360f
        delay(25)
    }
}
```

---

### ⚠️ Why `setCustomLight` causes flicker

`setCustomLight` schedules a `Handler.sendMessageDelayed(msg, onMs + offMs)` on every call — an auto-off timer. At animation speed (every 25–60ms) this timer sometimes fires before the next call arrives, producing visible blink/off gaps.

`setColorCommon` with `styleType=3` has **no timer at all** in its server-side implementation — it's an immediate unconditional state write. This is why it's the preferred call for smooth animation.

---

### 📖 Full Documentation

| Doc | Contents |
|---|---|
| [`docs/API.md`](docs/API.md) | Complete method signatures, styleType table, timing notes |
| [`docs/BINDER.md`](docs/BINDER.md) | Service registration, caller-check quirk, 3 ways to get IBinder |
| [`docs/FINDINGS.md`](docs/FINDINGS.md) | Hardware, framework components, confirmed dead ends, UPDATE |
| [`docs/RESEARCH.md`](docs/RESEARCH.md) | Tools, methodology, Termux notes, classloader collision warning |
| [`docs/TIMELINE.md`](docs/TIMELINE.md) | Full step-by-step history of the investigation |

---

### 📄 License

[GPL-3.0](LICENSE) © [fan-world-me](https://github.com/fan-world-me)

> This uses an internal, undocumented, unsupported system API found via reverse engineering.  
> No root, no exploit, no system file modification — only a normal signed app talking to a system service via Shizuku.  
> Xiaomi can change or remove any of this without notice. See [DISCLAIMER.md](DISCLAIMER.md).

---

Made with 🩵 in Ukraine 🇺🇦

[![Telegram](https://img.shields.io/badge/Telegram-@fan__world__me-2CA5E0?style=flat-square&logo=telegram)](https://t.me/fan_world_me)   [![Discord](https://img.shields.io/badge/Discord-fan__world__me-5865F2?style=flat-square&logo=discord)](https://discord.com/users/fan_world_me)   [![Portfolio](https://img.shields.io/badge/Portfolio-fan--world--me.github.io-00e5ff?style=flat-square&logo=githubpages&logoColor=white)](https://fan-world-me.github.io/)


## 🛠️ HaloLite Android app (integrated)

The repository now includes the standalone HaloLite Android application at the root, alongside the research documentation and examples.

- **App source:** `app/` (Kotlin, Android SDK, Shizuku)
- **Build system:** Gradle wrapper (`./gradlew` on Linux/macOS, `gradlew.bat` on Windows)
- **CI/CD:** [GitHub Actions workflow](.github/workflows/release.yml) builds the release APK on pushes to `main` and can be run manually from the Actions tab.
- **APK output:** `app/build/outputs/apk/release/app-release.apk`

### Local build

```bash
./gradlew assembleRelease
```

For a release signed with your own key, configure the `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables. Do not commit a keystore or its passwords. GitHub Actions reads the corresponding repository Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). If the signing secrets are absent, the workflow falls back to a debug-signed APK, which is not suitable for publishing as a trusted production update.

The workflow publishes versioned GitHub Releases (`v1.0.<run number>`). The `docs/` and `examples/` directories remain the reference material for the undocumented HyperOS Binder API.
