package com.khalil.calc

import com.khalil.calc.logic.LoanEngine
import com.khalil.calc.logic.ActiveLoan
import com.khalil.calc.logic.RateType
import com.khalil.calc.logic.InsightType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LiveTrackingTest {

    private val engine = LoanEngine()

    private fun createLoan(months: Int, balance: Double, rate: Double, startOffsetMonths: Int): ActiveLoan {
        val startDate = LocalDate.now().minusMonths(startOffsetMonths.toLong())
        val startMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return ActiveLoan(
            id = 1,
            name = "Test Loan",
            OriginalAssetPrice = balance,
            OriginalDownPayment = 0.0,
            OriginalMonths = months,
            OriginalAnnualRate = rate,
            currentBalanceOverride = balance,
            currentActiveRate = rate,
            startDateMillis = startMillis,
            paymentDay = 1,
            rateType = RateType.REDUCING
        )
    }

    @Test
    fun `Scenario 1 - Start of Loan - Golden Time Insight`() {
        // Loan started 1 month ago (Golden time <= 30% of 60 months = 18 months)
        val loan = createLoan(60, 10000.0, 5.0, 1)
        val result = engine.calculateLiveAmortization(loan, isArabic = false)

        assertEquals("Golden Time Insight not triggered", InsightType.TIP, result.proactiveInsight!!.type)
        assertTrue("Insight title doesn't contain Golden Time", result.proactiveInsight!!.title.contains("Golden"))
        // Check future interest is projected and full schedule generated
        assertTrue("Future projection EMI is not calculated", result.estimatedFutureInterest > 0)
        assertTrue("Schedule is not generated", result.fullSchedule.isNotEmpty())
    }

    @Test
    fun `Scenario 2 - Mid Loan - Steady Progress`() {
        // Loan started 30 months ago (Steady progress > 30% and < 80% of 60 months)
        val loan = createLoan(60, 10000.0, 5.0, 30)
        val result = engine.calculateLiveAmortization(loan, isArabic = false)

        assertEquals("Steady Progress Insight not triggered", InsightType.TIP, result.proactiveInsight!!.type)
        assertTrue("Insight title doesn't contain Steady Progress", result.proactiveInsight!!.title.contains("Steady Progress"))
    }

    @Test
    fun `Scenario 3 - End of Loan - Finish Line`() {
        // Loan started 50 months ago (Finish line >= 80% of 60 months = 48 months)
        val loan = createLoan(60, 10000.0, 5.0, 50)
        val result = engine.calculateLiveAmortization(loan, isArabic = false)

        assertEquals("Finish line Insight not triggered", InsightType.SUCCESS, result.proactiveInsight!!.type)
        assertTrue("Insight title doesn't contain finish line", result.proactiveInsight!!.title.contains("finish line", ignoreCase = true))
    }

    @Test
    fun `Scenario 4 - Loan started exactly today - 0 elapsed months`() {
        // Loan started 0 months ago
        val loan = createLoan(60, 10000.0, 5.0, 0)
        val result = engine.calculateLiveAmortization(loan, isArabic = false)

        // Elapsed = 0, so 60 remaining months.
        assertEquals("Progress should be 0%", 0.0, result.progressPercentage, 0.01)
        assertTrue("Insight title doesn't contain Golden Time", result.proactiveInsight!!.title.contains("Golden"))
    }

    @Test
    fun `Scenario 5 - Overdue Loan - Elapsed exceeds original`() {
        // Loan started 70 months ago but original duration was 60 months
        val loan = createLoan(60, 10000.0, 5.0, 70)
        val result = engine.calculateLiveAmortization(loan, isArabic = false)

        // Elapsed capped at 60 for progress calc -> 100%
        // Remaining should be capped at 1
        assertEquals("Progress should be capped at 100%", 100.0, result.progressPercentage, 0.01)
        assertTrue("Insight title doesn't contain finish line", result.proactiveInsight!!.title.contains("finish line", ignoreCase = true))
        // Check if future projection ran properly with at least 1 remaining month
        assertTrue("Schedule should not be empty", result.fullSchedule.isNotEmpty())
    }
}
