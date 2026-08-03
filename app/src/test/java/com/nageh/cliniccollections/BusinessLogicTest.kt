package com.nageh.cliniccollections

import com.nageh.cliniccollections.data.InvoiceEntity
import com.nageh.cliniccollections.data.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BusinessLogicTest {

    private fun invoice(
        clinic: String = "A",
        number: String = "1",
        due: Long = 10000,
        collected: Long = 0,
        dueDate: LocalDate = LocalDate.of(2026, 8, 1),
        collectionDate: LocalDate = dueDate,
        paidOn: LocalDate? = null
    ) = InvoiceEntity(
        clinicName = clinic,
        whatsappNumber = "971501234567",
        invoiceNumber = number,
        dueAmountMinor = due,
        collectedAmountMinor = collected,
        dueDate = dueDate,
        collectionDate = collectionDate,
        actualPaymentDate = paidOn
    )

    @Test
    fun overdueAndPaidAreComputed() {
        val today = LocalDate.of(2026, 8, 3)
        val row = invoice(dueDate = today.minusDays(1))
        assertEquals(PaymentStatus.OVERDUE, row.computedStatus(today))
        assertEquals(PaymentStatus.PAID, row.copy(actualPaymentDate = today).computedStatus(today))
        assertEquals(PaymentStatus.PENDING, row.copy(dueDate = today.plusDays(3)).computedStatus(today))
    }

    @Test
    fun manualStatusOverridesAutomaticStatus() {
        val today = LocalDate.of(2026, 8, 3)
        val row = invoice(dueDate = today.minusDays(5)).copy(manualStatus = PaymentStatus.PENDING)
        assertEquals(PaymentStatus.PENDING, row.computedStatus(today))
    }

    @Test
    fun reportTotalsAreCorrect() {
        val date = LocalDate.of(2026, 8, 1)
        val rows = listOf(
            invoice(clinic = "A", number = "1", due = 10000, collected = 10000, paidOn = date),
            invoice(clinic = "B", number = "2", due = 20000)
        )
        val totals = report(rows, date.plusDays(2))
        assertEquals(30000, totals.totalDueMinor)
        assertEquals(10000, totals.totalCollectedMinor)
        assertEquals(20000, totals.totalOutstandingMinor)
        assertEquals(20000, totals.totalOverdueMinor)
    }

    @Test
    fun partialCollectionIsCountedCorrectly() {
        val date = LocalDate.of(2026, 8, 1)
        val rows = listOf(invoice(due = 50000, collected = 20000))
        val totals = report(rows, date.plusDays(1))
        assertEquals(50000, totals.totalDueMinor)
        assertEquals(20000, totals.totalCollectedMinor)
        assertEquals(30000, totals.totalOutstandingMinor)
        assertEquals(30000, totals.totalOverdueMinor)
    }

    @Test
    fun overCollectionNeverProducesNegativeOutstanding() {
        val rows = listOf(invoice(due = 10000, collected = 12000))
        val totals = report(rows, LocalDate.of(2026, 8, 5))
        assertEquals(10000, totals.totalCollectedMinor)
        assertEquals(0, totals.totalOutstandingMinor)
        assertEquals(0, totals.totalOverdueMinor)
    }

    @Test
    fun moneyParsingUsesFilsAndRoundsHalfUp() {
        assertEquals(150000, parseMoney("1500"))
        assertEquals(150050, parseMoney("1500.50"))
        assertEquals(10056, parseMoney("100.555"))
        assertEquals("1,500.50", money(150050))
    }

    @Test
    fun phoneNumbersAreNormalised() {
        assertEquals("971501234567", normalizePhone("+971 50 123 4567"))
        assertEquals("971501234567", normalizePhone("00971501234567"))
        assertEquals("971501234567", normalizePhone("971-50-123-4567"))
        assertEquals("971501234567", normalizePhone("0501234567"))
        assertEquals("971501234567", normalizePhone("501234567"))
    }

    @Test
    fun invoicePrefixIsAddedExactlyOnce() {
        assertEquals("INV/2026/08/003", fullInvoiceNumber("08/003"))
        assertEquals("INV/2026/08/003", fullInvoiceNumber("INV/2026/08/003"))
        assertEquals("08/003", invoiceSuffix("INV/2026/08/003"))
        assertEquals(true, validInvoiceSuffix("08/003"))
        assertEquals(false, validInvoiceSuffix("8/3"))
        assertEquals(true, validInvoiceSuffix("08/3"))
        assertEquals(true, validInvoiceSuffix("12/1234"))
        assertEquals(false, validInvoiceSuffix("13/1"))
        assertEquals("08/3", formatInvoiceSuffixInput("083"))
        assertEquals("08/", formatInvoiceSuffixInput("08"))
        assertEquals("12/1234", formatInvoiceSuffixInput("121234"))
    }

    @Test
    fun formValidationCatchesBadInput() {
        val date = LocalDate.of(2026, 8, 10)
        assertNull(validateForm("Clinic", "971501234567", "08/003", "1500", date, date, null, ""))
        assertNotNull(validateForm("", "971501234567", "08/003", "1500", date, date, null, ""))
        assertNotNull(validateForm("Clinic", "123", "08/003", "1500", date, date, null, ""))
        assertNotNull(validateForm("Clinic", "971501234567", "08/003", "abc", date, date, null, ""))
        assertNotNull(validateForm("Clinic", "971501234567", "08/003", "0", date, date, null, ""))
        assertNotNull(validateForm("Clinic", "971501234567", "8/3", "1500", date, date, null, ""))
    }

    @Test
    fun reminderMessageContainsAmountAndInvoiceNumber() {
        val text = reminderMessage(invoice(clinic = "Al Noor Clinic", number = "INV-7", due = 250000))
        assert(text.contains("2,500.00"))
        assert(text.contains("INV-7"))
        assert(text.contains("Al Noor Clinic"))
    }

    // ------------------------------------------------------------------
    // Card status colour rules. These encode the existing business rules
    // exactly; the visual redesign must not change any of them.
    // ------------------------------------------------------------------

    @Test
    fun paidInvoicesAreAlwaysGreen() {
        val today = LocalDate.of(2026, 8, 10)
        val paidByDate = invoice(dueDate = today.minusDays(30), collectionDate = today.minusDays(40), paidOn = today)
        assertEquals(CollectionState.PAID, collectionState(paidByDate, today))
        val paidInFull = invoice(due = 10000, collected = 10000, dueDate = today.minusDays(5))
        assertEquals(CollectionState.PAID, collectionState(paidInFull, today))
        val manualPaid = invoice(dueDate = today.minusDays(5)).copy(manualStatus = PaymentStatus.PAID)
        assertEquals(CollectionState.PAID, collectionState(manualPaid, today))
        assertEquals("Paid", stateLabel(CollectionState.PAID))
    }

    @Test
    fun overduePaymentBeatsCollectionDue() {
        val today = LocalDate.of(2026, 8, 10)
        // Due date passed and unpaid -> red, even though the collection date also passed.
        val row = invoice(dueDate = today.minusDays(1), collectionDate = today.minusDays(3))
        assertEquals(CollectionState.PAYMENT_OVERDUE, collectionState(row, today))
        assertEquals("Payment Overdue", stateLabel(CollectionState.PAYMENT_OVERDUE))
    }

    @Test
    fun collectionDueIsOrangeWhileDueDateHasNotPassed() {
        val today = LocalDate.of(2026, 8, 10)
        assertEquals(
            CollectionState.COLLECTION_DUE,
            collectionState(invoice(dueDate = today.plusDays(20), collectionDate = today), today)
        )
        assertEquals(
            CollectionState.COLLECTION_DUE,
            collectionState(invoice(dueDate = today.plusDays(20), collectionDate = today.minusDays(4)), today)
        )
        assertEquals("Collection Due", stateLabel(CollectionState.COLLECTION_DUE))
    }

    @Test
    fun futureCollectionIsOnTrack() {
        val today = LocalDate.of(2026, 8, 10)
        val row = invoice(dueDate = today.plusDays(30), collectionDate = today.plusDays(2))
        assertEquals(CollectionState.NORMAL, collectionState(row, today))
        assertEquals("On Track", stateLabel(CollectionState.NORMAL))
    }

    @Test
    fun dashboardCountsUseTheSameRulesAsTheCards() {
        val today = LocalDate.of(2026, 8, 10)
        val rows = listOf(
            invoice(number = "1", dueDate = today.plusDays(20), collectionDate = today),
            invoice(number = "2", dueDate = today.minusDays(1), collectionDate = today.minusDays(5)),
            invoice(number = "3", dueDate = today.plusDays(30), collectionDate = today.plusDays(5)),
            invoice(number = "4", dueDate = today.minusDays(9), collectionDate = today.minusDays(9), paidOn = today)
        )
        val counts = dashboardCounts(rows, today)
        assertEquals(1, counts.collectionDue)
        assertEquals(1, counts.paymentOverdue)
    }

    // ------------------------------------------------------------------
    // Presentation helpers
    // ------------------------------------------------------------------

    @Test
    fun datesAreDisplayedAsDayMonthYear() {
        assertEquals("10/08/2026", formatDate(LocalDate.of(2026, 8, 10)))
        assertEquals("01/01/2026", formatDate(LocalDate.of(2026, 1, 1)))
        assertEquals("\u2014", formatDateOrDash(null))
        assertEquals("August 2026", formatMonth(java.time.YearMonth.of(2026, 8)))
    }

    @Test
    fun searchAcceptsDisplayedAndStoredDateFormats() {
        assertEquals("2026-08-10", normalizeSearchQuery("10/08/2026"))
        assertEquals("2026-08-10", normalizeSearchQuery(" 10/08/2026 "))
        assertEquals("Al Noor", normalizeSearchQuery("Al Noor"))
        assertEquals("08/003", normalizeSearchQuery("08/003"))
    }

    @Test
    fun invoiceSuffixAcceptsEveryDocumentedShape() {
        assertEquals(true, validInvoiceSuffix("08/3"))
        assertEquals(true, validInvoiceSuffix("08/33"))
        assertEquals(true, validInvoiceSuffix("08/003"))
        assertEquals(true, validInvoiceSuffix("08/1234"))
        assertEquals("INV/2026/08/1234", fullInvoiceNumber("08/1234"))
    }

    @Test
    fun localAndInternationalWhatsappNumbersBothNormalise() {
        assertEquals("971508984903", normalizePhone("+971 50 898 4903"))
        assertEquals("971508984903", normalizePhone("0508984903"))
        assertEquals("971508984903", normalizePhone("508984903"))
        assertEquals("971508984903", normalizePhone("00971508984903"))
    }

    // ------------------------------------------------------------------
    // Automatic slash and caret position
    // ------------------------------------------------------------------

    /** Simulates typing one character at the end of the current field value. */
    private fun type(current: SuffixEdit, ch: Char): SuffixEdit {
        val inserted = current.text.take(current.caret) + ch + current.text.drop(current.caret)
        return formatSuffixEdit(inserted, current.caret + 1, current.text)
    }

    /** Simulates one backspace at the current caret position. */
    private fun backspace(current: SuffixEdit): SuffixEdit {
        if (current.caret == 0) return current
        val removed = current.text.take(current.caret - 1) + current.text.drop(current.caret)
        return formatSuffixEdit(removed, current.caret - 1, current.text)
    }

    @Test
    fun slashIsInsertedAfterTheMonthWithTheCaretBehindIt() {
        var field = SuffixEdit("", 0)
        field = type(field, '0')
        assertEquals("0", field.text)
        assertEquals(1, field.caret)

        field = type(field, '8')
        assertEquals("08/", field.text)
        assertEquals(3, field.caret)

        field = type(field, '3')
        assertEquals("08/3", field.text)
        assertEquals(4, field.caret)
    }

    @Test
    fun everySequenceLengthFromOneToFourDigitsIsBuilt() {
        var field = SuffixEdit("", 0)
        "08003".forEach { field = type(field, it) }
        assertEquals("08/003", field.text)
        assertEquals(6, field.caret)
        assertEquals(true, validInvoiceSuffix(field.text))

        val cases = mapOf(
            "121" to "12/1",
            "1212" to "12/12",
            "12123" to "12/123",
            "121234" to "12/1234"
        )
        cases.forEach { (typed, expected) ->
            var f = SuffixEdit("", 0)
            typed.forEach { f = type(f, it) }
            assertEquals(expected, f.text)
            assertEquals(true, validInvoiceSuffix(f.text))
        }
    }

    @Test
    fun deletingTheSlashDoesNotTrapTheCaretOrReAddIt() {
        var field = SuffixEdit("", 0)
        "08".forEach { field = type(field, it) }
        assertEquals("08/", field.text)

        field = backspace(field)
        assertEquals("08", field.text)
        assertEquals(2, field.caret)

        // The slash must stay gone so the month digits can actually be erased.
        field = backspace(field)
        assertEquals("0", field.text)
        assertEquals(1, field.caret)

        field = backspace(field)
        assertEquals("", field.text)
        assertEquals(0, field.caret)
    }

    @Test
    fun deletingASequenceDigitKeepsTheSlashAndTheCaretAfterIt() {
        var field = SuffixEdit("", 0)
        "083".forEach { field = type(field, it) }
        assertEquals("08/3", field.text)

        field = backspace(field)
        assertEquals("08/", field.text)
        assertEquals(3, field.caret)

        field = type(field, '9')
        assertEquals("08/9", field.text)
    }

    @Test
    fun typedInputIsLimitedAndNonDigitsAreIgnored() {
        var field = SuffixEdit("", 0)
        "1212345678".forEach { field = type(field, it) }
        assertEquals("12/1234", field.text)

        assertEquals("08/3", formatSuffixEdit("0a8/b3", 6, "").text)
    }

    @Test
    fun onlyMonthsFromOneToTwelveAreAccepted() {
        listOf("01", "02", "08", "09", "10", "11", "12").forEach { month ->
            assertEquals(true, hasValidInvoiceMonth("$month/3"))
            assertEquals(true, validInvoiceSuffix("$month/3"))
        }
        listOf("00", "13", "20", "99").forEach { month ->
            assertEquals(false, hasValidInvoiceMonth("$month/3"))
            assertEquals(false, validInvoiceSuffix("$month/3"))
        }
        assertEquals(false, hasValidInvoiceMonth("8/3"))
        assertEquals(false, validInvoiceSuffix("8/3"))
    }

    // ------------------------------------------------------------------
    // Total collected follows the ACTUAL collection date
    // ------------------------------------------------------------------

    @Test
    fun collectedIsCountedInTheMonthItWasActuallyCollected() {
        // Scheduled for October, due in November, actually collected in August.
        val row = invoice(
            due = 100000,
            collected = 100000,
            dueDate = LocalDate.of(2026, 11, 15),
            collectionDate = LocalDate.of(2026, 10, 5),
            paidOn = LocalDate.of(2026, 8, 20)
        )
        val rows = listOf(row)

        assertEquals(100000, collectedInMonth(rows, YearMonth.of(2026, 8)))
        assertEquals(0, collectedInMonth(rows, YearMonth.of(2026, 10)))
        assertEquals(0, collectedInMonth(rows, YearMonth.of(2026, 11)))
    }

    @Test
    fun partialCollectionsCountAtTheirCollectedAmount() {
        val rows = listOf(
            invoice(number = "1", due = 100000, collected = 40000, paidOn = LocalDate.of(2026, 8, 3)),
            invoice(number = "2", due = 50000, collected = 50000, paidOn = LocalDate.of(2026, 8, 28)),
            invoice(number = "3", due = 70000, collected = 70000, paidOn = LocalDate.of(2026, 9, 1))
        )
        assertEquals(90000, collectedInMonth(rows, YearMonth.of(2026, 8)))
        assertEquals(70000, collectedInMonth(rows, YearMonth.of(2026, 9)))
    }

    @Test
    fun invoicesWithoutAnActualCollectionDateAreExcluded() {
        val rows = listOf(
            invoice(number = "1", due = 100000, collected = 100000, paidOn = null),
            invoice(number = "2", due = 30000, collected = 30000, paidOn = LocalDate.of(2026, 8, 9))
        )
        assertEquals(30000, collectedInMonth(rows, YearMonth.of(2026, 8)))
    }

    @Test
    fun overCollectionCannotInflateAMonth() {
        val rows = listOf(
            invoice(due = 10000, collected = 12000, paidOn = LocalDate.of(2026, 8, 9))
        )
        assertEquals(10000, collectedInMonth(rows, YearMonth.of(2026, 8)))
    }

    @Test
    fun movingTheActualCollectionDateMovesTheMoney() {
        val original = invoice(due = 80000, collected = 80000, paidOn = LocalDate.of(2026, 8, 4))
        assertEquals(80000, collectedInMonth(listOf(original), YearMonth.of(2026, 8)))

        val edited = original.copy(actualPaymentDate = LocalDate.of(2026, 9, 4))
        assertEquals(0, collectedInMonth(listOf(edited), YearMonth.of(2026, 8)))
        assertEquals(80000, collectedInMonth(listOf(edited), YearMonth.of(2026, 9)))
    }
}
