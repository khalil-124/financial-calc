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
    val createdAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromRateType(value: RateType): String = value.name

    @TypeConverter
    fun toRateType(value: String): RateType = RateType.valueOf(value)
}
