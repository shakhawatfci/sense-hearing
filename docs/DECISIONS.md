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
