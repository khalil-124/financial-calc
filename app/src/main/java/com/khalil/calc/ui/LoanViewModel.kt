package com.khalil.calc.ui

import androidx.lifecycle.ViewModel
import com.khalil.calc.logic.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LoanViewModel : ViewModel() {

    private val engine = LoanEngine()

    private val _input = MutableStateFlow(
        LoanInput(
            assetPrice = 25000.0,
            downPayment = 5000.0,
            annualRate = 6.5,
            months = 60,
            balloonPayments = listOf(BalloonPayment(12, 2000.0), BalloonPayment(24, 2000.0)),
            extraPaymentPeriods = listOf(ExtraPaymentPeriod(1, 12, 100.0))
        )
    )
    val input: StateFlow<LoanInput> = _input.asStateFlow()

    private val _resultArabic = MutableStateFlow<CalculationResult?>(null)
    val resultArabic: StateFlow<CalculationResult?> = _resultArabic.asStateFlow()

    private val _resultEnglish = MutableStateFlow<CalculationResult?>(null)
    val resultEnglish: StateFlow<CalculationResult?> = _resultEnglish.asStateFlow()

    init {
        recalculate()
    }

    fun updateInput(newInput: LoanInput) {
        _input.value = newInput
        recalculate()
    }

    private fun recalculate() {
        val currentInput = _input.value
        _resultArabic.value = engine.calculate(currentInput, isArabic = true)
        _resultEnglish.value = engine.calculate(currentInput, isArabic = false)
    }
}
