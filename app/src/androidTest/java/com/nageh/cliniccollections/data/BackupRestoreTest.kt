package com.nageh.cliniccollections.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = database.close()

    @Test
    fun failedReplacementRollsBackAndKeepsCurrentData() = runBlocking {
        val clinic = ClinicEntity(id = 1, name = "Current Clinic", whatsappNumber = "971500000000")
        val current = invoice(id = 1, clinicId = 1, number = "INV/2026/09/001")
        database.backupDao().replaceAll(listOf(clinic), listOf(current))

        val duplicateNumbers = listOf(
            invoice(id = 2, clinicId = 2, number = "INV/2027/01/001"),
            invoice(id = 3, clinicId = 2, number = "INV/2027/01/001")
        )
        runCatching {
            database.backupDao().replaceAll(
                listOf(ClinicEntity(id = 2, name = "Replacement", whatsappNumber = "971511111111")),
                duplicateNumbers
            )
        }

        assertEquals(listOf(clinic), database.backupDao().clinics())
        assertEquals(listOf(current), database.backupDao().invoices())
    }

    private fun invoice(id: Long, clinicId: Long, number: String) = InvoiceEntity(
        id = id,
        clinicId = clinicId,
        clinicName = "Clinic",
        whatsappNumber = "971500000000",
        invoiceNumber = number,
        dueAmountMinor = 100_00,
        invoiceDate = LocalDate.of(2026, 9, 1),
        dueDate = LocalDate.of(2026, 10, 1),
        collectionDate = LocalDate.of(2026, 9, 25)
    )
}
