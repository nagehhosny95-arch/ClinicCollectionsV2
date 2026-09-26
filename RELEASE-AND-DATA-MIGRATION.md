# Release, updates, and old-data migration

## Normal updates after V5

Every production APK must keep both values unchanged:

- Application ID: `com.nageh.cliniccollections`
- Release signing key: the same private keystore for every release

Increase `CLINIC_VERSION_CODE` for each release. Android then installs the new APK over the
old one and Room migrates the database without deleting user data.

Create one release keystore once and keep two encrypted copies outside the repository:

```bash
keytool -genkeypair -v -keystore clinic-release.jks -alias clinic \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 clinic-release.jks > clinic-release.jks.base64
```

Add these GitHub repository Actions secrets:

- `CLINIC_KEYSTORE_BASE64`: contents of `clinic-release.jks.base64`
- `CLINIC_STORE_PASSWORD`
- `CLINIC_KEY_ALIAS` (for the example: `clinic`)
- `CLINIC_KEY_PASSWORD`

Run **Test and build Android APK** from GitHub Actions. Enter a version code higher than the
APK already installed. Download the `advance-medical-release-*` artifact and keep the
keystore forever; losing it means Android will reject future in-place updates.

## One-time rescue from the old debug APK

The old GitHub debug builds may have been signed by a temporary debug key. A differently
signed V5 APK cannot update that installation. Do not uninstall the old app until its data
has been extracted and converted.

1. On the phone enable Developer options and USB debugging, connect it to a desktop, and
   confirm the USB debugging prompt.
2. Verify that the old debug build allows `run-as`:

   ```bash
   adb shell run-as com.nageh.cliniccollections id
   ```

3. Stop the old app so SQLite flushes its write-ahead log, then extract the database while
   the old app is still installed:

   ```bash
   adb shell am force-stop com.nageh.cliniccollections
   adb exec-out run-as com.nageh.cliniccollections \
     cat databases/clinic_collections.db > clinic_collections_old.db
   ```

4. Convert it to the backup format understood by V5:

   ```bash
   python3 tools/convert_legacy_db.py \
     clinic_collections_old.db clinic-collections-backup.json
   ```

5. Keep both files in a safe place and open the JSON on the desktop to confirm it contains
   the expected clinic and invoice counts.
6. Only now uninstall the old app, install the signed V5 release APK, open **About → Restore
   backup**, and choose `clinic-collections-backup.json`.
7. Confirm the invoice count and several clinic balances before deleting either backup file.

If step 2 says the package is not debuggable, stop: do not uninstall. That installation
requires the exact old signing key or a device-specific data extraction method.
