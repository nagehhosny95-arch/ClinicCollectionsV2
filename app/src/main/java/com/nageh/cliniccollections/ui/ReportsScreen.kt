package com.nageh.cliniccollections.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.collectedInMonth
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.formatMonth
import com.nageh.cliniccollections.report
import java.time.LocalDate
import java.time.YearMonth

private enum class ReportBasis { DUE_DATE, COLLECTION_DATE }

@Composable
fun ReportsScreen(dao: InvoiceDao, contentPadding: PaddingValues) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var basis by remember { mutableStateOf(ReportBasis.COLLECTION_DATE) }
    val today = remember { LocalDate.now() }

    // Identical queries and identical maths as before; only the presentation changed.
    val flow = remember(month, basis) {
        val start = month.atDay(1)
        val end = month.plusMonths(1).atDay(1)
        if (basis == ReportBasis.DUE_DATE) dao.monthByDueDate(start, end)
        else dao.monthByCollectionDate(start, end)
    }
    val rows by flow.collectAsStateWithLifecycle(emptyList())
    val totals = remember(rows, today) { report(rows, today) }

    // Total collected is independent of the Due Date / Collection Date selector: it is
    // always the money actually collected during the selected calendar month, which is
    // why it needs the full invoice list rather than the month-filtered query above.
    val allRows by remember { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val collectedInSelectedMonth = remember(allRows, month) { collectedInMonth(allRows, month) }
    val basisName = if (basis == ReportBasis.DUE_DATE) "Due Date" else "Collection Date"

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
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Previous month", tint = Emerald)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                null,
                                Modifier.height(17.dp),
                                tint = Emerald
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                "  " + formatMonth(month),
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDeep
                            )
                        }
                        Text(
                            "${rows.size} invoice${if (rows.size == 1) "" else "s"} in this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Next month", tint = Emerald)
                    }
                }
            }
        }

        item {
            SectionCard("Group this month by", Icons.Default.EventAvailable) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ReportBasis.entries.forEach { option ->
                        val label = if (option == ReportBasis.DUE_DATE) "Due Date" else "Collection Date"
                        FilterChip(
                            selected = basis == option,
                            onClick = { basis = option },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSoft,
                                selectedLabelColor = EmeraldDeep
                            )
                        )
                    }
                }
                Text(
                    "Due, outstanding, overdue and scheduled collections are grouped by " +
                        "$basisName. Collected in month always follows the Actual " +
                        "Collection Date instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        item { SectionLabel("Amounts") }

        item {
            SummaryCard(
                "Outstanding",
                aed(totals.totalOutstandingMinor),
                Icons.Default.AccountBalanceWallet,
                Modifier.fillMaxWidth(),
                accent = EmeraldDeep
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                SummaryCard(
                    "Total due",
                    aed(totals.totalDueMinor),
                    Icons.Default.ReceiptLong,
                    Modifier.weight(1f)
                )
                SummaryCard(
                    "Collected in month",
                    aed(collectedInSelectedMonth),
                    Icons.Default.TrendingUp,
                    Modifier.weight(1f),
                    accent = OkGreen,
                    tint = OkSoft
                )
            }
        }

        item {
            SummaryCard(
                "Overdue balance",
                aed(totals.totalOverdueMinor),
                Icons.Default.WarningAmber,
                Modifier.fillMaxWidth(),
                accent = DangerRed,
                tint = DangerSoft
            )
        }

        item { SectionLabel("Collections") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                SummaryCard(
                    "Collections scheduled",
                    totals.scheduledCollections.toString(),
                    Icons.Default.EventAvailable,
                    Modifier.weight(1f),
                    accent = WarnOrange,
                    tint = WarnSoft
                )
                SummaryCard(
                    "Overdue collections",
                    totals.overdueCollections.toString(),
                    Icons.Default.WarningAmber,
                    Modifier.weight(1f),
                    accent = DangerRed,
                    tint = DangerSoft
                )
            }
        }

        if (rows.isEmpty()) {
            item {
                EmptyState(
                    "Nothing in ${formatMonth(month)}",
                    "No invoice has a ${basisName.lowercase()} in this month."
                )
            }
        }

        item { BrandFooter() }
    }
}
