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
