package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.data.MonthlyTotals
import com.nageh.cliniccollections.data.PaymentStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure business logic, deliberately free of Android and Compose imports so that
 * it runs in plain JVM unit tests without the "not mocked" android.jar problem.
 */

fun report(rows: List<InvoiceEntity>, today: LocalDate = LocalDate.now()): MonthlyTotals {
    val due = rows.sumOf { it.dueAmountMinor }
    val collected = rows.sumOf { it.collectedAmountMinor.coerceAtMost(it.dueAmountMinor) }
    val outstanding = rows.sumOf { (it.dueAmountMinor - it.collectedAmountMinor).coerceAtLeast(0L) }
    val overdue = rows
        .filter { it.computedStatus(today) == PaymentStatus.OVERDUE }
        .sumOf { (it.dueAmountMinor - it.collectedAmountMinor).coerceAtLeast(0L) }
    return MonthlyTotals(
        due, collected, outstanding, overdue,
        scheduledCollections = rows.size,
        overdueCollections = rows.count { it.computedStatus(today) == PaymentStatus.OVERDUE }
    )
}

const val INVOICE_PREFIX = "INV/2026/"

fun invoiceSuffix(value: String): String = value.trim().removePrefix(INVOICE_PREFIX)

fun fullInvoiceNumber(value: String): String = INVOICE_PREFIX + invoiceSuffix(value)

fun validInvoiceSuffix(value: String): Boolean =
    Regex("^(0[1-9]|1[0-2])/\\d{1,4}$").matches(invoiceSuffix(value))

fun formatInvoiceSuffixInput(value: String): String {
    val digits = invoiceSuffix(value).filter(Char::isDigit).take(6)
    if (digits.length < 2) return digits
    if (digits.length == 2) return "$digits/"
    return digits.take(2) + "/" + digits.drop(2).take(4)
}

fun normalizePhone(value: String): String {
    val digits = value.filter(Char::isDigit).removePrefix("00")
    return when {
        digits.startsWith("971") -> digits
        digits.startsWith("0") -> "971" + digits.drop(1)
        digits.length == 9 && digits.startsWith("5") -> "971$digits"
        else -> digits
    }
}

/**
 * Parses a user-entered AED amount into integer fils.
 * Rounds to two decimals instead of throwing, so "100.555" becomes 10056 rather than a crash.
 */
fun parseMoney(value: String): Long =
    BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

fun money(value: Long): String = String.format(Locale.US, "%,.2f", value / 100.0)

fun reminderMessage(invoice: InvoiceEntity): String =
    "Dear ${invoice.clinicName}, this is a friendly payment reminder for " +
        "AED ${money(invoice.dueAmountMinor)} against invoice ${invoice.invoiceNumber}, " +
        "due on ${invoice.dueDate}. Our collection date is ${invoice.collectionDate}. Thank you."

/** Returns an error message, or null when the form is valid. */
fun validateForm(
    clinic: String,
    phone: String,
    invoiceSuffix: String,
    amount: String,
    due: LocalDate?,
    collection: LocalDate?,
    payment: LocalDate?,
    collected: String
): String? {
    if (clinic.isBlank()) return "Clinic name is required."
    if (!validInvoiceSuffix(invoiceSuffix)) return "Use month/number, e.g. 08/3 or 12/1234."
    if (normalizePhone(phone).length < 8) {
        return "Enter a WhatsApp number in international format, e.g. 971501234567."
    }
    val amountMinor = runCatching { parseMoney(amount) }.getOrNull()
        ?: return "Due amount must be a number, e.g. 1500.00."
    if (amountMinor <= 0L) return "Due amount must be greater than zero."
    if (due == null) return "Due date is required."
    if (collection == null) return "Collection date is required."
    if (collected.isNotBlank()) {
        runCatching { parseMoney(collected) }.getOrNull()
            ?: return "Collected amount must be a number."
    }
    return null
}

// ---------------------------------------------------------------------------
// Card status rules. Moved here unchanged from MainActivity so that the colour
// rules are unit-tested rather than living inside a private UI helper.
//
//   Green  : paid, or on track.
//   Orange : the collection date has arrived or passed, while the due date has not.
//   Red    : the due date has passed and the invoice is unpaid.
// ---------------------------------------------------------------------------

enum class CollectionState { PAID, NORMAL, COLLECTION_DUE, PAYMENT_OVERDUE }

fun collectionState(
    invoice: InvoiceEntity,
    today: LocalDate = LocalDate.now()
): CollectionState = when {
    invoice.computedStatus(today) == PaymentStatus.PAID -> CollectionState.PAID
    invoice.dueDate.isBefore(today) -> CollectionState.PAYMENT_OVERDUE
    !invoice.collectionDate.isAfter(today) -> CollectionState.COLLECTION_DUE
    else -> CollectionState.NORMAL
}

fun stateLabel(state: CollectionState): String = when (state) {
    CollectionState.PAID -> "Paid"
    CollectionState.NORMAL -> "On Track"
    CollectionState.COLLECTION_DUE -> "Collection Due"
    CollectionState.PAYMENT_OVERDUE -> "Payment Overdue"
}

// ---------------------------------------------------------------------------
// Presentation helpers, kept pure so both the UI and the tests use one source.
// ---------------------------------------------------------------------------

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
private val DISPLAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

fun formatDate(date: LocalDate): String = date.format(DISPLAY_DATE)

fun formatDateOrDash(date: LocalDate?): String = date?.let(::formatDate) ?: "—"

fun formatMonth(month: YearMonth): String = month.atDay(1).format(DISPLAY_MONTH)

/** Lets the search box accept a displayed 10/08/2026 as well as the stored ISO form. */
fun normalizeSearchQuery(value: String): String {
    val clean = value.trim()
    return runCatching { LocalDate.parse(clean, DISPLAY_DATE).toString() }.getOrDefault(clean)
}

fun aed(minor: Long): String = "AED ${money(minor)}"

/** Dashboard counters derived from the same rules used to colour the cards. */
data class DashboardCounts(
    val collectionDue: Int,
    val paymentOverdue: Int
)

fun dashboardCounts(
    rows: List<InvoiceEntity>,
    today: LocalDate = LocalDate.now()
): DashboardCounts = DashboardCounts(
    collectionDue = rows.count { collectionState(it, today) == CollectionState.COLLECTION_DUE },
    paymentOverdue = rows.count { collectionState(it, today) == CollectionState.PAYMENT_OVERDUE }
)
