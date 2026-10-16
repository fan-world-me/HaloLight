# Per-app rhythm lighting (music-reactive camera ring)

HyperOS Settings has a page called **Rhythmic light effects** (internal string `musical_rhythm_app_title`, class `com.android.settings.special.backstrap.MusicalRhythmFragment`). It lists installed apps with a switch each; apps that are switched on make the camera ring react to their audio. HaloLight reuses the same mechanism so you can toggle apps from its long-press Quick Settings menu.

Everything below was verified on a **POCO X8 Pro (`klee`), HyperOS 3 / Android 16 (SDK 36)** unless marked *unverified*.

## API

It is a plain `AudioManager` key/value pair, no Binder code needed.

| Action | Call | Result |
| --- | --- | --- |
| Read all states | `audioManager.getParameters("Get_MusicLight_Package_State=")` | `pkg1=true;pkg2=false;...` |
| Set one app | `audioManager.setParameters("Set_MusicLight_Package_State=<pkg>:<true\|false>")` | state changes |

Details:

- The value is the literal string `true` or `false`. `:1` / `:0` is **not** understood.
- The read result contains **every installed package** (132 entries on the test device), not only music apps. Parse strictly: split on `;`, then on `=`, accept only `true` / `false` (this is what Settings does, via `toBooleanStrictOrNull`).
- Both calls work from an ordinary app process (uid `10xxx`). **Shizuku is not required** for them.
- `setParameters` throws `SecurityException: Permission denial, must have one of: [android.permission.WRITE_SECURE_SETTINGS]`, **but the state is still changed**. Treat the exception as non-fatal and re-read the state afterwards to confirm the result. *Unverified:* whether granting `WRITE_SECURE_SETTINGS` makes the exception disappear.
- Declare `MODIFY_AUDIO_SETTINGS` in the manifest. On Android 11+ also declare the packages you want to see in `<queries>` (do not use `QUERY_ALL_PACKAGES`).

## What happens inside the system

Logcat while tapping the switch in Settings (filter: `MusicLight|SetLedAPP|LedParam|MusicalRhythm`):

```
MusicalRhythmFragment: onPreferenceChange: Set_MusicLight_Package_State=<pkg>:true
AudioFlingerImpl:      SetLedAPPValue appName <pkg>, value true
AudioFlingerImpl:      startSendLedParam+ flag 0x8
AS.AudioService:       Set_MusicLight_Package_State=<pkg>:true
```

Switching off logs `value false` and `stopSendLedParam`. The Xiaomi extension of AudioFlinger (`AudioFlingerImpl`) keeps the list; no audio-HAL (`AHAL`) line appears for these keys.

Settings-side classes (decompiled): `MusicalRhythmFragment` (the page), `BackStrapCloudBackupHelper` (cloud backup/restore uses the same two `AudioManager` calls), `BackStrapTrackHelper.backStrapSwitchChange` (analytics only). The feature is internally called *back strap*. `ColorLedMusicModeController` / `ColorLightManager.isMusicLightEnable()` belong to the older LED-strip music mode and are a separate toggle.

## What does NOT work

Calling the AudioFlinger Binder directly from the shell:

```
service call media.audio_flinger 20 i32 0 s16 "Set_MusicLight_Package_State=<pkg>:true"
```

It returns `Parcel(00000000 00000000)` (looks like success), but the logic never runs: no `SetLedAPPValue` line appears and the Settings page does not change. The matching read (`... 21 ...`) returns `Parcel(NULL)`. Do not use raw `service call` for this; use `AudioManager` from an app process. Transaction numbers also differ between Android versions, so re-check them after OS upgrades.

## In the HaloLight app

- `MusicLightManager.kt` implements the read / write above (off the main thread, re-reads the state after every write).
- Apps offered in the long-press menu, shown only if installed: Spotify (`com.spotify.music`), YouTube Music (`app.revanced.android.apps.youtube.music` if present, otherwise `com.google.android.apps.youtube.music`), SoundCloud (`com.soundcloud.android`).
- The menu re-reads the state each time it opens, there is no separate local state. The sync button in the section header refreshes the list.
- The ring itself is driven by the system, HaloLight only flips the per-app switch.

## Open questions

- *Unverified:* whether the master switch `settings_strip_light_enable` has to be on for the ring to react to music.
- *Unverified:* end-to-end result on the ring (ring animates to music after the switch is turned on from HaloLight).
- Behaviour on other devices and firmware builds.

## Debugging

```bash
rish -c 'logcat -c'
rish -c 'logcat -v time' | grep -iE "MusicLight|SetLedAPP|LedParam|MusicalRhythm"
```

Toggle an app in Settings and compare the log lines with your own `setParameters` call.
