package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class ReportingLogicTest {

    @Test
    fun oldInvoiceCollectionsCountInTheMonthCashPerformance() {
        val rows = listOf(
            invoice("current", 1_000_000_00, LocalDate.of(2026, 9, 2)),
            invoice(
                "old",
                500_000_00,
                LocalDate.of(2026, 7, 2),
                collected = 500_000_00,
                paidOn = LocalDate.of(2026, 9, 10)
            ),
            invoice(
                "current-paid",
                500_000_00,
                LocalDate.of(2026, 9, 3),
                collected = 500_000_00,
                paidOn = LocalDate.of(2026, 9, 20)
            )
        )

        val september = monthlyPerformance(rows, YearMonth.of(2026, 9))

        assertEquals(1_500_000_00L, september.invoicedMinor)
        assertEquals(1_000_000_00L, september.cashCollectedMinor)
        assertEquals(BigDecimal("66.67"), september.percentage)
    }

    @Test
    fun approvedOneMillionExampleProducesOneHundredPercent() {
        val rows = listOf(
            invoice("new-open", 500_000_00, LocalDate.of(2026, 9, 1)),
            invoice(
                "new-paid",
                500_000_00,
                LocalDate.of(2026, 9, 2),
                collected = 500_000_00,
                paidOn = LocalDate.of(2026, 9, 12)
            ),
            invoice(
                "old-paid",
                500_000_00,
                LocalDate.of(2026, 8, 2),
                collected = 500_000_00,
                paidOn = LocalDate.of(2026, 9, 18)
            )
        )

        val result = monthlyPerformance(rows, YearMonth.of(2026, 9))

        assertEquals(1_000_000_00L, result.invoicedMinor)
        assertEquals(1_000_000_00L, result.cashCollectedMinor)
        assertEquals(BigDecimal("100.00"), result.percentage)
        assertEquals("100%", formatPercentage(result.percentage))
    }

    @Test
    fun collectionPercentageMayExceedOneHundredPercent() {
        val rows = listOf(
            invoice("issued", 500_000_00, LocalDate.of(2026, 9, 1)),
            invoice(
                "old",
                700_000_00,
                LocalDate.of(2026, 6, 1),
                collected = 700_000_00,
                paidOn = LocalDate.of(2026, 9, 3)
            )
        )

        val result = monthlyPerformance(rows, YearMonth.of(2026, 9))

        assertEquals(BigDecimal("140.00"), result.percentage)
        assertEquals("140%", formatPercentage(result.percentage))
    }

    @Test
    fun monthWithoutInvoicesKeepsCashAndShowsUnavailablePercentage() {
        val rows = listOf(
            invoice(
                "old",
                90_000_00,
                LocalDate.of(2026, 7, 1),
                collected = 90_000_00,
                paidOn = LocalDate.of(2026, 9, 8)
            )
        )

        val result = monthlyPerformance(rows, YearMonth.of(2026, 9))

        assertEquals(0L, result.invoicedMinor)
        assertEquals(90_000_00L, result.cashCollectedMinor)
        assertNull(result.percentage)
        assertEquals("N/A", formatPercentage(result.percentage))
    }

    @Test
    fun annualPercentageUsesAnnualTotalsNotAverageOfMonthPercentages() {
        val rows = listOf(
            invoice(
                "january",
                100_000_00,
                LocalDate.of(2026, 1, 1),
                collected = 100_000_00,
                paidOn = LocalDate.of(2026, 1, 20)
            ),
            invoice(
                "february",
                300_000_00,
                LocalDate.of(2026, 2, 1),
                collected = 150_000_00,
                paidOn = LocalDate.of(2026, 2, 20)
            )
        )

        val result = annualPerformance(rows, 2026)

        assertEquals(400_000_00L, result.invoicedMinor)
        assertEquals(250_000_00L, result.cashCollectedMinor)
        assertEquals(BigDecimal("62.50"), result.percentage)
    }

    @Test
    fun partialAndOverCollectionsUseSavedAmountCappedAtDue() {
        val rows = listOf(
            invoice(
                "partial",
                100_000,
                LocalDate.of(2026, 9, 1),
                collected = 40_000,
                paidOn = LocalDate.of(2026, 9, 3)
            ),
            invoice(
                "over",
                50_000,
                LocalDate.of(2026, 8, 1),
                collected = 70_000,
                paidOn = LocalDate.of(2026, 9, 4)
            )
        )

        val september = monthlyPerformance(rows, YearMonth.of(2026, 9))

        assertEquals(90_000L, september.cashCollectedMinor)
        assertEquals(60_000L, september.outstandingIssuedMinor)
    }

    @Test
    fun yearAlwaysContainsJanuaryThroughDecember() {
        val months = monthlyPerformances(emptyList(), 2027)

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2027, 1), months.first().month)
        assertEquals(YearMonth.of(2027, 12), months.last().month)
    }

    @Test
    fun paymentTimingComparesActualDateWithPlannedCollectionDate() {
        val planned = LocalDate.of(2026, 9, 10)

        assertEquals(
            PaymentTiming(TimingState.EARLY, 2),
            clinicPaymentTiming(
                invoice("early", 1, planned, paidOn = planned.minusDays(2))
                    .copy(collectionDate = planned),
                planned
            )
        )
        assertEquals(
            PaymentTiming(TimingState.LATE, 4),
            clinicPaymentTiming(
                invoice("late", 1, planned, paidOn = planned.plusDays(4))
                    .copy(collectionDate = planned),
                planned
            )
        )
        assertEquals(
            PaymentTiming(TimingState.OPEN_LATE, 5),
            clinicPaymentTiming(
                invoice("open", 1, planned).copy(collectionDate = planned),
                planned.plusDays(5)
            )
        )
    }

    private fun invoice(
        number: String,
        due: Long,
        issued: LocalDate,
        collected: Long = 0,
        paidOn: LocalDate? = null
    ) = InvoiceEntity(
        clinicName = "Clinic $number",
        whatsappNumber = "971501234567",
        invoiceNumber = "INV/${issued.year}/09/$number",
        dueAmountMinor = due,
        invoiceDate = issued,
        dueDate = issued.plusMonths(2),
        collectionDate = issued.plusMonths(1),
        actualPaymentDate = paidOn,
        collectedAmountMinor = collected
    )
}
