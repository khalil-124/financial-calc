package com.khalil.calc.logic

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "saved_loans")
data class SavedLoan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val assetPrice: Double,
    val downPayment: Double,
    val months: Int,
    val annualRate: Double,
    val rateType: RateType,
    val graceMonths: Int = 0,
    val capitalizeGraceInterest: Boolean = false,
    val extraMonthly: Double = 0.0,
    val extraMonthlyStrategy: ExtraPaymentStrategy = ExtraPaymentStrategy.REDUCE_TERM,
    val inflationRate: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class LoanCategory {
    PERSONAL, AUTO, MORTGAGE, OTHER
}

@Entity(tableName = "active_loans")
data class ActiveLoan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val loanCategory: LoanCategory = LoanCategory.PERSONAL,
    val currentEMI: Double = 0.0,
    val OriginalAssetPrice: Double,
    val OriginalDownPayment: Double,
    val OriginalMonths: Int,
    val OriginalAnnualRate: Double,
    val rateType: RateType,
    val startDateMillis: Long,
    val paymentDay: Int,
    val currentBalanceOverride: Double,
    val currentActiveRate: Double,
    val createdAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromRateType(value: RateType): String = value.name

    @TypeConverter
    fun toRateType(value: String): RateType = RateType.valueOf(value)

    @TypeConverter
    fun fromExtraPaymentStrategy(value: ExtraPaymentStrategy): String = value.name

    @TypeConverter
    fun toExtraPaymentStrategy(value: String): ExtraPaymentStrategy = ExtraPaymentStrategy.valueOf(value)

    @TypeConverter
    fun fromLoanCategory(value: LoanCategory): String = value.name

    @TypeConverter
    fun toLoanCategory(value: String): LoanCategory = LoanCategory.valueOf(value)
}
