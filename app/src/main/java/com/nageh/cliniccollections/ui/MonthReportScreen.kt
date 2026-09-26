package com.nageh.cliniccollections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.countedCollectedMinor
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.formatDate
import com.nageh.cliniccollections.formatMonth
import com.nageh.cliniccollections.formatPercentage
import com.nageh.cliniccollections.monthlyPerformance
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthReportScreen(dao: InvoiceDao, month: YearMonth, onBack: () -> Unit) {
    val all by remember { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val performance = remember(all, month) { monthlyPerformance(all, month) }
    val issued = remember(all, month) {
        all.filter { YearMonth.from(it.invoiceDate) == month }.sortedByDescending { it.invoiceDate }
    }
    val collected = remember(all, month) {
        all.filter { invoice ->
            invoice.actualPaymentDate?.let { date -> YearMonth.from(date) } == month
        }
            .sortedByDescending { it.actualPaymentDate }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatMonth(month)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldSoft,
                    titleContentColor = EmeraldDeep,
                    navigationIconContentColor = EmeraldDeep
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item {
                SectionCard("Month totals", Icons.Default.Payments) {
                    InfoRow("Invoices issued", aed(performance.invoicedMinor))
                    InfoRow("Cash collected", aed(performance.cashCollectedMinor))
                    InfoRow("Collection rate", formatPercentage(performance.percentage))
                }
            }
            item { SectionLabel("Invoices issued (${issued.size})") }
            if (issued.isEmpty()) {
                item { EmptyState("No invoices issued", "Cash collected can still include older invoices.") }
            } else {
                items(issued, key = { "issued-${it.id}" }) { invoice ->
                    SectionCard {
                        Text(invoice.clinicName, color = EmeraldDeep)
                        InfoRow("Invoice", invoice.invoiceNumber)
                        InfoRow("Invoice date", formatDate(invoice.invoiceDate))
                        InfoRow("Amount", aed(invoice.dueAmountMinor))
                    }
                }
            }
            item { SectionLabel("Cash collected (${collected.size})") }
            if (collected.isEmpty()) {
                item { EmptyState("No cash collected", "No Actual Collection Date falls in this month.") }
            } else {
                items(collected, key = { "collected-${it.id}" }) { invoice ->
                    SectionCard {
                        Text(invoice.clinicName, color = EmeraldDeep)
                        InfoRow("Invoice", invoice.invoiceNumber)
                        InfoRow("Actual payment", formatDate(invoice.actualPaymentDate!!))
                        InfoRow("Collected", aed(countedCollectedMinor(invoice)))
                    }
                }
            }
        }
    }
}
