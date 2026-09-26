package com.nageh.cliniccollections.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nageh.cliniccollections.BuildConfig
import com.nageh.cliniccollections.R
import com.nageh.cliniccollections.backup.BackupRepository
import com.nageh.cliniccollections.data.AppDatabase
import com.nageh.cliniccollections.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AboutScreen(database: AppDatabase, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(database, context) { BackupRepository(context, database) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { repository.export(uri) }
                .onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> restoreUri = uri }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = Space.lg,
                end = Space.lg,
                top = Space.lg,
                bottom = contentPadding.calculateBottomPadding() + Space.lg
            ),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(92.dp).background(EmeraldInk, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(60.dp)
            )
        }
        Text("Advance Medical", style = MaterialTheme.typography.headlineSmall, color = EmeraldDeep)
        Text(
            "Clinic Collections · V${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(Modifier.height(Space.xs))

        SectionCard("About this app", Icons.Default.Info) {
            Text(
                "A private, offline collection tracker for Advance Medical. Data stays " +
                    "on this device unless you explicitly create a backup file.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            InfoRow("Invoice prefix", "INV/[invoice year]/")
            InfoRow("Date format", "DD/MM/YYYY")
            InfoRow("Reminders", "10:30, day before and on the day")
        }

        SectionCard("Backup and restore", Icons.Default.Save) {
            Text(
                "Save a backup before installing an update or changing phones. Restore replaces " +
                    "the current clinics and invoices with the selected backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            PrimaryButton(
                text = "Create backup",
                icon = Icons.Default.Save,
                onClick = {
                    exportLauncher.launch("clinic-collections-${LocalDate.now()}.json")
                },
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton(
                text = "Restore backup",
                icon = Icons.Default.Restore,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard("What the card colours mean", Icons.Default.Palette) {
            ColourNote(OkSoft, OkGreen, "Green", "Paid, or on track.")
            ColourNote(
                WarnSoft,
                WarnOrange,
                "Orange",
                "The collection date has arrived or passed, while the due date has not."
            )
            ColourNote(
                DangerSoft,
                DangerRed,
                "Red",
                "The due date has passed and the invoice is still unpaid."
            )
        }

        SectionCard("How reminders work", Icons.Default.EventAvailable) {
            Text(
                "Two alarms are set for every unpaid invoice: one at 10:30 the day before " +
                    "the collection date, and one at 10:30 on the collection date itself. " +
                    "They survive a reboot, and they are cancelled as soon as an invoice is " +
                    "marked as paid.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        BrandFooter()
    }

    val selectedRestore = restoreUri
    if (selectedRestore != null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Replace current data?") },
            text = { Text("Restore will replace every clinic and invoice currently saved on this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    scope.launch {
                        runCatching { repository.import(selectedRestore) }
                            .onSuccess { snapshot ->
                                ReminderScheduler.rescheduleAll(context, snapshot.invoices)
                                Toast.makeText(context, "Backup restored", Toast.LENGTH_LONG).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ColourNote(container: Color, accent: Color, title: String, message: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(18.dp).background(container, RoundedCornerShape(6.dp))) {
            Box(Modifier.size(18.dp).padding(5.dp).background(accent, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.size(Space.md))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Start
            )
        }
    }
}
