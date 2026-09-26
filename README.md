# Advance Medical Clinic Collections V5

Offline Android 10+ app for tracking clinic invoices, scheduled collections, actual cash
collection, overdue balances, and payment timing. Data stays in a local Room database; the
user can explicitly export and restore a JSON backup through Android's document picker.

## V5 highlights

- Search and operational status filters at the top of Home.
- Clickable Collection Due, Payment Overdue, and Collected This Month dashboards.
- Invoice Date stored separately; invoice numbers use `INV/{invoiceDate.year}/` automatically.
- Twelve-month yearly report: invoices issued, cash collected, and collection percentage.
- Monthly percentage is cash collected in that month divided by invoices issued in that month;
  it may exceed 100%. Annual percentage uses annual sums rather than averaging months.
- Per-clinic payment history with invoice, planned collection, due, actual payment, and timing.
- Backup/Restore in About, Room migration 3 → 4, and stable release-signing support.
- Advance Medical user-facing branding with developer credit for Nageh Hosny.

## Financial rules

All AED values are integer fils (`Long`). An invoice belongs to an issue month using
`invoiceDate`. Cash belongs to a collection month using `actualPaymentDate`, regardless of when
the invoice was issued. A month with no issued invoices displays `N/A` for its percentage while
retaining cash collected. Collected amounts are capped at the invoice amount for reporting.

## Build and verification

GitHub Actions runs JVM unit tests, lint, a debug APK build, and Room migration/restore tests on
an Android emulator. With the four signing secrets configured, it also creates a signed release
APK suitable for future in-place updates. See [BUILD-AR.md](BUILD-AR.md) for the Arabic steps and
[RELEASE-AND-DATA-MIGRATION.md](RELEASE-AND-DATA-MIGRATION.md) before replacing the old debug app.

Local commands, when Gradle and Android SDK dependencies are available:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

## Platform

- Kotlin 2.0.21, Compose Material 3, Room 2.6.1
- JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.7.3
- minSdk 29, compileSdk/targetSdk 35
- Application ID: `com.nageh.cliniccollections`

Reminder alarms are scheduled at 10:30 local time one day before and on the planned Collection
Date. Paid invoices cancel their alarms; reboot and app replacement reschedule open invoices.
