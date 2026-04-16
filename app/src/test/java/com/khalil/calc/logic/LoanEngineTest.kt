package com.khalil.calc.logic

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoanEngineTest {

    private val engine = LoanEngine()

    @Test
    fun testReducingRate_Basic() {
        // Loan: 10,000, Rate: 12% (1% monthly), Months: 12
        val input = LoanInput(
            assetPrice = 10000.0,
            downPayment = 0.0,
            months = 12,
            annualRate = 12.0,
            rateType = RateType.REDUCING
        )
        val result = engine.calculate(input)
        
        // standard EMI for 10000 at 1% for 12 months is ~888.49
        assertEquals(888.49, Math.round(result.monthlyEMI * 100.0) / 100.0)
        assertEquals(0, result.monthsSaved)
        assertTrue(result.totalInterest > 0)
        assertEquals(0.0, result.interestSaved)
    }

    @Test
    fun testMurabaha_NoInterestSaved() {
        val input = LoanInput(
            assetPrice = 10000.0,
            downPayment = 0.0,
            months = 12,
            annualRate = 12.0,
            rateType = RateType.MURABAHA,
            balloonPayments = listOf(BalloonPayment(month = 6, amount = 2000.0))
        )
        val result = engine.calculate(input)
        
        // For Murabaha, interestSaved must be exactly 0.0 regardless of early payments
        assertEquals(0.0, result.interestSaved, "Murabaha should report 0.0 interest saved")
        assertTrue(result.monthsSaved > 0, "Early payment should still reduce term")
    }

    @Test
    fun testZeroInterestLoan() {
        val input = LoanInput(
            assetPrice = 12000.0,
            downPayment = 0.0,
            months = 12,
            annualRate = 0.0,
            rateType = RateType.REDUCING
        )
        val result = engine.calculate(input)
        
        assertEquals(1000.0, result.monthlyEMI)
        assertEquals(0.0, result.totalInterest)
        assertEquals(12000.0, result.totalPayment)
    }

    @Test
    fun testMixedBalloonStrategies() {
        val input = LoanInput(
            assetPrice = 20000.0,
            downPayment = 0.0,
            months = 24,
            annualRate = 10.0,
            rateType = RateType.REDUCING,
            balloonPayments = listOf(
                BalloonPayment(month = 5, amount = 5000.0, strategy = ExtraPaymentStrategy.REDUCE_TERM),
                BalloonPayment(month = 10, amount = 5000.0, strategy = ExtraPaymentStrategy.REDUCE_EMI)
            )
        )
        val result = engine.calculate(input)
        
        assertTrue(result.monthsSaved > 0)
        assertTrue(result.interestSaved > 0)
        // Verify schedule is shorter than 24 months
        assertTrue(result.schedule.size < 24)
    }

    @Test
    fun testGracePeriodCapitalization() {
        val input = LoanInput(
            assetPrice = 10000.0,
            downPayment = 0.0,
            months = 12,
            annualRate = 12.0,
            rateType = RateType.REDUCING,
            graceMonths = 3,
            capitalizeGraceInterest = true
        )
        val result = engine.calculate(input)
        
        // After 3 months of 1% interest capitalized: 10000 * (1.01)^3 = 10303.01
        // The EMI will be based on 10303.01 over 9 months
        assertTrue(result.monthlyEMI > 1111.0, "EMI should be higher due to capitalized interest")
    }
}
