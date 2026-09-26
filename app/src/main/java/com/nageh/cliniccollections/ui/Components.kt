package com.nageh.cliniccollections.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nageh.cliniccollections.aed
import com.nageh.cliniccollections.collectionState
import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.formatDate
import com.nageh.cliniccollections.formatSuffixEdit
import com.nageh.cliniccollections.normalizePhone
import com.nageh.cliniccollections.reminderMessage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val ButtonHeight = 52.dp
private val ButtonShape = RoundedCornerShape(14.dp)

// --------------------------------------------------------------------------
// Buttons. One height, one radius, one typography across the whole app.
// --------------------------------------------------------------------------

/** Small press feedback: a 2% scale dip. Nothing that draws attention to itself. */
@Composable
private fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    return this.scale(scale)
}

@Composable
fun PrimaryButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
        modifier = modifier.heightIn(min = ButtonHeight).pressFeedback(interaction)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: Color = MaterialTheme.colorScheme.primary
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = ButtonShape,
        border = BorderStroke(1.dp, content.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        modifier = modifier.heightIn(min = ButtonHeight).pressFeedback(interaction)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Destructive actions are always red and always paired with a confirmation dialog. */
@Composable
fun DangerButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = SecondaryButton(text, icon, onClick, modifier, content = DangerRed)

@Composable
fun WhatsAppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    PrimaryButton(text, Icons.AutoMirrored.Filled.Send, onClick, modifier, container = WhatsAppGreen)

// --------------------------------------------------------------------------
// Containers and text
// --------------------------------------------------------------------------

@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall, color = EmeraldDeep)
                }
            }
            content()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = TextMuted,
    modifier = modifier
)

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.width(Space.md))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

@Composable
fun StatusPill(style: StateStyle) {
    Row(
        Modifier
            .background(style.accent.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = Space.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(style.accent, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(style.label, style = MaterialTheme.typography.labelMedium, color = style.onContainer)
    }
}

@Composable
fun SummaryCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = Emerald,
    tint: Color = EmeraldSoft,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        cardModifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(
                Modifier.size(34.dp).background(tint, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(19.dp), tint = accent)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.ReceiptLong,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Box(
                Modifier.size(56.dp).background(EmeraldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(26.dp), tint = Emerald)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// --------------------------------------------------------------------------
// Inputs
// --------------------------------------------------------------------------

@Composable
fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    supporting: String? = null,
    isError: Boolean = false
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = modifier.fillMaxWidth(),
    label = { Text(label) },
    leadingIcon = icon?.let { { Icon(it, null, Modifier.size(19.dp)) } },
    prefix = prefix?.let { { Text(it, fontWeight = FontWeight.Bold) } },
    supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
    isError = isError,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    singleLine = true,
    shape = MaterialTheme.shapes.small
)

/**
 * Invoice suffix input.
 *
 * The prefix is a non-editable slot, the keyboard is numeric, and [formatSuffixEdit]
 * inserts the "/" after the month while placing the caret behind it, so the sequence
 * can be typed without ever reaching for the slash key.
 */
@Composable
fun AppSuffixField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    prefix: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null
) = OutlinedTextField(
    value = value,
    onValueChange = { typed ->
        val edit = formatSuffixEdit(typed.text, typed.selection.start, value.text)
        onChange(TextFieldValue(edit.text, TextRange(edit.caret)))
    },
    modifier = modifier.fillMaxWidth(),
    label = { Text("Invoice number") },
    prefix = { Text(prefix, fontWeight = FontWeight.Bold) },
    supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
    isError = isError,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
    shape = MaterialTheme.shapes.small
)

/**
 * Material 3 date picker. The text field is read-only and the picker's keyboard
 * toggle is disabled, so a date can only ever be chosen, never typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    isError: Boolean = false
) {
    var show by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value?.let(::formatDate) ?: "",
        onValueChange = {},
        readOnly = true,
        modifier = modifier.fillMaxWidth().clickable { show = true },
        label = { Text(label) },
        placeholder = { Text("Select date") },
        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(19.dp)) },
        isError = isError,
        trailingIcon = {
            Row {
                if (allowClear && value != null) {
                    IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Close, "Clear date") }
                }
                IconButton(onClick = { show = true }) {
                    Icon(Icons.Default.CalendarMonth, "Choose date")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small
    )

    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    show = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

// --------------------------------------------------------------------------
// Invoice card
// --------------------------------------------------------------------------

@Composable
fun InvoiceCard(
    invoice: InvoiceEntity,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onOpen: (() -> Unit)? = null
) {
    val style = styleFor(collectionState(invoice, today))
    val base = modifier.fillMaxWidth()

    Card(
        if (onOpen != null) base.clickable(onClick = onOpen) else base,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = style.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(style.accent))
            Column(
                Modifier.padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        invoice.clinicName,
                        style = MaterialTheme.typography.titleMedium,
                        color = style.onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        aed(invoice.dueAmountMinor),
                        style = MaterialTheme.typography.titleMedium,
                        color = style.onContainer
                    )
                }
                Text(
                    invoice.invoiceNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.onContainer
                )
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("COLLECTION", style = MaterialTheme.typography.labelSmall, color = style.onContainer)
                        Text(
                            formatDate(invoice.collectionDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = style.onContainer
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("DUE", style = MaterialTheme.typography.labelSmall, color = style.onContainer)
                        Text(
                            formatDate(invoice.dueDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = style.onContainer
                        )
                    }
                }
                StatusPill(style)
            }
        }
    }
}

// --------------------------------------------------------------------------
// Branding footer. Deliberately only used on About, Reports and the clinic
// directory so it never becomes visual noise.
// --------------------------------------------------------------------------

@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Space.md))
        Text("Developed by Nageh Hosny", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            "+971 50 898 4903",
            style = MaterialTheme.typography.labelLarge,
            color = Emerald,
            modifier = Modifier
                .clickable { openWhatsAppNumber(context, "971508984903") }
                .padding(vertical = 6.dp, horizontal = Space.sm)
        )
        Text("Advance Medical · Private offline app", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

// --------------------------------------------------------------------------
// Intents
// --------------------------------------------------------------------------

fun openWhatsAppInvoice(context: Context, invoice: InvoiceEntity) {
    val encoded = URLEncoder.encode(reminderMessage(invoice), StandardCharsets.UTF_8.name())
    launchView(
        context,
        "https://wa.me/${normalizePhone(invoice.whatsappNumber)}?text=$encoded",
        "No app can open WhatsApp links."
    )
}

fun openWhatsAppNumber(context: Context, phone: String) {
    launchView(context, "https://wa.me/${normalizePhone(phone)}", "No app can open WhatsApp links.")
}

private fun launchView(context: Context, uri: String, failure: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
    }
}
