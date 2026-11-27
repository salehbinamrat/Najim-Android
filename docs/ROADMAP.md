# خطة التطوير
| المرحلة | النطاق | بوابة القبول |
|---|---|---|
| 00 | التأسيس | JVM وLint وبناء APK تجريبي |
| 01 | اكتشاف Wi-Fi والبوابة | بوابات مختلفة؛ عدم إرسال الكروت خارج الشبكة؛ تبديل الشبكات؛ Wi-Fi بلا إنترنت |
| 02 | المصادقة | اسم مستخدم فقط وكلمة مرور منفصلة وCHAP وHTTPS حيث تتاح؛ منع النجاح الوهمي |
| 03 | الأخطاء | تمييز RADIUS timeout والوقت والبيانات والجلسات والصلاحية |
| 04 | OCR والكاميرا | نماذج محلية؛ رفض الإذن؛ أرقام عربية وإنجليزية؛ قياس الدقة |
| 05 | QR والصور | إدخال بموافقة المستخدم؛ لا إرسال خارجي |
| 06 | الجلسة | الحالة والرصيد والوقت حيث تتيحها البوابة؛ خروج مؤكد |
| 07 | الحفظ الآمن | Android Keystore؛ تشفير؛ منع تسريب الأسرار |
| 08 | الواجهات | RTL وأحجام مختلفة والوضع الليلي |
| 09 | الوصول | TalkBack وتوجيه صوتي اختياري لا يقرأ الأسرار افتراضيا |
| 10 | الأداء | قياس الذاكرة والطاقة وزمن القراءة |
| 11 | التوافق | اختبار RouterOS 6/7 وUM وFreeRADIUS ميدانيا |
| 12 | التدقيق | اختبارات تكاملية وأمنية وتنظيف الكود |
| 13 | الإصدار | Full/Lite × ARM64/ARM32 بعد إثبات التوافق، توقيع وSHA-256 |

لا نجاح مصادقة من HTTP 200 أو اتصال الإنترنت وحدهما. لا تغيير لإعدادات الراوتر. مسارا Core وRelease متوازيان؛ الاختبارات قبل البناء. لا دمج عند فشل أو غياب الفحوصات.


## 2026-11-27 — approved scope revision
- Store at most five new saved voucher records globally; no automatic eviction, preserve pre-limit data for explicit deletion. Network profile capacity unchanged.
- Phase 08A: unified Material 3 blue/navy day/night palette, shape system, responsive foundation width and shared header; preserve existing flow and controllers. Phase 08B: staged navigation and screen extraction with explicit state/lifecycle checks. Phase 08C: dashboard/card refinements and device-size verification.

- 2026-11-27 correction: five vouchers per network profile, manual or automatic oldest replacement (profile-specific persistent setting), replacing the previous global limit. Preserve local encryption, explicit save approval, atomicity and separate networks.

## Phase 08B1 — navigation shell
- Three bottom destinations (home/session, login/scanning, saved cards/network profiles). Existing controllers unchanged; shared ephemeral selected profile and form state, no navigation dependency or saved secret back stack. Leaving login clears credentials and consent. Confirmed saved-card selection fills the masked form and opens login without submitting. Camera disposed when section leaves composition; gallery activity keeps login section and navigation locked until finished; navigation locked during active login/session/storage operations. Device and large-font layout testing still pending. Separate settings and refined scan/dashboard screens follow in 08B2/08C.

## Phase 08B2 — component extraction and appearance settings
- Four bottom destinations: home/session, login/scanning, cards/network profiles, settings. Extract home, cards and scan panel from shell without changing auth or storage controllers. Existing form scope and explicit confirmations retained; reset password and per-attempt consent when accepting a scan. Android back returns home when idle; system exit remains possible while busy with Activity lifecycle cancellation.
- System/light/dark mode stored in private non-sensitive preferences, read and committed on serial worker, fallback to system on invalid value; no credentials or consent in preferences. Report read/write failure; apply selected mode only after successful commit. Settings includes factual storage/privacy guidance. Device appearance, large-font layout and lifecycle tests remain pending.
- Added four JVM appearance policy tests. No new dependencies or permissions.

## Phase 08C — responsive dashboard and visual refinement
- Session balance and traffic separated into adaptive one/two-column metric groups using measured content width and font scale; missing values remain unavailable, no inferred quota, percent or countdown. Masked account and status are distinct from internet connectivity.
- Saved cards displayed newest successful save first, oldest labeled explicitly; persisted eviction order unchanged. Show per-profile count and retention mode, preserve all confirmation and transport safeguards. Scan guidance and selected-profile card improve clarity; no automatic login. Short bottom navigation label improves compact width.
- Seven JVM layout policy tests; physical/emulator display validation has not been performed. Check 320/360/390/430/600dp, portrait and landscape, system/forced light/dark, font scales 1.0/1.3/2.0, long values and names, TalkBack focus, keyboard and camera/photo-picker lifecycle. No clipping, horizontal overflow, hidden actions, fabricated metrics or credential leakage accepted. Phase 09 adds accessibility and optional audio; field UI acceptance remains outstanding.

## Phase 09A — accessible controls and optional non-verbal feedback
- Radio/checkbox rows are single accessible touch targets with labels, roles and 48dp minimum height; grouping for scan/protocol/profile/retention/settings. Polite live regions for login, logout and storage messages; raw scan candidates are visually reviewable but replaced with a generic accessibility description. Password fields retain platform password semantics. Full TalkBack validation and explicit accessible private review remain pending; third-party screen readers and IMEs are outside app guarantees.
- Optional short local tones default off, persisted alongside non-sensitive appearance preferences; no replay, background tones, raw credentials, TTS or network audio. Foreground gate pauses/releases audio, confirmation vs caution vs failure distinct; legacy evidence never mapped to confirmed tone. Device audio behavior and Arab-language offline spoken guidance remain Phase 09B acceptance work.
- Eight JVM tests cover policy and foreground gate; no new dependency or permission. Physical accessibility testing still required.

## 2026-11-27 — approved burgundy and white identity
- Day: primary #761C40, background #FFFFFF, surface #FFFBFC, muted rose support. Night: primary #FFB2CA, background #161216, surface #1C171B, burgundy containers. Red error roles separate from brand; text continues to identify errors. Explicit Material 3 roles including surface containers, inverse and tertiary. Same shape and typography rules. Visual device verification outstanding.

## Phase 09B — optional Arabic spoken guidance
- Speech is off by default, separately opted into under audio feedback, using only fixed public phrases for login/logout/accepted scan. Platform TTS must report an installed Arabic voice that does not require a network; no automatic download or cloud fallback. If the engine is not ready or rejects speech, local tone fallback. Third-party engine behavior and actual offline operation depend on device; no credentials, account IDs, images or router messages are passed. Release engine on pause or disable; no queued replay. Package visibility query for TTS service only, no new permission or dependency.
- JVM guidance tests and static color contrast verification; Arabic voice availability, TalkBack, actual audio routing, and device layout remain field acceptance work. No changes to authentication or five-card-per-profile retention.

- Static contrast audit: 11 foreground/background role pairs per theme, all >= 4.5:1; minimum day 6.46:1, night 6.89:1. Primary pairs day 10.45:1, night 8.36:1. This is not a rendered-device accessibility test. The earlier tone-only gate is replaced by a single guidance gate; its tests are migrated.

## Phase 10A — camera and recognition performance
- Baseline code audit: camera bitmap copy and ML Kit candidate extraction previously used main-thread callbacks. Move bitmap copy onto a process-owned serial CameraX executor and pure candidate extraction onto a separate serial executor. Keep CameraX KEEP_ONLY_LATEST, existing 250ms throttle and three independent matching frames, QR restrictions and manual confirmation. Main thread owns recognizer lifecycle, stability and UI publishing; synchronized gate covers copy through final callback, including disposal races. Release CameraX image before ML inference; recycle bitmap on stale or completed result. No new app-owned frame queue; per-session admission, not global inference limit across rapid recreation.
- Limit pre-copy dimensions to 2,073,600 pixels and valid rotation; reject excessive OCR output before constructing a full flattened list, QR list cap remains 16. Seven JVM tests added (concurrent admission/stop and frame bounds). No new library or permission.
- Baseline/after timing, peak PSS, UI jank, CPU and battery are NOT measured yet; see docs/PERFORMANCE.md for device protocol. Keep accuracy and three-frame stability; no claimed acceleration until measured. Actual CameraX/ML Kit lifecycle and low-memory behavior require device tests.

## Phase 10B — bounded local work and lifecycle
- Coalesce profile catalog invalidations during an active operation into at most one follow-up refresh; hold ownership until callback, clear visible catalog immediately, discard stale result/credential fill and cancel pending refresh on stop. Initial discovery triggers one catalog reload; stop callback no longer queues decryption. No plaintext catalog cache or changes to encrypted format, authentication or retention. An atomic write already begun may finish.
- Gallery stream checks cooperative cancellation before/after reads, before decode and after bitmap creation; discard partial input and recycle abandoned bitmap. Cancel gallery on foreground network change, bound process-owned gallery executor to one running plus one queued job; rejection returns existing error state. Cancellation cannot interrupt a provider blocked inside read/open or native decode; no hard timeout claimed. Existing image size and accuracy safeguards unchanged.
- Seven new JVM tests: four catalog lifecycle/coalescing and three cancellation tests. Device performance measurements and actual provider behavior still pending. No new dependency or permission.

## Phase 10C — bounded gateway work
- Add a monotonic 15-second cooperative per-exchange budget checked before and after blocking calls and each response chunk. Retain 5-second connect/read socket timeouts and 64KiB response limit; no hard overall timeout guaranteed for blocked platform calls, no automatic retry. Cancellation during body read discards partial data. Existing login/logout evidence rules, network binding, TLS, HTTP consent and endpoint restrictions unchanged. A POST may already have reached the router on failure; no repeat.
- Bound process-wide serial gateway executor to one active plus one queued operation across Activity recreation. Existing rejection handling returns failure/unverified rather than queueing indefinitely. Six additional JVM transport tests with injected monotonic clock and cancellation. No new dependency, permission, storage format or UI change.
- Phase 10 code improvements ready for CI; actual device performance measurements remain pending. Further storage optimization must be justified by measurements; retain authenticated full-document read and atomic write rather than introducing plaintext caching or compromising verification. Proceed to Phase 11 controlled RouterOS compatibility fixtures after CI passes.

## Phase 11A — portal evidence hardening and compatibility preparation
- Correct session semantics: configured session-timeout is not remaining time; generic balance/traffic labels require an explicit data unit; byte-labelled fields retain raw byte support. Missing or ambiguous values remain unknown. Hidden descendant evidence is rejected for account headings, table values and error markers. Login form must expose a usable username, reject disabled credentials and form overrides; no relaxed TLS, redirect, CHAP or account-verification checks.
- Ten JVM regression tests added. Synthetic template compatibility matrix and lab checklist in docs/ROUTEROS-COMPATIBILITY.md. RouterOS 6/7, User Manager and FreeRADIUS actual deployment testing is NOT yet verified; no configuration modifications authorized or performed. Next stage 11B expands whole-flow synthetic scenarios; field tests remain a release acceptance gate.

- Phase 11A CI correction: run 45959484841 failed both jobs at one regression assertion (369 tests, 1 failure). Validate decoded wholeText rather than normalized text for account identity; preserve original assertion and add three tests. No merge until corrected head passes.

## Phase 11B — whole-flow compatibility regressions
- Revalidate selected Android network after baseline/form/error/status parsing before publishing evidence. Preserve decoded error text to avoid converting zero-width characters into a known rejection; no weakened authentication or save rules. Ten new synthetic integration-style JVM tests exercise all protocols and account authorization, error cases and same-IP network changes. No new dependencies or permissions. Field acceptance still outstanding; no version-specific compatibility claim.
