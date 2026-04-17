package com.khalil.calc.logic

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FlatRateVerificationTest {

    private lateinit var engine: LoanEngine

    @Before
    fun setup() {
        engine = LoanEngine()
    }

    @Test
    fun testFlatRate_BasicScenario() {
        // 10,000 JOD, 5% Flat, 12 months
        // Total Interest = 10,000 * 0.05 * 1 = 500
        // Total Debt = 10,500
        // EMI = 10,500 / 12 = 875.0
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.FLAT)
        val result = engine.calculate(input, false)
        
        assertEquals(875.0, result.monthlyEMI, 0.01)
        assertEquals(500.0, result.totalInterest, 0.01)
        assertEquals(10500.0, result.totalPayment, 0.01)
    }

    @Test
    fun testFlatRate_WithGraceInterestOnly() {
        // 10,000 JOD, 5% Flat, 12 months term total, 3 months grace (Interest Only)
        // Monthly interest = 10,000 * 0.05 / 12 = 41.666
        // Remaining months = 9
        // Total Interest = 500 (Fixed for the whole term in Flat Rate models)
        // EMI for the remaining 9 months should distribute the remaining debt fairly.
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.FLAT, graceMonths = 3, capitalizeGraceInterest = false)
        val result = engine.calculate(input, false)
        
        // Months 1-3: Interest Only
        assertEquals(41.67, result.schedule[0].payment, 0.1)
        assertEquals(41.67, result.schedule[1].payment, 0.1)
        assertEquals(41.67, result.schedule[2].payment, 0.1)
        
        // Total Debt = 10,500. 
        // We paid 3 * 41.67 = 125.01 as interest.
        // Remaining to pay: 10,500 - 125.01 = 10,374.99
        // New EMI for 9 months = 10,374.99 / 9 = 1152.77
        assertEquals(1152.78, result.monthlyEMI, 1.0)
    }

    @Test
    fun testFlatRate_ExtraPayment_ReduceEMI() {
        // 12 months, 5% Flat on 10,000
        // Month 6: Extra 5,000 payment with REDUCE_EMI
        val input = LoanInput(
            assetPrice = 10000.0, 
            months = 12, 
            annualRate = 5.0, 
            rateType = RateType.FLAT,
            manualExtraPayments = listOf(ExtraPayment(6, 5000.0)),
            extraPaymentStrategy = ExtraPaymentStrategy.REDUCE_EMI
        )
        val result = engine.calculate(input, false)
        
        // Before extra payment (months 1-5), EMI should be 875
        assertEquals(875.0, result.schedule[0].emiAmount, 0.01)
        
        // Month 6 has the extra payment.
        // Remaining debt should be significantly lower, and month 7 EMI should be recalculated.
        val emiMonth5 = result.schedule[4].emiAmount
        val emiMonth7 = result.schedule[6].emiAmount
        
        // It must be lower than original
        assert(emiMonth7 < emiMonth5)
        
        // Total principal should still be 10,000
        assertEquals(10000.0, result.schedule.sumOf { it.principalPart }, 0.01)
    }

    @Test
    fun testFlatRate_vs_Murabaha_Consistency() {
        val flatInput = LoanInput(assetPrice = 12000.0, months = 24, annualRate = 6.0, rateType = RateType.FLAT)
        val murabahaInput = LoanInput(assetPrice = 12000.0, months = 24, annualRate = 6.0, rateType = RateType.MURABAHA)
        
        val flatResult = engine.calculate(flatInput, false)
        val murabahaResult = engine.calculate(murabahaInput, false)
        
        assertEquals(flatResult.monthlyEMI, murabahaResult.monthlyEMI, 0.01)
        assertEquals(flatResult.totalInterest, murabahaResult.totalInterest, 0.01)
    }
}
