package com.nageh.cliniccollections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ReceiptLong
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
import com.nageh.cliniccollections.DashboardListKind
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.dashboardInvoices
import com.nageh.cliniccollections.dashboardListAmount
import com.nageh.cliniccollections.dashboardListTitle
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.formatDate
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredInvoicesScreen(
    dao: InvoiceDao,
    kind: DashboardListKind,
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit
) {
    val all by remember { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val today = remember { LocalDate.now() }
    val month = remember { YearMonth.now() }
    val rows = remember(all, kind, today, month) { dashboardInvoices(all, kind, today, month) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dashboardListTitle(kind)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
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
                SectionCard {
                    InfoRow("Invoices", rows.size.toString())
                    InfoRow("Amount", aed(dashboardListAmount(rows, kind)))
                }
            }
            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        title = "No invoices",
                        message = "There are no invoices in this list right now.",
                        icon = Icons.Default.ReceiptLong
                    )
                }
            } else {
                items(rows, key = { it.id }) { invoice ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        if (kind == DashboardListKind.COLLECTED_THIS_MONTH) {
                            Text(
                                "Paid ${formatDate(invoice.actualPaymentDate!!)}",
                                color = OkGreen
                            )
                        }
                        InvoiceCard(invoice, today = today) { onOpenInvoice(invoice.id) }
                    }
                }
            }
        }
    }
}
