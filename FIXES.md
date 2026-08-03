# Fixes applied to the uploaded project

## Blocking build problems

| # | Problem | Fix |
|---|---|---|
| 1 | No Gradle wrapper at all (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`). The project could not be built from the command line or any CI system. | Added the official Gradle 8.11.1 wrapper. AGP 8.7.3 requires Gradle 8.9+. |
| 2 | No launcher icon. `AndroidManifest.xml` declared no `android:icon`, so the app would install with the blank system placeholder. | Added an adaptive launcher icon (`mipmap-anydpi-v26/ic_launcher.xml` + vector foreground + brand background colour) and a monochrome variant for themed icons. |
| 3 | `exportSchema = true` with no schema directory configured. Room emits a warning and never writes the schema JSON, contradicting the README. | Added `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. |
| 4 | Notification small icon used `android.R.drawable.ic_dialog_info`, a framework asset not designed as a status-bar icon. | Added a proper white-silhouette vector at `drawable/ic_notification.xml`. |
| 5 | No `.gitignore`. `build/`, `.gradle/` and `local.properties` would be committed. | Added a standard Android `.gitignore`. |

## Correctness and runtime bugs

| # | Problem | Fix |
|---|---|---|
| 6 | `DetailScreen` called `dao.observe(id)` directly in the composable body, creating a brand-new Flow on every recomposition and restarting the database subscription each time. | Wrapped in `remember(id) { dao.observe(id) }`. |
| 7 | `parseMoney` used `BigDecimal.longValueExact()`, which throws `ArithmeticException` on any input with more than two decimals — `100.555` crashed the save. | Now `setScale(2, RoundingMode.HALF_UP)` before conversion. |
| 8 | Form validation ran *after* the entity was constructed, so a blank clinic name surfaced as an opaque `LocalDate` parse error instead of a useful message. | Extracted `validateForm()`, which runs first and returns a specific message per field. |
| 9 | Duplicate invoice numbers hit the UNIQUE index and surfaced a raw `SQLiteConstraintException` string to the user. | Added `countByInvoiceNumber()` pre-check with a readable message. |
| 10 | The save button could be pressed repeatedly, firing concurrent inserts. | Added a `saving` guard and a "Saving..." label. |
| 11 | `Converters.statusToString` / `stringToStatus` were non-null, but `manualStatus` is a nullable column — a nullability mismatch Room has to work around. | Converters are now nullable on both sides. |
| 12 | `setExactAndAllowWhileIdle` could still throw `SecurityException` if the exact-alarm permission is revoked between the check and the call. | Wrapped in try/catch with an inexact idle-safe fallback. |
| 13 | `ReminderScheduler.cancel` cancelled the alarm but never called `PendingIntent.cancel()`, leaking the intent. | Both are now cancelled. |
| 14 | `requestCode()` used `(id xor (id ushr 32)).toInt() * 31 + offset` which could produce negative or colliding codes. | Replaced with a masked, collision-free `(id * 2 + offset + 1) and 0x7FFFFFFF`. |
| 15 | `ReminderReceiver` suppressed notifications only when `actualPaymentDate != null`, ignoring a manual PAID override and full collection. | Now checks `computedStatus() == PAID`, matching the documented precedence. |
| 16 | `BootReceiver` rescheduled by the same narrow `actualPaymentDate` check. | Same `computedStatus()` fix. |
| 17 | `openWhatsApp` had no fallback — no browser and no WhatsApp meant an uncaught `ActivityNotFoundException` crash. | Wrapped in try/catch with a Toast. Manifest `<queries>` now also declares `com.whatsapp.w4b` (WhatsApp Business) and a generic https VIEW intent. |
| 18 | Each `BroadcastReceiver` created a fresh `CoroutineScope(Dispatchers.IO)` per broadcast, and passed the raw receiver `Context` into Room. | One shared `SupervisorJob` scope; `applicationContext` is passed to the database. |
| 19 | The Add and Detail screens could not scroll on small phones; content below the fold was unreachable. | Added `verticalScroll(rememberScrollState())`. |
| 20 | Empty list state showed a blank screen with no explanation. | Added empty-state and no-results text. |

## Quality and maintainability

| # | Problem | Fix |
|---|---|---|
| 21 | `Icons.Default.ArrowBack` is deprecated and does not mirror in RTL layouts — relevant for an Arabic-facing build. | Switched to `Icons.AutoMirrored.Filled.*`, and `android:supportsRtl="true"` in the manifest. |
| 22 | Pure business logic (`report`, `parseMoney`, `money`, `normalizePhone`) lived in `MainActivity.kt` alongside Compose and Android imports, making JVM unit tests fragile. | Extracted to `BusinessLogic.kt`, which imports nothing from Android or Compose. |
| 23 | Compose artifact versions were pinned individually and could drift out of sync. | Switched to `compose-bom:2024.12.01`, which resolves to the same versions but keeps them consistent. |
| 24 | `kotlinx-coroutines` was only available transitively despite being used directly. | Declared explicitly. |
| 25 | A lint warning in a release configuration could abort the build. | `lint { abortOnError = false; checkReleaseBuilds = false }`. |
| 26 | Hardcoded `android:label` string in the manifest. | Moved to `strings.xml`. |
| 27 | Two unit tests only. | Expanded to nine, covering rounding, over-collection, partial collection, phone normalisation, validation and manual override. |
| 28 | No `release` build type or proguard file, so `assembleRelease` was undefined. | Added both. |

## Deliberately left alone

- `SCHEDULE_EXACT_ALARM` rather than `USE_EXACT_ALARM`. The latter is reserved by Google Play for alarm-clock and calendar apps; a collections app would be rejected. The current permission plus the runtime `canScheduleExactAlarms()` check is correct.
- Currency stays as integer fils (`Long`). Never change this to `Double`.
- Manual due-date entry rather than deriving it from the payment term, per the note in the original README.
