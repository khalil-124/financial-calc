package com.khalil.calc.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DebtManagerEngine {

    fun simulatePayoff(
        activeLoans: List<ActiveLoan>,
        extraMonthlyBudget: Double,
        isArabic: Boolean
    ): DebtManagerResult? {
        if (activeLoans.isEmpty()) return null

        val engine = LoanEngine()

        // 1. Calculate base stats for all loans (what happens if we just pay EMI)
        val baseSimLoans = activeLoans.map { mapToSimLoan(it, engine) }
        val baseResult = simulateStrategy(baseSimLoans, 0.0, null)

        // 2. Simulate Snowball (Smallest Balance First)
        val snowballSimLoans = activeLoans.map { mapToSimLoan(it, engine) }
        val snowballResult = simulateStrategy(snowballSimLoans, extraMonthlyBudget, DebtStrategy.SNOWBALL)

        // 3. Simulate Avalanche (Highest Interest First)
        val avalancheSimLoans = activeLoans.map { mapToSimLoan(it, engine) }
        val avalancheResult = simulateStrategy(avalancheSimLoans, extraMonthlyBudget, DebtStrategy.AVALANCHE)

        return DebtManagerResult(
            baseInterestPaid = baseResult.first,
            baseMonthsToPayoff = baseResult.second,
            snowballSimulation = DebtStrategySimulation(
                strategy = DebtStrategy.SNOWBALL,
                totalInterestPaid = snowballResult.first,
                totalMonthsToPayoff = snowballResult.second,
                payoffOrder = snowballResult.third,
                savedInterestComparedToBaseline = (baseResult.first - snowballResult.first).coerceAtLeast(0.0),
                savedMonthsComparedToBaseline = (baseResult.second - snowballResult.second).coerceAtLeast(0)
            ),
            avalancheSimulation = DebtStrategySimulation(
                strategy = DebtStrategy.AVALANCHE,
                totalInterestPaid = avalancheResult.first,
                totalMonthsToPayoff = avalancheResult.second,
                payoffOrder = avalancheResult.third,
                savedInterestComparedToBaseline = (baseResult.first - avalancheResult.first).coerceAtLeast(0.0),
                savedMonthsComparedToBaseline = (baseResult.second - avalancheResult.second).coerceAtLeast(0)
            )
        )
    }

    private fun mapToSimLoan(loan: ActiveLoan, engine: LoanEngine): SimLoan {
        val startLocalDate = LocalDate.ofEpochDay(loan.startDateMillis / (1000 * 60 * 60 * 24))
        val currentDate = LocalDate.now()
        val monthsElapsed = ChronoUnit.MONTHS.between(startLocalDate.withDayOfMonth(1), currentDate.withDayOfMonth(1)).toInt()
        val pastPaidMonths = monthsElapsed.coerceAtLeast(0).coerceAtMost(loan.OriginalMonths)
        val remainingMonths = (loan.OriginalMonths - pastPaidMonths).coerceAtLeast(1)

        val liveInput = LoanInput(
            assetPrice = loan.currentBalanceOverride,
            downPayment = 0.0,
            months = remainingMonths,
            annualRate = loan.currentActiveRate,
            rateType = loan.rateType
        )

        val calcResult = engine.calculate(liveInput, false)
        return SimLoan(
            id = loan.id,
            name = loan.name,
            remainingBalance = loan.currentBalanceOverride,
            emi = calcResult.monthlyEMI,
            rate = loan.currentActiveRate,
            rateType = loan.rateType
        )
    }

    // Returns Pair of (Total Interest, Total Months, Payoff Order)
    private fun simulateStrategy(
        loans: List<SimLoan>,
        extraBudget: Double,
        strategy: DebtStrategy?
    ): Triple<Double, Int, List<String>> {
        var totalMonths = 0
        var allPaidOff = false
        val payoffOrder = mutableListOf<String>()
        val totalFixedMonthlyPayment = loans.sumOf { it.emi } + extraBudget

        while (!allPaidOff && totalMonths < 1200) { // Safety limit 100 years
            totalMonths++
            allPaidOff = true

            val activeThisMonth = loans.filter { !it.isPaidOff }
            if (activeThisMonth.isEmpty()) break

            allPaidOff = false

            // Track how much of the total fixed budget is used for standard EMIs
            var budgetUsedForEMI = 0.0

            for (loan in activeThisMonth) {
                // Calculate interest for this month
                val monthlyInterestRate = loan.rate / 100.0 / 12.0
                val interestPart = loan.remainingBalance * monthlyInterestRate
                loan.interestPaid += interestPart

                // Normal EMI payment
                val principalPart = loan.emi - interestPart
                if (principalPart > 0) {
                    if (loan.remainingBalance <= principalPart) {
                        budgetUsedForEMI += loan.remainingBalance + interestPart
                        loan.remainingBalance = 0.0
                        loan.isPaidOff = true
                        payoffOrder.add(loan.name)
                    } else {
                        budgetUsedForEMI += loan.emi
                        loan.remainingBalance -= principalPart
                    }
                } else {
                     budgetUsedForEMI += loan.emi
                }
            }

            // The remaining budget is the true "Snowball/Avalanche" extra payment
            var availableExtra = if (strategy != null) {
                (totalFixedMonthlyPayment - budgetUsedForEMI).coerceAtLeast(0.0)
            } else {
                0.0
            }

            // Apply extra budget if any strategy is chosen
            if (strategy != null && availableExtra > 0) {
                val stillActive = loans.filter { !it.isPaidOff }
                if (stillActive.isNotEmpty()) {
                    // Sort based on strategy
                    val targetLoans = when (strategy) {
                        DebtStrategy.SNOWBALL -> stillActive.sortedBy { it.remainingBalance }
                        DebtStrategy.AVALANCHE -> stillActive.sortedByDescending { it.rate }
                    }

                    for (targetLoan in targetLoans) {
                        if (availableExtra <= 0.001) break // Float tolerance

                        if (targetLoan.remainingBalance <= availableExtra) {
                            availableExtra -= targetLoan.remainingBalance
                            targetLoan.remainingBalance = 0.0
                            targetLoan.isPaidOff = true
                            payoffOrder.add(targetLoan.name)
                        } else {
                            targetLoan.remainingBalance -= availableExtra
                            availableExtra = 0.0
                        }
                    }
                }
            }
        }

        return Triple(loans.sumOf { it.interestPaid }, totalMonths, payoffOrder.distinct())
    }
}
