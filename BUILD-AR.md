# بناء واختبار Advance Medical Collections V5 على GitHub

## أول مرة

1. ارفع **محتويات المشروع** إلى مستودع GitHub بحيث تكون الملفات `gradlew` و`app/`
   و`.github/` في جذر المستودع.
2. افتح **Actions → Test and build Android APK**. التشغيل يبدأ تلقائيًا بعد الرفع، أو اضغط
   **Run workflow**.
3. GitHub ينفذ:
   - اختبارات الحسابات والفلاتر والنسخ الاحتياطي.
   - Lint وبناء Debug APK.
   - اختبار ترقية قاعدة البيانات 3 → 4 والاسترجاع على Android Emulator.
4. بعد ظهور العلامة الخضراء، ستجد `advance-medical-debug` في **Artifacts**. هذه نسخة تجربة
   فقط وليست النسخة الثابتة للتحديثات.

## إعداد APK ثابت يقبل التحديث بدون مسح البيانات

أنشئ مفتاح Release مرة واحدة فقط، ثم أضف القيم الأربع التالية في:
**Repository Settings → Secrets and variables → Actions**:

- `CLINIC_KEYSTORE_BASE64`
- `CLINIC_STORE_PASSWORD`
- `CLINIC_KEY_ALIAS`
- `CLINIC_KEY_PASSWORD`

التفاصيل وأمر إنشاء المفتاح موجودة في `RELEASE-AND-DATA-MIGRATION.md`. لا ترفع ملف المفتاح
أو كلمات المرور داخل المشروع.

بعد إضافة الأسرار شغّل Workflow يدويًا، واكتب:

- `version_code`: رقم أكبر من كل نسخة سابقة، مثل 5 ثم 6 ثم 7.
- `version_name`: اسم ظاهر مثل `5.0.0` ثم `5.0.1`.

نزّل Artifact باسم `advance-medical-release-*`. هذا هو APK الذي تثبته وتستخدمه للتحديثات
القادمة. كل نسخة مستقبلية يجب أن تستخدم نفس المفتاح وVersion Code أكبر.

## مهم جدًا قبل استبدال النسخة القديمة

إذا كانت النسخة القديمة Debug وترفض التحديث، لا تمسحها مباشرة. اتبع قسم **One-time rescue
from the old debug APK** في `RELEASE-AND-DATA-MIGRATION.md` لاستخراج البيانات وتحويلها إلى
Backup، ثم استرجعها من **About → Restore backup** داخل V5.

بعد تثبيت V5، أنشئ Backup من صفحة About قبل أي تحديث مهم أو تغيير هاتف.
