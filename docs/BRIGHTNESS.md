# Ring brightness (`lamp_effect_brightness`)

How the HaloLight ring brightness works on HyperOS and how HaloLight controls it. Findings come from decompiling the system Settings app and the lights service on a **POCO X8 Pro (HyperOS 3 / Android 16)**; other devices and builds may differ.

## The setting

| | |
|---|---|
| Storage | `Settings.Global` int `lamp_effect_brightness` |
| Range | `80..255` (min/max may be overridden by the device feature list `light_brightness_list`) |
| Default | device feature `default_light_barpos` |
| Written by | Settings → Lighting effects → *Brightness* slider (`LampEffectBrightnessSeekBarPreference`) |
| Read by | the system lights service (`HyperLightsService`) |

The slider in the Settings app does nothing more than `Settings.Global.putInt(resolver, "lamp_effect_brightness", value)` on every progress change.

## How the service applies it

When the key changes, the service re-reads it and recomputes a reduced value that is mapped to the device's hardware interval:

```text
reduced = intervalLeft + (brightness - 80) * (intervalRight - intervalLeft) / 175
```

Every colour the service sends to the ring (custom animations through `setColorCommon`, notification, call and rhythm effects) gets this value **in the alpha byte**:

```text
color = (reduced << 24) | (rgb & 0xFFFFFF)
```

Consequence: the alpha you pass to `setColorCommon` is ignored. Both HaloLight's rainbow animation and the system's music-reactive effects follow the global brightness automatically; no per-animation scaling is needed.

## The white preview flash

The content observer for the key does two things:

1. recompute the brightness (`updateCustomizBrightness`);
2. `displayLight("#FFFFFF")`: force the ring to white (the *force* flag skips the usual enable/scene checks) and post a delayed message that fires after **5000 ms**.

The delayed message calls `setLedStrip(null, 0, true)`, which turns the ring off. This is the "live preview" you see while dragging the slider in Settings, but it also **interrupts whatever the ring was doing** (rainbow animation, rhythm effect), and the timer turns the ring off again 5 s after the last change.

### Priorities

The lights are `LightImpl` objects with a priority. The music/custom strip used by `setColorCommon` (id 19) has priority 4. In `setPriorityLightLocked`, a non-zero colour is dropped when another light is on and has the same or higher priority, but the **turn-off path (colour 0) has no such check**. That is why `setColorCommon(0)` can always clear the white preview immediately.

## What HaloLight does

Writing (`RingBrightness.set`): `Settings.Global.putInt` if the app holds `WRITE_SECURE_SETTINGS`, otherwise `settings put global lamp_effect_brightness N` through the Shizuku shell. A one-time grant makes it faster:

```bash
rish -c "pm grant dev.fanworld.halolite android.permission.WRITE_SECURE_SETTINGS"
```

Live update: while the slider is dragged, values go into a conflated queue (latest wins) that is drained on an IO thread, so the ring follows the finger like in Settings. The white preview is expected during the drag.

Recovery (`RingBrightness.recover`), started when the finger is released:

1. wait 300 ms, then `setColorCommon(0)` to kill the white;
2. restart the enabled rhythm apps (`Set_MusicLight_Package_State=<pkg>:false`, then `:true`);
3. wait 5 s for the service's own timer to fire, then restart the rhythm apps once more.

A running rainbow animation needs nothing: it sends a new frame every 25 ms and simply takes over again.

## Quick checks

```bash
# read / write (note: plain Termux `settings` fails with "Failed transaction"; use rish)
rish -c "settings get global lamp_effect_brightness"
rish -c "settings put global lamp_effect_brightness 130"
```

Open *Lighting effects* in Settings afterwards: the slider should show the new value.
