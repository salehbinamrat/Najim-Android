# Najim Android
تطبيق مستقل لمشتركي MikroTik، باللغة العربية وواجهة RTL عنابية وبيضاء مع وضع ليلي. المرجعية الوظيفية: [NetPass](https://github.com/AbdulAziz-Hatem/netpass/commit/d401d50121afe4f09992d4f838c2c48689615d09).

## الحالة
اكتشاف Wi-Fi والبوابة، مصادقة HTTPS PAP وHTTP PAP/CHAP بموافقة، قراءة OCR وQR والصور، إدارة الجلسة، وخمسة كروت لكل شبكة بتخزين مشفر وحذف يدوي أو تلقائي. التوافق الميداني غير مثبت بعد.

## البناء التجريبي
JDK 17، Gradle 8.11.1، AGP 8.10.1، Kotlin 2.2.21، Android SDK 35.
- Full: OCR وQR مدمجان.
- Lite: OCR عبر نموذج محلي توفره خدمات Google Play، وقد يلزم تنزيله؛ QR مدمج.
- ARM32: armeabi-v7a. ARM64: arm64-v8a.

```sh
bash .github/scripts/verify-variants.sh
gradle :app:assembleFullArm32Debug :app:assembleFullArm64Debug :app:assembleLiteArm32Debug :app:assembleLiteArm64Debug
```
CI يستهدف أربعة APK تجريبية بتوقيع debug فقط وSHA-256. لا دمج قبل نجاح المسارين. لا نشر إنتاج قبل توقيع المالك واختبارات الأجهزة والراوترات. لا Gradle Wrapper حتى الآن؛ CI يستخدم Gradle محددا.
انظر docs/ROADMAP.md وdocs/RELEASE.md. لا نسخ لأكواد أو أصول NetPass. رخصة التوزيع بانتظار اختيار المالك.
