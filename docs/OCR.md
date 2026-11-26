# Phase 04A — offline OCR foundation
Reference: NetPass VoucherAnalyzer (behavior only, independent source) and Google ML Kit:
https://developers.google.com/ml-kit/vision/text-recognition/v2/android
https://developers.google.com/ml-kit/vision/text-recognition/v2/languages

- Bundled ML Kit Latin model 16.0.1; no first-use model download required. Official Android minimum API 23; project stays API 24. Do not claim Arabic-script image recognition: Arabic-Indic and Persian digits are normalized only if present in recognized text; actual recognition accuracy requires device samples and potentially a separate Arabic-capable engine.
- Conservative digit-only candidate suggestion from a whole numeric line or explicitly labeled line, preserve leading zeros, never join fragments or guess OCR substitutions. Distinct numeric candidates are ambiguous. Default scan bounds 4–32 are provisional, configurable in domain code; manual login remains unchanged, including alphanumeric codes.
- Three consecutive matching candidates for live scanning; reset on ambiguous/no result or session restart. A stable candidate is not proof of validity; UI must require confirmation and never auto-submit.
- Local recognizer adapter accepts InputImage, serializes inference, defers closing until completion and returns a release callback on all normal outcomes; no image saving or credential logging. Camera integration must close every ImageProxy in completion, stop on lifecycle, and handle failures.
- 21 JVM policy tests. No physical camera or OCR model inference tests yet. CameraX preview, runtime permission, frame ownership, candidate confirmation and accuracy measurements are Phase 04B. No camera permission or active scanner UI in 04A.
- Retain mandatory CI tests/lint before APK assembly. Bundled model increases APK size; measure it after CI, not yet an optimized final Full/Lite split.

## Phase 04B — live CameraX integration
- Pin CameraX core/camera2/lifecycle/view 1.4.2 as a matched set; ImageProxy.toBitmap introduced in 1.3 (https://developer.android.com/reference/androidx/camera/core/ImageProxy). API 24 unchanged. Request CAMERA only after user action; optional hardware and manual fallback.
- Lifecycle-bound rear camera, keep-only-latest analysis, one in-flight inference and 250 ms minimum interval; request 1280x720, reject frame copies above 2,073,600 pixels. Copy selected frame before releasing ImageProxy; release bitmap after inference. Frame copy runs on main thread at bounded rate initially; profile on low-end devices before optimizing. No saving images.
- Three matching frames produce a candidate and stop further analysis; UI disposes camera preview, explicitly confirms or rescans; only confirmed number populates username, never submits login. Clear old password and HTTP consent. Activity's existing onStop key reset clears candidate and closes preview.
- Five JVM frame-gate tests; device tests needed for permission denial/regrant, background/recreation, camera errors, memory, rotation, low light and actual recognition. No inference or camera tests claimed by JVM suite.
- Existing limitations on Arabic-script recognition remain; no new OCR engine or final ABI split in this change.
