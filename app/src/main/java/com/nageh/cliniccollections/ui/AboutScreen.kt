package com.nageh.cliniccollections.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nageh.cliniccollections.INVOICE_PREFIX
import com.nageh.cliniccollections.R

@Composable
fun AboutScreen(contentPadding: PaddingValues) {
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
        Text("Clinic Collections", style = MaterialTheme.typography.headlineSmall, color = EmeraldDeep)
        Text(
            "Smart Payment Tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(Modifier.height(Space.xs))

        SectionCard("About this app", Icons.Default.Info) {
            Text(
                "An offline collection tracker for clinic invoices. Everything is stored " +
                    "in a local database on this device: there is no cloud sync, no backup " +
                    "and no export.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            InfoRow("Invoice prefix", INVOICE_PREFIX)
            InfoRow("Date format", "DD/MM/YYYY")
            InfoRow("Reminders", "10:30, day before and on the day")
            InfoRow("Minimum Android", "Android 10 (API 29)")
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
