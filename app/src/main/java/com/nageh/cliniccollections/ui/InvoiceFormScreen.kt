package com.nageh.cliniccollections.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.data.ClinicDao
import com.nageh.cliniccollections.data.ClinicEntity
import com.nageh.cliniccollections.data.InvoiceDao
import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.data.PaymentStatus
import com.nageh.cliniccollections.hasValidInvoiceMonth
import com.nageh.cliniccollections.fullInvoiceNumber
import com.nageh.cliniccollections.invoiceSuffix
import com.nageh.cliniccollections.invoicePrefix
import com.nageh.cliniccollections.money
import com.nageh.cliniccollections.normalizePhone
import com.nageh.cliniccollections.parseMoney
import com.nageh.cliniccollections.reminders.ReminderScheduler
import com.nageh.cliniccollections.validInvoiceSuffix
import com.nageh.cliniccollections.validateForm
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Add / edit invoice. The save path, clinic linking, prefix handling and reminder
 * rescheduling are unchanged from the previous version; only the layout, grouping
 * and per-field error highlighting are new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceFormScreen(
    dao: InvoiceDao,
    clinicDao: ClinicDao,
    invoiceId: Long?,
    presetClinicId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<InvoiceEntity?>(null) }
    var loaded by remember { mutableStateOf(invoiceId == null) }
    var clinic by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var suffixField by remember { mutableStateOf(TextFieldValue("")) }
    var invoiceDate by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var collection by remember { mutableStateOf<LocalDate?>(null) }
    var paidDate by remember { mutableStateOf<LocalDate?>(null) }
    var collected by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var selectedClinicId by remember { mutableStateOf(presetClinicId) }
    val suffix = suffixField.text

    val savedClinics by clinicDao.observeAll().collectAsStateWithLifecycle(emptyList())
    val clinicMatches = remember(clinic, savedClinics) {
        if (clinic.trim().length < 2) emptyList()
        else savedClinics.filter {
            it.name.contains(clinic.trim(), ignoreCase = true) &&
                !it.name.equals(clinic.trim(), ignoreCase = true)
        }.take(6)
    }

    LaunchedEffect(invoiceId, presetClinicId) {
        if (invoiceId != null) {
            dao.get(invoiceId)?.let { item ->
                original = item
                clinic = item.clinicName
                phone = item.whatsappNumber
                val existingSuffix = invoiceSuffix(item.invoiceNumber)
                suffixField = TextFieldValue(existingSuffix, TextRange(existingSuffix.length))
                invoiceDate = item.invoiceDate
                selectedClinicId = item.clinicId
                amount = money(item.dueAmountMinor).replace(",", "")
                due = item.dueDate
                collection = item.collectionDate
                paidDate = item.actualPaymentDate
                collected = if (item.collectedAmountMinor > 0) {
                    money(item.collectedAmountMinor).replace(",", "")
                } else {
                    ""
                }
            }
        } else if (presetClinicId != null) {
            clinicDao.get(presetClinicId)?.let { saved ->
                clinic = saved.name
                phone = saved.whatsappNumber
                selectedClinicId = saved.id
            }
        }
        loaded = true
    }

    // Highlighting only. The save gate is still validateForm(), unchanged.
    val clinicError = submitted && clinic.isBlank()
    val suffixError = submitted && !validInvoiceSuffix(suffix)
    val phoneError = submitted && normalizePhone(phone).length < 8
    val amountError = submitted &&
        (runCatching { parseMoney(amount) }.getOrNull() ?: 0L) <= 0L
    val dueError = submitted && due == null
    val collectionError = submitted && collection == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (invoiceId == null) "Add invoice" else "Edit invoice") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
        if (!loaded) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Emerald) }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            SectionCard("Clinic details", Icons.Default.LocalHospital) {
                AppField(
                    clinic,
                    { clinic = it; selectedClinicId = null },
                    "Clinic name",
                    icon = Icons.Default.LocalHospital,
                    isError = clinicError,
                    supporting = if (clinicError) "Clinic name is required." else null
                )

                if (clinicMatches.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(EmeraldSoft, MaterialTheme.shapes.small)
                    ) {
                        clinicMatches.forEachIndexed { index, saved ->
                            if (index > 0) HorizontalDivider(color = Color(0x1A064E3B))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clinic = saved.name
                                        phone = saved.whatsappNumber
                                        selectedClinicId = saved.id
                                    }
                                    .padding(Space.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(28.dp).background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocalHospital,
                                        null,
                                        Modifier.size(15.dp),
                                        tint = Emerald
                                    )
                                }
                                Spacer(Modifier.width(Space.md))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        saved.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldDeep
                                    )
                                    Text(
                                        saved.whatsappNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                AppField(
                    phone,
                    { phone = it },
                    "WhatsApp number",
                    icon = Icons.Default.Phone,
                    keyboardType = KeyboardType.Phone,
                    isError = phoneError,
                    supporting = if (phoneError) {
                        "Enter a valid number, e.g. 0501234567 or 971501234567."
                    } else {
                        "Local 05x or international 9715x both work"
                    }
                )
            }

            SectionCard("Invoice details", Icons.Default.ReceiptLong) {
                AppDateField(
                    "Invoice Date",
                    invoiceDate,
                    { selected -> if (selected != null) invoiceDate = selected }
                )
                Text(
                    "Controls the invoice month report and the year in ${invoicePrefix(invoiceDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                AppSuffixField(
                    value = suffixField,
                    onChange = { suffixField = it },
                    prefix = invoicePrefix(invoiceDate),
                    isError = suffixError,
                    supporting = if (suffixError) {
                        if (!hasValidInvoiceMonth(suffix)) {
                            "The month must be between 01 and 12."
                        } else {
                            "Add the sequence after the slash, e.g. 08/3 or 08/1234."
                        }
                    } else {
                        "Type the month, the slash is added for you. Example: 08/003"
                    }
                )
                AppField(
                    amount,
                    { amount = it },
                    "Due amount (AED)",
                    icon = Icons.Default.AttachMoney,
                    keyboardType = KeyboardType.Decimal,
                    isError = amountError,
                    supporting = if (amountError) "Enter an amount greater than zero." else null
                )
            }

            SectionCard("Collection schedule", Icons.Default.Payments) {
                AppDateField("Due date", due, { due = it }, isError = dueError)
                AppDateField(
                    "Collection date",
                    collection,
                    { collection = it },
                    isError = collectionError
                )
                Text(
                    "Reminders are scheduled at 10:30 the day before and on the collection date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            SectionCard("Payment (optional)", Icons.Default.Payments) {
                Text(
                    "The Actual Collection Date decides which month the collected amount " +
                        "is reported in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                AppDateField("Actual Collection Date", paidDate, { paidDate = it }, allowClear = true)
                AppField(
                    collected,
                    { collected = it },
                    "Collected amount (AED)",
                    icon = Icons.Default.AttachMoney,
                    keyboardType = KeyboardType.Decimal
                )
            }

            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = DangerRed)
            }

            PrimaryButton(
                text = if (saving) "Saving…" else "Save invoice",
                icon = Icons.Default.Save,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (saving) return@PrimaryButton
                    submitted = true
                    val validation =
                        validateForm(clinic, phone, suffix, amount, due, collection, paidDate, collected)
                    if (validation != null) {
                        error = validation
                        return@PrimaryButton
                    }
                    error = null
                    saving = true
                    scope.launch {
                        try {
                            val fullNumber = fullInvoiceNumber(invoiceDate, suffix)
                            if (fullNumber != original?.invoiceNumber &&
                                dao.countByInvoiceNumber(fullNumber) > 0
                            ) {
                                error = "Invoice $fullNumber already exists."
                            } else {
                                val savedClinicId = clinicDao.save(
                                    ClinicEntity(
                                        id = selectedClinicId ?: 0,
                                        name = clinic.trim(),
                                        whatsappNumber = normalizePhone(phone)
                                    )
                                )
                                val item = (
                                    original ?: InvoiceEntity(
                                        clinicName = "",
                                        whatsappNumber = "",
                                        invoiceNumber = "",
                                        dueAmountMinor = 0,
                                        dueDate = due!!,
                                        collectionDate = collection!!
                                    )
                                    ).copy(
                                    clinicId = savedClinicId,
                                    clinicName = clinic.trim(),
                                    whatsappNumber = normalizePhone(phone),
                                    invoiceNumber = fullNumber,
                                    dueAmountMinor = parseMoney(amount),
                                    invoiceDate = invoiceDate,
                                    dueDate = due!!,
                                    collectionDate = collection!!,
                                    actualPaymentDate = paidDate,
                                    collectedAmountMinor = collected
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::parseMoney) ?: 0L,
                                    manualStatus = if (paidDate != null) {
                                        PaymentStatus.PAID
                                    } else {
                                        original?.manualStatus?.takeIf { it != PaymentStatus.PAID }
                                    }
                                )
                                val saved = if (original == null) {
                                    item.copy(id = dao.insert(item))
                                } else {
                                    item.also { dao.update(it) }
                                }
                                if (saved.computedStatus() == PaymentStatus.PAID) {
                                    ReminderScheduler.cancel(context, saved.id)
                                } else {
                                    ReminderScheduler.schedule(context, saved)
                                }
                                onDone()
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Could not save this invoice."
                        } finally {
                            saving = false
                        }
                    }
                }
            )

            Spacer(Modifier.height(Space.xl))
        }
    }
}
