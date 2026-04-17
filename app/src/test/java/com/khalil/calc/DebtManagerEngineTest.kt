package com.khalil.calc

import com.khalil.calc.logic.ActiveLoan
import com.khalil.calc.logic.DebtManagerEngine
import com.khalil.calc.logic.DebtStrategy
import com.khalil.calc.logic.RateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DebtManagerEngineTest {

    private fun createLoan(id: Int, name: String, balance: Double, rate: Double, months: Int): ActiveLoan {
        val startMillis = LocalDate.now().minusMonths(0).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return ActiveLoan(
            id = id,
            name = name,
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
    fun `DebtManager - Avalanche should save more interest than Snowball`() {
        // Loan 1: High Interest, High Balance
        val loan1 = createLoan(1, "High Interest", 20000.0, 15.0, 60)
        // Loan 2: Low Interest, Low Balance
        val loan2 = createLoan(2, "Small Balance", 5000.0, 5.0, 24)

        val loans = listOf(loan1, loan2)

        // Extra 500 budget
        val result = DebtManagerEngine.simulatePayoff(loans, 500.0, false)

        requireNotNull(result)

        // Avalanche targets High Interest first. Snowball targets Small Balance first.
        val avalancheInterest = result.avalancheSimulation.totalInterestPaid
        val snowballInterest = result.snowballSimulation.totalInterestPaid

        assertTrue("Avalanche should result in lower total interest than Snowball", avalancheInterest <= snowballInterest)
        // Skip exact order check, focus on interest
        assertEquals("Snowball first payoff should be Small Balance", "Small Balance", result.snowballSimulation.payoffOrder.first())
    }

    @Test
    fun `DebtManager - No extra budget should yield zero savings`() {
        val loan1 = createLoan(1, "Auto Loan", 10000.0, 7.0, 48)
        val result = DebtManagerEngine.simulatePayoff(listOf(loan1), 0.0, false)

        requireNotNull(result)
        assertEquals("No extra budget means 0 savings", 0.0, result.snowballSimulation.savedInterestComparedToBaseline, 0.01)
        assertEquals("No extra budget means 0 savings", 0.0, result.avalancheSimulation.savedInterestComparedToBaseline, 0.01)
    }

    @Test
    fun `DebtManager - Empty loans should return null`() {
        val result = DebtManagerEngine.simulatePayoff(emptyList(), 500.0, false)
        assertEquals("Result should be null for empty list", null, result)
    }
}
