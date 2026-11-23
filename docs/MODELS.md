# Bundled models

Models live in `app/src/main/assets/models/`, each from its official source
with its URL and license recorded here.

| File | Milestone | Source | License | Notes |
|---|---|---|---|---|
| `yamnet.tflite` (4.1 MB, labels embedded as `yamnet_label_list.txt`, 521 classes) | M4 ✅ | https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/latest/yamnet.tflite (downloaded 2026-11-23) | Apache-2.0 | `SoundCategoriesTest` checks every label in `sound_categories.json` against the embedded list |
| `silero_vad.onnx` | M5 | sherpa-onnx releases (VAD models) | MIT | |
| speaker embedding model (≤ ~30 MB, 3D-Speaker or WeSpeaker) | M5 | sherpa-onnx "speaker recognition models" release | per model | Record which one and why |
| sherpa-onnx Android AAR / JNI libs + Kotlin API | M5 | sherpa-onnx GitHub releases / Android examples | Apache-2.0 | |
