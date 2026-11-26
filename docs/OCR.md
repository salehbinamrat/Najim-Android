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
