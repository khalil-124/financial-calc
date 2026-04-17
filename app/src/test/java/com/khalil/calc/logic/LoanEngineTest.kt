package com.khalil.calc.logic

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LoanEngineTest {

    private lateinit var engine: LoanEngine

    @Before
    fun setup() {
        engine = LoanEngine()
    }

    @Test
    fun testReducingRate_CalculatesCorrectly() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.REDUCING)
        val result = engine.calculate(input, false)
        
        // معادلة القسط المتناقص لـ 10,000 على 12 شهر بفائدة 5% تعطي قسطاً تقريبياً 856.07
        assertEquals(856.07, result.monthlyEMI, 0.1)
        
        // يجب أن يكون إجمالي أصل القرض المسدد في نهاية الجدول هو 10,000 تماماً
        val totalPrincipal = result.schedule.sumOf { it.principalPart }
        assertEquals(10000.0, totalPrincipal, 0.01)
    }

    @Test
    fun testFlatRate_CalculatesCorrectly() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.FLAT)
        val result = engine.calculate(input, false)
        
        // الفائدة الثابتة لـ 10,000 على سنة بنسبة 5% هي 500. إجمالي القرض 10500 / 12 شهر = 875.0
        assertEquals(875.0, result.monthlyEMI, 0.01)
        
        val totalPrincipal = result.schedule.sumOf { it.principalPart }
        assertEquals(10000.0, totalPrincipal, 0.01)
        
        val totalInterest = result.schedule.sumOf { it.interestPart }
        assertEquals(500.0, totalInterest, 0.01)
    }

    @Test
    fun testMurabahaRate_CalculatesCorrectly() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.MURABAHA)
        val result = engine.calculate(input, false)
        
        // المرابحة تتصرف مثل الفائدة الثابتة كقسط وكمجموع فوائد وأصل
        assertEquals(875.0, result.monthlyEMI, 0.01)
        
        val totalPrincipal = result.schedule.sumOf { it.principalPart }
        assertEquals(10000.0, totalPrincipal, 0.01)
    }

    @Test
    fun testRuleOf78Rate_CalculatesCorrectly() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.RULE_OF_78)
        val result = engine.calculate(input, false)
        
        // Rule of 78 تحافظ على نفس القسط وإجمالي الفائدة للثابتة، لكن تغير توزيعها الداخلي
        assertEquals(875.0, result.monthlyEMI, 0.01)
        
        val totalPrincipal = result.schedule.sumOf { it.principalPart }
        assertEquals(10000.0, totalPrincipal, 0.01)
    }

    @Test
    fun testInterestOnlyRate_CalculatesCorrectly() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.INTEREST_ONLY)
        val result = engine.calculate(input, false)
        
        // القسط الشهري هو فقط نسبة الفائدة الشهرية (10000 * 0.05 / 12 = 41.666...)
        assertEquals(41.67, result.monthlyEMI, 0.1)
        
        // يجب أن يتضمن الشهر الأخير دفع أصل القرض بالكامل
        val lastMonth = result.schedule.last()
        assertEquals(10000.0, lastMonth.principalPart, 0.01)
    }

    @Test
    fun testGracePeriod_WithCapitalization() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.REDUCING, graceMonths = 3, capitalizeGraceInterest = true)
        val result = engine.calculate(input, false)
        
        // خلال فترة السماح (أول 3 أشهر)، يجب أن يكون القسط 0.0 تماماً
        assertEquals(0.0, result.schedule[0].payment, 0.01)
        assertEquals(0.0, result.schedule[1].payment, 0.01)
        assertEquals(0.0, result.schedule[2].payment, 0.01)
        
        // الرصيد يجب أن يرتفع بسبب رسملة الفائدة (فائدة مركبة لـ 3 أشهر)
        val balanceAfterGrace = result.schedule[2].remainingBalance
        assertEquals(10125.52, balanceAfterGrace, 1.0)
        
        // بما أن الفائدة ترأسمَلَت، إجمالي "الأصل" المسدد في النهاية سيساوي الرصيد المتضخم
        assertEquals(balanceAfterGrace, result.schedule.sumOf { it.principalPart }, 1.0)
    }

    @Test
    fun testGracePeriod_WithoutCapitalization() {
        val input = LoanInput(assetPrice = 10000.0, downPayment = 0.0, months = 12, annualRate = 5.0, rateType = RateType.REDUCING, graceMonths = 3, capitalizeGraceInterest = false)
        val result = engine.calculate(input, false)
        
        // خلال فترة السماح الفائدة فقط تُدفع (حوالي 41.67 دينار) ولا يُخصم شيء من الأصل
        val expectedInterestOnly = 10000.0 * (5.0 / 100.0) / 12.0
        assertEquals(expectedInterestOnly, result.schedule[0].payment, 0.01)
        assertEquals(0.0, result.schedule[0].principalPart, 0.01) 
        
        // في النهاية يجب أن يكون إجمالي الأصل المسدد هو 10,000 تماماً
        assertEquals(10000.0, result.schedule.sumOf { it.principalPart }, 0.01)
    }
}