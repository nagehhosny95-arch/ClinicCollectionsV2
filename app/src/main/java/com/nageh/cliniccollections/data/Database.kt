package com.nageh.cliniccollections.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class PaymentStatus { PENDING, PAID, OVERDUE }

enum class PaymentTerm { IMMEDIATE, ONE_WEEK, ONE_MONTH, TWO_MONTHS, CUSTOM }

class Converters {
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun statusToString(value: PaymentStatus?): String? = value?.name
    @TypeConverter fun stringToStatus(value: String?): PaymentStatus? = value?.let(PaymentStatus::valueOf)
    @TypeConverter fun termToString(value: PaymentTerm): String = value.name
    @TypeConverter fun stringToTerm(value: String): PaymentTerm = PaymentTerm.valueOf(value)
}

@Entity(
    tableName = "invoices",
    indices = [
        Index("clinicName"),
        Index(value = ["invoiceNumber"], unique = true),
        Index("dueDate"),
        Index("collectionDate"),
        Index("actualPaymentDate")
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clinicName: String,
    val whatsappNumber: String,
    val invoiceNumber: String,
    val dueAmountMinor: Long,
    val dueDate: LocalDate,
    @ColumnInfo(defaultValue = "'1970-01-01'") val collectionDate: LocalDate,
    val actualPaymentDate: LocalDate? = null,
    val collectedAmountMinor: Long = 0,
    val paymentTerm: PaymentTerm = PaymentTerm.CUSTOM,
    val customTermDays: Int? = null,
    val manualStatus: PaymentStatus? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun computedStatus(today: LocalDate = LocalDate.now()): PaymentStatus = manualStatus ?: when {
        actualPaymentDate != null || collectedAmountMinor >= dueAmountMinor -> PaymentStatus.PAID
        collectionDate.isBefore(today) -> PaymentStatus.OVERDUE
        else -> PaymentStatus.PENDING
    }
}

data class MonthlyTotals(
    val totalDueMinor: Long,
    val totalCollectedMinor: Long,
    val totalOutstandingMinor: Long,
    val totalOverdueMinor: Long,
    val scheduledCollections: Int = 0,
    val overdueCollections: Int = 0
)

@Dao
interface InvoiceDao {

    @Query("SELECT * FROM invoices ORDER BY collectionDate ASC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    fun observe(id: Long): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun get(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices")
    suspend fun getAllOnce(): List<InvoiceEntity>

    @Query(
        """
        SELECT * FROM invoices
        WHERE clinicName LIKE '%' || :q || '%' COLLATE NOCASE
           OR invoiceNumber LIKE '%' || :q || '%' COLLATE NOCASE
           OR dueDate LIKE '%' || :q || '%'
           OR collectionDate LIKE '%' || :q || '%'
           OR actualPaymentDate LIKE '%' || :q || '%'
        ORDER BY collectionDate ASC
        LIMIT 1000
        """
    )
    fun search(q: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE dueDate >= :start AND dueDate < :end ORDER BY dueDate ASC")
    fun monthByDueDate(start: LocalDate, end: LocalDate): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE collectionDate >= :start AND collectionDate < :end ORDER BY collectionDate ASC")
    fun monthByCollectionDate(start: LocalDate, end: LocalDate): Flow<List<InvoiceEntity>>

    @Query("SELECT COUNT(*) FROM invoices WHERE invoiceNumber = :invoiceNumber")
    suspend fun countByInvoiceNumber(invoiceNumber: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: InvoiceEntity): Long

    @Update
    suspend fun update(item: InvoiceEntity)

    @Delete
    suspend fun delete(item: InvoiceEntity)
}

@Database(entities = [InvoiceEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun invoiceDao(): InvoiceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN collectionDate TEXT NOT NULL DEFAULT '1970-01-01'")
                db.execSQL("UPDATE invoices SET collectionDate = dueDate")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_collectionDate ON invoices(collectionDate)")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "clinic_collections.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
