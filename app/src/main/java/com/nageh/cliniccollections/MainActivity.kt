package com.nageh.cliniccollections

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nageh.cliniccollections.data.*
import com.nageh.cliniccollections.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrandGreen = Color(0xFF0B6B4F)
private val DeepGreen = Color(0xFF064E3B)
private val SoftGreen = Color(0xFFE7F6EF)
private val SoftOrange = Color(0xFFFFE8C7)
private val SoftRed = Color(0xFFFFE0E0)
private val SoftBlue = Color(0xFFEAF2F8)
private val DisplayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(applicationContext).invoiceDao()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BrandGreen,
                    secondary = DeepGreen,
                    surfaceVariant = Color(0xFFF1F5F3),
                    background = Color(0xFFF7FAF8)
                )
            ) { App(dao) }
        }
    }
}

sealed interface Screen {
    data object List : Screen
    data class Form(val id: Long? = null) : Screen
    data object Report : Screen
    data object About : Screen
    data class Detail(val id: Long) : Screen
}

@Composable
fun App(dao: InvoiceDao) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    when (val current = screen) {
        Screen.List -> InvoiceList(dao, { screen = Screen.Detail(it) }, { screen = Screen.Form() }, { screen = Screen.Report }, { screen = Screen.About })
        is Screen.Form -> InvoiceForm(dao, current.id) { screen = Screen.List }
        Screen.Report -> ReportScreen(dao) { screen = Screen.List }
        Screen.About -> AboutScreen { screen = Screen.List }
        is Screen.Detail -> DetailScreen(dao, current.id, { screen = Screen.List }, { screen = Screen.Form(current.id) })
    }
}

private enum class CollectionState { PAID, TODAY, OVERDUE, UPCOMING }

private fun collectionState(invoice: InvoiceEntity, today: LocalDate = LocalDate.now()): CollectionState = when {
    invoice.computedStatus(today) == PaymentStatus.PAID -> CollectionState.PAID
    invoice.collectionDate.isBefore(today) -> CollectionState.OVERDUE
    invoice.collectionDate == today -> CollectionState.TODAY
    else -> CollectionState.UPCOMING
}

private fun stateLabel(state: CollectionState) = when (state) {
    CollectionState.PAID -> "Paid"
    CollectionState.TODAY -> "Collection Today"
    CollectionState.OVERDUE -> "Collection Overdue"
    CollectionState.UPCOMING -> "Upcoming"
}

private fun stateColor(state: CollectionState) = when (state) {
    CollectionState.PAID -> SoftGreen
    CollectionState.TODAY -> SoftOrange
    CollectionState.OVERDUE -> SoftRed
    CollectionState.UPCOMING -> SoftBlue
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceList(dao: InvoiceDao, onOpen: (Long) -> Unit, onAdd: () -> Unit, onReport: () -> Unit, onAbout: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val flow = remember(query) { if (query.isBlank()) dao.observeAll() else dao.search(normalizeSearchQuery(query)) }
    val rows by flow.collectAsStateWithLifecycle(emptyList())
    val dashboardRows by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    val totals = remember(dashboardRows) { report(dashboardRows) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Clinic Collections", fontWeight = FontWeight.Bold); Text("Smart collection tracker", style = MaterialTheme.typography.labelMedium) } },
                actions = {
                    IconButton(onClick = onReport) { Icon(Icons.Default.BarChart, "Reports") }
                    IconButton(onClick = onAbout) { Icon(Icons.Default.Info, "About") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftGreen)
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add invoice") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard("Total due", "AED ${money(totals.totalDueMinor)}", Modifier.weight(1f))
                        SummaryCard("Collected", "AED ${money(totals.totalCollectedMinor)}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard("Outstanding", "AED ${money(totals.totalOutstandingMinor)}", Modifier.weight(1f))
                        SummaryCard("Today / Overdue", "${dashboardRows.count { collectionState(it, today) == CollectionState.TODAY }} / ${dashboardRows.count { collectionState(it, today) == CollectionState.OVERDUE }}", Modifier.weight(1f))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search clinic, invoice or date") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    shape = MaterialTheme.shapes.large,
                    singleLine = true
                )
            }
            if (rows.isEmpty()) {
                item { EmptyState(if (query.isBlank()) "No invoices yet" else "No matching invoices", if (query.isBlank()) "Tap Add invoice to create your first collection." else "Try clinic name, invoice suffix, or a date.") }
            } else {
                items(rows, key = { it.id }) { InvoiceCard(it) { onOpen(it.id) } }
            }
            item { Footer() }
        }
    }
}

private fun normalizeSearchQuery(value: String): String {
    val clean = value.trim()
    return runCatching { LocalDate.parse(clean, DisplayDate).toString() }.getOrDefault(clean)
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DeepGreen) }
    }
}

@Composable
private fun InvoiceCard(invoice: InvoiceEntity, onOpen: () -> Unit) {
    val state = collectionState(invoice)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = stateColor(state)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(invoice.clinicName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SuggestionChip(onClick = {}, label = { Text(stateLabel(state)) })
            }
            Text(invoice.invoiceNumber, style = MaterialTheme.typography.bodyMedium)
            Text("AED ${money(invoice.dueAmountMinor)}", style = MaterialTheme.typography.titleMedium, color = DeepGreen, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Collect: ${invoice.collectionDate.format(DisplayDate)}", style = MaterialTheme.typography.bodySmall)
                Text("Due: ${invoice.dueDate.format(DisplayDate)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ReceiptLong, null, tint = BrandGreen, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold); Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceForm(dao: InvoiceDao, invoiceId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<InvoiceEntity?>(null) }
    var loaded by remember { mutableStateOf(invoiceId == null) }
    var clinic by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }; var collection by remember { mutableStateOf<LocalDate?>(null) }
    var paidDate by remember { mutableStateOf<LocalDate?>(null) }; var collected by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }

    LaunchedEffect(invoiceId) {
        if (invoiceId != null) dao.get(invoiceId)?.let { item ->
            original = item; clinic = item.clinicName; phone = item.whatsappNumber; suffix = invoiceSuffix(item.invoiceNumber)
            amount = money(item.dueAmountMinor).replace(",", ""); due = item.dueDate; collection = item.collectionDate
            paidDate = item.actualPaymentDate; collected = if (item.collectedAmountMinor > 0) money(item.collectedAmountMinor).replace(",", "") else ""
        }
        loaded = true
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (invoiceId == null) "Add invoice" else "Edit invoice") }, navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        if (!loaded) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Clinic details")
            Field(clinic, { clinic = it }, "Clinic name")
            Field(phone, { phone = it }, "WhatsApp number", KeyboardType.Phone)
            SectionTitle("Invoice details")
            OutlinedTextField(
                value = suffix,
                onValueChange = { suffix = invoiceSuffix(it).filter { ch -> ch.isDigit() || ch == '/' }.take(7) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Invoice number") },
                prefix = { Text(INVOICE_PREFIX, fontWeight = FontWeight.Bold) },
                supportingText = { Text("Enter only month and sequence, e.g. 08/003") },
                singleLine = true
            )
            Field(amount, { amount = it }, "Due amount (AED)", KeyboardType.Decimal)
            DateField("Due date", due, { due = it })
            DateField("Collection date", collection, { collection = it })
            SectionTitle("Payment (optional)")
            DateField("Actual paid date", paidDate, { paidDate = it }, allowClear = true)
            Field(collected, { collected = it }, "Collected amount", KeyboardType.Decimal)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (saving) return@Button
                    val validation = validateForm(clinic, phone, suffix, amount, due, collection, paidDate, collected)
                    if (validation != null) { error = validation; return@Button }
                    saving = true
                    scope.launch {
                        try {
                            val fullNumber = fullInvoiceNumber(suffix)
                            if (fullNumber != original?.invoiceNumber && dao.countByInvoiceNumber(fullNumber) > 0) error = "Invoice $fullNumber already exists."
                            else {
                                val item = (original ?: InvoiceEntity(clinicName = "", whatsappNumber = "", invoiceNumber = "", dueAmountMinor = 0, dueDate = due!!, collectionDate = collection!!)).copy(
                                    clinicName = clinic.trim(), whatsappNumber = normalizePhone(phone), invoiceNumber = fullNumber,
                                    dueAmountMinor = parseMoney(amount), dueDate = due!!, collectionDate = collection!!,
                                    actualPaymentDate = paidDate, collectedAmountMinor = collected.takeIf { it.isNotBlank() }?.let(::parseMoney) ?: 0L,
                                    manualStatus = if (paidDate != null) PaymentStatus.PAID else original?.manualStatus?.takeIf { it != PaymentStatus.PAID }
                                )
                                val saved = if (original == null) item.copy(id = dao.insert(item)) else item.also { dao.update(it) }
                                if (saved.computedStatus() == PaymentStatus.PAID) ReminderScheduler.cancel(context, saved.id) else ReminderScheduler.schedule(context, saved)
                                onDone()
                            }
                        } catch (e: Exception) { error = e.message ?: "Could not save this invoice." } finally { saving = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saving) "Saving..." else "Save invoice") }
            Footer()
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, color = DeepGreen, fontWeight = FontWeight.Bold) }

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType = KeyboardType.Text) = OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = type), singleLine = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, allowClear: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.format(DisplayDate) ?: "",
        onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().clickable { show = true },
        label = { Text(label) }, placeholder = { Text("Select date") },
        trailingIcon = { Row { if (allowClear && value != null) IconButton({ onChange(null) }) { Icon(Icons.Default.Close, "Clear") }; IconButton({ show = true }) { Icon(Icons.Default.CalendarMonth, "Choose date") } } }
    )
    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; show = false }) { Text("Select") } },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } }
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(dao: InvoiceDao, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val invoice by remember(id) { dao.observe(id) }.collectAsStateWithLifecycle(null)
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Invoice details") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { IconButton(onEdit) { Icon(Icons.Default.Edit, "Edit") }; IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") } }) }) { padding ->
        invoice?.let { item ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InvoiceCard(item) {}; DetailLine("Invoice", item.invoiceNumber); DetailLine("Amount due", "AED ${money(item.dueAmountMinor)}"); DetailLine("Collection date", item.collectionDate.format(DisplayDate)); DetailLine("Due date", item.dueDate.format(DisplayDate)); DetailLine("Paid date", item.actualPaymentDate?.format(DisplayDate) ?: "Not paid"); DetailLine("WhatsApp", item.whatsappNumber)
                Button({ openWhatsApp(context, item) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Message, null); Spacer(Modifier.width(8.dp)); Text("Send WhatsApp reminder") }
                if (item.computedStatus() != PaymentStatus.PAID) OutlinedButton(onClick = { scope.launch { val updated = item.copy(actualPaymentDate = LocalDate.now(), collectedAmountMinor = item.dueAmountMinor, manualStatus = PaymentStatus.PAID); dao.update(updated); ReminderScheduler.cancel(context, item.id) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Mark paid today") }
                Footer()
            }
            if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete invoice?") }, text = { Text("This permanently removes ${item.invoiceNumber} from this device.") }, confirmButton = { TextButton({ scope.launch { ReminderScheduler.cancel(context, item.id); dao.delete(item); onBack() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
        }
    }
}

@Composable private fun DetailLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.Gray); Text(value, fontWeight = FontWeight.SemiBold) } }

private enum class ReportBasis { DUE_DATE, COLLECTION_DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(dao: InvoiceDao, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }; var basis by remember { mutableStateOf(ReportBasis.COLLECTION_DATE) }
    val flow = remember(month, basis) { val start = month.atDay(1); val end = month.plusMonths(1).atDay(1); if (basis == ReportBasis.DUE_DATE) dao.monthByDueDate(start, end) else dao.monthByCollectionDate(start, end) }
    val rows by flow.collectAsStateWithLifecycle(emptyList()); val totals = remember(rows) { report(rows) }
    Scaffold(topBar = { TopAppBar(title = { Text("Monthly report") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }; Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(basis == ReportBasis.COLLECTION_DATE, { basis = ReportBasis.COLLECTION_DATE }, { Text("Collection Date") }); FilterChip(basis == ReportBasis.DUE_DATE, { basis = ReportBasis.DUE_DATE }, { Text("Due Date") }) }; Text("Totals are grouped by ${if (basis == ReportBasis.DUE_DATE) "Due Date" else "Collection Date"}.", style = MaterialTheme.typography.bodySmall) }
            item { ReportMetric("Total due", "AED ${money(totals.totalDueMinor)}"); ReportMetric("Total collected", "AED ${money(totals.totalCollectedMinor)}"); ReportMetric("Outstanding", "AED ${money(totals.totalOutstandingMinor)}"); ReportMetric("Overdue balance", "AED ${money(totals.totalOverdueMinor)}"); ReportMetric("Collections scheduled", totals.scheduledCollections.toString()); ReportMetric("Overdue collections", totals.overdueCollections.toString()) }
            item { Footer() }
        }
    }
}

@Composable private fun ReportMetric(label: String, value: String) { Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold, color = DeepGreen) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AboutScreen(onBack: () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text("About") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = BrandGreen, modifier = Modifier.size(64.dp)); Text("Clinic Collections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Offline payment collection manager"); Spacer(Modifier.height(28.dp)); Footer() } } }

@Composable
private fun Footer() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = Color(0xFFE0E7E3)); Spacer(Modifier.height(14.dp)); Text("Powered by Nageh Hosny", style = MaterialTheme.typography.labelLarge, color = DeepGreen); Text("+971508984903", color = BrandGreen, modifier = Modifier.clickable { openBrandWhatsApp(context) })
    }
}

private fun openBrandWhatsApp(context: Context) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/971508984903"))) }

fun openWhatsApp(context: Context, invoice: InvoiceEntity) {
    val encoded = URLEncoder.encode(reminderMessage(invoice), StandardCharsets.UTF_8.toString())
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${normalizePhone(invoice.whatsappNumber)}?text=$encoded"))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app can open WhatsApp links.", Toast.LENGTH_LONG).show() }
}
