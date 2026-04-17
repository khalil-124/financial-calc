package com.khalil.calc.logic

import java.text.DecimalFormat

fun main() {
    val engine = LoanEngine()
    val formatter = DecimalFormat("#,##0.00")

    println("=== Manual Verification: Flat Interest Logic ===")

    // Scenario 1: Standard Flat
    val input1 = LoanInput(assetPrice = 10000.0, months = 12, annualRate = 5.0, rateType = RateType.FLAT)
    val res1 = engine.calculate(input1, false)
    println("\nScenario 1: 10,000 @ 5% Flat, 12 months")
    println("EMI: ${formatter.format(res1.monthlyEMI)} (Expected: 875.00)")
    println("Total Interest: ${formatter.format(res1.totalInterest)} (Expected: 500.00)")

    // Scenario 2: Extra Payment + Recalculate EMI
    val input2 = LoanInput(
        assetPrice = 10000.0, 
        months = 12, 
        annualRate = 5.0, 
        rateType = RateType.FLAT,
        manualExtraPayments = listOf(ExtraPayment(6, 5000.0))
    )
    val res2 = engine.calculate(input2, false)
    println("\nScenario 2: Flat with 5,000 Extra in month 6 (Reduce EMI)")
    println("EMI Month 1: ${formatter.format(res2.schedule[0].emiAmount)}")
    println("EMI Month 7: ${formatter.format(res2.schedule[6].emiAmount)}")
    println("Remaining Count: ${res2.schedule.count { it.monthNumber > 6 }} months")
    
    // Check if Month 7 EMI is correctly reduced and follows Flat logic
    // Unified debt before month 6 was around 5250 (roughly half). 
    // Paid 875 (month 6 P+I) + 5000 Extra = 5875 total reduction in month 6? 
    // Let's see what the engine says.
}
