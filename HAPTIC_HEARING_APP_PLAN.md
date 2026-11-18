# Haptic Hearing — Android App Build Plan

> **For Claude (local build agent):** This file is the full specification for an Android app.
> Put it in the project root (you can rename it to `CLAUDE.md`). Build it **milestone by milestone**
> (section 13). After each milestone: make sure the project compiles, run the unit tests, and confirm
> the milestone's acceptance criteria before moving on. When a library API differs from what is
> written here, follow the library's current official docs/sample code and note the change in
> `docs/DECISIONS.md`. Never upload audio, voice data or photos anywhere — everything stays on the device.

---

## 1. What the app does

An app for deaf and hard-of-hearing users that turns the sound around them into **vibration + pictures**,
fully **offline**:

1. **Feel every sound.** Every sound above the user's loudness threshold, inside their chosen
   frequency range, makes the phone vibrate. Louder = stronger. Low/mid/high pitch = different pulse
   rhythms. **Steady background noise (fan, AC, traffic hum) must NOT vibrate.**
2. **Recognise general sounds.** Doorbell, knock, alarm, siren, baby crying, dog, car horn, phone ringing,
   water running, glass breaking, etc. Shows a picture + name, and plays that sound's vibration pattern.
3. **Recognise family members' voices.** When Mom, Sister, Dad… talk, show **their photo** + name +
   their personal vibration pattern.
4. **Detect someone calling the user's name.** Show **the caller's photo AND the user's own photo**
   ("Mom → You") with a strong, unique vibration and a full-screen alert.
5. **User control.** The user sets the minimum loudness (dB), the frequency range (Hz) to consider,
   which sounds alert them, and the vibration strength.

Target user: deaf. So the UI must be **visual-first**: large text, big icons/photos, high contrast,
no information conveyed by sound only.

---

## 2. Tech stack (decisions)

| Area | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Min / target SDK | minSdk 26 (needs `VibrationEffect` amplitude API), target the latest stable SDK |
| Architecture | MVVM + unidirectional state, Kotlin Coroutines/Flow, Hilt for DI |
| Storage | Room (people, voice samples, rules, history) + DataStore (settings) + app-internal files (photos, WAV) |
| Audio capture | `AudioRecord`, 16 kHz, mono, PCM float (or 16-bit converted to float) |
| DSP / FFT | Own Kotlin FFT (radix-2, 512 points), no heavy dependency |
| General sound classification | **MediaPipe Audio Classifier + YAMNet** (`com.google.mediapipe:tasks-audio`), model `yamnet.tflite` in `assets/` |
| Voice activity detection | **sherpa-onnx** VAD (Silero VAD model) |
| Speaker (family) identification | **sherpa-onnx** speaker embedding extractor + embedding manager, a small pre-trained speaker model in `assets/` |
| Fallback speaker embedder | MediaPipe **Audio Embedder** (YAMNet embeddings) + cosine similarity (weaker; used only if sherpa-onnx integration fails) |
| Name-call detection | **Few-shot template matching: MFCC + DTW** in Kotlin (works for any language, including Bangla names). Optional later: sherpa-onnx keyword spotter |
| Vibration | `VibratorManager` (API 31+) / `Vibrator`; amplitude control when `hasAmplitudeControl()`, otherwise duty-cycle fallback |
| Background | Foreground service, type `microphone` |
| Images | Bundled vector icons (Material Symbols, Apache-2.0) for sound categories; user photos for people. **Do not download images from the web.** |

### Models to download and place in `app/src/main/assets/models/`
Get each from its **official** source and record the source URL + license in `docs/MODELS.md`:
- `yamnet.tflite`: from the MediaPipe Audio Classifier model page (float32, "latest"), plus its class-map / labels.
- `silero_vad.onnx`: from the sherpa-onnx releases (VAD model).
- One **small speaker-embedding model** from the sherpa-onnx "speaker recognition models" release page.
  Prefer a small one (≤ ~30 MB) such as a 3D-Speaker or WeSpeaker model. Record which one you picked and why.
- sherpa-onnx Android library: use the official prebuilt AAR / JNI libs + Kotlin API files, following the
  official sherpa-onnx **Android speaker-identification** and **VAD** examples.

---

## 3. Architecture overview

```
                       ┌──────────────────────── HearingService (foreground, microphone) ───────────────────────┐
 Mic → AudioEngine ──▶ │ RingBuffer (16 kHz float, ~10 s)                                                        │
                       │   ├─▶ DspProcessor (every 16 ms)  → band levels, dB, noise floor → HapticMapper → live   │
                       │   ├─▶ SoundClassifier (YAMNet, every 0.5 s) → SoundEvent                                 │
                       │   ├─▶ VadProcessor → speech segments ─┬─▶ SpeakerIdentifier → PersonEvent              │
                       │   │                                   └─▶ NameCallDetector  → NameCallEvent (+ speaker)│
                       │   └─▶ EventArbiter (priority, debounce, cooldown) → AlertPresenter + VibrationEngine    │
                       └───────────────────────────────────────────────────────────────────────────────────────┘
                                        │ StateFlow<LiveState>, SharedFlow<AppEvent>
                                        ▼
                         Compose UI (Home live view, alerts, settings, family, history)
```

### Package layout
```
com.hapticHearing/
  app/            HapticHearingApp, MainActivity, navigation
  service/        HearingService, ServiceController, notifications
  audio/          AudioEngine, RingBuffer, AudioFormat
  dsp/            Fft, Window, BandAnalyzer, NoiseFloorTracker, LoudnessMeter, Mfcc, Dtw
  ml/             SoundClassifier (MediaPipe), VadProcessor, SpeakerEmbedder (interface),
                  SherpaSpeakerEmbedder, MediaPipeSpeakerEmbedder, SpeakerIdentifier, NameCallDetector
  haptics/        VibrationEngine, HapticMapper, VibrationPatterns, PatternDesigner
  events/         AppEvent, EventArbiter, Priorities
  data/           Room entities/DAOs, repositories, SettingsDataStore, FileStore
  ui/             screens/*, components/*, theme/
  ble/            (milestone 8, optional) WristbandLink
```

---

## 4. Audio engine

- `AudioRecord` at **16 000 Hz, mono**. Audio source: prefer `MediaRecorder.AudioSource.UNPROCESSED` when
  `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` is true, otherwise `VOICE_RECOGNITION`.
  (Both avoid the automatic gain and noise suppression that would distort dB readings.)
- Read 256-sample chunks (16 ms) on a dedicated high-priority thread and write them into a lock-free **ring buffer**
  (about 10 s of audio). Each consumer reads at its own pace.
- Expose `Flow<FloatArray>` of chunks plus `readLast(nSamples)` for windowed consumers.
- Handle mic loss (another app or a phone call takes the mic): show "Microphone busy" and retry every 2 s.

---

## 5. DSP → continuous vibration (core feature)

### 5.1 Per frame (hop 256 samples = 16 ms, window 512 samples)
1. Hann window → FFT 512 → power spectrum (bin width 31.25 Hz).
2. **Frequency filter:** keep only bins inside the user's range `[minHz, maxHz]` (default 100–8000 Hz).
3. Split the kept range into **3 log-spaced bands** (low / mid / high). With the default range:
   low 100–500 Hz, mid 500–2000 Hz, high 2000–8000 Hz. Recompute the band edges when the range changes.
4. Band level in dBFS: `L = 10·log10(sum(power) + 1e-12)`.
   Approximate dB SPL: `dB = L + calibrationOffset` (default offset +100; the user can calibrate in Settings).
5. Overall level = power sum of all kept bins (also shown on screen as "≈ xx dB").

### 5.2 Background-noise suppression (MUST)
Per band, keep an adaptive **noise floor** `F`:
- If `L < F`: `F ← F + 0.2·(L − F)` (falls fast).
- Else: `F ← F + riseRate·Δt` with `riseRate = 1.5 dB/s` (rises slowly, so steady noise is absorbed within a few seconds).

A band is **active** only when **both** hold:
- `dB_band ≥ userMinDb` (user setting, default 50 dB), **and**
- `L ≥ F + noiseMarginDb` (user setting "Ignore background noise", default 8 dB, range 3–20).

This means a running fan or AC stops vibrating after a few seconds, while a voice or door slam on top of it still vibrates.

Optional extra filter: ignore a band whose level has varied less than 1.5 dB over the last 3 s (very steady hum).

### 5.3 Mapping to vibration (single phone motor)
- `excess = L − max(F + noiseMarginDb, threshold)`; `strength = clamp(excess / 30 dB, 0, 1)`, then `amplitude = 20 + 235·strength^0.7`, scaled by the user's "Vibration strength" setting.
- Smoothing: attack ~10 ms, release ~120 ms.
- **Pitch → rhythm**, using the dominant active band:
  - low → pulses at about 4 Hz (on 120 ms / off 130 ms), deep and strong
  - mid → about 8 Hz (on 60 / off 65)
  - high → continuous fast buzz (on 30 / off 30) at reduced amplitude
- Update the vibrator every **~50 ms** with a short `VibrationEffect.createWaveform(timings, amplitudes, -1)`
  (newer calls replace older ones). No active band → `cancel()`.
- **No amplitude control** (`hasAmplitudeControl() == false`): express strength through the duty cycle
  (longer "on" time = stronger).
- Target latency from sound to vibration: **< 60 ms**.

### 5.4 Wristband output
The `HapticMapper` produces a `HapticFrame(bands: FloatArray(6), dominantBand, eventId?)`. The phone motor uses it
now; the BLE wristband (milestone 8) will use the same frame later. Design for 6 motor channels from the start
(low/mid/high × front/back) even though the phone uses one.

---

## 6. General sound classification (YAMNet)

- MediaPipe `AudioClassifier` in `AUDIO_STREAM` mode (or `AUDIO_CLIPS` on 0.975 s windows), run **every 0.5 s**
  on a background dispatcher. `maxResults = 5`, `scoreThreshold = 0.2`.
- Map YAMNet labels to app **categories** with a bundled table `assets/sound_categories.json`.
  **Verify label strings against the bundled YAMNet class map.** Starting categories:

| Category | Example YAMNet labels | Priority | Default |
|---|---|---|---|
| Fire / smoke alarm | Smoke detector, smoke alarm; Fire alarm; Alarm | DANGER | On |
| Siren | Siren; Police car (siren); Ambulance (siren); Fire engine | DANGER | On |
| Car horn | Vehicle horn, car horn, honking | DANGER | On |
| Glass breaking | Glass; Shatter | DANGER | On |
| Baby crying | Baby cry, infant cry | HIGH | On |
| Doorbell | Doorbell; Ding-dong | HIGH | On |
| Knock | Knock | HIGH | On |
| Phone ringing | Telephone bell ringing; Ringtone | HIGH | On |
| Alarm clock | Alarm clock; Beep, bleep | NORMAL | On |
| Dog | Dog; Bark | NORMAL | On |
| Cat | Cat; Meow | NORMAL | Off |
| Water running | Water tap, faucet; Sink (filling or washing) | NORMAL | On |
| Microwave / kettle | Microwave oven; Whistle | NORMAL | Off |
| Speech | Speech | (routes to speaker ID, no general alert) | — |
| Music | Music | LOW | Off |

- Each category has: icon (bundled vector), display name (English + Bangla strings), vibration pattern,
  minimum confidence (default 0.40; DANGER 0.30), enabled flag, cooldown (default 10 s).
- **Debounce:** fire an event only if the category's score ≥ its minimum confidence in **2 of the last 3** windows
  AND the overall level passed the user's dB threshold during that time.
- The user can turn categories on or off and change their patterns in the **Sounds** screen.

---

## 7. Family member identification (speaker ID)

### 7.1 Enrolment (Family → Add person)
1. Name, relation (Mom / Dad / Sister / Brother / Friend / Other), **photo** (camera or gallery; crop to square;
   copy into app-internal storage).
2. Choose a **personal vibration pattern** (presets in section 9 or the pattern designer).
3. **Record voice:** a guided recorder with **8 clips × 4 s**. Show on-screen prompts for the person to read or say
   (normal talking, calling loudly, from 3 m away, with TV on). Show a level meter, and reject clips that are too quiet
   or mostly silent (VAD speech < 1.5 s).
4. Compute an embedding for each clip → store each embedding + the mean embedding (L2-normalised) in Room.
   Keep the WAV files so embeddings can be recomputed if the model changes.
5. **Test** button: the person talks, the app shows the similarity score live.

### 7.2 Live recognition
- `VadProcessor` (sherpa-onnx Silero VAD) produces speech segments. When a segment reaches **≥ 1.5 s**
  (process at most 4 s), compute its embedding.
- Cosine similarity against every enrolled person (the max over their individual clip embeddings and their mean).
  Accept the best match if `score ≥ speakerThreshold` (default 0.50, adjustable as a "Strictness" slider)
  **and** `best − secondBest ≥ 0.05`.
- Accepted → `PersonEvent(person, confidence)`: show the photo card ("Mom is talking · 82%") + the person's pattern.
  Cooldown per person: 20 s (they keep talking → no repeated alerts; just keep the card visible with a "talking" indicator).
- Not accepted, but Speech was detected → a small "Someone is talking" card (no special pattern; the continuous
  vibration already conveys it).
- **Correction:** on each person card, "Wrong person?" → pick the right person → that segment's embedding is
  added to that person's samples (improves over time).

### 7.3 Implementation notes
- `interface SpeakerEmbedder { fun embed(samples: FloatArray, sampleRate: Int): FloatArray; val dim: Int }`
  with `SherpaSpeakerEmbedder` (primary) and `MediaPipeSpeakerEmbedder` (fallback, using the MediaPipe Audio
  Embedder on YAMNet). Store `embedderId` with every embedding; re-embed from the saved WAVs if it changes.

---

## 8. Name-call detection ("Someone is calling YOU")

### 8.1 Setup (Me → My name)
1. The user's name, nicknames (e.g. "Rahim", "Rahim bhai", "Babu"), and **the user's own photo**.
2. **Record name calls:** each enrolled family member records the user's name being called **5–10 times**
   (normal and loud, near and far). Each recording is trimmed with VAD, then stored as WAV + MFCC template,
   tagged with the person who said it.
3. "Test" mode: shows the live match distance.

### 8.2 Detection (MFCC + DTW template matching)
- Candidate segments: VAD speech segments of **0.3–1.8 s** (also check the first 1.8 s of longer segments).
- Features: 13 MFCC + Δ (25 ms window, 10 ms hop, 26 mel filters, cepstral mean normalisation).
- Distance: DTW (Sakoe-Chiba band 20%) normalised by path length, against every template.
  Match when `minDistance ≤ nameThreshold`. Calibrate the default threshold automatically during setup:
  leave-one-out distances between the user's own templates vs. distances to 20 s of ordinary non-name speech;
  choose the midpoint and store it. A "Sensitivity" slider in Settings adjusts it.
- **Who is calling:** run the speaker identifier on the same segment (plus up to 1.5 s of surrounding speech).
  If no confident match, use the person whose own template matched best.
- Result → `NameCallEvent(caller: Person?, confidence)`.

### 8.3 The alert
- Full-screen alert (works on the lock screen, `showWhenLocked` / `turnScreenOn`):
  **[Caller photo] → [User's photo]**, large text "**Mom is calling you!**".
  Unknown caller → silhouette icon + "Someone is calling you!".
- The `NAME_CALL` vibration pattern (section 9), repeated up to 3 times until the user taps "Seen".
- On Android 14+, the full-screen-intent permission may not be granted to this app category. Detect that, and fall back to a
  heads-up notification + bringing the alert activity to the front when the app is visible.

### 8.4 Optional upgrade (later)
sherpa-onnx open-vocabulary keyword spotter for names that can be written in English letters. Keep it behind
the same `NameCallDetector` interface; combine both detectors with OR, logging which one fired.

---

## 9. Vibration patterns & priority

### 9.1 Event priority (the `EventArbiter` decides)
`NAME_CALL` > `DANGER` > `PERSON` > `HIGH` > `NORMAL` > continuous vibration.
- An event pattern **pauses** the continuous vibration while it plays, then continuous vibration resumes.
- A higher-priority event interrupts a lower one; equal or lower priority waits (max queue 3, drop the oldest NORMAL).
- Per-category / per-person cooldowns as described above.

### 9.2 Preset patterns (timings in ms: off, on, off, on…; amplitude 1–255)
| Name | Timings | Amplitude | Use |
|---|---|---|---|
| NAME_CALL | 0, 400, 150, 400, 150, 800 (×3) | 255 | Someone calls the user's name |
| DANGER | 0, 1000, 200, 1000, 200, 1000 | 255 | Alarm, siren, horn, glass |
| HEARTBEAT | 0, 150, 100, 150, 600 (×2) | 200 | Baby crying |
| DOORBELL | 0, 250, 120, 600 | 220 | Doorbell, knock |
| RING | 0, 100, 60, 100, 60, 100, 400 (×2) | 200 | Phone ringing |
| PERSON_A | 0, 600, 200, 600 | 200 | e.g. Mom |
| PERSON_B | 0, 150, 100, 150, 100, 150 | 200 | e.g. Sister |
| PERSON_C | 0, 600, 150, 150 | 200 | e.g. Dad |
| PERSON_D | 0, 150, 150, 600 | 200 | Others |
| GENERAL | 0, 200, 100, 200 | 160 | Other enabled sounds |

- **Pattern designer** screen: tap-and-hold to record a rhythm, preview, save, assign to a person or category.
- Every pattern has a "Test" button.

---

## 10. Screens (Jetpack Compose)

1. **Onboarding:** what the app does (pictures, not text walls), then permissions: microphone, notifications,
   full-screen alerts, ignore battery optimisation (explain why), camera (for photos, optional).
2. **Home (Live):**
   - Big on/off "Listening" switch (starts/stops the service).
   - Live meter: overall ≈dB number, 3 band bars (low/mid/high) with the user threshold line and noise floor marker.
   - A "Now hearing" card: the latest event with a big icon or photo, name, confidence and time; fades after 8 s.
   - Quick chips: "Quiet mode (danger only)", "Sensitivity".
3. **Alert screens:** a full-screen alert for NAME_CALL and DANGER; a large card/overlay for others.
4. **Family:** grid of people (photo, name, pattern). Add/Edit person, record voice, test, delete (also deletes the
   person's audio files and embeddings).
5. **Me:** user's name, nicknames, own photo, name-call recordings per family member, test.
6. **Sounds:** category list with icon, on/off, priority, pattern, confidence slider.
7. **Settings:**
   - **Minimum loudness (dB):** slider 30–90 dB (default 50), with the live level shown next to it.
   - **Frequency range (Hz):** range slider 50–8000 Hz (default 100–8000), with presets:
     "All sounds", "Voices only (300–3400 Hz)", "Low sounds only (50–500 Hz)", "High sounds only (2000–8000 Hz)".
   - **Ignore background noise:** 3–20 dB margin (default 8).
   - Vibration strength 0–100%, continuous vibration on/off (events only).
   - Speaker strictness, name sensitivity.
   - Quiet hours (danger + name call only), battery saver (YAMNet every 1 s, speaker ID only when the screen is on, etc.).
   - Calibration: "Make a sound you know is about 60 dB (normal talking at 1 m)" → set the offset.
   - Language: English / বাংলা.
8. **History:** a timeline of events (icon/photo, label, time, dB), filter by type, clear all.
9. **Debug (developer option):** replay WAV files through the full pipeline; show all scores live.

UI rules: minimum text 18 sp, primary actions ≥ 56 dp, high-contrast theme + dark theme, TalkBack labels,
**no information conveyed by sound only**, colours plus icons (never colour alone).

---

## 11. Data model (Room)

```kotlin
@Entity data class Person(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String, val relation: String, val photoPath: String?,
  val patternId: String, val isSelf: Boolean = false, val createdAt: Long)

@Entity data class VoiceSample(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val personId: Long, val wavPath: String, val embedding: ByteArray, // float32 little-endian
  val embedderId: String, val kind: String /* "voice" | "name_call" */, val createdAt: Long)

@Entity data class NameTemplate(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val speakerPersonId: Long?, val wavPath: String, val mfcc: ByteArray, val frames: Int)

@Entity data class SoundCategoryRule(
  @PrimaryKey val categoryId: String, val enabled: Boolean, val priority: String,
  val patternId: String, val minConfidence: Float, val cooldownSec: Int)

@Entity data class EventLog(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val time: Long, val type: String, val label: String, val personId: Long?,
  val confidence: Float, val levelDb: Float)

@Entity data class CustomPattern(@PrimaryKey val id: String, val name: String,
  val timings: String, val amplitudes: String)
```
Settings in DataStore: `minDb, minHz, maxHz, noiseMarginDb, vibrationStrength, continuousEnabled,
speakerThreshold, nameThreshold, calibrationOffset, quietHours, batterySaver, language`.

---

## 12. Android platform requirements

- Manifest permissions: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `VIBRATE`,
  `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` (optional auto-start), `CAMERA` (optional).
- `HearingService`: `foregroundServiceType="microphone"`. It must be **started while the app is visible**
  (Android 14+ rule). Persistent notification: "Listening for sounds" + Pause / Stop actions.
- Auto-restart after reboot: show a notification "Tap to resume listening" (the microphone service can't start from background).
- Ask the user to exclude the app from battery optimisation; detect aggressive OEM battery killers and show
  manufacturer-specific instructions.
- Release the mic while a phone call is active; resume afterwards.

---

## 13. Milestones (build in this order)

| # | Milestone | Acceptance criteria |
|---|---|---|
| M0 | Project setup: Compose, Hilt, Room, DataStore, navigation, theme, CI unit tests | App launches; empty screens navigate; `./gradlew test` passes |
| M1 | Audio engine + live meter | Home shows a live ≈dB number and 3 band bars; mic permission flow works |
| M2 | DSP + continuous vibration + noise floor + dB/Hz settings | Clap → vibration < 60 ms; a steady fan stops vibrating within 5 s while talking over it still vibrates; changing the Hz range to "Voices only" stops a low hum; the dB slider works |
| M3 | Foreground service | Works with the screen off and the app in the background for 1 h; Pause/Stop from the notification |
| M4 | YAMNet classification + categories + icons + event patterns + arbiter | Doorbell/knock/alarm sound clips (played from another device) trigger the correct icon + pattern; categories can be toggled; history logs events |
| M5 | Family enrolment + speaker identification | 2 enrolled people are correctly identified ≥ 80% of the time in a quiet room; unknown voices show "Someone is talking"; correction works |
| M6 | Name-call detection + caller/user dual-photo alert | Calling the user's name triggers the full-screen alert with the correct caller photo in ≥ 70% of tries; ordinary conversation causes < 1 false alert per 10 min |
| M7 | Polish: history, pattern designer, quiet hours, battery saver, Bangla strings, accessibility pass, debug screen | TalkBack works on all screens; battery use measured and noted in `docs/BATTERY.md` |
| M8 (optional) | BLE output to the ESP32 wristband | Sends `HapticFrame` packets (section 14) at 50 Hz; wristband motors follow the phone |

---

## 14. BLE wristband protocol (for M8)

- Custom GATT service, one write-without-response characteristic.
- Packet (9 bytes): `0xA5 | seq | m0 m1 m2 m3 m4 m5 | eventId`
  - `m0–m2` = front low/mid/high, `m3–m5` = back low/mid/high (0–255).
  - `eventId`: 0 = none, 1 = NAME_CALL, 2 = DANGER, 3 = PERSON, 4 = HIGH, 5 = NORMAL (the wristband plays its own pattern).
- Rate 50 Hz while sound is active, 2 Hz keep-alive otherwise.

---

## 15. Testing

- **Unit tests:** FFT (1 kHz sine → peak in the right bin), band mapping, dB maths, noise-floor tracker (stationary white
  noise → band inactive within 5 s; a burst 15 dB above it → active), HapticMapper, DTW (identical → 0;
  time-stretched → small), EventArbiter priority/cooldown, cosine similarity.
- **Pipeline tests:** put test WAVs in `app/src/test/resources/audio/` (generated tones/noise, plus freely licensed
  recordings with their source noted) and replay them through the pipeline in the Debug screen and in instrumented tests.
- **Manual test script** in `docs/TEST_PLAN.md`: fan-noise test, doorbell, two family voices, name calls from 1 m / 3 m / another room, TV on.

---

## 16. Privacy & safety

- 100% offline: **no INTERNET permission** in release builds (add it only to debug builds if a tool needs it).
- Voice recordings, embeddings and photos are stored in app-internal storage only; "Delete all my data" in Settings.
- The enrolment screen reminds the user to get each family member's permission before recording their voice.
- Show confidence honestly ("Mom, probably"). Never claim certainty. Danger alerts must favour false alarms over misses.
- The app is an aid, **not** a certified safety device. Say this in onboarding: keep real smoke alarms with visual/vibrating alerts.

---

## 17. Known limitations (state in the app's Help page)

- Phone microphones aren't calibrated: dB values are approximate until calibrated.
- Similar voices (sisters, mother/daughter) may be confused; more enrolment clips help.
- Several people talking at once, or loud TV, reduce speaker and name accuracy.
- One phone motor can't show direction; the wristband (M8) adds that.
