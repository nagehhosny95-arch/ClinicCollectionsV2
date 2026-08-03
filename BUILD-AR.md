# بناء ملف APK — دليل عملي

المشروع بعد الإصلاح جاهز للبناء بالكامل. أمامك طريقتان.

---

## الطريقة الأولى: GitHub Actions (الأسهل — بدون تثبيت أي شيء)

سيبني السيرفر ملف `app-debug.apk` نيابةً عنك وتنزّله جاهزاً. مجاني للمستودعات العامة.

1. افتح <https://github.com/new> وأنشئ مستودعاً جديداً باسم `ClinicCollections`.
2. اضغط **uploading an existing file**، ثم اسحب **محتويات** مجلد `ClinicCollections`
   (وليس المجلد نفسه). لازم تشوف `gradlew` و `settings.gradle.kts` و `app/`
   و `.github/` في جذر المستودع مباشرةً.

   > مهم: تأكد أن `.github/workflows/build-apk.yml` مرفوع. GitHub أحياناً يخفي
   > المجلدات التي تبدأ بنقطة أثناء السحب — إن لم يظهر، ارفعه يدوياً عبر
   > **Add file → Create new file** بنفس المسار والاسم.

3. اضغط **Commit changes**. سيبدأ البناء تلقائياً.
4. افتح تبويب **Actions** → اختر آخر تشغيل باسم *Build Debug APK*.
5. انتظر ٥ إلى ٨ دقائق حتى تظهر علامة ✅.
6. انزل لأسفل الصفحة إلى قسم **Artifacts** → نزّل `app-debug`.
7. فك الضغط عن الملف المنزَّل، ستجد بداخله `app-debug.apk`.

### التثبيت على الهاتف
انقل الملف إلى الهاتف، افتحه، واسمح بـ **تثبيت من مصادر غير معروفة**
عند الطلب. يعمل على Android 10 (API 29) فما فوق.

### بعد التثبيت — إعدادان مهمان
- **الإشعارات:** التطبيق سيطلب الإذن عند أول تشغيل على Android 13 فأحدث. اقبله.
- **المنبهات الدقيقة:** الإعدادات ← التطبيقات ← Clinic Collections ←
  المنبهات والتذكيرات ← تفعيل. بدونها ستصل التذكيرات لكن بدقة توقيت أقل.

---

## الطريقة الثانية: Android Studio على جهازك

1. ثبّت أحدث نسخة مستقرة من Android Studio.
2. **File → Open** واختر مجلد `ClinicCollections`.
3. انتظر انتهاء Gradle Sync (سيحمّل Gradle 8.11.1 و SDK 35 تلقائياً — يحتاج إنترنت).
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. الملف الناتج في: `app/build/outputs/apk/debug/app-debug.apk`

من الطرفية مباشرةً:

```bash
cd ClinicCollections
./gradlew assembleDebug          # على ويندوز: gradlew.bat assembleDebug
./gradlew testDebugUnitTest      # لتشغيل اختبارات منطق الحسابات
```

---

## نسخة موقّعة للنشر (لاحقاً)

**Build → Generate Signed Bundle / APK → APK**، أنشئ keystore واحتفظ به في مكان آمن —
هو نفسه المطلوب لكل تحديث مستقبلي. فقدانه يعني عدم قدرتك على تحديث التطبيق.

---

## متطلبات البناء

| العنصر | الإصدار |
|---|---|
| JDK | 17 |
| Gradle | 8.11.1 (عبر الـ wrapper المرفق) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 (Android 10) |
