package com.nageh.cliniccollections.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.collectionState
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.data.PaymentStatus
import com.nageh.cliniccollections.formatDate
import com.nageh.cliniccollections.formatDateOrDash
import com.nageh.cliniccollections.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    dao: InvoiceDao,
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val invoice by remember(id) { dao.observe(id) }.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmPaid by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit invoice") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete invoice", tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldSoft,
                    titleContentColor = EmeraldDeep,
                    navigationIconContentColor = EmeraldDeep,
                    actionIconContentColor = EmeraldDeep
                )
            )
        }
    ) { padding ->
        val row = invoice ?: return@Scaffold
        val style = styleFor(collectionState(row, today))
        val isPaid = row.computedStatus(today) == PaymentStatus.PAID

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Space.lg, Space.md, Space.lg, Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item { InvoiceCard(row, today = today) }

            item {
                SectionCard("Clinic information", Icons.Default.LocalHospital) {
                    InfoRow("Clinic", row.clinicName)
                    InfoRow("WhatsApp", row.whatsappNumber)
                }
            }

            item {
                SectionCard("Invoice information", Icons.Default.ReceiptLong) {
                    InfoRow("Invoice number", row.invoiceNumber)
                    InfoRow("Amount due", aed(row.dueAmountMinor))
                    InfoRow("Due date", formatDate(row.dueDate))
                }
            }

            item {
                SectionCard("Collection information", Icons.Default.EventAvailable) {
                    InfoRow("Collection date", formatDate(row.collectionDate))
                    InfoRow(
                        "Reminders",
                        if (isPaid) "Cancelled (invoice paid)" else "09:00 the day before and on the day"
                    )
                }
            }

            item {
                SectionCard("Payment information", Icons.Default.Payments) {
                    InfoRow("Actual paid date", formatDateOrDash(row.actualPaymentDate))
                    InfoRow("Collected", aed(row.collectedAmountMinor))
                    InfoRow(
                        "Remaining",
                        aed((row.dueAmountMinor - row.collectedAmountMinor).coerceAtLeast(0L))
                    )
                }
            }

            item {
                SectionCard("Status") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Current status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        StatusPill(style)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    WhatsAppButton(
                        "Send WhatsApp reminder",
                        { openWhatsAppInvoice(context, row) },
                        Modifier.fillMaxWidth()
                    )
                    if (!isPaid) {
                        PrimaryButton(
                            "Mark as paid today",
                            Icons.Default.CheckCircle,
                            { confirmPaid = true },
                            Modifier.fillMaxWidth(),
                            container = OkGreen
                        )
                    }
                    SecondaryButton("Edit invoice", Icons.Default.Edit, onEdit, Modifier.fillMaxWidth())
                    DangerButton(
                        "Delete invoice",
                        Icons.Default.Delete,
                        { confirmDelete = true },
                        Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Space.sm))
                }
            }
        }

        if (confirmPaid) {
            AlertDialog(
                onDismissRequest = { confirmPaid = false },
                title = { Text("Mark as paid?") },
                text = {
                    Text(
                        "${row.invoiceNumber} will be recorded as fully collected today " +
                            "and its pending reminders will be cancelled."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPaid = false
                        scope.launch {
                            val updated = row.copy(
                                actualPaymentDate = LocalDate.now(),
                                collectedAmountMinor = row.dueAmountMinor,
                                manualStatus = PaymentStatus.PAID
                            )
                            dao.update(updated)
                            ReminderScheduler.cancel(context, row.id)
                        }
                    }) { Text("Mark paid") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmPaid = false }) { Text("Cancel") }
                }
            )
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete invoice?") },
                text = { Text("This permanently removes ${row.invoiceNumber} from this device.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        scope.launch {
                            ReminderScheduler.cancel(context, row.id)
                            dao.delete(row)
                            onBack()
                        }
                    }) { Text("Delete", color = DangerRed) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}
