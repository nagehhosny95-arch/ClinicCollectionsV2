# Clinic Collections V5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a management-ready V5 Android app with fast operational filters, annual/monthly/clinic reporting, safe backups, dynamic invoice years, preserved existing data, and a stable in-place APK update path.

**Architecture:** Extend the current Compose + Room app without replacing its working invoice, clinic, reminder, and WhatsApp flows. Put all calculations, filtering, sorting, and backup validation in pure testable Kotlin units; keep Android file selection, Compose presentation, Room transactions, and alarm scheduling in thin platform adapters.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose Material 3, Room 2.6.1, kotlinx.serialization JSON, Android Storage Access Framework, JUnit 4, AndroidX Room migration testing, Gradle 8.11.1, GitHub Actions, Java 17.

**Spec:** `docs/superpowers/specs/2026-09-26-clinic-collections-v5-design.md`

## Global Constraints

- Keep `applicationId = "com.nageh.cliniccollections"`; changing it would break the update and data-migration path.
- Keep `minSdk = 29`, `compileSdk = 35`, `targetSdk = 35`, and Java/Kotlin JVM target 17.
- Room must migrate from version 3 to 4 without destructive migration or loss of any existing clinic or invoice field.
- Store AED as integer fils (`Long`); never use floating-point arithmetic for money.
- Monthly percentage is total cash collected during the month divided by invoices issued during the month; it may exceed 100%.
- Annual percentage is annual cash collected divided by annual invoices issued, not an average of monthly percentages.
- A zero-invoice period shows N/A while retaining its collected amount.
- Invoice numbers use the Invoice Date year; the UI date format remains `DD/MM/YYYY`.
- Keep reminders at 10:30 local time, WhatsApp integration, existing status precedence, and offline operation.
- Brand user-facing V5 screens for Advance Medical and retain clear developer credit for Nageh Hosny.
- The permanent signing key and passwords must never be committed to the repository.
- The supplied source is a GitHub ZIP without `.git`; commit commands below become checkpoints until work is copied back into the real repository.

## Review Focus

- A real version-3 database containing clinics and invoices must open on V5 with counts, amounts, dates, and links intact; Task 1 owns the migration test.
- Partial, over-, backdated, and old-invoice collections crossing month/year boundaries must calculate correctly, including percentages above 100%; Task 2 owns these tests.
- January year rollover and a backdated invoice entered in a later year must generate the official invoice-date year exactly once; Task 3 owns these tests.
- Corrupt, unsupported, duplicate-invoice, and interrupted restore inputs must leave current data unchanged; Task 6 owns codec and transaction tests.
- A higher-version APK signed by the permanent key must install over the stable migration build, while the old ephemeral-debug build is backed up before uninstall; Task 7 owns the rehearsal checklist.

---

## File structure

### New production files

- `app/src/main/java/com/nageh/cliniccollections/ReportingLogic.kt` — monthly, annual, and clinic performance models and pure calculations.
- `app/src/main/java/com/nageh/cliniccollections/InvoiceListLogic.kt` — operational filters and deterministic list sorting.
- `app/src/main/java/com/nageh/cliniccollections/data/BackupModels.kt` — versioned serializable backup DTOs.
- `app/src/main/java/com/nageh/cliniccollections/data/BackupCodec.kt` — pure JSON encode/decode/validation.
- `app/src/main/java/com/nageh/cliniccollections/data/BackupRepository.kt` — Room snapshot/replace transactions and URI file I/O.
- `app/src/main/java/com/nageh/cliniccollections/ui/FilteredInvoicesScreen.kt` — dedicated dashboard-result list.
- `app/src/main/java/com/nageh/cliniccollections/ui/MonthReportScreen.kt` — selected-month details and invoice lists.
- `app/src/main/java/com/nageh/cliniccollections/ui/ClinicReportContent.kt` — clinic selector, totals, filters, and expected/actual date rows.
- `app/src/main/java/com/nageh/cliniccollections/AppBrand.kt` — centralized Advance Medical name and product copy.
- `app/src/androidTest/java/com/nageh/cliniccollections/data/Migration3To4Test.kt` — real SQLite version-3 to Room version-4 test.
- `app/src/androidTest/java/com/nageh/cliniccollections/data/BackupRestoreTest.kt` — transactional replacement/rollback test.
- `app/src/test/java/com/nageh/cliniccollections/ReportingLogicTest.kt` — period calculation tests.
- `app/src/test/java/com/nageh/cliniccollections/InvoiceListLogicTest.kt` — filter and order tests.
- `app/src/test/java/com/nageh/cliniccollections/data/BackupCodecTest.kt` — backup round-trip and validation tests.
- `RELEASE-AND-DATA-MIGRATION.md` — owner instructions for signing, GitHub secrets, ADB rescue, verification, and future updates.

### Existing files changed

- `build.gradle.kts` — add Kotlin serialization plugin declaration.
- `app/build.gradle.kts` — schema/test/serialization dependencies, version inputs, and release signing configuration.
- `app/src/main/java/com/nageh/cliniccollections/data/Database.kt` — `invoiceDate`, index, database version 4, migration 3 to 4, backup DAO, transactional restore.
- `app/src/main/java/com/nageh/cliniccollections/BusinessLogic.kt` — dynamic invoice prefixes and compatible suffix parsing.
- `app/src/main/java/com/nageh/cliniccollections/MainActivity.kt` — database-level dependency wiring and new overlay destinations.
- `app/src/main/java/com/nageh/cliniccollections/reminders/Reminders.kt` — public reschedule-all helper after restore.
- `app/src/main/java/com/nageh/cliniccollections/ui/Components.kt` — optional click handling, dynamic suffix prefix, report rows/chips.
- `app/src/main/java/com/nageh/cliniccollections/ui/HomeScreen.kt` — search-first hierarchy, status chips, dashboard callbacks.
- `app/src/main/java/com/nageh/cliniccollections/ui/InvoiceFormScreen.kt` — Invoice Date field, persistence, and dynamic number year.
- `app/src/main/java/com/nageh/cliniccollections/ui/InvoiceDetailScreen.kt` — display Invoice Date.
- `app/src/main/java/com/nageh/cliniccollections/ui/ReportsScreen.kt` — year overview and clinic-report mode.
- `app/src/main/java/com/nageh/cliniccollections/ui/AboutScreen.kt` — Advance Medical copy, app version, backup and restore actions.
- `app/src/test/java/com/nageh/cliniccollections/BusinessLogicTest.kt` — adapt old helpers and prefix tests to `invoiceDate`.
- `.github/workflows/build-apk.yml` — tests, migration test job, stable release signing, increasing version code, release artifact.
- `README.md`, `BUILD-AR.md` — V5 behavior and build/update instructions.

---

### Task 1: Invoice Date schema and lossless Room migration

**Files:**
- Modify: `app/src/main/java/com/nageh/cliniccollections/data/Database.kt`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/nageh/cliniccollections/data/Migration3To4Test.kt`
- Generate: `app/schemas/com.nageh.cliniccollections.data.AppDatabase/4.json`

**Interfaces:**
- Produces: `InvoiceEntity.invoiceDate: LocalDate`, with a Kotlin construction default of `LocalDate.now()` so existing named constructors keep compiling while migration backfills persisted rows.
- Produces: `AppDatabase.MIGRATION_3_4: Migration`.
- Produces: `InvoiceDao.observeAll()` rows containing the migrated date.
- Consumes: existing `createdAtEpochMillis`, migrations 1 to 2 and 2 to 3, and `Converters`.

- [ ] **Step 1: Write the failing migration test**

Create a raw SQLite version-3 database with one clinic and two invoices, including different `createdAtEpochMillis`, null/non-null payment dates, and a clinic link. Open it through Room version 4 with `MIGRATION_3_4`; assert invoice count 2, clinic count 1, all pre-existing values unchanged, `invoiceDate` populated from creation date, and clinic IDs preserved.

- [ ] **Step 2: Run the migration test and verify failure**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nageh.cliniccollections.data.Migration3To4Test`  
Expected: FAIL because version 4, `invoiceDate`, and `MIGRATION_3_4` do not exist.

- [ ] **Step 3: Add the schema field and migration**

Add `invoiceDate: LocalDate` with an indexed column and a safe SQL default. Increment the Room version to 4. `MIGRATION_3_4` must add the column, backfill from `createdAtEpochMillis`, add the index, and never alter another field.

- [ ] **Step 4: Add Android migration-test dependencies and export schema 4**

Add AndroidX test runner/core, Room testing, and the test instrumentation configuration required by `Migration3To4Test`. Generate and retain schema JSON 4.

- [ ] **Step 5: Run migration and existing tests**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest`  
Expected: migration test PASS; existing unit tests compile after their invoice helper receives a default Invoice Date.

- [ ] **Step 6: Commit/checkpoint**

```bash
git add app/build.gradle.kts app/src/main/java/com/nageh/cliniccollections/data/Database.kt app/src/androidTest app/schemas
git commit -m "feat: add invoice date migration"
```

### Task 2: Monthly, annual, clinic, filter, and sorting logic

**Files:**
- Create: `app/src/main/java/com/nageh/cliniccollections/ReportingLogic.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/InvoiceListLogic.kt`
- Create: `app/src/test/java/com/nageh/cliniccollections/ReportingLogicTest.kt`
- Create: `app/src/test/java/com/nageh/cliniccollections/InvoiceListLogicTest.kt`
- Modify: `app/src/test/java/com/nageh/cliniccollections/BusinessLogicTest.kt`

**Interfaces:**
- Produces: `data class PeriodPerformance(val invoicedMinor: Long, val cashCollectedMinor: Long, val outstandingIssuedMinor: Long, val percentage: BigDecimal?)`.
- Produces: `fun monthlyPerformance(rows: List<InvoiceEntity>, month: YearMonth): PeriodPerformance`.
- Produces: `fun annualPerformance(rows: List<InvoiceEntity>, year: Int): PeriodPerformance`.
- Produces: `fun monthlyPerformances(rows: List<InvoiceEntity>, year: Int): List<MonthPerformance>` returning January through December.
- Produces: `fun formatPercentage(value: BigDecimal?): String` returning `N/A` for null and retaining values above 100.
- Produces: `enum class InvoiceFilter { ALL, ON_TRACK, COLLECTION_DUE, PAYMENT_OVERDUE, PAID }`.
- Produces: `fun filterInvoices(rows: List<InvoiceEntity>, filter: InvoiceFilter, today: LocalDate): List<InvoiceEntity>`.
- Produces: `enum class DashboardListKind { COLLECTION_DUE, PAYMENT_OVERDUE, COLLECTED_THIS_MONTH }`.
- Produces: `fun dashboardInvoices(rows: List<InvoiceEntity>, kind: DashboardListKind, today: LocalDate, month: YearMonth): List<InvoiceEntity>`.
- Produces: `fun clinicPaymentTiming(invoice: InvoiceEntity, today: LocalDate): PaymentTiming` for early/late/open labels.

- [ ] **Step 1: Write failing monthly and annual performance tests**

Cover the approved AED examples: AED 1,000,000 invoiced and AED 1,000,000 collected, including AED 500,000 from old invoices, equals 100%; AED 500,000 invoiced and AED 700,000 collected equals 140%; zero invoiced with non-zero cash yields null/N/A; yearly percentage uses annual totals rather than averaging month percentages.

- [ ] **Step 2: Add boundary tests for collected money**

Assert partial collection uses the saved collected amount, over-collection is capped at invoice due amount, an Actual Collection Date moves cash between months/years, and an invoice without Actual Collection Date contributes no cash.

- [ ] **Step 3: Run reporting tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*ReportingLogicTest*'`  
Expected: FAIL because the new models and functions do not exist.

- [ ] **Step 4: Implement reporting models and functions**

Use `Long` for all money sums and `BigDecimal` only for percentage division/formatting. Return twelve ordered month results for every selected year, including empty months.

- [ ] **Step 5: Write failing filter and sorting tests**

Assert each operational status uses `collectionState`; Collection Due sorts oldest collection date first; Payment Overdue sorts oldest due date first; Collected This Month includes only matching Actual Collection Dates and sorts newest first; paid always wins over overdue/due.

- [ ] **Step 6: Implement filter, dashboard-list, and clinic-timing functions**

Keep status semantics in one source of truth by delegating to `collectionState` rather than duplicating date rules.

- [ ] **Step 7: Run all unit tests**

Run: `./gradlew testDebugUnitTest`  
Expected: PASS.

- [ ] **Step 8: Commit/checkpoint**

```bash
git add app/src/main/java/com/nageh/cliniccollections/ReportingLogic.kt app/src/main/java/com/nageh/cliniccollections/InvoiceListLogic.kt app/src/test
git commit -m "feat: add V5 reporting and filter logic"
```

### Task 3: Invoice Date form and automatic invoice year

**Files:**
- Modify: `app/src/main/java/com/nageh/cliniccollections/BusinessLogic.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/Components.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/InvoiceFormScreen.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/InvoiceDetailScreen.kt`
- Modify: `app/src/test/java/com/nageh/cliniccollections/BusinessLogicTest.kt`

**Interfaces:**
- Produces: `fun invoicePrefix(invoiceDate: LocalDate): String`.
- Produces: `fun fullInvoiceNumber(invoiceDate: LocalDate, suffix: String): String`.
- Produces: `fun invoiceSuffix(value: String): String` accepting any saved `INV/yyyy/` prefix.
- Changes: `AppSuffixField(..., prefix: String, ...)` receives the displayed dynamic prefix.
- Consumes: `InvoiceEntity.invoiceDate` from Task 1.

- [ ] **Step 1: Replace fixed-prefix tests with failing date-aware tests**

Assert 2026, 2027, and a backdated December 2026 invoice entered during 2027. Assert an already-prefixed suffix receives exactly one prefix and suffix validation remains unchanged.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*BusinessLogicTest*'`  
Expected: FAIL because date-aware prefix signatures do not exist.

- [ ] **Step 3: Implement date-aware prefix parsing and formatting**

Remove the fixed constant from save logic. Strip only the leading `INV/` plus four digits plus `/`; preserve the month/sequence rules.

- [ ] **Step 4: Add Invoice Date state and field to the form**

New invoices default to `LocalDate.now()`. Existing invoices load their persisted date. The Invoice Date field appears before the invoice number; its year immediately updates the non-editable prefix; save and uniqueness checks use `fullInvoiceNumber(invoiceDate, suffix)`.

- [ ] **Step 5: Display Invoice Date in invoice details**

Place it with the other invoice dates and use the existing date formatter.

- [ ] **Step 6: Run all unit tests and assemble debug**

Run: `./gradlew testDebugUnitTest assembleDebug`  
Expected: PASS and APK assembled.

- [ ] **Step 7: Commit/checkpoint**

```bash
git add app/src/main/java/com/nageh/cliniccollections/BusinessLogic.kt app/src/main/java/com/nageh/cliniccollections/ui app/src/test
git commit -m "feat: derive invoice year from invoice date"
```

### Task 4: Search-first Home, quick filters, and clickable dashboard lists

**Files:**
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/HomeScreen.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/Components.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/ui/FilteredInvoicesScreen.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/MainActivity.kt`

**Interfaces:**
- Consumes: `InvoiceFilter`, `DashboardListKind`, `filterInvoices`, and `dashboardInvoices` from Task 2.
- Produces: `Overlay.FilteredInvoices(kind: DashboardListKind)`.
- Changes: `HomeScreen` receives `onOpenDashboardList: (DashboardListKind) -> Unit` alongside `onOpenInvoice`.
- Produces: `FilteredInvoicesScreen(dao, kind, onBack, onOpenInvoice)`.

- [ ] **Step 1: Move search directly below the branded header**

Preserve SQL search normalization, clear action, empty messages, and invoice-card opening.

- [ ] **Step 2: Add status chips and combine them with search results**

Render horizontally scrollable All, On Track, Collection Due, Payment Overdue, and Paid chips. Filter the current search result using Task 2 logic and show the filtered count.

- [ ] **Step 3: Add optional click behavior to summary cards**

Keep non-clickable cards source-compatible. Give the three requested dashboard cards clear click affordances and accessibility labels.

- [ ] **Step 4: Build the dedicated filtered list screen**

Show title, count, relevant amount total, Back action, sorted invoice cards, and a useful empty state. Use Actual Collection Date for the collected-month list.

- [ ] **Step 5: Wire overlay navigation**

Add the new overlay to `MainActivity`, preserve the existing BackHandler stack, and let filtered rows open invoice details.

- [ ] **Step 6: Verify Home manually and run tests**

Run: `./gradlew testDebugUnitTest assembleDebug`  
Expected: PASS. Manual check at a narrow phone width confirms chip scrolling, search visibility, and no FAB/list overlap.

- [ ] **Step 7: Commit/checkpoint**

```bash
git add app/src/main/java/com/nageh/cliniccollections/MainActivity.kt app/src/main/java/com/nageh/cliniccollections/ui/HomeScreen.kt app/src/main/java/com/nageh/cliniccollections/ui/Components.kt app/src/main/java/com/nageh/cliniccollections/ui/FilteredInvoicesScreen.kt
git commit -m "feat: add dashboard filters and drill-down lists"
```

### Task 5: Year, month, and clinic reports

**Files:**
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/ReportsScreen.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/ui/MonthReportScreen.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/ui/ClinicReportContent.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/Components.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/MainActivity.kt`

**Interfaces:**
- Consumes: reporting and clinic-timing functions from Task 2.
- Produces: `Overlay.MonthReport(month: YearMonth)`.
- Changes: `ReportsScreen(invoiceDao, clinicDao, contentPadding, onOpenMonth, onOpenInvoice)`.
- Produces: `MonthReportScreen(invoiceDao, month, onBack, onOpenInvoice)`.
- Produces: `ClinicReportContent(clinics, invoices, onOpenInvoice)`.

- [ ] **Step 1: Replace the single-month landing view with a year overview**

Default to the current year, support previous/next year, display annual totals, and render January through December with invoiced, collected, outstanding, and percentage/N/A.

- [ ] **Step 2: Add month drill-down**

Open a full-screen month report showing approved metrics and separate issued-in-month and collected-in-month lists. Clearly label the different date bases and allow opening invoice details.

- [ ] **Step 3: Add Clinic Report mode**

Provide clinic selection plus All/Paid/Open chips. Show clinic totals and invoice rows with Invoice Date, Collection Date, Due Date, Actual Collection Date, amounts, status, and early/late/open timing.

- [ ] **Step 4: Wire reports to both DAOs and overlay navigation**

Pass `ClinicDao` into Reports from `MainActivity`; preserve bottom navigation and physical Back behavior.

- [ ] **Step 5: Verify report examples and layouts**

Run: `./gradlew testDebugUnitTest assembleDebug`  
Expected: PASS. Manually verify 100%, 140%, N/A, twelve months, year switching, and a clinic with paid and unpaid rows.

- [ ] **Step 6: Commit/checkpoint**

```bash
git add app/src/main/java/com/nageh/cliniccollections/MainActivity.kt app/src/main/java/com/nageh/cliniccollections/ui/ReportsScreen.kt app/src/main/java/com/nageh/cliniccollections/ui/MonthReportScreen.kt app/src/main/java/com/nageh/cliniccollections/ui/ClinicReportContent.kt app/src/main/java/com/nageh/cliniccollections/ui/Components.kt
git commit -m "feat: add annual monthly and clinic reports"
```

### Task 6: Versioned Backup/Restore and management-ready About

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/nageh/cliniccollections/data/BackupModels.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/data/BackupCodec.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/data/BackupRepository.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/data/Database.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/reminders/Reminders.kt`
- Create: `app/src/main/java/com/nageh/cliniccollections/AppBrand.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/ui/AboutScreen.kt`
- Modify: `app/src/main/java/com/nageh/cliniccollections/MainActivity.kt`
- Create: `app/src/test/java/com/nageh/cliniccollections/data/BackupCodecTest.kt`
- Create: `app/src/androidTest/java/com/nageh/cliniccollections/data/BackupRestoreTest.kt`

**Interfaces:**
- Produces: `@Serializable data class BackupDocument(formatVersion: Int, createdAtEpochMillis: Long, appVersion: String, clinics: List<ClinicBackup>, invoices: List<InvoiceBackup>)`.
- Produces: `object BackupCodec { fun encode(document: BackupDocument): String; fun decodeAndValidate(json: String): BackupDocument }`.
- Produces: `class BackupRepository(context: Context, database: AppDatabase)` with `suspend fun export(uri: Uri)`, `suspend fun inspect(uri: Uri): BackupSummary`, and `suspend fun restore(uri: Uri)`.
- Produces: `BackupDao.snapshot()` and transactional `replaceAll(clinics, invoices)` preserving entity IDs.
- Produces: `suspend fun ReminderScheduler.rescheduleAll(context, invoices)` or equivalent public helper.

- [ ] **Step 1: Add serialization and write failing codec tests**

Test complete round-trip equality; ISO dates and Long fils; unsupported format version; malformed JSON; duplicate invoice numbers; missing clinic references; and invalid date/amount fields.

- [ ] **Step 2: Run codec tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests '*BackupCodecTest*'`  
Expected: FAIL because DTOs and codec do not exist.

- [ ] **Step 3: Implement version-1 DTOs and strict codec validation**

Keep DTOs independent of Room annotations. Validation returns actionable messages and performs no database access.

- [ ] **Step 4: Write failing transactional restore instrumentation tests**

Assert successful full replacement preserves IDs/links; duplicate/invalid data makes no changes; an injected failure rolls back both clinics and invoices.

- [ ] **Step 5: Implement backup DAO and repository**

Use one Room transaction for replacement. Before restore, serialize the current snapshot to an internal `last_pre_restore.clinicbackup`; then restore and reschedule unpaid reminders only after commit.

- [ ] **Step 6: Add Storage Access Framework controls to About**

Create Backup uses `CreateDocument` with a timestamped `.clinicbackup` name. Restore uses `OpenDocument`, inspects first, displays backup timestamp/counts, and requires confirmation before replacement. Show progress and success/failure without blocking the main thread.

- [ ] **Step 7: Replace About copy and centralize branding**

Show Advance Medical identity, concise benefit, colour legend, reminders, local-data privacy, app version, backup controls, and a concise `Developed by Nageh Hosny` credit with his WhatsApp number. Remove minimum-API, fixed-prefix, and no-backup/no-export wording.

- [ ] **Step 8: Run unit, instrumentation, and build verification**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`  
Expected: PASS.

- [ ] **Step 9: Commit/checkpoint**

```bash
git add build.gradle.kts app/build.gradle.kts app/src/main app/src/test app/src/androidTest
git commit -m "feat: add safe backup restore and Advance Medical branding"
```

### Task 7: Stable release signing, CI, and one-time phone migration guide

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/build-apk.yml`
- Create: `RELEASE-AND-DATA-MIGRATION.md`
- Modify: `README.md`
- Modify: `BUILD-AR.md`

**Interfaces:**
- Consumes environment variables: `VERSION_CODE`, `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Consumes GitHub secrets containing the Base64 keystore and credentials.
- Produces: a one-time `migration` build type with no application-ID suffix, `isDebuggable = true`, the permanent release key, and a lower version code than the final release.
- Produces: signed `app-release.apk` with unchanged application ID and increasing version code.

- [ ] **Step 1: Make build version and signing environment-driven**

Use a V5 local fallback version code/name. Configure release signing only when all required variables are present; fail the CI release job clearly if any secret is missing. Add a one-time `migration` build type signed by the same key and marked debuggable so ADB `run-as` can restore the extracted database; it must keep the production application ID and use a lower version code than the final non-debuggable release. Never print secret values.

- [ ] **Step 2: Upgrade GitHub Actions**

Run unit tests, run migration/restore instrumentation tests in an emulator job, decode the keystore into the runner temp directory, use `github.run_number` to derive increasing migration/release version codes, assemble signed migration and release artifacts, and upload the migration APK only for the explicit one-time workflow while normal pushes expose only the release APK plus test reports.

- [ ] **Step 3: Write exact owner setup instructions**

Document one permanent keystore generation, separate secure backups, GitHub secret names, release download, version verification, and the warning that losing the key prevents future in-place updates.

- [ ] **Step 4: Write the exact one-time ADB rescue procedure**

Include device prerequisites, `adb devices`, `run-as` access check, database/WAL/SHM extraction, file-size/hash verification, the stop condition before uninstall, stable-key migration APK install, database restore, Room 3-to-4 launch, 47-invoice/count/total/sample verification, reminder verification, and final update to the non-debuggable release APK.

- [ ] **Step 5: Verify release metadata without exposing secrets**

Run locally when a keystore is available: `./gradlew signingReport assembleRelease`  
Expected: release variant uses the permanent certificate, application ID is unchanged, and version code is greater than the installed stable migration build.

- [ ] **Step 6: Commit/checkpoint**

```bash
git add app/build.gradle.kts .github/workflows/build-apk.yml RELEASE-AND-DATA-MIGRATION.md README.md BUILD-AR.md
git commit -m "build: add stable signed V5 releases"
```

### Task 8: Full regression, acceptance checks, and delivery package

**Files:**
- Modify as needed: files changed in Tasks 1 through 7
- Create: `V5-CHANGES.md`

**Interfaces:**
- Consumes: all V5 features and CI artifacts.
- Produces: reviewed source ZIP, signed APK after owner secrets are configured, backup sample, migration guide, and V5 release notes.

- [ ] **Step 1: Run the complete automated suite**

Run: `./gradlew clean testDebugUnitTest connectedDebugAndroidTest assembleDebug assembleRelease`  
Expected: all tests PASS and both required build variants assemble; if the local environment cannot download Gradle, require the equivalent green GitHub Actions jobs before delivery.

- [ ] **Step 2: Perform static checks**

Run: `./gradlew lintDebug` and search the source for fixed `INV/2026/`, exposed passwords/keystore data, `fallbackToDestructiveMigration`, personal phone/footer copy, and stale V4 report copy.  
Expected: no release-blocking issue and none of the forbidden strings/configurations remain in production behavior.

- [ ] **Step 3: Perform manual acceptance checks**

On Android 10 or later, verify add/edit invoice, 2027 prefix, all five Home filters, all three dashboard lists/orderings, search combined with filters, 12-month year report, 100%/140%/N/A cases, clinic report timing, backup creation, restore confirmation, restored reminders, and physical Back behavior.

- [ ] **Step 4: Rehearse update preservation**

Install a stable-key migration build with sample data, then install a higher-version stable release over it. Confirm the app updates without uninstalling and all data remains.

- [ ] **Step 5: Write release notes and package source**

Document user-visible V5 changes, known limitation of one cumulative payment/date per invoice, first-phone migration requirement, backup usage, and future update steps. Exclude `.gradle`, `build`, `.superpowers`, APK signing keys, and credentials from the source ZIP.

- [ ] **Step 6: Final commit/checkpoint**

```bash
git add V5-CHANGES.md
git commit -m "docs: finalize Clinic Collections V5"
```
