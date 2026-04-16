package com.khalil.calc.logic

data class BalloonPayment(
    val month: Int,
    val amount: Double,
    val strategy: ExtraPaymentStrategy = ExtraPaymentStrategy.REDUCE_TERM
)
data class ExtraPayment(val month: Int, val amount: Double)
data class ExtraPaymentPeriod(val startMonth: Int, val endMonth: Int, val amountPerMonth: Double)

data class FinancialInsight(
    val title: String,
    val description: String,
    val type: InsightType = InsightType.TIP
)

enum class InsightType { TIP, SUCCESS, WARNING, ALERT }

enum class ExtraPaymentStrategy {
    REDUCE_TERM,
    REDUCE_EMI
}

data class LoanInput(
    val assetPrice: Double = 0.0,
    val downPayment: Double = 0.0,
    val months: Int = 12,
    val annualRate: Double = 5.0,
    val rateType: RateType = RateType.REDUCING,
    val feePercent: Double = 0.0,
    val feeFixed: Double = 0.0,
    val deductFeesFromLoan: Boolean = true,
    val deductInsuranceFromLoan: Boolean = true,
    val insuranceUpfrontPercent: Double = 0.0,
    val insuranceUpfrontFixed: Double = 0.0,
    val monthlyInsurance: Double = 0.0,
    val annualTax: Double = 0.0,
    val graceMonths: Int = 0,
    val capitalizeGraceInterest: Boolean = true,
    val inflationRate: Double = 2.5,
    val extraMonthly: Double = 0.0,
    val balloonPayments: List<BalloonPayment> = emptyList(),
    val manualExtraPayments: List<ExtraPayment> = emptyList(),
    val extraPaymentPeriods: List<ExtraPaymentPeriod> = emptyList(),
    val extraPaymentStrategy: ExtraPaymentStrategy = ExtraPaymentStrategy.REDUCE_TERM,
    val earlySettlementFeePercent: Double = 0.0,
    val earlySettlementFeeFixed: Double = 0.0,
    val mandatoryCardFee: Double = 0.0 
)

enum class RateType {
    REDUCING, FLAT, MURABAHA, INTEREST_ONLY, RULE_OF_78
}

data class AmortizationMonth(
    val monthNumber: Int,
    val label: String,
    val openingBalance: Double,
    val payment: Double,
    val principalPart: Double,
    val interestPart: Double,
    val remainingBalance: Double,
    val extraPaid: Double = 0.0,
    val balloonPaid: Double = 0.0,
    val isGrace: Boolean = false,
    val earlySettlementFeePaid: Double = 0.0,
    // حقول التفصيل الدقيق
    val emiAmount: Double = 0.0,
    val principalFromEMI: Double = 0.0,
    val extraToPrincipal: Double = 0.0,
    val balloonToPrincipal: Double = 0.0,
    val insurancePart: Double = 0.0,
    val recurringFeesPart: Double = 0.0
)

data class CalculationResult(
    val monthlyEMI: Double,
    val netReceived: Double,
    val totalInterest: Double,
    val totalPayment: Double,
    val npv: Double,
    val schedule: List<AmortizationMonth>,
    val trueAPR: Double, // The exact calculated APR using IRR
    val nominalAPR: Double, // The nominal APR entered by user
    val interestToPrincipalRatio: Double,
    val totalInsurance: Double = 0.0,
    val monthsSaved: Int = 0,
    val interestSaved: Double = 0.0,
    val earlySettlementFeesTotal: Double = 0.0,
    val insights: List<FinancialInsight> = emptyList() // الحقل الجديد للنصائح
)

// ══════════════════════════════════════════════════════════
// الملف المالي الشخصي (Personal Finance Profile)
// ══════════════════════════════════════════════════════════

data class PersonalFinanceProfile(
    val monthlySalary: Double = 0.0,
    val otherIncome: Double = 0.0,
    val existingLoansEMI: Double = 0.0,
    val creditCardMinPayment: Double = 0.0,
    val rentExpense: Double = 0.0,
    val utilitiesExpense: Double = 0.0,
    val educationExpense: Double = 0.0,
    val transportExpense: Double = 0.0,
    val otherExpenses: Double = 0.0,
    val emergencyFund: Double = 0.0
) {
    val totalIncome: Double get() = monthlySalary + otherIncome
    val totalDebtObligations: Double get() = existingLoansEMI + creditCardMinPayment
    val totalLivingExpenses: Double get() = rentExpense + utilitiesExpense + educationExpense + transportExpense + otherExpenses
}

enum class RiskLevel { SAFE, MODERATE, HIGH, CRITICAL }

data class AffordabilityResult(
    val totalIncome: Double,
    val totalObligations: Double,
    val totalExpenses: Double,
    val currentDTI: Double,
    val dtiWithNewLoan: Double,
    val maxAffordableEMI: Double,
    val maxLoanAmount: Double,
    val monthlyDisposable: Double,
    val emergencyMonths: Double,
    val healthScore: Int,
    val riskLevel: RiskLevel,
    val personalInsights: List<FinancialInsight>
)

// ══════════════════════════════════════════════════════════
// كائنات مستشار إعادة التمويل (Refinance & Buyout)
// ══════════════════════════════════════════════════════════

data class RefinanceInput(
    val currentBalance: Double,
    val currentRemainingMonths: Int,
    val currentEMI: Double,
    val currentEarlySettlementFeePercent: Double,

    val newBankAnnualRate: Double,
    val newBankMonths: Int,
    val newBankProcessingFeePercent: Double,
    val newBankProcessingFeeFixed: Double,
    val newBankRateType: RateType = RateType.REDUCING
)

data class RefinanceResult(
    val oldBankTotalRemainingCost: Double, // Cost if you stay (Balance + Remaining Interest)
    val settlementPenalty: Double, // Penalty paid to old bank

    val newBankLoanAmount: Double, // Amount to borrow from new bank (Balance + Penalty + New Fees)
    val newBankEMI: Double,
    val newBankTotalCost: Double, // Total paid to new bank

    val netSavings: Double, // Positive means saving money!
    val recommendationTitle: String,
    val recommendationDesc: String,
    val isRecommended: Boolean
)

// ══════════════════════════════════════════════════════════
// كائنات اختبار الصدمات (Rate Shock / Stress Test)
// ══════════════════════════════════════════════════════════

data class RateShockResult(
    val originalEMI: Double,
    val newEMI: Double,
    val monthlyDifference: Double,
    val totalExtraInterest: Double,
    val impactWarning: String
)
