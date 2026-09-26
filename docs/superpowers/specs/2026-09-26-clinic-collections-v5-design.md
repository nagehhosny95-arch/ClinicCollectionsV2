# Clinic Collections V5 Design

Date: 2026-09-26  
Product: Clinic Collections for Advance Medical  
Status: Approved conversational design; written specification pending user review

## 1. Purpose

V5 turns the existing offline Android collection tracker into a management-ready version that can be shown to Advance Medical's CEO. It must make urgent work easy to find, provide accurate monthly and clinic-level reporting, protect the existing on-device data, and establish a repeatable APK update process that does not require uninstalling the app.

The app remains an offline, single-device Android application. Cloud sync, multi-user accounts, permissions, and server infrastructure are outside this release.

## 2. Success criteria

- Search is visible before the dashboard cards on Home.
- Dashboard status cards open their corresponding invoice lists.
- Users can filter the Home invoice list by operational status without scrolling through unrelated invoices.
- Reports show every month separately and calculate invoiced amount, cash collected, outstanding amount, and collection percentage.
- Monthly and annual collection percentages may exceed 100%.
- A clinic report compares expected and actual payment dates invoice by invoice.
- New invoice numbers automatically use the invoice date's year, including 2027 and later.
- Existing clinics and invoices survive the database upgrade.
- The current phone's data is rescued once, and every later APK can update in place.
- Users can create and restore a complete backup from inside the app.
- About content is suitable for an Advance Medical management demonstration.

## 3. Existing architecture and retained approach

The project remains a Kotlin, Jetpack Compose, Room application with local notifications. Existing invoice and clinic screens, reminders, WhatsApp integration, themes, and Room entities are extended rather than replaced.

V5 adds focused reusable units:

- pure reporting and filtering functions in the business-logic layer;
- a reusable filtered-invoice-list screen;
- annual, monthly-detail, and clinic-report UI sections;
- a versioned backup service using Android's Storage Access Framework;
- Room schema migration 3 to 4;
- stable release signing and increasing application version codes.

This keeps the implementation close to the current code while creating clear boundaries for future exports or cloud features.

## 4. Data model and migration

### 4.1 Invoice date

Add a non-null `invoiceDate: LocalDate` field to `InvoiceEntity` and index it for reporting.

- New invoice default: the device's current local date.
- The user may change it to the official date printed on the invoice.
- Existing invoices: migration 3 to 4 derives the date from `createdAtEpochMillis` using the device-local calendar date when practical. If SQLite migration constraints require UTC conversion, the implementation must document the conversion and provide an editable field so the user can correct historical dates.
- No existing amount, clinic, payment state, due date, collection date, actual payment date, or invoice identifier is deleted.

The four dates have distinct meanings:

| Field | Meaning | Reporting use |
|---|---|---|
| Invoice Date | Official invoice issue date | Monthly and annual invoiced amount; invoice-number year |
| Collection Date | Planned follow-up or cheque-collection date | Operational reminders and Collection Due status |
| Due Date | Contractual payment deadline | Payment Overdue status |
| Actual Collection Date | Date money was actually received | Monthly and annual cash collected |

### 4.2 Dynamic invoice number year

Replace the fixed `INV/2026/` prefix with a prefix derived from `invoiceDate.year`.

Example: an invoice dated 2027-01-05 uses `INV/2027/`. An invoice entered during 2027 but officially dated 2026-12-20 keeps `INV/2026/`.

The user continues to enter the month/sequence suffix. Existing saved invoice numbers remain unchanged unless the user edits the invoice date or suffix and saves the edited invoice. Uniqueness validation remains mandatory.

### 4.3 Database version

- Room database version changes from 3 to 4.
- Add `MIGRATION_3_4` and preserve migrations 1 to 2 and 2 to 3.
- Destructive migration is forbidden.
- Migration verification must check invoice and clinic counts and representative field values.

## 5. Reporting rules

### 5.1 Monthly performance

For a selected calendar month:

- `invoicedAmount` = sum of due amounts for invoices whose Invoice Date is in the month.
- `cashCollected` = sum of collected amounts for invoices whose Actual Collection Date is in the month, regardless of the invoices' issue months.
- `outstandingFromIssuedInvoices` = sum of remaining balances for invoices issued in the selected month.
- `collectionPercentage` = `cashCollected / invoicedAmount * 100`.

The percentage is not capped. If AED 500,000 is invoiced and AED 700,000 is collected, the result is 140% because collections from older invoices contribute to the month's performance.

If `invoicedAmount` is zero, the monthly percentage is unavailable and the UI shows `N/A — No invoices issued`. Cash collected is still displayed and still contributes to the annual calculation.

### 5.2 Annual performance

For a selected calendar year:

- annual invoiced = all invoices with Invoice Date in that year;
- annual collected = all cash with Actual Collection Date in that year;
- annual percentage = annual collected / annual invoiced * 100;
- the annual percentage is not an average of the twelve displayed percentages;
- the percentage may exceed 100%;
- if annual invoiced is zero, the percentage is N/A.

This lets strong collection months compensate for weak months exactly as requested.

### 5.3 Collected amount rule

The existing per-invoice protection remains: counted collected amount cannot exceed that invoice's due amount. Partial collections count at their saved collected amount. V5 continues to use the current model of one saved Actual Collection Date and one cumulative collected amount per invoice.

### 5.4 Clinic report

The user chooses a clinic and sees a summary plus its invoices. Each row includes:

- invoice number;
- invoice date;
- due amount and collected amount;
- planned collection date;
- due date;
- actual collection date or Not paid;
- operational status;
- days early or late relative to the planned collection date when an actual date exists;
- current delay when unpaid and the planned collection date has passed.

The clinic report supports All, Paid, and Open filters. Invoice rows open the existing invoice-detail screen.

## 6. Home screen and filtered lists

### 6.1 Home hierarchy

The Home content order is:

1. Advance Medical branded header.
2. Search field.
3. Outstanding summary.
4. Total Due and Collected This Month.
5. Collection Due and Payment Overdue.
6. Status chips.
7. Invoice result list.

Search and the selected status filter combine. Clearing the search does not clear the status filter, and selecting All returns the unfiltered list.

### 6.2 Status filters

Home provides horizontally scrollable chips:

- All
- On Track
- Collection Due
- Payment Overdue
- Paid

Counts and labels use the same `collectionState` rules as invoice-card colours so the filter and dashboard can never disagree.

### 6.3 Clickable dashboard cards

The following cards open a dedicated filtered list screen:

- Collection Due: unpaid invoices whose planned collection date has arrived or passed while the due date has not passed; oldest planned collection date first.
- Payment Overdue: unpaid invoices whose due date has passed; oldest due date first.
- Collected This Month: invoices with an Actual Collection Date in the current month; latest actual collection date first.

Each screen shows its title, invoice count, relevant total amount, a Back action, and invoice cards that open invoice details.

## 7. Reports user experience

### 7.1 Year overview

The Reports tab opens to the current year and allows previous/next-year navigation. It displays annual totals followed by all twelve months. Each month card shows:

- invoiced amount;
- cash collected during the month;
- outstanding from invoices issued in the month;
- collection percentage or N/A;
- an affordance to open month details.

Months remain visible even when their amounts are zero.

### 7.2 Month detail

Tapping a month opens its detailed totals and relevant invoice lists. The screen clearly explains that invoiced amount follows Invoice Date while cash collected follows Actual Collection Date.

### 7.3 Clinic report

Reports also contains a Clinic Report mode with a clinic selector, totals, All/Paid/Open filters, and the fields defined in section 5.4.

## 8. Backup and restore

### 8.1 Backup

Create Backup opens Android's document picker and writes a timestamped versioned backup file containing:

- format version;
- creation timestamp;
- application version;
- all clinics;
- all invoices and every persisted field.

The format is structured and app-controlled rather than a raw database copy so future versions can validate and migrate it safely.

### 8.2 Restore

Restore Backup follows this sequence:

1. Read and parse without changing the database.
2. Validate format version, required fields, dates, money values, invoice uniqueness, and clinic references.
3. Show the user the backup date and clinic/invoice counts.
4. Require explicit confirmation for a full replacement.
5. Create a safety backup of the current data.
6. Replace clinics and invoices in one Room transaction.
7. Reschedule reminders for restored unpaid invoices.
8. Show a success or actionable error message.

Merge restore is excluded because it risks duplicate invoices and ambiguous clinic matching.

## 9. APK signing and preservation of current phone data

### 9.1 Root cause

The current GitHub Actions workflow builds an ephemeral debug APK. Hosted runners generate different debug signing keys, and the app's version code is fixed. Android therefore rejects later APKs as updates.

### 9.2 Permanent update path

- Generate one permanent application signing keystore.
- Never commit the keystore or passwords to the repository.
- Store the encoded keystore and credentials in GitHub Actions secrets.
- Build a signed APK with the same application ID.
- Increase `versionCode` for every distributed build.
- Keep the signing keystore in a separately protected owner-controlled backup.

### 9.3 One-time migration of the currently installed app

Because the current installed build is debuggable but signed with an unavailable key:

1. Connect the phone to a Windows computer and enable USB debugging.
2. Use ADB `run-as com.nageh.cliniccollections` to extract the current database and related Room files.
3. Verify the backup exists and is non-empty before uninstalling anything.
4. Uninstall the old-signature app only after verification.
5. Install a temporary debuggable migration APK signed with the new permanent key.
6. Restore the extracted database into the app sandbox with ADB.
7. Launch V5 so Room performs migration 3 to 4.
8. Verify clinic count, invoice count, sample records, totals, and reminders.
9. Update to the normal release APK signed with the same permanent key.

No destructive action is allowed before the extracted database is verified. After this one-time process, subsequent signed APKs install normally over the existing app and preserve data.

## 10. About and Advance Medical branding

The app title and About page identify Advance Medical. Branding is centralized so it can be changed later without rewriting screens.

About shows:

- a concise product benefit statement;
- Advance Medical identity;
- colour/status legend;
- reminder behavior;
- a privacy statement that data is stored locally on the device;
- application version;
- Create Backup and Restore Backup actions.

Remove sales-negative technical details such as minimum API, fixed invoice prefix, and prominent no-backup/no-export wording. Keep a concise developer credit for Nageh Hosny and his contact number.

## 11. Error handling

- Invalid or unsupported backup files never modify data.
- Restore failures roll back the entire database transaction.
- Duplicate invoice numbers produce a clear validation message.
- Report calculations use integer minor currency units and avoid floating-point money arithmetic.
- Percentage formatting handles zero denominators and supports values above 100%.
- Empty filtered lists explain why nothing is shown and provide a clear way back.
- A future year with no records still renders twelve zero-value month cards.
- Reminder rescheduling failures are reported without corrupting restored data.

## 12. Verification

Automated tests cover:

- monthly and annual invoiced/collected calculations;
- collections from older invoices contributing to the current month's percentage;
- percentages above 100%;
- zero-invoice months returning N/A while retaining cash totals;
- yearly aggregation using totals rather than an average of month percentages;
- dynamic invoice prefixes for 2026, 2027, and a backdated invoice;
- All, On Track, Collection Due, Payment Overdue, and Paid filters;
- required sort orders for dashboard lists;
- migration 3 to 4 preserving existing data and assigning Invoice Date;
- backup round-trip fidelity;
- invalid backup rejection and transactional restore rollback.

Release verification includes:

- unit tests in GitHub Actions;
- debug and signed-release assembly;
- manual checks on an Android 10 or later device;
- one-time current-phone data migration rehearsal and count/total comparison;
- installing a higher-version APK over the stable-signed build without uninstalling.

## 13. Deferred features

The following are intentionally deferred until after the CEO review or a commercial agreement:

- cloud sync and multi-device support;
- multiple users and role permissions;
- PDF or Excel report export;
- server-managed in-app update delivery;
- audit history for multiple partial-payment events.
