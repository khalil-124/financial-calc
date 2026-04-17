package com.khalil.calc.logic

enum class DebtStrategy {
    SNOWBALL, // Pay smallest balance first
    AVALANCHE // Pay highest interest rate first
}

data class DebtStrategySimulation(
    val strategy: DebtStrategy,
    val totalInterestPaid: Double,
    val totalMonthsToPayoff: Int,
    val payoffOrder: List<String>, // List of loan names in order of payoff
    val savedInterestComparedToBaseline: Double,
    val savedMonthsComparedToBaseline: Int
)

data class DebtManagerResult(
    val baseInterestPaid: Double,
    val baseMonthsToPayoff: Int,
    val snowballSimulation: DebtStrategySimulation,
    val avalancheSimulation: DebtStrategySimulation
)

// Internal class for simulation
data class SimLoan(
    val id: Int,
    val name: String,
    var remainingBalance: Double,
    val emi: Double,
    val rate: Double, // annual rate
    val rateType: RateType,
    var isPaidOff: Boolean = false,
    var interestPaid: Double = 0.0,
    var monthsPaid: Int = 0
)
