package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.data.MonthlyTotals
import com.nageh.cliniccollections.data.PaymentStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
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
    Regex("^(0[1-9]|1[0-2])/\\d{3,}$").matches(invoiceSuffix(value))

fun normalizePhone(value: String): String = value.filter(Char::isDigit).removePrefix("00")

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
    if (!validInvoiceSuffix(invoiceSuffix)) return "Invoice suffix must look like 08/003."
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
