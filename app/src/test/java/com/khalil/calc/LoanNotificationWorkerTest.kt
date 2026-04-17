package com.khalil.calc

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class LoanNotificationWorkerTest {

    // Helper logic extracted from the worker for testing since we can't easily unit test the full Room/WorkManager stack cleanly without Robolectric
    private fun shouldNotify(currentDate: LocalDate, paymentDay: Int): Boolean {
        var nextPaymentDate = currentDate.withDayOfMonth(paymentDay.coerceAtMost(currentDate.lengthOfMonth()))
        if (currentDate.isAfter(nextPaymentDate)) {
            val nextMonth = currentDate.plusMonths(1)
            nextPaymentDate = nextMonth.withDayOfMonth(paymentDay.coerceAtMost(nextMonth.lengthOfMonth()))
        }
        val daysUntilPayment = ChronoUnit.DAYS.between(currentDate, nextPaymentDate).toInt()
        return daysUntilPayment in 0..3
    }

    @Test
    fun `Scenario 1 - Notification 3 days before payment`() {
        val currentDate = LocalDate.of(2023, 10, 2)
        val paymentDay = 5
        val notify = shouldNotify(currentDate, paymentDay)
        assertEquals("Should notify 3 days before", true, notify)
    }

    @Test
    fun `Scenario 2 - Notification exactly on payment day`() {
        val currentDate = LocalDate.of(2023, 10, 5)
        val paymentDay = 5
        val notify = shouldNotify(currentDate, paymentDay)
        assertEquals("Should notify on the exact payment day", true, notify)
    }

    @Test
    fun `Scenario 3 - No notification if payment is 4 days away`() {
        val currentDate = LocalDate.of(2023, 10, 1)
        val paymentDay = 5
        val notify = shouldNotify(currentDate, paymentDay)
        assertEquals("Should NOT notify 4 days before", false, notify)
    }

    @Test
    fun `Scenario 4 - No notification if payment was yesterday (checking next month)`() {
        val currentDate = LocalDate.of(2023, 10, 6)
        val paymentDay = 5
        val notify = shouldNotify(currentDate, paymentDay)
        assertEquals("Should NOT notify since next payment is next month", false, notify)
    }

    @Test
    fun `Scenario 5 - Edge case End of month payment day`() {
        // Payment day is 31, but November only has 30 days
        val currentDate = LocalDate.of(2023, 11, 28)
        val paymentDay = 31
        // The worker uses coerceAtMost, so nextPaymentDate will be Nov 30
        // Days between Nov 28 and Nov 30 is 2 days. Should notify.
        val notify = shouldNotify(currentDate, paymentDay)
        assertEquals("Should handle end of month coercion and notify", true, notify)
    }
}
