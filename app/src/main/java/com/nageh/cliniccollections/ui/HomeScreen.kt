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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.R
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.dashboardCounts
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.formatMonth
import com.nageh.cliniccollections.normalizeSearchQuery
import com.nageh.cliniccollections.report
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    dao: InvoiceDao,
    contentPadding: PaddingValues,
    onOpenInvoice: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchFlow = remember(query) {
        if (query.isBlank()) dao.observeAll() else dao.search(normalizeSearchQuery(query))
    }
    val rows by searchFlow.collectAsStateWithLifecycle(emptyList())
    val all by remember { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())

    val today = remember { LocalDate.now() }
    val totals = remember(all, today) { report(all, today) }
    val counts = remember(all, today) { dashboardCounts(all, today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg,
            end = Space.lg,
            top = Space.md,
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item { BrandedHeader(invoiceCount = all.size) }

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
                    "Total collected",
                    aed(totals.totalCollectedMinor),
                    Icons.Default.TrendingUp,
                    Modifier.weight(1f),
                    accent = OkGreen,
                    tint = OkSoft
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                SummaryCard(
                    "Collection due",
                    counts.collectionDue.toString(),
                    Icons.Default.EventAvailable,
                    Modifier.weight(1f),
                    accent = WarnOrange,
                    tint = WarnSoft
                )
                SummaryCard(
                    "Payment overdue",
                    counts.paymentOverdue.toString(),
                    Icons.Default.WarningAmber,
                    Modifier.weight(1f),
                    accent = DangerRed,
                    tint = DangerSoft
                )
            }
        }

        item {
            Spacer(Modifier.height(Space.xs))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search clinic, invoice or date") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
        }

        item {
            SectionLabel(if (query.isBlank()) "All invoices (${rows.size})" else "Results (${rows.size})")
        }

        if (rows.isEmpty()) {
            item {
                EmptyState(
                    title = if (query.isBlank()) "No invoices yet" else "No matching invoices",
                    message = if (query.isBlank()) {
                        "Tap Add invoice to record your first clinic collection."
                    } else {
                        "Try a clinic name, an invoice suffix such as 08/003, or a date like 10/08/2026."
                    }
                )
            }
        } else {
            items(rows, key = { it.id }) { invoice ->
                InvoiceCard(invoice, today = today) { onOpenInvoice(invoice.id) }
            }
        }
    }
}

/** Clean branded header: logo mark, greeting, and the current month. */
@Composable
private fun BrandedHeader(invoiceCount: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).background(EmeraldInk, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text("Clinic Collections", style = MaterialTheme.typography.titleLarge, color = EmeraldDeep)
            Text(
                "$invoiceCount invoice${if (invoiceCount == 1) "" else "s"} tracked",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Box(
            Modifier
                .background(EmeraldSoft, RoundedCornerShape(10.dp))
                .padding(horizontal = Space.md, vertical = 6.dp)
        ) {
            Text(
                formatMonth(YearMonth.now()),
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldDeep
            )
        }
    }
}
