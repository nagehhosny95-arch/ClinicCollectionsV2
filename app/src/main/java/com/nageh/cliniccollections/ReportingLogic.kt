package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class PeriodPerformance(
    val invoicedMinor: Long,
    val cashCollectedMinor: Long,
    val outstandingIssuedMinor: Long,
    val percentage: BigDecimal?
)

data class MonthPerformance(
    val month: YearMonth,
    val performance: PeriodPerformance
)

enum class TimingState { EARLY, ON_TIME, LATE, OPEN_LATE, UPCOMING }

data class PaymentTiming(
    val state: TimingState,
    val days: Int
)

fun monthlyPerformance(
    rows: List<InvoiceEntity>,
    month: YearMonth
): PeriodPerformance = performance(
    issued = rows.filter { YearMonth.from(it.invoiceDate) == month },
    collected = rows.filter {
        it.actualPaymentDate?.let { date -> YearMonth.from(date) == month } == true
    }
)

fun annualPerformance(
    rows: List<InvoiceEntity>,
    year: Int
): PeriodPerformance = performance(
    issued = rows.filter { it.invoiceDate.year == year },
    collected = rows.filter { it.actualPaymentDate?.year == year }
)

fun monthlyPerformances(rows: List<InvoiceEntity>, year: Int): List<MonthPerformance> =
    (1..12).map { monthNumber ->
        val month = YearMonth.of(year, monthNumber)
        MonthPerformance(month, monthlyPerformance(rows, month))
    }

private fun performance(
    issued: List<InvoiceEntity>,
    collected: List<InvoiceEntity>
): PeriodPerformance {
    val invoicedMinor = issued.sumOf { it.dueAmountMinor }
    val cashCollectedMinor = collected.sumOf(::countedCollectedMinor)
    val outstandingIssuedMinor = issued.sumOf(::outstandingMinor)
    val percentage = if (invoicedMinor == 0L) {
        null
    } else {
        BigDecimal.valueOf(cashCollectedMinor)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(invoicedMinor), 2, RoundingMode.HALF_UP)
    }
    return PeriodPerformance(
        invoicedMinor = invoicedMinor,
        cashCollectedMinor = cashCollectedMinor,
        outstandingIssuedMinor = outstandingIssuedMinor,
        percentage = percentage
    )
}

fun countedCollectedMinor(invoice: InvoiceEntity): Long =
    invoice.collectedAmountMinor.coerceAtLeast(0L).coerceAtMost(invoice.dueAmountMinor)

fun outstandingMinor(invoice: InvoiceEntity): Long =
    (invoice.dueAmountMinor - countedCollectedMinor(invoice)).coerceAtLeast(0L)

fun formatPercentage(value: BigDecimal?): String {
    if (value == null) return "N/A"
    return value.setScale(1, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString() + "%"
}

fun clinicPaymentTiming(
    invoice: InvoiceEntity,
    today: LocalDate = LocalDate.now()
): PaymentTiming {
    val actual = invoice.actualPaymentDate
    if (actual != null) {
        val signedDays = ChronoUnit.DAYS.between(invoice.collectionDate, actual).toInt()
        return when {
            signedDays < 0 -> PaymentTiming(TimingState.EARLY, -signedDays)
            signedDays > 0 -> PaymentTiming(TimingState.LATE, signedDays)
            else -> PaymentTiming(TimingState.ON_TIME, 0)
        }
    }

    val signedDays = ChronoUnit.DAYS.between(invoice.collectionDate, today).toInt()
    return if (signedDays > 0) {
        PaymentTiming(TimingState.OPEN_LATE, signedDays)
    } else {
        PaymentTiming(TimingState.UPCOMING, -signedDays)
    }
}

fun paymentTimingLabel(timing: PaymentTiming): String = when (timing.state) {
    TimingState.EARLY -> "${timing.days} day${plural(timing.days)} early"
    TimingState.ON_TIME -> "Paid on the planned date"
    TimingState.LATE -> "${timing.days} day${plural(timing.days)} late"
    TimingState.OPEN_LATE -> "Open · ${timing.days} day${plural(timing.days)} late"
    TimingState.UPCOMING -> "${timing.days} day${plural(timing.days)} until collection"
}

private fun plural(value: Int): String = if (value == 1) "" else "s"
