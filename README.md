# Najim Android
تطبيق مستقل لمشتركي MikroTik. المرجعية الوظيفية: [NetPass](https://github.com/AbdulAziz-Hatem/netpass/commit/d401d50121afe4f09992d4f838c2c48689615d09).
## المرحلة 02 — تسجيل الدخول التجريبي
اكتشاف Wi-Fi والبوابة IPv4، واجهة عربية RTL، مصادقة محلية تجريبية HTTPS PAP وHTTP PAP وHTTP CHAP بموافقة صريحة. التحقق من أدلة الجلسة لا يثبت توفر الإنترنت. لا OCR ولا تخزين للأسرار؛ التوافق الميداني غير مثبت.
## البناء
JDK 17، Gradle 8.11.1، AGP 8.10.1، Kotlin 2.2.21، Android SDK 35.
```sh
gradle :app:testDebugUnitTest :app:lintDebug
gradle :app:assembleDebug
```
CI يستخدم Gradle محددا. لم يرفق Gradle Wrapper بعد؛ يولد رسميا عند توفر Gradle. الحزمة الحالية تجريبية وليست إصدار إنتاج. لا دمج قبل نجاح Android Core وAndroid Release Preflight على نفس SHA.
انظر docs/ROADMAP.md. لا نسخ لأكواد أو أصول NetPass. لا رخصة توزيع محددة حتى اختيار المالك.
