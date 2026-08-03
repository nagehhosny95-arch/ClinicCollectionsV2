# Targeted update — reminders, invoice slash, monthly collected

Three changes only. No redesign, no schema change, no navigation change.

`data/Database.kt` is byte-for-byte identical to the previous release: same version 3,
same `MIGRATION_1_2` and `MIGRATION_2_3`, same entities, same DAOs. Clinic directory,
status colours, icon, splash screen, WhatsApp integration and the GitHub Actions
workflow are untouched.

## Files changed

| File | Change |
|---|---|
| `ClinicApp.kt` | new versioned high-importance notification channel |
| `reminders/Reminders.kt` | 10:30 trigger time, richer notification content |
| `BusinessLogic.kt` | added `formatSuffixEdit`, `hasValidInvoiceMonth`, `collectedInMonth` |
| `ui/Components.kt` | added `AppSuffixField` |
| `ui/InvoiceFormScreen.kt` | uses the new suffix field, relabels Actual Collection Date |
| `ui/InvoiceDetailScreen.kt` | relabels Actual Collection Date, 10:30 text |
| `ui/HomeScreen.kt` | Collected tile now scoped to the current calendar month |
| `ui/ReportsScreen.kt` | Collected total now driven by Actual Collection Date |
| `ui/AboutScreen.kt` | 10:30 text |
| `res/values/strings.xml` | channel description mentions sound, vibration and 10:30 |
| `app/src/test/.../BusinessLogicTest.kt` | 19 → 30 tests |

## 1. Reminders at 10:30 with sound and vibration

`HOUR = 10`, `MINUTE = 30`. Both alarms — the day before and on the collection date —
now fire at 10:30 local device time.

**Why the channel ID changed.** A notification channel's importance, sound and
vibration are frozen the moment it is first created. An app update cannot raise them,
so existing users would have stayed silent on the old `payments` channel forever. The
app now registers `payments_v2_high` and calls `deleteNotificationChannel("payments")`
so nobody is left with a stale duplicate in system settings. Everyone gets sound and
vibration without reinstalling.

The channel is `IMPORTANCE_HIGH` with the default notification sound, a
`vibrationPattern`, lights, a badge and `VISIBILITY_PUBLIC`. The notification itself
is `PRIORITY_HIGH`, `CATEGORY_REMINDER`, and still uses `BigTextStyle`. There is no
full-screen intent anywhere.

Notification content:

```
Title:     Collection today: Al Noor Clinic
Collapsed: AED 1,500.00 - INV/2026/08/003
Expanded:  Al Noor Clinic
           Amount: AED 1,500.00
           Invoice: INV/2026/08/003
           Collection date: 10/08/2026
           Due date: 15/11/2026
           Status: Collection Due
```

`setExactAndAllowWhileIdle` with the existing `SecurityException` fallback, reboot
restore, reschedule-on-edit and cancel-on-paid are all unchanged.

## 2. Automatic slash and caret position

`formatSuffixEdit(newText, caret, oldText)` returns both the text and where the caret
belongs. Typing `0` then `8` produces `08/` with the caret at index 3, so the next
keystroke lands after the slash. The keyboard is numeric and the field never needs the
slash key.

Deletion is the part that usually breaks. The formatter only drops the automatic slash
when the user actually deleted the slash itself — it compares the old and new text
rather than blindly reformatting. So backspacing through `08/` gives `08` then `0`
then empty, instead of the slash being re-added and trapping the caret. Deleting a
sequence digit from `08/3` keeps `08/` with the caret still after the slash.

Month validation stays `01`–`12` via the existing regex, with `hasValidInvoiceMonth`
added so the form can show the specific "month must be between 01 and 12" message.

Eleven new tests cover typing, deletion, non-digit filtering, the six-digit cap,
valid and invalid months, and sequences of one, two, three and four digits.

## 3. Total Collected uses the Actual Collection Date

```kotlin
fun collectedInMonth(rows: List<InvoiceEntity>, month: YearMonth): Long = rows
    .filter { it.actualPaymentDate != null && YearMonth.from(it.actualPaymentDate) == month }
    .sumOf { it.collectedAmountMinor.coerceAtMost(it.dueAmountMinor) }
```

The worked example is a test: scheduled collection October 2026, due November 2026,
actually collected August 2026, AED 1,000 → the money lands in **August**, and August
only.

- Rows with no Actual Collection Date are excluded.
- `collectedAmountMinor` is summed, so partial collections stay accurate. The cap at
  the due amount is the pre-existing rule and only bites on over-collection.
- Editing the Actual Collection Date moves the amount automatically — nothing is
  cached, the figure is derived on read.
- Mark Paid Today already set the actual date to today and the collected amount to the
  due amount; that logic is unchanged.

Because membership is decided by a different date than the report query, both screens
now read the full invoice list for this one figure. Due, outstanding, overdue and
scheduled-collection figures still follow the Due Date / Collection Date selector
exactly as before; the Reports screen states this in a line under the selector.

The field is now labelled **Actual Collection Date** in the form and in invoice details.
