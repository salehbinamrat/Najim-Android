# Performance acceptance — Phase 10

Baseline commit: 06cde4a8d1414db4cdacaf955a3f97d7ba90af96 (Phase 09B).
Compare the candidate build against baseline on the same device, Android version, ABI, build type, network, lighting and printed synthetic test cards. No actual subscriber credentials.

| Metric | Protocol | Acceptance |
|---|---|---|
| Cold launch | 10 cold starts; record Activity displayed time via adb shell am start -W, report median and p95 | No regression |
| UI responsiveness | 30s scanning and switching sections, adb shell dumpsys gfxinfo com.najim.android reset / framestats; compare slow frames | No regression; reduced UI blocking expected |
| Scan latency | 20 OCR and 20 QR attempts, time from first stable framing to candidate; report median and p95, misses and false candidates separately | No loss of correct results or stability; no auto-login |
| Memory | adb shell dumpsys meminfo com.najim.android at idle, after scan, after 20 open/close cycles and after 30s idle | No retained growth beyond normal caches; no OOM |
| Battery / thermal | Same 10-minute scan scenario, brightness and radio state; measure supported device battery/thermal stats; note measurement noise | No regression |
| Lifecycle | Rapid camera open/close, switch OCR/QR, background/resume, gallery cancellation, orientation change | No stale candidate, crash, or leaked camera |
| Data safety | Leading zeros, ambiguous input, invalid QR, failed login, profile switch | Preserve previous behavior |

Use a low-memory ARM32 device and ARM64 device where available, include Android 7/API 24 and current Android. Report hardware and sample counts. Do not compare debug vs release. CI JVM tests are not substitutes for these measurements. Baseline and candidate results: **not measured**. No numerical improvement is asserted.


Phase 10B adds stress cases: 50 consecutive catalog invalidations during a pending operation (one follow-up read at most), foreground network changes during gallery import, Activity stop with pending refresh, repeated recreation while a provider read is blocked (bounded gallery queue). Verify no credential fill after invalidation and no storage read launched by stop callback. Report actual measurements separately; provider blocking cannot be forcibly interrupted by cooperative checks.


Phase 10C: test stalled and slow-drip local gateway responses, cancellation during read, slow POST response and repeated Activity recreation. Verify no false confirmation or automatic retry, per-exchange 15s cooperative budget, existing 5s socket timeouts, one running plus one queued gateway job, and independent status evidence. Record actual durations; blocked platform I/O may exceed cooperative budget. No physical network or device measurements yet.
