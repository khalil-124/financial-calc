package com.khalil.calc.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class LoanEngineDeepTest {

    @Test
    fun testRuleOf78_GraceMonthsEqualsTotalMonths_DivByZeroBug() {
        val engine = LoanEngine()
        val input = LoanInput(
            assetPrice = 10000.0,
            months = 12,
            graceMonths = 12,
            annualRate = 10.0,
            rateType = RateType.RULE_OF_78
        )
        
        // This will likely result in NaN due to division by zero in sumOfDigits
        val result = engine.calculate(input, false)
        
        // Let's assert that it doesn't return NaN for EMI and payment is valid
        assertEquals(false, result.monthlyEMI.isNaN())
        
        // And that schedule payments are not NaN
        if (result.schedule.isNotEmpty()) {
            assertEquals(false, result.schedule[0].payment.isNaN())
        }
    }
}