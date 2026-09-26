package com.nageh.cliniccollections.backup

import android.content.Context
import android.net.Uri
import com.nageh.cliniccollections.data.AppDatabase
import com.nageh.cliniccollections.data.ClinicEntity
import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.data.PaymentStatus
import com.nageh.cliniccollections.data.PaymentTerm
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class BackupSnapshot(
    val clinics: List<ClinicEntity>,
    val invoices: List<InvoiceEntity>
)

@Serializable
private data class BackupDocument(
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val clinics: List<BackupClinic>,
    val invoices: List<BackupInvoice>
)

@Serializable
private data class BackupClinic(
    val id: Long,
    val name: String,
    val whatsappNumber: String,
    val createdAtEpochMillis: Long
)

@Serializable
private data class BackupInvoice(
    val id: Long,
    val clinicId: Long?,
    val clinicName: String,
    val whatsappNumber: String,
    val invoiceNumber: String,
    val dueAmountMinor: Long,
    val invoiceDate: String,
    val dueDate: String,
    val collectionDate: String,
    val actualPaymentDate: String?,
    val collectedAmountMinor: Long,
    val paymentTerm: String,
    val customTermDays: Int?,
    val manualStatus: String?,
    val createdAtEpochMillis: Long
)

object BackupCodec {
    private const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    fun encode(clinics: List<ClinicEntity>, invoices: List<InvoiceEntity>): String =
        json.encodeToString(
            BackupDocument(
                schemaVersion = VERSION,
                exportedAtEpochMillis = System.currentTimeMillis(),
                clinics = clinics.map {
                    BackupClinic(it.id, it.name, it.whatsappNumber, it.createdAtEpochMillis)
                },
                invoices = invoices.map {
                    BackupInvoice(
                        it.id, it.clinicId, it.clinicName, it.whatsappNumber,
                        it.invoiceNumber, it.dueAmountMinor, it.invoiceDate.toString(),
                        it.dueDate.toString(), it.collectionDate.toString(),
                        it.actualPaymentDate?.toString(), it.collectedAmountMinor,
                        it.paymentTerm.name, it.customTermDays, it.manualStatus?.name,
                        it.createdAtEpochMillis
                    )
                }
            )
        )

    fun decode(value: String): BackupSnapshot {
        val document = try {
            json.decodeFromString<BackupDocument>(value)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("This is not a valid Clinic Collections backup.", error)
        }
        require(document.schemaVersion == VERSION) { "Unsupported backup version." }
        val clinics = document.clinics.map {
            require(it.id > 0 && it.name.isNotBlank()) { "Invalid clinic in backup." }
            ClinicEntity(it.id, it.name, it.whatsappNumber, it.createdAtEpochMillis)
        }
        require(clinics.map { it.id }.distinct().size == clinics.size) { "Duplicate clinic IDs." }
        val clinicIds = clinics.mapTo(mutableSetOf()) { it.id }
        val invoices = document.invoices.map {
            require(
                it.id > 0 && it.clinicName.isNotBlank() && it.invoiceNumber.isNotBlank() &&
                    it.dueAmountMinor >= 0 && it.collectedAmountMinor >= 0
            ) {
                "Invalid invoice in backup."
            }
            require(it.clinicId == null || it.clinicId in clinicIds) { "Missing clinic relation." }
            InvoiceEntity(
                id = it.id,
                clinicId = it.clinicId,
                clinicName = it.clinicName,
                whatsappNumber = it.whatsappNumber,
                invoiceNumber = it.invoiceNumber,
                dueAmountMinor = it.dueAmountMinor,
                invoiceDate = LocalDate.parse(it.invoiceDate),
                dueDate = LocalDate.parse(it.dueDate),
                collectionDate = LocalDate.parse(it.collectionDate),
                actualPaymentDate = it.actualPaymentDate?.let(LocalDate::parse),
                collectedAmountMinor = it.collectedAmountMinor,
                paymentTerm = PaymentTerm.valueOf(it.paymentTerm),
                customTermDays = it.customTermDays,
                manualStatus = it.manualStatus?.let(PaymentStatus::valueOf),
                createdAtEpochMillis = it.createdAtEpochMillis
            )
        }
        require(invoices.map { it.id }.distinct().size == invoices.size) { "Duplicate invoice IDs." }
        require(invoices.map { it.invoiceNumber }.distinct().size == invoices.size) {
            "Duplicate invoice numbers."
        }
        return BackupSnapshot(clinics, invoices)
    }
}

class BackupRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        val text = BackupCodec.encode(dao.clinics(), dao.invoices())
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
            it.write(text)
        }
    }

    suspend fun import(uri: Uri): BackupSnapshot = withContext(Dispatchers.IO) {
        val text = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use {
            it.readText()
        }
        val snapshot = BackupCodec.decode(text)
        database.backupDao().replaceAll(snapshot.clinics, snapshot.invoices)
        snapshot
    }
}
