package com.nageh.cliniccollections.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.data.ClinicDao
import com.nageh.cliniccollections.data.ClinicEntity
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.normalizePhone
import kotlinx.coroutines.launch

@Composable
fun ClinicsScreen(
    clinicDao: ClinicDao,
    invoiceDao: InvoiceDao,
    contentPadding: PaddingValues,
    onOpenClinic: (Long) -> Unit
) {
    val clinics by clinicDao.observeAll().collectAsStateWithLifecycle(emptyList())
    val invoices by remember { invoiceDao.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var search by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ClinicEntity?>(null) }

    val stats = remember(invoices) { invoices.groupBy { it.clinicId } }
    val visible = remember(clinics, search) {
        val q = search.trim()
        if (q.isBlank()) clinics
        else clinics.filter {
            it.name.contains(q, true) || it.whatsappNumber.contains(normalizePhone(q))
        }
    }

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
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search clinics") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
        }

        item {
            SectionCard(
                title = if (editingId == null) "Register a clinic" else "Edit clinic",
                icon = Icons.Default.PersonAdd
            ) {
                Text(
                    "Save the clinic once, then pick it instantly whenever you add an invoice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                AppField(name, { name = it; error = null }, "Clinic name", icon = Icons.Default.LocalHospital, isError = error != null && name.isBlank())
                AppField(
                    phone,
                    { phone = it; error = null },
                    "WhatsApp number",
                    icon = Icons.Default.Phone,
                    keyboardType = KeyboardType.Phone,
                    supporting = "Local 05x or international 9715x both work"
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PrimaryButton(
                        text = if (editingId == null) "Save clinic" else "Update clinic",
                        icon = Icons.Default.Save,
                        onClick = {
                            val normalized = normalizePhone(phone)
                            when {
                                name.isBlank() -> error = "Clinic name is required."
                                normalized.length < 11 -> error = "Enter a valid UAE mobile number."
                                else -> scope.launch {
                                    clinicDao.save(
                                        ClinicEntity(
                                            id = editingId ?: 0,
                                            name = name.trim(),
                                            whatsappNumber = normalized
                                        )
                                    )
                                    name = ""; phone = ""; error = null; editingId = null
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (editingId != null) {
                        SecondaryButton("Cancel", null, {
                            editingId = null; name = ""; phone = ""; error = null
                        })
                    }
                }
            }
        }

        item { SectionLabel("Saved clinics (${visible.size})") }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    title = if (clinics.isEmpty()) "No clinics yet" else "No matching clinics",
                    message = if (clinics.isEmpty()) {
                        "Register a clinic above. Each clinic can hold an unlimited number of invoices."
                    } else {
                        "Nothing matches \"$search\"."
                    },
                    icon = Icons.Default.LocalHospital
                )
            }
        } else {
            items(visible, key = { it.id }) { clinic ->
                ClinicCard(
                    clinic = clinic,
                    invoices = stats[clinic.id].orEmpty(),
                    onOpen = { onOpenClinic(clinic.id) },
                    onEdit = {
                        editingId = clinic.id
                        name = clinic.name
                        phone = clinic.whatsappNumber
                        error = null
                    },
                    onDelete = { deleteTarget = clinic }
                )
            }
        }

        item { BrandFooter() }
    }

    deleteTarget?.let { clinic ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete clinic?") },
            text = {
                Text("Invoices already saved for ${clinic.name} are kept and will not be deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { clinicDao.delete(clinic); deleteTarget = null }
                }) { Text("Delete", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ClinicCard(
    clinic: ClinicEntity,
    invoices: List<InvoiceEntity>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val outstanding = remember(invoices) {
        invoices.sumOf { (it.dueAmountMinor - it.collectedAmountMinor).coerceAtLeast(0L) }
    }

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(EmeraldSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalHospital, null, Modifier.size(21.dp), tint = Emerald)
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        clinic.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        clinic.whatsappNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit clinic", tint = Emerald)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete clinic", tint = DangerRed)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${invoices.size} invoice${if (invoices.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    "Outstanding ${aed(outstanding)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDeep
                )
            }
        }
    }
}
