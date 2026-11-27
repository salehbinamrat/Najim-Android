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
