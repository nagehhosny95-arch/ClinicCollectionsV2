package com.nageh.cliniccollections.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.InvoiceFilter
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.annualPerformance
import com.nageh.cliniccollections.clinicPaymentTiming
import com.nageh.cliniccollections.data.ClinicDao
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.filterInvoices
import com.nageh.cliniccollections.formatDate
import com.nageh.cliniccollections.formatDateOrDash
import com.nageh.cliniccollections.formatMonth
import com.nageh.cliniccollections.formatPercentage
import com.nageh.cliniccollections.monthlyPerformances
import com.nageh.cliniccollections.paymentTimingLabel
import java.time.LocalDate
import java.time.YearMonth

private enum class ReportMode { YEAR, CLINIC }

@Composable
fun ReportsScreen(
    dao: InvoiceDao,
    clinicDao: ClinicDao,
    contentPadding: PaddingValues,
    onOpenMonth: (YearMonth) -> Unit
) {
    var mode by remember { mutableStateOf(ReportMode.YEAR) }
    val rows by remember { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val clinics by remember { clinicDao.observeAll() }.collectAsStateWithLifecycle(emptyList())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg,
            end = Space.lg,
            top = Space.md,
            bottom = contentPadding.calculateBottomPadding() + Space.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilterChip(
                    selected = mode == ReportMode.YEAR,
                    onClick = { mode = ReportMode.YEAR },
                    label = { Text("Year overview") }
                )
                FilterChip(
                    selected = mode == ReportMode.CLINIC,
                    onClick = { mode = ReportMode.CLINIC },
                    label = { Text("Clinic report") }
                )
            }
        }
        when (mode) {
            ReportMode.YEAR -> yearReport(rows, onOpenMonth)
            ReportMode.CLINIC -> clinicReport(rows, clinics.map { it.id to it.name })
        }
        item { BrandFooter() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.yearReport(
    rows: List<InvoiceEntity>,
    onOpenMonth: (YearMonth) -> Unit
) {
    item {
        var year by remember { mutableStateOf(YearMonth.now().year) }
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { year -= 1 }) {
                        Icon(Icons.Default.ChevronLeft, "Previous year", tint = Emerald)
                    }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
                    IconButton(onClick = { year += 1 }) {
                        Icon(Icons.Default.ChevronRight, "Next year", tint = Emerald)
                    }
                }
            }
            val annual = annualPerformance(rows, year)
            SectionCard("Annual totals", Icons.Default.Payments) {
                InfoRow("Invoices issued", aed(annual.invoicedMinor))
                InfoRow("Cash collected", aed(annual.cashCollectedMinor))
                InfoRow("Collection rate", formatPercentage(annual.percentage))
            }
            SectionLabel("Months")
            monthlyPerformances(rows, year).forEach { month ->
                val performance = month.performance
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenMonth(month.month) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatMonth(month.month), fontWeight = FontWeight.Bold, color = EmeraldDeep)
                            Text(formatPercentage(performance.percentage), fontWeight = FontWeight.Bold, color = Emerald)
                        }
                        InfoRow("Invoiced", aed(performance.invoicedMinor))
                        InfoRow("Collected", aed(performance.cashCollectedMinor))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.clinicReport(
    allRows: List<InvoiceEntity>,
    clinics: List<Pair<Long, String>>
) {
    item {
        var selectedClinicId by remember(clinics) { mutableStateOf(clinics.firstOrNull()?.first) }
        var filter by remember { mutableStateOf(InvoiceFilter.ALL) }
        val today = remember { LocalDate.now() }
        val selectedRows = remember(allRows, selectedClinicId, filter, today) {
            filterInvoices(allRows.filter { it.clinicId == selectedClinicId }, filter, today)
                .sortedByDescending { it.invoiceDate }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            SectionCard("Choose clinic", Icons.Default.LocalHospital) {
                if (clinics.isEmpty()) {
                    Text("No clinics yet", color = TextMuted)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(clinics) { (id, name) ->
                            FilterChip(
                                selected = selectedClinicId == id,
                                onClick = { selectedClinicId = id },
                                label = { Text(name) }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(InvoiceFilter.entries) { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(option.shortLabel()) }
                            )
                        }
                    }
                }
            }
            SectionLabel("Payment history (${selectedRows.size})")
            if (selectedRows.isEmpty()) {
                EmptyState("No matching invoices", "Choose another clinic or status filter.")
            } else {
                selectedRows.forEach { invoice ->
                    SectionCard {
                        Text(invoice.invoiceNumber, fontWeight = FontWeight.Bold, color = EmeraldDeep)
                        InfoRow("Invoice date", formatDate(invoice.invoiceDate))
                        InfoRow("Planned collection", formatDate(invoice.collectionDate))
                        InfoRow("Due date", formatDate(invoice.dueDate))
                        InfoRow("Actual payment", formatDateOrDash(invoice.actualPaymentDate))
                        InfoRow("Invoice amount", aed(invoice.dueAmountMinor))
                        InfoRow("Collected amount", aed(invoice.collectedAmountMinor))
                        InfoRow("Timing", paymentTimingLabel(clinicPaymentTiming(invoice, today)))
                    }
                }
            }
        }
    }
}

private fun InvoiceFilter.shortLabel(): String = when (this) {
    InvoiceFilter.ALL -> "All"
    InvoiceFilter.PAID -> "Paid"
    InvoiceFilter.ON_TRACK -> "On Track"
    InvoiceFilter.COLLECTION_DUE -> "Collection Due"
    InvoiceFilter.PAYMENT_OVERDUE -> "Payment Overdue"
}
