package com.nageh.cliniccollections

import com.nageh.cliniccollections.backup.BackupCodec
import com.nageh.cliniccollections.data.ClinicEntity
import com.nageh.cliniccollections.data.InvoiceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    @Test
    fun roundTripPreservesInvoiceDateAndRelations() {
        val clinic = ClinicEntity(id = 7, name = "Advance Clinic", whatsappNumber = "971500000000")
        val invoice = InvoiceEntity(
            id = 9,
            clinicId = 7,
            clinicName = clinic.name,
            whatsappNumber = clinic.whatsappNumber,
            invoiceNumber = "INV/2027/01/001",
            dueAmountMinor = 250_000,
            invoiceDate = LocalDate.of(2027, 1, 2),
            collectionDate = LocalDate.of(2027, 2, 2),
            dueDate = LocalDate.of(2027, 3, 2)
        )

        val restored = BackupCodec.decode(BackupCodec.encode(listOf(clinic), listOf(invoice)))

        assertEquals(listOf(clinic), restored.clinics)
        assertEquals(listOf(invoice), restored.invoices)
    }

    @Test
    fun rejectsUnsupportedOrBrokenBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"schemaVersion":99,"exportedAtEpochMillis":1,"clinics":[],"invoices":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("not-json")
        }
    }
}
