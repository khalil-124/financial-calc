package com.khalil.calc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class LoanEngineAdvancedTest {

    private lateinit var engine: LoanEngine

    @Before
    fun setup() {
        engine = LoanEngine()
    }

    /**
     * اختبار استراتيجية تقليل القسط عند الدفع الإضافي
     */
    @Test
    fun testExtraPayment_Strategy_REDUCE_EMI() {
        val input = LoanInput(
            assetPrice = 20000.0,
            downPayment = 0.0,
            months = 60,
            annualRate = 6.0,
            rateType = RateType.REDUCING,
            extraMonthly = 0.0
        )
        
        // القسط الأساسي لـ 20,000 على 5 سنوات بفائدة 6% هو حوالي 386.66
        val initialResult = engine.calculate(input, false)
        val initialEMI = initialResult.monthlyEMI
        
        // الآن نضيف دفعة بالون كبيرة في الشهر 12 (مثلاً 5000 دينار)
        val inputWithBalloon = input.copy(
            balloonPayments = listOf(BalloonPayment(month = 12, amount = 5000.0, strategy = ExtraPaymentStrategy.REDUCE_EMI))
        )
        val balloonResult = engine.calculate(inputWithBalloon, false)
        
        // في الشهر 13، بما أننا اخترنا REDUCE_EMI، يجب أن يكون القسط الجديد أقل من initialEMI
        val emiMonth13 = balloonResult.schedule[12].payment
        assertTrue("EMI after balloon should be significantly lower", emiMonth13 < initialEMI)
        
        // والمدة يجب أن تبقى 60 شهراً
        assertEquals(60, balloonResult.schedule.size)
    }

    /**
     * اختبار استراتيجية تقليل المدة عند الدفع الإضافي
     */
    @Test
    fun testExtraPayment_Strategy_REDUCE_TERM() {
        val input = LoanInput(
            assetPrice = 20000.0,
            downPayment = 0.0,
            months = 60,
            annualRate = 6.0,
            rateType = RateType.REDUCING,
            extraMonthly = 0.0
        )
        
        val initialResult = engine.calculate(input, false)
        val initialEMI = initialResult.monthlyEMI
        
        val inputWithBalloon = input.copy(
            balloonPayments = listOf(BalloonPayment(month = 12, amount = 10000.0)) // دفع نصف القرض
        )
        val balloonResult = engine.calculate(inputWithBalloon, false)
        
        // القسط الشهري في الشهر 13 يجب أن يبقى قريباً من القسط الأصلي (لأنه يقلل المدة لا القسط)
        val emiMonth13 = balloonResult.schedule[12].payment
        assertEquals(initialEMI, emiMonth13, 10.0) 
        
        // المدة يجب أن تكون أقصر بكثير من 60 شهراً
        assertTrue("Loan should end much earlier than 60 months", balloonResult.schedule.size < 60)
    }

    /**
     * اختبار التأمين والرسوم المتكررة
     */
    @Test
    fun testInsuranceAndFees_ImpactOnEMI() {
        val baseInput = LoanInput(
            assetPrice = 10000.0,
            months = 12,
            annualRate = 10.0,
            rateType = RateType.REDUCING
        )
        val baseRes = engine.calculate(baseInput, false)
        
        val inputWithInsurance = baseInput.copy(
            monthlyInsurance = 10.0 // 10 دنانير شهرياً (بدلاً من النسبة)
        )
        val resWithIns = engine.calculate(inputWithInsurance, false)
        
        // القسط الجديد يجب أن يكون القسط الأصلي + 10 دنانير تقريباً
        assertEquals(baseRes.monthlyEMI + 10.0, resWithIns.monthlyEMI, 0.5)
        
        // إجمالي التأمين يجب أن يكون 120 تقريباً (10 * 12)
        assertEquals(120.0, resWithIns.totalInsurance, 5.0)
    }

    /**
     * فحص دقة الـ True APR (IRR)
     * القرض الذي فيه رسوم استخراج يجب أن يكون الـ True APR فيه أعلى من الفائدة الظاهرة
     */
    @Test
    fun testTrueAPR_WithUpfrontFees() {
        val input = LoanInput(
            assetPrice = 10000.0,
            months = 12,
            annualRate = 5.0,
            rateType = RateType.REDUCING,
            feePercent = 2.0 // رسوم 2% = 200 دينار تُخصم مسبقاً
        )
        val result = engine.calculate(input, false)
        
        // الفائدة الظاهرة 5%، لكن بما أن المستخدم استلم 9800 ودفع أقساط الـ 10,000، فالنسبة الحقيقية يجب أن تكون أعلى (قرابة 8.8%)
        assertTrue("True APR (${result.trueAPR}) should be higher than annual rate (5.0)", result.trueAPR > 5.0)
        assertEquals(8.8, result.trueAPR, 0.5)
    }

    /**
     * اختبار المرابحة (Murabaha) عند السداد المبكر
     * في المرابحة، الربح يُثبت عند التعاقد ولا يُخصم عند السداد المبكر في الأنظمة التقليدية
     */
    @Test
    fun testMurabaha_EarlySettlement_DoesNotReduceProfit() {
        val input = LoanInput(
            assetPrice = 10000.0,
            months = 12,
            annualRate = 10.0,
            rateType = RateType.MURABAHA
        )
        val resNormal = engine.calculate(input, false)
        
        val inputWithPrepay = input.copy(
            balloonPayments = listOf(BalloonPayment(month = 6, amount = 5000.0))
        )
        val resPrepay = engine.calculate(inputWithPrepay, false)
        
        // إجمالي الربح (Interest) في المرابحة يجب أن يبقى ثابتاً حتى لو سددنا باكراً
        assertEquals(resNormal.totalInterest, resPrepay.totalInterest, 0.01)
    }

    /**
     * اختبار ملاءمة القرض (Affordability) بناءً على الراتب والمصاريف
     */
    @Test
    fun testAffordability_Logic() {
        val profile = PersonalFinanceProfile(
            monthlySalary = 1000.0,
            existingLoansEMI = 200.0,
            rentExpense = 150.0
        )
        val proposedEMI = 300.0
        val input = LoanInput(assetPrice = 10000.0, months = 12, annualRate = 5.0)
        
        val analysis = engine.analyzeAffordability(profile, proposedEMI, input, false)
        
        // إجمالي الالتزامات مع القرض الجديد = 200 + 300 = 500
        // DTI = 500 / 1000 = 50%
        assertEquals(50.0, analysis.dtiWithNewLoan, 0.1)
        
        // عند 50% المخاطرة يجب أن تكون Moderate أو High حسب الإعدادات
        assertTrue(analysis.riskLevel == RiskLevel.MODERATE || analysis.riskLevel == RiskLevel.HIGH)
    }
}
