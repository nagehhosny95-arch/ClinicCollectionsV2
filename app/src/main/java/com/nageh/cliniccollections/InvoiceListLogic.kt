package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import java.time.LocalDate
import java.time.YearMonth

enum class InvoiceFilter {
    ALL,
    ON_TRACK,
    COLLECTION_DUE,
    PAYMENT_OVERDUE,
    PAID
}

enum class DashboardListKind {
    COLLECTION_DUE,
    PAYMENT_OVERDUE,
    COLLECTED_THIS_MONTH
}

fun filterInvoices(
    rows: List<InvoiceEntity>,
    filter: InvoiceFilter,
    today: LocalDate = LocalDate.now()
): List<InvoiceEntity> {
    if (filter == InvoiceFilter.ALL) return rows
    val required = when (filter) {
        InvoiceFilter.ALL -> null
        InvoiceFilter.ON_TRACK -> CollectionState.NORMAL
        InvoiceFilter.COLLECTION_DUE -> CollectionState.COLLECTION_DUE
        InvoiceFilter.PAYMENT_OVERDUE -> CollectionState.PAYMENT_OVERDUE
        InvoiceFilter.PAID -> CollectionState.PAID
    }
    return rows.filter { collectionState(it, today) == required }
}

fun dashboardInvoices(
    rows: List<InvoiceEntity>,
    kind: DashboardListKind,
    today: LocalDate = LocalDate.now(),
    month: YearMonth = YearMonth.from(today)
): List<InvoiceEntity> = when (kind) {
    DashboardListKind.COLLECTION_DUE -> rows
        .filter { collectionState(it, today) == CollectionState.COLLECTION_DUE }
        .sortedWith(compareBy<InvoiceEntity> { it.collectionDate }.thenBy { it.invoiceNumber })

    DashboardListKind.PAYMENT_OVERDUE -> rows
        .filter { collectionState(it, today) == CollectionState.PAYMENT_OVERDUE }
        .sortedWith(compareBy<InvoiceEntity> { it.dueDate }.thenBy { it.invoiceNumber })

    DashboardListKind.COLLECTED_THIS_MONTH -> rows
        .filter { invoice ->
            invoice.actualPaymentDate?.let { YearMonth.from(it) == month } == true
        }
        .sortedWith(
            compareByDescending<InvoiceEntity> { it.actualPaymentDate }
                .thenBy { it.invoiceNumber }
        )
}

fun dashboardListTitle(kind: DashboardListKind): String = when (kind) {
    DashboardListKind.COLLECTION_DUE -> "Collection due"
    DashboardListKind.PAYMENT_OVERDUE -> "Payment overdue"
    DashboardListKind.COLLECTED_THIS_MONTH -> "Collected this month"
}

fun dashboardListAmount(rows: List<InvoiceEntity>, kind: DashboardListKind): Long = when (kind) {
    DashboardListKind.COLLECTION_DUE,
    DashboardListKind.PAYMENT_OVERDUE -> rows.sumOf(::outstandingMinor)

    DashboardListKind.COLLECTED_THIS_MONTH -> rows.sumOf(::countedCollectedMinor)
}
