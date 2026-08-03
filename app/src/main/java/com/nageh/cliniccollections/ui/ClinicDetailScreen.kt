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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.data.ClinicDao
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.report
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicDetailScreen(
    invoiceDao: InvoiceDao,
    clinicDao: ClinicDao,
    clinicId: Long,
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onAddInvoice: () -> Unit
) {
    val clinic by remember(clinicId) { clinicDao.observe(clinicId) }
        .collectAsStateWithLifecycle(null)
    val invoices by remember(clinicId) { invoiceDao.observeForClinic(clinicId) }
        .collectAsStateWithLifecycle(emptyList())
    val today = remember { LocalDate.now() }
    val totals = remember(invoices, today) { report(invoices, today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        clinic?.name ?: "Clinic",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddInvoice,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add invoice") },
                containerColor = Emerald,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Space.lg, Space.md, Space.lg, 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).background(EmeraldSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocalHospital, null, Modifier.size(23.dp), tint = Emerald)
                        }
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                clinic?.name.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDeep
                            )
                            Text(
                                clinic?.whatsappNumber.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                    InfoRow(
                        "Invoices",
                        "${invoices.size} invoice${if (invoices.size == 1) "" else "s"}"
                    )
                    InfoRow("Total due", aed(totals.totalDueMinor))
                    InfoRow("Collected", aed(totals.totalCollectedMinor))
                    InfoRow("Outstanding", aed(totals.totalOutstandingMinor))
                }
            }

            item { SectionLabel("Invoices for this clinic") }

            if (invoices.isEmpty()) {
                item {
                    EmptyState(
                        "No invoices for this clinic",
                        "Tap Add invoice. The clinic name and WhatsApp number are filled in automatically."
                    )
                }
            } else {
                items(invoices, key = { it.id }) { invoice ->
                    InvoiceCard(invoice, today = today) { onOpenInvoice(invoice.id) }
                }
            }
        }
    }
}
