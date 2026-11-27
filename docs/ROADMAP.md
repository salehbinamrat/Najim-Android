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
