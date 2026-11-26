# Najim Android
تطبيق مستقل لمشتركي MikroTik. المرجعية الوظيفية: [NetPass](https://github.com/AbdulAziz-Hatem/netpass/commit/d401d50121afe4f09992d4f838c2c48689615d09).
## المرحلة 00
واجهة عربية RTL وفحص صيغة الإدخال فقط. لا مصادقة ولا OCR ولا اتصال أو تخزين للأسرار بعد.
## البناء
JDK 17، Gradle 8.11.1، AGP 8.10.1، Kotlin 2.2.21، Android SDK 35.
```sh
gradle :app:testDebugUnitTest :app:lintDebug
gradle :app:assembleDebug
```
CI يستخدم Gradle محددا. لم يرفق Gradle Wrapper بعد؛ يولد رسميا عند توفر Gradle. الحزمة الحالية تجريبية وليست إصدار إنتاج. لا دمج قبل نجاح Android Core وAndroid Release Preflight على نفس SHA.
انظر docs/ROADMAP.md. لا نسخ لأكواد أو أصول NetPass. لا رخصة توزيع محددة حتى اختيار المالك.
