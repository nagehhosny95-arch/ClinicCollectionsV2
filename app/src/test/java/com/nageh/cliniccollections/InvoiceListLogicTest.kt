package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InvoiceListLogicTest {

    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun everyQuickFilterUsesTheCardStatusRules() {
        val rows = sampleRows()

        assertEquals(5, filterInvoices(rows, InvoiceFilter.ALL, today).size)
        assertEquals(listOf("track"), numbers(filterInvoices(rows, InvoiceFilter.ON_TRACK, today)))
        assertEquals(listOf("due"), numbers(filterInvoices(rows, InvoiceFilter.COLLECTION_DUE, today)))
        assertEquals(listOf("overdue"), numbers(filterInvoices(rows, InvoiceFilter.PAYMENT_OVERDUE, today)))
        assertEquals(listOf("paid", "paid-old"), numbers(filterInvoices(rows, InvoiceFilter.PAID, today)))
    }

    @Test
    fun collectionDueSortsOldestPlannedDateFirst() {
        val rows = listOf(
            invoice("recent", today.plusDays(20), today.minusDays(1)),
            invoice("oldest", today.plusDays(20), today.minusDays(12)),
            invoice("middle", today.plusDays(20), today.minusDays(5))
        )

        val result = dashboardInvoices(
            rows,
            DashboardListKind.COLLECTION_DUE,
            today,
            YearMonth.from(today)
        )

        assertEquals(listOf("oldest", "middle", "recent"), numbers(result))
    }

    @Test
    fun paymentOverdueSortsOldestDueDateFirst() {
        val rows = listOf(
            invoice("recent", today.minusDays(1), today.minusDays(10)),
            invoice("oldest", today.minusDays(20), today.minusDays(30)),
            invoice("middle", today.minusDays(7), today.minusDays(15))
        )

        val result = dashboardInvoices(
            rows,
            DashboardListKind.PAYMENT_OVERDUE,
            today,
            YearMonth.from(today)
        )

        assertEquals(listOf("oldest", "middle", "recent"), numbers(result))
    }

    @Test
    fun collectedThisMonthUsesActualDateAndSortsNewestFirst() {
        val rows = listOf(
            invoice("older", today.plusDays(5), today, paidOn = LocalDate.of(2026, 9, 2), collected = 100),
            invoice("newest", today.plusDays(5), today, paidOn = LocalDate.of(2026, 9, 25), collected = 100),
            invoice("other-month", today.plusDays(5), today, paidOn = LocalDate.of(2026, 8, 30), collected = 100),
            invoice("no-date", today.plusDays(5), today, paidOn = null, collected = 100)
        )

        val result = dashboardInvoices(
            rows,
            DashboardListKind.COLLECTED_THIS_MONTH,
            today,
            YearMonth.of(2026, 9)
        )

        assertEquals(listOf("newest", "older"), numbers(result))
    }

    @Test
    fun paidAlwaysWinsOverAnOldDueDate() {
        val paidOverdue = invoice(
            "paid",
            today.minusDays(30),
            today.minusDays(40),
            paidOn = today,
            collected = 100
        )

        assertEquals(listOf("paid"), numbers(filterInvoices(listOf(paidOverdue), InvoiceFilter.PAID, today)))
        assertEquals(emptyList<String>(), numbers(filterInvoices(listOf(paidOverdue), InvoiceFilter.PAYMENT_OVERDUE, today)))
    }

    private fun sampleRows() = listOf(
        invoice("track", today.plusDays(30), today.plusDays(5)),
        invoice("due", today.plusDays(10), today.minusDays(2)),
        invoice("overdue", today.minusDays(1), today.minusDays(5)),
        invoice("paid", today.minusDays(10), today.minusDays(15), today, 100),
        invoice("paid-old", today.plusDays(10), today.plusDays(2), today.minusDays(2), 100)
    )

    private fun invoice(
        number: String,
        dueDate: LocalDate,
        collectionDate: LocalDate,
        paidOn: LocalDate? = null,
        collected: Long = 0
    ) = InvoiceEntity(
        clinicName = number,
        whatsappNumber = "971501234567",
        invoiceNumber = number,
        dueAmountMinor = 100,
        invoiceDate = LocalDate.of(2026, 9, 1),
        dueDate = dueDate,
        collectionDate = collectionDate,
        actualPaymentDate = paidOn,
        collectedAmountMinor = collected
    )

    private fun numbers(rows: List<InvoiceEntity>) = rows.map { it.invoiceNumber }
}
