# Manual test plan

Unit tests: `./gradlew testDebugUnitTest`. These run on a real device, with notes kept per device model.

## M1 — Audio engine + live meter
1. Fresh install → onboarding → grant the microphone → Home → turn **Listening** on.
2. The ≈dB number moves when you talk, and the 3 band bars move (low: hum, mid: voice, high: "sss" or keys).
3. Deny the mic permission (app settings) → turning it on shows the permission card; "Allow microphone" works.
4. Start a voice recorder app while listening → status shows "Microphone busy", and recovers within ~2 s after.

## M2 — Continuous vibration
1. **Clap latency:** a sharp clap 1 m away → vibration feels instant (target < 60 ms). To measure, film the hand
   and the phone at 240 fps and count frames.
2. **Fan test:** turn on a fan/AC near the phone → it vibrates at first, **stops within 5 s**. Talk over the fan →
   it vibrates again.
3. **Voices only:** play a 100 Hz hum (tone generator app on another phone). Set Frequency range → "Voices only" → the
   hum stops vibrating; talking still vibrates.
4. **dB slider:** raise Minimum loudness to 80 dB → normal talking stops vibrating; lower it to 40 dB → it vibrates again.
5. **Rhythms:** low hum ≈ slow 4 Hz pulses; voice ≈ 8 Hz; whistle or hiss ≈ fast buzz, a bit weaker.
6. **Self-feedback:** in a quiet room, a single knock must not lead to vibration that keeps going by itself. If it does,
   raise `selfVibrationGuardDb` in `DspProcessor` and note the device here.
7. Strength 0 % → no vibration. "Feel every sound" off → no vibration. Test vibration button works.
8. Calibration: talk at 1 m, tap Calibrate → the displayed level reads ≈ 60 dB while talking.

## M3 — Foreground service
1. Turn listening on, lock the screen, leave for **1 hour** with periodic sounds → it still vibrates. Note the battery % used.
2. Notification shows "Listening for sounds" with **Pause** and **Stop**. Pause → vibration stops, the notification shows
   Resume. Resume → works again. Stop → the notification disappears and Home shows "Not listening".
3. Phone call during listening → status shows "Phone call active"; resumes after hanging up.
4. Reboot with listening on → the "Tap to resume listening" notification → tapping it opens the app and listening starts.
5. Repeat test 1 on a Xiaomi/Samsung/Oppo device if available; follow the OEM tip in Settings → Battery.

## Later milestones
Doorbell, two family voices, and name calls from 1 m / 3 m / another room with the TV on: added with M4–M6.
