# Bundled models

None are bundled yet (M0–M3 need no ML). They go in `app/src/main/assets/models/`, each from its official source
with its URL and license recorded here.

| File | Milestone | Source | License | Notes |
|---|---|---|---|---|
| `yamnet.tflite` + class map | M4 | MediaPipe Audio Classifier model page (float32, latest) | Apache-2.0 | |
| `silero_vad.onnx` | M5 | sherpa-onnx releases (VAD models) | MIT | |
| speaker embedding model (≤ ~30 MB, 3D-Speaker or WeSpeaker) | M5 | sherpa-onnx "speaker recognition models" release | per model | Record which one and why |
| sherpa-onnx Android AAR / JNI libs + Kotlin API | M5 | sherpa-onnx GitHub releases / Android examples | Apache-2.0 | |
