# Decisions and deviations from the plan

Changes from `HAPTIC_HEARING_APP_PLAN.md`, or choices the plan left open.

## Toolchain
- AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21 (+ Compose compiler plugin), KSP 2.0.21-1.0.28, Hilt 2.52,
  Room 2.6.1, Compose BOM 2024.12.01, JDK 17. **compileSdk/targetSdk 35.** This is a known-good combination.
  Moving to a newer SDK/AGP is a separate, deliberate step.
- Package / applicationId `com.hapticHearing` is kept exactly as the plan says, even though it's mixed-case.

## Audio (§4)
- Capture is 16-bit PCM converted to float. Some devices reject `ENCODING_PCM_FLOAT` for the mic.
- Mic loss is detected two ways: `AudioRecord.read` errors, and `AudioRecordingCallback.isClientSilenced`
  (API 29+), which is how Android reports that another app took the mic.
- Calls: `AudioManager.getMode()` is polled every 1 s (IN_CALL / IN_COMMUNICATION / RINGTONE → release mic).
  This avoids the `READ_PHONE_STATE` permission.

## DSP (§5)
- **dBFS reference:** power is normalised so a full-scale sine = 0 dBFS. dB SPL ≈ dBFS + offset (default 100).
- **Band edges** are strictly log-spaced as §5.1 step 3 says. The default 100–8000 Hz range gives
  100 / 431 / 1857 / 8000 Hz (the plan's "500 / 2000" values were approximate).
- **Steady-hum filter (the optional extra of §5.2), implemented differently:** a band whose 100 ms-smoothed level has a
  std-dev < 1.5 dB over 3 s is marked steady, and its noise floor is lifted to 1 dB under that level. It does
  **not** veto the band outright. A veto would also swallow a clap or a voice on top of the fan until the 3 s window
  catches up. Lifting the floor still lets anything ≥ margin above the hum vibrate at once. With only the
  1.5 dB/s rise, a fan 20 dB above a quiet room would take about 8 s to absorb. With this filter it takes about 3 s,
  which meets M2's "within 5 s".
- **Self-vibration guard:** while the motor runs (+40 ms tail), the low band needs +6 dB extra margin and the mid
  band +3 dB. This stops the mic hearing the phone's own motor and keeping the vibration going by itself. Needs
  tuning on real devices (see TEST_PLAN).

## Haptics (§5.3, §9)
- Continuous mode sends an 80 ms waveform, refreshed every 50 ms, generated from a running rhythm phase. Each
  refresh replaces the previous one, so the motor never gaps and the 4 Hz / 8 Hz rhythms stay even. An onset or a
  change of dominant band sends **immediately**, not on the 50 ms tick, to meet the < 60 ms latency target.
- All vibrations use the `ALARM` usage so DND and "touch feedback off" don't silence them.
- "Quiet mode (danger only)" also turns off continuous vibration.
- Event patterns that repeat (`×N`) get a 300 ms gap between repeats (NAME_CALL). HEARTBEAT and RING repeat with
  no extra gap because their last "off" already acts as the gap.

## Service (§12)
- `START_NOT_STICKY`: Android 14+ won't let a microphone FGS restart from the background, so sticky restarts
  would just crash. Reboot → "Tap to resume listening" notification (only if the user had listening on).
- A `PARTIAL_WAKE_LOCK` is held while listening (not while paused). This keeps capture alive with the screen off on
  devices that suspend the CPU despite an active `AudioRecord`. Its battery cost is measured in M7.
- No `INTERNET` permission in any build type. Backup and device-transfer are disabled
  (`allowBackup=false` + `data_extraction_rules.xml`).

## UI (§10)
- The frequency range slider uses a log scale so 50–500 Hz is usable. The "All sounds" preset is 50–8000 Hz; the
  default setting stays 100–8000 Hz as the plan says.
- Calibration averages the louder half of 3 s of readings while the user talks (speech has pauses) and sets
  `offset = 60 − level`.

## Sound recognition (§6, M4)
- MediaPipe `tasks-audio` 1.2.3 (latest on Google Maven), `AUDIO_CLIPS` mode on the last 15 600 samples
  (0.975 s) from the ring buffer, every 0.5 s (1 s with battery saver).
- MediaPipe's manifest adds `INTERNET` and `ACCESS_NETWORK_STATE`; both are removed with `tools:node="remove"`.
- Extra **display-only (INFO)** categories, beyond the plan's table: voice (speech), laughter, clapping, vehicle,
  truck/bus, train, aircraft, bird, plus music, which is display-only rather than a LOW alert. They show an icon in
  "Now hearing" but never vibrate a pattern or go into History. The plan's alert categories keep their priorities
  and patterns.
- The debounce (2 of 3 windows) also drives the display, so the icon doesn't flicker. It stays up 8 s after the
  sound stops. DANGER categories skip the "above the dB threshold" check (favour false alarms over misses).
- Material Icons has no bird icon, so a simple bird silhouette is drawn in code (`SoundIcons.Bird`).
- Debug APKs are split per ABI (arm64-v8a / armeabi-v7a / x86_64): MediaPipe's native libs are about 30 MB per ABI.

## Natural feel mode (added after reviewing os6/nospeaker)
- nospeaker drives a Mac trackpad's Taptic Engine with arbitrary sine frequencies through a private macOS API.
  Android has no equivalent on most phones: frequency control (`WaveformEnvelopeBuilder`) exists only from API 36,
  on a few devices. Skin also can't tell frequencies apart above about 500 Hz. So the idea we borrowed is
  **following the sound's envelope over time**, not its frequency.
- Settings → "How sounds feel": **Rhythm** (default, pitch → pulse speed) or **Natural feel**. Natural feel uses a
  `HapticMapper` with 5 ms attack / 40 ms release. It resends a 48 ms waveform every 32 ms, or immediately on a
  change of ≥ 12 % or an onset. An onset (a rise of ≥ 0.25 in one frame) starts with a 20 ms strong tap. Without
  amplitude control the strength becomes a 20 ms PWM duty cycle.
- Not done yet: mapping pitch → motor frequency on API 36 devices with envelope support. It needs
  compileSdk 36 and an AGP upgrade, and wouldn't help the Realme C55 test phone.
