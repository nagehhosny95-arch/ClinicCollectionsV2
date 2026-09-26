package com.nageh.cliniccollections.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-3-4-test.db"
    private var opened: AppDatabase? = null

    @After
    fun cleanUp() {
        opened?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesRowsAndBackfillsInvoiceDateFromCreationDate() {
        context.deleteDatabase(databaseName)
        val septemberCreated = LocalDate.of(2026, 9, 5)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val augustCreated = LocalDate.of(2026, 8, 18)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            db.execSQL(
                """CREATE TABLE clinics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    whatsappNumber TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )""".trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX index_clinics_name ON clinics(name)")
            db.execSQL(
                """CREATE TABLE invoices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    clinicId INTEGER,
                    clinicName TEXT NOT NULL,
                    whatsappNumber TEXT NOT NULL,
                    invoiceNumber TEXT NOT NULL,
                    dueAmountMinor INTEGER NOT NULL,
                    dueDate TEXT NOT NULL,
                    collectionDate TEXT NOT NULL DEFAULT '1970-01-01',
                    actualPaymentDate TEXT,
                    collectedAmountMinor INTEGER NOT NULL,
                    paymentTerm TEXT NOT NULL,
                    customTermDays INTEGER,
                    manualStatus TEXT,
                    createdAtEpochMillis INTEGER NOT NULL
                )""".trimIndent()
            )
            db.execSQL("CREATE INDEX index_invoices_clinicName ON invoices(clinicName)")
            db.execSQL("CREATE UNIQUE INDEX index_invoices_invoiceNumber ON invoices(invoiceNumber)")
            db.execSQL("CREATE INDEX index_invoices_dueDate ON invoices(dueDate)")
            db.execSQL("CREATE INDEX index_invoices_collectionDate ON invoices(collectionDate)")
            db.execSQL("CREATE INDEX index_invoices_actualPaymentDate ON invoices(actualPaymentDate)")
            db.execSQL("CREATE INDEX index_invoices_clinicId ON invoices(clinicId)")
            db.execSQL(
                "INSERT INTO clinics VALUES (7, 'King''s College', '971501234567', ?)",
                arrayOf(septemberCreated)
            )
            db.execSQL(
                """INSERT INTO invoices VALUES (
                    11, 7, 'King''s College', '971501234567', 'INV/2026/09/0042',
                    1837500, '2026-09-30', '2026-09-15', NULL, 0,
                    'CUSTOM', NULL, NULL, ?
                )""".trimIndent(),
                arrayOf(septemberCreated)
            )
            db.execSQL(
                """INSERT INTO invoices VALUES (
                    12, 7, 'King''s College', '971501234567', 'INV/2026/08/0038',
                    980000, '2026-08-30', '2026-08-20', '2026-09-01', 980000,
                    'ONE_MONTH', NULL, 'PAID', ?
                )""".trimIndent(),
                arrayOf(augustCreated)
            )
            db.version = 3
        }

        opened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val invoices = runBlocking { opened!!.invoiceDao().getAllOnce() }
        assertEquals(2, invoices.size)
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM clinics"))

        val september = invoices.single { it.id == 11L }
        assertEquals(7L, september.clinicId)
        assertEquals("King's College", september.clinicName)
        assertEquals("INV/2026/09/0042", september.invoiceNumber)
        assertEquals(1_837_500L, september.dueAmountMinor)
        assertEquals(LocalDate.of(2026, 9, 5), september.invoiceDate)
        assertNull(september.actualPaymentDate)

        val august = invoices.single { it.id == 12L }
        assertEquals(LocalDate.of(2026, 8, 18), august.invoiceDate)
        assertEquals(LocalDate.of(2026, 9, 1), august.actualPaymentDate)
        assertEquals(980_000L, august.collectedAmountMinor)
        assertEquals(PaymentStatus.PAID, august.manualStatus)
    }

    private fun scalarLong(sql: String): Long = opened!!.openHelper.readableDatabase
        .query(sql)
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
