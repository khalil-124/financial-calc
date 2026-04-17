package com.khalil.calc.logic

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AffordabilityTest {

    private val engine = LoanEngine()

    @Test
    fun testAffordability_SafeLevel() {
        val profile = PersonalFinanceProfile(
            monthlySalary = 1000.0,
            otherIncome = 0.0,
            existingLoansEMI = 100.0,
            creditCardMinPayment = 0.0
        )
        val input = LoanInput(annualRate = 10.0, months = 12)
        val proposedEMI = 100.0 // 10% DTI for this loan, 20% total
        
        val result = engine.analyzeAffordability(profile, proposedEMI, input, true)
        
        assertEquals(20.0, result.dtiWithNewLoan)
        assertEquals(RiskLevel.SAFE, result.riskLevel)
        assertTrue(result.healthScore >= 75)
    }

    @Test
    fun testAffordability_CriticalLevel() {
        val profile = PersonalFinanceProfile(
            monthlySalary = 1000.0,
            otherIncome = 0.0,
            existingLoansEMI = 450.0, // Already at 45%
            rentExpense = 200.0 // Add expense to reduce disposable income
        )
        val input = LoanInput(annualRate = 10.0, months = 12)
        val proposedEMI = 200.0 // Total DTI 65%
        
        val result = engine.analyzeAffordability(profile, proposedEMI, input, true)
        
        assertEquals(65.0, result.dtiWithNewLoan)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertTrue(result.healthScore < 40)
    }
}
