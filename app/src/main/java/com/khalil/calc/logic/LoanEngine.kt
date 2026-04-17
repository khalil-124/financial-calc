package com.khalil.calc.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * محرك مالي احترافي لحساب القروض بدقة بنكية.
 *
 * المنهجية المعتمدة:
 * ─────────────────
 * 1. القسط الشهري (EMI) يُحسب كقرض تقليدي بالكامل (Fully Amortizing)
 *    بدون أي تعديل للبالونات أو الدفعات الإضافية.
 *
 * 2. البالونات = دفعات سداد مبكر جزئي (Partial Early Repayment).
 *    كل بالون يحمل إستراتيجية مستقلة:
 *    - REDUCE_TERM: تقليل مدة القرض مع الحفاظ على نفس القسط
 *    - REDUCE_EMI:  تخفيض القسط مع الحفاظ على نفس المدة
 *
 * 3. الدفعات الإضافية والبالونات تتبع استراتيجيات مستقلة (Per-payment strategy).
 *
 * 4. رسوم السداد المبكر تُحسب على كل الدفعات الإضافية والبالونات.
 *
 * 5. التوفير يُقاس بمقارنة الفوائد الفعلية مع قرض عادي بدون أي تدخل.
 */
class LoanEngine {

    companion object {
        private const val BALANCE_THRESHOLD = 0.001
        private const val HIGH_INTEREST_THRESHOLD = 0.4
        private const val MIN_SAVINGS_TO_REPORT = 1.0
    }

    fun calculate(input: LoanInput, isArabic: Boolean = true): CalculationResult {
        return calculateInternal(input, isArabic, false)
    }

    private fun calculateInternal(input: LoanInput, isArabic: Boolean, isBaseline: Boolean): CalculationResult {
        val initialPrincipal = (input.assetPrice - input.downPayment).coerceAtLeast(0.0)

        val r = (input.annualRate / 100.0) / 12.0
        val n = (input.months - input.graceMonths).coerceAtLeast(1).toDouble()

        // حساب رسوم البنك المبدئية (Processing Fees & Upfront Insurance)
        val upfrontProcessingFees = (initialPrincipal * (input.feePercent / 100.0)) + input.feeFixed
        val upfrontInsuranceFees = (initialPrincipal * (input.insuranceUpfrontPercent / 100.0)) + input.insuranceUpfrontFixed
        
        val netReceived = (initialPrincipal - 
            (if (input.deductFeesFromLoan) upfrontProcessingFees else 0.0) -
            (if (input.deductInsuranceFromLoan) upfrontInsuranceFees else 0.0)
        ).coerceAtLeast(0.0)

        val actualCashInPocket = initialPrincipal - upfrontProcessingFees - upfrontInsuranceFees

        // ══════════════════════════════════════════════════════════════
        // 【الخطوة 1】حساب القسط الشهري الأساسي (EMI)
        // القسط يُحسب كقرض تقليدي بالكامل - بدون أي تعديل للبالونات
        // لأن البالونات هي دفعات مبكرة مفاجئة وليست جزءاً من العقد
        // ══════════════════════════════════════════════════════════════
        var standardEMI = 0.0
        if (initialPrincipal > 0) {
            if (r > 0) {
                when (input.rateType) {
                    RateType.REDUCING -> {
                        val balanceAfterGrace = if (input.capitalizeGraceInterest) initialPrincipal * (1 + r).pow(input.graceMonths) else initialPrincipal
                        val powN = (1 + r).pow(n)
                        standardEMI = (balanceAfterGrace * r * powN) / (powN - 1)
                    }
                    RateType.FLAT, RateType.MURABAHA, RateType.RULE_OF_78 -> {
                        val totalInterest = initialPrincipal * (input.annualRate / 100.0) * (input.months / 12.0)
                        standardEMI = (initialPrincipal + totalInterest) / n.toInt()
                    }
                    RateType.INTEREST_ONLY -> {
                        val balanceAfterGrace = if (input.capitalizeGraceInterest) initialPrincipal * (1 + r).pow(input.graceMonths) else initialPrincipal
                        standardEMI = balanceAfterGrace * r
                    }
                }
            } else {
                standardEMI = initialPrincipal / n.toInt()
            }
        }

        val initialStandardEMI = standardEMI

        val activeRate = r
        val isFlatRate = input.rateType == RateType.FLAT

        // ══════════════════════════════════════════════════════════════
        // 【الخطوة 2】تجهيز هياكل البيانات
        // ══════════════════════════════════════════════════════════════
        val schedule = mutableListOf<AmortizationMonth>()
        var currentBalance = initialPrincipal
        var totalEarlySettlementFees = 0.0
        var expectedMaturityMonth = input.months
        val monthlyRecurringCost = input.monthlyInsurance + (input.annualTax / 12.0) + (input.mandatoryCardFee / 12.0)

        val isMurabaha = input.rateType == RateType.MURABAHA
        val isRuleOf78 = input.rateType == RateType.RULE_OF_78
        
        val totalFixedInterest = if (input.rateType == RateType.FLAT || isMurabaha || isRuleOf78) {
            initialPrincipal * (input.annualRate / 100.0) * (input.months / 12.0)
        } else 0.0
        
        val totalUnifiedDebt = initialPrincipal + totalFixedInterest
        val ratioP = if (totalUnifiedDebt > 0) initialPrincipal / totalUnifiedDebt else 1.0
        val ratioI = if (totalUnifiedDebt > 0) totalFixedInterest / totalUnifiedDebt else 0.0
        
        val sumOfDigits = if (isRuleOf78) n * (n + 1) / 2.0 else 0.0

        val manualExtraMap = input.manualExtraPayments.associateBy { it.month }
        val balloonsMap = input.balloonPayments.associateBy { it.month }

        val periodExtraArray = DoubleArray(input.months + 1)
        input.extraPaymentPeriods.forEach { period ->
            for (i in period.startMonth..period.endMonth) {
                if (i in periodExtraArray.indices) {
                    periodExtraArray[i] += period.amountPerMonth
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 【الخطوة 3】حلقة الإطفاء الشهرية (Amortization Loop)
        // ══════════════════════════════════════════════════════════════
        for (m in 1..input.months) {
            if (currentBalance <= BALANCE_THRESHOLD && m > input.graceMonths) break

            val balloonEntry = balloonsMap[m]
            val balloonToday = balloonEntry?.amount ?: 0.0
            val balloonStrategy = balloonEntry?.strategy ?: ExtraPaymentStrategy.REDUCE_TERM

            val manualExtraValue = manualExtraMap[m]?.amount ?: 0.0
            val monthlyExtraValue = if (m > input.graceMonths) input.extraMonthly else 0.0
            val periodExtraValue = if (m < periodExtraArray.size) periodExtraArray[m] else 0.0

            val extraPaidNow = manualExtraValue + monthlyExtraValue + periodExtraValue
            val totalPrepayment = extraPaidNow + balloonToday

            var feePaidThisMonth = 0.0
            if (!isBaseline && totalPrepayment > 0.0) {
                if (input.earlySettlementFeePercent > 0.0 || input.earlySettlementFeeFixed > 0.0) {
                    feePaidThisMonth = (totalPrepayment * (input.earlySettlementFeePercent / 100.0)) + input.earlySettlementFeeFixed
                    totalEarlySettlementFees += feePaidThisMonth
                }
            }

            val baseEMI = if (m <= input.graceMonths) {
                if (input.capitalizeGraceInterest) 0.0 else {
                    if (isMurabaha || isRuleOf78 || isFlatRate) {
                        (initialPrincipal * (input.annualRate / 100.0) / 12.0).coerceAtLeast(0.0)
                    } else {
                        (currentBalance * activeRate).coerceAtLeast(0.0)
                    }
                }
            } else standardEMI

            val actualEMI: Double
            val emiInterest: Double
            val emiPrincipal: Double
            val unpaidInterest: Double
            val actualExtra: Double
            val actualBalloon: Double
            val totalPrincipalReduction: Double
            val totalPayment: Double
            val openingBalance = currentBalance
            var totalInterestPaidThisMonth = 0.0

            if (isRuleOf78) {
                val activeMonth = m - input.graceMonths
                val interestThisMonth = if (activeMonth > 0) {
                    totalFixedInterest * (n - activeMonth + 1) / sumOfDigits
                } else {
                    (initialPrincipal * (input.annualRate / 100.0) / 12.0).coerceAtLeast(0.0)
                }
                actualEMI = minOf(baseEMI, currentBalance + interestThisMonth)
                emiInterest = minOf(actualEMI, interestThisMonth)
                emiPrincipal = (actualEMI - emiInterest).coerceAtLeast(0.0)
                
                unpaidInterest = interestThisMonth - emiInterest
                
                val balanceAfterEMI = (currentBalance - emiPrincipal).coerceAtLeast(0.0)
                actualExtra = minOf(extraPaidNow, balanceAfterEMI)
                actualBalloon = minOf(balloonToday, (balanceAfterEMI - actualExtra).coerceAtLeast(0.0))
                
                totalPrincipalReduction = emiPrincipal + actualExtra + actualBalloon
                totalInterestPaidThisMonth = emiInterest
                totalPayment = totalInterestPaidThisMonth + totalPrincipalReduction + feePaidThisMonth + monthlyRecurringCost
                
                currentBalance = (currentBalance + unpaidInterest - totalPrincipalReduction).coerceAtLeast(0.0)
            } else if (isMurabaha || isFlatRate) {
                val currentUnifiedDebt = if (ratioP > 0) currentBalance / ratioP else 0.0
                
                actualEMI = minOf(baseEMI, currentUnifiedDebt)
                emiPrincipal = actualEMI * ratioP
                emiInterest = actualEMI * ratioI
                unpaidInterest = 0.0
                
                val unifiedDebtAfterEMI = currentUnifiedDebt - actualEMI
                val totalExtraAvailable = extraPaidNow + balloonToday
                val actualExtraTotal = minOf(totalExtraAvailable, unifiedDebtAfterEMI)
                
                actualExtra = if (totalExtraAvailable > 0) actualExtraTotal * (extraPaidNow / totalExtraAvailable) else 0.0
                actualBalloon = if (totalExtraAvailable > 0) actualExtraTotal * (balloonToday / totalExtraAvailable) else 0.0
                
                val extraPrincipal = actualExtra * ratioP
                val balloonPrincipal = actualBalloon * ratioP
                val extraInterest = actualExtra * ratioI
                val balloonInterest = actualBalloon * ratioI
                
                totalPrincipalReduction = emiPrincipal + extraPrincipal + balloonPrincipal
                totalInterestPaidThisMonth = emiInterest + extraInterest + balloonInterest
                
                totalPayment = totalInterestPaidThisMonth + totalPrincipalReduction + feePaidThisMonth + monthlyRecurringCost
                
                currentBalance = (currentBalance - totalPrincipalReduction).coerceAtLeast(0.0)
            } else {
                val interestThisMonth = (currentBalance * activeRate).coerceAtLeast(0.0)
                actualEMI = minOf(baseEMI, currentBalance + interestThisMonth)
                emiInterest = minOf(actualEMI, interestThisMonth)
                emiPrincipal = (actualEMI - emiInterest).coerceAtLeast(0.0)
                
                unpaidInterest = interestThisMonth - emiInterest
                
                val balanceAfterEMI = (currentBalance - emiPrincipal).coerceAtLeast(0.0)
                actualExtra = minOf(extraPaidNow, balanceAfterEMI)

                var actualBalloonTemp = balloonToday
                // Interest Only requires full principal balloon payment at the very end
                if (input.rateType == RateType.INTEREST_ONLY && m == input.months) {
                    actualBalloonTemp += balanceAfterEMI // Enforce final payoff
                }
                
                actualBalloon = minOf(actualBalloonTemp, (balanceAfterEMI - actualExtra).coerceAtLeast(0.0))
                
                totalPrincipalReduction = emiPrincipal + actualExtra + actualBalloon
                totalInterestPaidThisMonth = emiInterest
                totalPayment = totalInterestPaidThisMonth + totalPrincipalReduction + feePaidThisMonth + monthlyRecurringCost
                
                currentBalance = (currentBalance + unpaidInterest - totalPrincipalReduction).coerceAtLeast(0.0)
            }

            val label = when {
                balloonToday > 0 && extraPaidNow > 0 -> if (isArabic) "سداد مبكر + إضافي" else "Prepay + Extra"
                balloonToday > 0 -> if (isArabic) "سداد مبكر" else "Prepayment"
                extraPaidNow > 0.0 -> if (isArabic) "دفعة إضافية" else "Extra"
                m <= input.graceMonths -> if (isArabic) "فترة سماح" else "Grace"
                else -> if (isArabic) "قسط عادي" else "Regular"
            }

            schedule.add(AmortizationMonth(
                monthNumber = m,
                label = label,
                openingBalance = openingBalance,
                payment = totalPayment,
                principalPart = totalPrincipalReduction,
                interestPart = totalInterestPaidThisMonth,
                remainingBalance = currentBalance.coerceAtLeast(0.0),
                extraPaid = actualExtra,
                balloonPaid = actualBalloon,
                isGrace = m <= input.graceMonths,
                earlySettlementFeePaid = feePaidThisMonth,
                emiAmount = actualEMI,
                principalFromEMI = emiPrincipal,
                extraToPrincipal = if (isMurabaha) actualExtra * ratioP else actualExtra,
                balloonToPrincipal = if (isMurabaha) actualBalloon * ratioP else actualBalloon,
                insurancePart = if (m <= input.months) input.monthlyInsurance else 0.0,
                recurringFeesPart = if (m <= input.months) (input.annualTax / 12.0) + (input.mandatoryCardFee / 12.0) else 0.0
            ))

            if (currentBalance > BALANCE_THRESHOLD) {
                var needsEMIRecalc = false
                
                // Determine if ANY payment this month triggers EMI reduction
                if (balloonToday > 0 && balloonStrategy == ExtraPaymentStrategy.REDUCE_EMI) {
                    needsEMIRecalc = true
                }
                if (input.extraMonthly > 0 && input.extraMonthlyStrategy == ExtraPaymentStrategy.REDUCE_EMI) {
                    needsEMIRecalc = true
                }
                input.extraPaymentPeriods.forEach { p ->
                    if (m in p.startMonth..p.endMonth && p.strategy == ExtraPaymentStrategy.REDUCE_EMI) {
                        needsEMIRecalc = true
                    }
                }
                input.manualExtraPayments.forEach { me ->
                    if (me.month == m && me.strategy == ExtraPaymentStrategy.REDUCE_EMI) {
                        needsEMIRecalc = true
                    }
                }

                if (needsEMIRecalc) {
                    val remainingMonths = expectedMaturityMonth - m
                    if (remainingMonths > 0) {
                        when {
                            input.rateType == RateType.REDUCING && r > 0 -> {
                                val pw = (1 + r).pow(remainingMonths.toDouble())
                                standardEMI = (currentBalance * r * pw) / (pw - 1)
                            }
                            input.rateType == RateType.FLAT || input.rateType == RateType.MURABAHA -> {
                                val currUnifiedDebt = if (ratioP > 0) currentBalance / ratioP else 0.0
                                standardEMI = currUnifiedDebt / remainingMonths.toDouble()
                            }
                            input.rateType == RateType.RULE_OF_78 -> {
                                val remainingInterest = totalFixedInterest - schedule.sumOf { it.interestPart }
                                standardEMI = (currentBalance + remainingInterest) / remainingMonths.toDouble()
                            }
                        }
                    }
                } else {
                    if (isMurabaha && standardEMI > 0) {
                        val currDebt = if (ratioP > 0) currentBalance / ratioP else 0.0
                        expectedMaturityMonth = m + kotlin.math.ceil(currDebt / standardEMI).toInt()
                    } else if (input.rateType == RateType.FLAT && activeRate > 0 && standardEMI > (currentBalance * activeRate)) {
                        val nExp = kotlin.math.ln(standardEMI / (standardEMI - currentBalance * activeRate)) / kotlin.math.ln(1 + activeRate)
                        expectedMaturityMonth = m + kotlin.math.ceil(nExp).toInt()
                    } else if (isRuleOf78 && standardEMI > 0) {
                        val remInt = totalFixedInterest - schedule.sumOf { it.interestPart }
                        expectedMaturityMonth = m + kotlin.math.ceil((currentBalance + remInt) / standardEMI).toInt()
                    } else if (r > 0 && standardEMI > (currentBalance * r)) {
                        val nExp = kotlin.math.ln(standardEMI / (standardEMI - currentBalance * r)) / kotlin.math.ln(1 + r)
                        expectedMaturityMonth = m + kotlin.math.ceil(nExp).toInt()
                    } else if (r == 0.0 && standardEMI > 0) {
                        expectedMaturityMonth = m + kotlin.math.ceil(currentBalance / standardEMI).toInt()
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // 【الخطوة 5】حساب الإجماليات
        // ══════════════════════════════════════════════════════════════
        val trueTotalPayment = schedule.sumOf { it.payment }
        val trueTotalInterest = schedule.sumOf { it.interestPart }

        // صافي القيمة الحالية (NPV) بخصم التضخم
        val inflationMonthlyRate = (input.inflationRate / 100.0) / 12.0
        var calculatedNpv = 0.0
        if (inflationMonthlyRate > 0) {
            schedule.forEach { month ->
                calculatedNpv += month.payment / (1.0 + inflationMonthlyRate).pow(month.monthNumber)
            }
        } else {
            calculatedNpv = trueTotalPayment
        }

        // ══════════════════════════════════════════════════════════════
        // 【الخطوة 5.5】حساب معدل العائد الداخلي (IRR) لمعرفة True APR 🧮
        // ══════════════════════════════════════════════════════════════
        val cashFlows = DoubleArray(schedule.size + 1)
        cashFlows[0] = actualCashInPocket // التدفق النقدي الداخلي 
        
        val totalInsurance = upfrontInsuranceFees + (schedule.size * input.monthlyInsurance) + 
                             ((schedule.size / 12.0) * input.annualTax) + ((schedule.size / 12.0) * input.mandatoryCardFee)
        
        // Include recurring fees in APR flows accurately
        schedule.forEachIndexed { index, month ->
            cashFlows[index + 1] = -month.payment
        }
        
        val trueMonthlyRate = calculateIRR(cashFlows)
        val trueAPR = if (trueMonthlyRate > 0) trueMonthlyRate * 12.0 * 100.0 else input.annualRate

        // ══════════════════════════════════════════════════════════════
        // 【الخطوة 6】حساب التوفير مقارنة بالقرض العادي (Baseline)
        // القرض المرجعي = نفس المعطيات بدون أي بالونات أو دفعات إضافية
        // ══════════════════════════════════════════════════════════════
        var baselineInterest = 0.0
        var interestSaved = 0.0
        var monthsSaved = 0

        if (!isBaseline) {
            val baselineInput = input.copy(
                extraMonthly = 0.0,
                manualExtraPayments = emptyList(),
                extraPaymentPeriods = emptyList(),
                balloonPayments = emptyList()
            )
            val baselineResult = calculateInternal(baselineInput, isArabic, true)
            baselineInterest = baselineResult.totalInterest
            interestSaved = (baselineInterest - trueTotalInterest).coerceAtLeast(0.0)
            monthsSaved = (baselineResult.schedule.size - schedule.size).coerceAtLeast(0)

            // ═══ مرابحة: الربح ثابت ومتفق عليه مسبقاً ═══
            if (input.rateType == RateType.MURABAHA) {
                interestSaved = 0.0
            }
        }

        return CalculationResult(
            monthlyEMI = initialStandardEMI + monthlyRecurringCost,
            netReceived = netReceived,
            totalInterest = trueTotalInterest,
            totalPayment = trueTotalPayment,
            npv = calculatedNpv,
            schedule = schedule,
            trueAPR = trueAPR,
            nominalAPR = input.annualRate,
            interestToPrincipalRatio = if (initialPrincipal > 0) (trueTotalInterest / initialPrincipal) * 100.0 else 0.0,
            totalInsurance = totalInsurance,
            monthsSaved = monthsSaved,
            interestSaved = interestSaved.coerceAtLeast(0.0),
            earlySettlementFeesTotal = totalEarlySettlementFees,
            insights = if (!isBaseline) generateInsights(input, trueTotalInterest, interestSaved, monthsSaved, initialPrincipal, totalEarlySettlementFees, calculatedNpv, trueTotalPayment, isArabic, emi = initialStandardEMI + monthlyRecurringCost) else emptyList()
        )
    }

    private fun generateInsights(input: LoanInput, totalInterest: Double, saved: Double, monthsSaved: Int, principal: Double, totalEarlySettlementFees: Double, npv: Double, totalPayment: Double, isArabic: Boolean, profile: PersonalFinanceProfile? = null, emi: Double = 0.0): List<FinancialInsight> {
        val list = mutableListOf<FinancialInsight>()
        
        if (principal > 0) {
            // 1. تنبيه نسبة الفائدة إلى أصل القرض (Cost of Borrowing)
        if (totalInterest > principal * 0.5) {
            list.add(FinancialInsight(
                if(isArabic) "تنبيه: تكلفة تمويل عالية" else "Warning: High Borrowing Cost",
                if(isArabic) "إجمالي الفائدة يتجاوز 50% من أصل القرض. ابحث عن خيارات فائدة أقل أو زد الدفعات الإضافية." 
                else "Total interest exceeds 50% of the principal. Look for lower rates or increase extra payments.",
                InsightType.WARNING
            ))
        }

        // 2. نصيحة الدفع المبكر (The Power of Early Extra)
        if (input.extraMonthly == 0.0 && input.balloonPayments.isEmpty()) {
            val suggestedExtra = emi * 0.1
            if (suggestedExtra > 0) {
                list.add(FinancialInsight(
                    if(isArabic) "💡 قوة الـ 10%" else "💡 The 10% Power",
                    if(isArabic) "لو أضفت ${String.format("%.0f", suggestedExtra)} دينار شهرياً (10% من قسطك) ستقلل مدة القرض بشكل كبير وتوفر آلاف الدنانير."
                    else "Adding just ${String.format("%.0f", suggestedExtra)} JOD/month (10% of your EMI) can dramatically reduce your loan term and save thousands.",
                    InsightType.TIP
                ))
            }
        }

        // 3. مقارنة الفائدة الثابتة بالمتناقصة
        if (input.rateType == RateType.FLAT || input.rateType == RateType.MURABAHA) {
            list.add(FinancialInsight(
                if(isArabic) "⚠️ تنبيه الفائدة الثابتة" else "⚠️ Flat Rate Warning",
                if(isArabic) "الفائدة الثابتة (Flat) تبدو أقل ظاهرياً، لكنها غالباً أغلى من المتناقصة لنفس النسبة. النسبة الحقيقية (APR) هي المقياس الصحيح." 
                else "Flat rates seem lower but are often more expensive than reducing rates. True APR is the real measure.",
                InsightType.WARNING
            ))
        }

        // 4. أثر فترة السماح
        if (input.graceMonths > 0 && input.capitalizeGraceInterest) {
            list.add(FinancialInsight(
                if(isArabic) "⚠️ تنبيه فترة السماح" else "⚠️ Grace Period Alert",
                if(isArabic) "رسملة الفائدة خلال فترة السماح تزيد رصيد القرض. حاول دفع الفوائد فقط إن أمكن." 
                else "Capitalizing interest during grace increases the balance. Try paying interest-only if possible.",
                InsightType.ALERT
            ))
        }

        // 5. القرض الطويل جداً
        if (input.months > 120) {
            val yearsTotal = input.months / 12
            list.add(FinancialInsight(
                if(isArabic) "📅 قرض طويل الأمد ($yearsTotal سنة)" else "📅 Long-Term Loan ($yearsTotal years)",
                if(isArabic) "القروض الطويلة تضاعف الفائدة المدفوعة. كل سنة تقللها توفر عليك مبالغ كبيرة."
                else "Long loans multiply interest paid. Every year you shorten saves significant money.",
                InsightType.TIP
            ))
        }

        // 6. نجاح التوفير
        if (saved > MIN_SAVINGS_TO_REPORT && monthsSaved > 0) {
            list.add(FinancialInsight(
                if(isArabic) "🎉 ممتاز! وفّرت بذكاء" else "🎉 Great! Smart Savings",
                if(isArabic) "دفعاتك الإضافية وفّرت لك ${String.format("%.0f", saved)} دينار و $monthsSaved شهراً من عمر القرض!"
                else "Your extra payments saved you ${String.format("%.0f", saved)} JOD and $monthsSaved months off your loan!",
                InsightType.SUCCESS
            ))
        } else if (saved <= MIN_SAVINGS_TO_REPORT && monthsSaved > 0) {
            list.add(FinancialInsight(
                if(isArabic) "🎉 خطوة ممتازة!" else "🎉 Great Move!",
                if(isArabic) "دفعاتك الإضافية قلصت مدة سدادك بمقدار $monthsSaved شهراً! ستنهي التزامك مبكراً بدون تغيير في إجمالي الأرباح الثابتة."
                else "Your extra payments reduced your term by $monthsSaved months! You will finish your obligation early.",
                InsightType.SUCCESS
            ))
        }

        // 7. رسوم السداد المبكر مرتفعة
        if (totalEarlySettlementFees > saved * 0.5 && totalEarlySettlementFees > 0) {
            list.add(FinancialInsight(
                if(isArabic) "⚠️ رسوم السداد المبكر مرتفعة" else "⚠️ High Early Settlement Fees",
                if(isArabic) "رسوم السداد المبكر (${String.format("%.0f", totalEarlySettlementFees)} دينار) تأكل جزءاً كبيراً من توفيرك. تفاوض مع البنك."
                else "Early settlement fees (${String.format("%.0f", totalEarlySettlementFees)} JOD) eat into your savings. Negotiate with the bank.",
                InsightType.WARNING
            ))
        }

        // 8. فائدة مرتفعة جداً
        if (input.annualRate > 8.0) {
            val savedIfOneLess = principal * 0.01 * (input.months / 12.0)
            list.add(FinancialInsight(
                if(isArabic) "📊 الفائدة مرتفعة (${input.annualRate}%)" else "📊 High Interest Rate (${input.annualRate}%)",
                if(isArabic) "لو حصلت على فائدة أقل بـ 1% فقط ستوفر تقريباً ${String.format("%.0f", savedIfOneLess)} دينار. تسوّق بين البنوك!" 
                else "Getting just 1% lower rate would save ~${String.format("%.0f", savedIfOneLess)} JOD. Shop around!",
                InsightType.TIP
            ))
        }
        } // End of principal check
        
        // ═══ نصائح مرتبطة بالملف المالي ═══
        if (profile != null && profile.totalIncome > 0) {
            val dtiWithLoan = ((profile.totalDebtObligations + emi) / profile.totalIncome) * 100.0
            
            // 9. DTI تحذير
            if (dtiWithLoan > 50) {
                list.add(FinancialInsight(
                    if(isArabic) "🚨 تجاوزت حد البنك المركزي" else "🚨 Exceeds Central Bank Limit",
                    if(isArabic) "أقساطك ستشكل ${String.format("%.0f", dtiWithLoan)}% من دخلك — البنك المركزي الأردني لا يسمح بأكثر من 50%. قد يُرفض طلبك."
                    else "Your obligations would be ${String.format("%.0f", dtiWithLoan)}% of income — Jordan's Central Bank limit is 50%. Your application may be rejected.",
                    InsightType.ALERT
                ))
            } else if (dtiWithLoan > 40) {
                list.add(FinancialInsight(
                    if(isArabic) "⚠️ اقتربت من الحد الأقصى" else "⚠️ Approaching the Limit",
                    if(isArabic) "أقساطك ستشكل ${String.format("%.0f", dtiWithLoan)}% من دخلك. أي التزام إضافي قد يُرفض."
                    else "Your obligations would be ${String.format("%.0f", dtiWithLoan)}% of income. Any additional debt may be rejected.",
                    InsightType.WARNING
                ))
            } else if (dtiWithLoan < 20) {
                list.add(FinancialInsight(
                    if(isArabic) "✅ وضع مالي ممتاز" else "✅ Excellent Financial Position",
                    if(isArabic) "أقساطك ${String.format("%.0f", dtiWithLoan)}% فقط من دخلك — لديك مرونة مالية عالية."
                    else "Your obligations are only ${String.format("%.0f", dtiWithLoan)}% of income — you have strong financial flexibility.",
                    InsightType.SUCCESS
                ))
            }

            // 10. صندوق الطوارئ
            val monthlyNeeds = profile.totalDebtObligations + profile.totalLivingExpenses + emi
            if (monthlyNeeds > 0) {
                val emergencyMonths = profile.emergencyFund / monthlyNeeds
                if (emergencyMonths < 3 && profile.emergencyFund > 0) {
                    list.add(FinancialInsight(
                        if(isArabic) "⚠️ صندوق الطوارئ غير كافٍ" else "⚠️ Insufficient Emergency Fund",
                        if(isArabic) "مدخراتك تكفي ${String.format("%.1f", emergencyMonths)} شهر فقط. يُنصح بـ 3-6 أشهر على الأقل."
                        else "Your savings cover only ${String.format("%.1f", emergencyMonths)} months. 3-6 months is recommended.",
                        InsightType.WARNING
                    ))
                } else if (emergencyMonths >= 6) {
                    list.add(FinancialInsight(
                        if(isArabic) "🛡️ وسادة أمان قوية" else "🛡️ Strong Safety Net",
                        if(isArabic) "مدخراتك تكفي ${String.format("%.1f", emergencyMonths)} شهر — ممتاز! أنت جاهز للطوارئ."
                        else "Your savings cover ${String.format("%.1f", emergencyMonths)} months — you're well prepared for emergencies.",
                        InsightType.SUCCESS
                    ))
                }
            }

            // 11. المتبقي بعد القسط قليل
            val disposable = profile.totalIncome - profile.totalDebtObligations - profile.totalLivingExpenses - emi
            if (disposable < 200 && disposable > 0) {
                list.add(FinancialInsight(
                    if(isArabic) "⚠️ هامش ضيق" else "⚠️ Tight Margin",
                    if(isArabic) "سيتبقى لك فقط ${String.format("%.0f", disposable)} دينار شهرياً بعد كل الالتزامات. أي طارئ سيضغط عليك."
                    else "You'll have only ${String.format("%.0f", disposable)} JOD left monthly. Any emergency will strain you.",
                    InsightType.WARNING
                ))
            } else if (disposable < 0) {
                list.add(FinancialInsight(
                    if(isArabic) "🚨 عجز مالي!" else "🚨 Financial Deficit!",
                    if(isArabic) "مصاريفك والتزاماتك تتجاوز دخلك بـ ${String.format("%.0f", kotlin.math.abs(disposable))} دينار! لا تأخذ هذا القرض."
                    else "Your expenses exceed income by ${String.format("%.0f", kotlin.math.abs(disposable))} JOD! Do not take this loan.",
                    InsightType.ALERT
                ))
            }
        }

        return list
    }

    // ══════════════════════════════════════════════════════════════
    // محرك تحليل القدرة الشرائية (Affordability Engine)
    // ══════════════════════════════════════════════════════════════
    fun analyzeAffordability(
        profile: PersonalFinanceProfile,
        proposedEMI: Double,
        input: LoanInput,
        isArabic: Boolean
    ): AffordabilityResult {
        val totalIncome = profile.totalIncome
        val totalObligations = profile.totalDebtObligations
        val totalExpenses = profile.totalLivingExpenses

        // DTI الحالي (بدون القرض الجديد)
        val currentDTI = if (totalIncome > 0) (totalObligations / totalIncome) * 100.0 else 0.0

        // DTI مع القرض الجديد
        val dtiWithNewLoan = if (totalIncome > 0) ((totalObligations + proposedEMI) / totalIncome) * 100.0 else 0.0

        // أقصى قسط ممكن (حد البنك المركزي 50%)
        val maxAffordableEMI = ((totalIncome * 0.50) - totalObligations).coerceAtLeast(0.0)

        // أقصى مبلغ قرض (حساب عكسي)
        val r = (input.annualRate / 100.0) / 12.0
        val n = input.months.toDouble()
        val maxLoanAmount = if (r > 0 && maxAffordableEMI > 0) {
            val pw = (1 + r).pow(n)
            maxAffordableEMI * (pw - 1) / (r * pw)
        } else if (maxAffordableEMI > 0) {
            maxAffordableEMI * n
        } else 0.0

        // المتبقي شهرياً
        val monthlyDisposable = totalIncome - totalObligations - totalExpenses - proposedEMI

        // أشهر الطوارئ
        val monthlyNeeds = totalObligations + totalExpenses + proposedEMI
        val emergencyMonths = if (monthlyNeeds > 0) profile.emergencyFund / monthlyNeeds else 0.0

        // مقياس الصحة المالية (0-100)
        var score = 100
        // DTI penalty
        if (dtiWithNewLoan > 50) score -= 40
        else if (dtiWithNewLoan > 40) score -= 25
        else if (dtiWithNewLoan > 30) score -= 10

        // Emergency fund penalty
        if (emergencyMonths < 1) score -= 25
        else if (emergencyMonths < 3) score -= 15
        else if (emergencyMonths < 6) score -= 5

        // Disposable penalty
        if (monthlyDisposable < 0) score -= 30
        else if (monthlyDisposable < 200) score -= 15

        score = score.coerceIn(0, 100)

        val riskLevel = when {
            score >= 75 -> RiskLevel.SAFE
            score >= 50 -> RiskLevel.MODERATE
            score >= 25 -> RiskLevel.HIGH
            else -> RiskLevel.CRITICAL
        }

        // Generate personalized insights
        val insights = generateInsights(input, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, isArabic, profile, proposedEMI)

        return AffordabilityResult(
            totalIncome = totalIncome,
            totalObligations = totalObligations,
            totalExpenses = totalExpenses,
            currentDTI = currentDTI,
            dtiWithNewLoan = dtiWithNewLoan,
            maxAffordableEMI = maxAffordableEMI,
            maxLoanAmount = maxLoanAmount,
            monthlyDisposable = monthlyDisposable,
            emergencyMonths = emergencyMonths,
            healthScore = score,
            riskLevel = riskLevel,
            personalInsights = insights
        )
    }

    /**
     * حساب العائد الداخلي (IRR) باستخدام خوارزمية Newton-Raphson الدقيقة.
     * تعتبر هذه الخوارزمية المعيار الذهبي في البنوك لقانون "True APR".
     */
    fun analyzeRefinance(input: RefinanceInput, isArabic: Boolean): RefinanceResult {
        val oldTotalRemainingCost = input.currentEMI * input.currentRemainingMonths
        val settlementPenalty = input.currentBalance * (input.currentEarlySettlementFeePercent / 100.0)
        
        val newLoanAmount = input.currentBalance + settlementPenalty
        val newUpfrontFees = (newLoanAmount * (input.newBankProcessingFeePercent / 100.0)) + input.newBankProcessingFeeFixed
        
        val r = (input.newBankAnnualRate / 100.0) / 12.0
        val n = input.newBankMonths.toDouble()
        var newEMI = 0.0
        var newTotalCost = 0.0
        
        if (n > 0) {
            when (input.newBankRateType) {
                RateType.REDUCING -> {
                    if (r > 0) {
                        val powN = (1 + r).pow(n)
                        newEMI = (newLoanAmount * r * powN) / (powN - 1)
                    } else {
                        newEMI = newLoanAmount / n
                    }
                    newTotalCost = newEMI * n + newUpfrontFees
                }
                RateType.FLAT, RateType.MURABAHA, RateType.RULE_OF_78 -> {
                    val totalInterest = newLoanAmount * (input.newBankAnnualRate / 100.0) * (input.newBankMonths / 12.0)
                    newEMI = (newLoanAmount + totalInterest) / n
                    newTotalCost = newEMI * n + newUpfrontFees
                }
                RateType.INTEREST_ONLY -> {
                    newEMI = newLoanAmount * r
                    newTotalCost = (newEMI * n) + newLoanAmount + newUpfrontFees
                }
            }
        }
        
        val netSavings = oldTotalRemainingCost - newTotalCost
        val isRecommended = netSavings > 0
        
        val recTitle = if (isRecommended) (if(isArabic) "خطوة ممتازة!" else "Excellent Move!") else (if(isArabic) "فخ مالي!" else "Financial Trap!")
        val recDesc = if (isRecommended) {
            if(isArabic) "الانتقال سيوفر لك ${String.format("%.0f", netSavings)} دينار بالرغم من دفع غرامات البنك القديم."
            else "Refinancing will save you ${String.format("%.0f", netSavings)} JOD even after paying old bank penalties."
        } else {
            if(isArabic) "إياك والانتقال! ستخسر ${String.format("%.0f", kotlin.math.abs(netSavings))} دينار وتدفع رسوماً بلا جدوى."
            else "Do not refinance! You will lose ${String.format("%.0f", kotlin.math.abs(netSavings))} JOD and pay useless fees."
        }
        
        return RefinanceResult(oldTotalRemainingCost, settlementPenalty, newLoanAmount, newEMI, newTotalCost, netSavings, recTitle, recDesc, isRecommended)
    }

    fun simulateRateShock(input: LoanInput, rateIncreasePercent: Double, isArabic: Boolean): RateShockResult {
        val origResult = calculate(input, isArabic)
        val shockedInput = input.copy(annualRate = input.annualRate + rateIncreasePercent)
        val shockedResult = calculate(shockedInput, isArabic)
        
        val diff = (shockedResult.monthlyEMI - origResult.monthlyEMI).coerceAtLeast(0.0)
        val totalExtra = (shockedResult.totalInterest - origResult.totalInterest).coerceAtLeast(0.0)
        
        val warning = if (isArabic) {
            "إذا رفع البنك المركزي الفائدة بـ ${rateIncreasePercent}%، سيرتفع قسطك بـ ${String.format("%.0f", diff)} دينار شهرياً، وستدفع فوائد إضافية بقيمة ${String.format("%.0f", totalExtra)} دينار."
        } else {
            "If the Central Bank hikes rates by ${rateIncreasePercent}%, your EMI will rise by ${String.format("%.0f", diff)} JOD, and you'll pay ${String.format("%.0f", totalExtra)} JOD extra in interest."
        }
        
        return RateShockResult(origResult.monthlyEMI, shockedResult.monthlyEMI, diff, totalExtra, warning)
    }

    private fun calculateIRR(cashFlows: DoubleArray, guess: Double = 0.01): Double {
        val maxIterations = 1000
        val precision = 1e-7
        var rate = guess

        for (i in 0 until maxIterations) {
            var npv = 0.0
            var derivativeNpv = 0.0

            for (t in cashFlows.indices) {
                val denominator = (1.0 + rate).pow(t.toDouble())
                npv += cashFlows[t] / denominator
                derivativeNpv -= t * cashFlows[t] / (denominator * (1.0 + rate))
            }

            val newRate = rate - npv / derivativeNpv

            if (kotlin.math.abs(newRate - rate) < precision) {
                return newRate
            }

            rate = newRate
        }
        return rate // Return best guess if it didn't strictly converge
    }

    fun calculateLiveAmortization(activeLoan: ActiveLoan, isArabic: Boolean, currentDate: LocalDate = LocalDate.now()): LiveCalculationResult {
        val startLocalDate = LocalDate.ofEpochDay(activeLoan.startDateMillis / (1000 * 60 * 60 * 24))
        
        val monthsElapsed = ChronoUnit.MONTHS.between(startLocalDate.withDayOfMonth(1), currentDate.withDayOfMonth(1)).toInt()
        val pastPaidMonths = monthsElapsed.coerceAtLeast(0).coerceAtMost(activeLoan.OriginalMonths)

        val remainingMonths = (activeLoan.OriginalMonths - pastPaidMonths).coerceAtLeast(1)
        
        val liveInput = LoanInput(
            assetPrice = activeLoan.currentBalanceOverride,
            downPayment = 0.0,
            months = remainingMonths,
            annualRate = activeLoan.currentActiveRate,
            rateType = activeLoan.rateType
        )
        
        val futureResult = calculate(liveInput, isArabic)
        val progressPercentage = if (activeLoan.OriginalMonths > 0) (pastPaidMonths.toDouble() / activeLoan.OriginalMonths.toDouble()) * 100.0 else 0.0
        
        val insight = if (pastPaidMonths <= activeLoan.OriginalMonths * 0.3) {
            FinancialInsight(
                if(isArabic) "💡 الوقت الذهبي للسداد المبكر!" else "💡 Golden Time for Prepayment!",
                if(isArabic) "أنت في الشهر $pastPaidMonths من أصل ${activeLoan.OriginalMonths}. دفع مبلغ إضافي اليوم سيقضي على فوائد هائلة في المستقبل ويقصر عمر القرض!" 
                else "You are in month $pastPaidMonths of ${activeLoan.OriginalMonths}. Prepaying now will crush huge future interest!",
                InsightType.TIP
            )
        } else if (pastPaidMonths >= activeLoan.OriginalMonths * 0.8) {
            FinancialInsight(
                if(isArabic) "✅ تقترب من خط النهاية!" else "✅ Near the finish line!",
                if(isArabic) "تبقّى لك $remainingMonths أشهر فقط لإنهاء هذا العبء. حافظ على الالتزام!" 
                else "Only $remainingMonths months left to finish. Keep it up!",
                InsightType.SUCCESS
            )
        } else {
            FinancialInsight(
                if(isArabic) "📊 رحلة منتظمة" else "📊 Steady Progress",
                if(isArabic) "لقد قطعت ${String.format("%.1f", progressPercentage)}% من عمر القرض. ركز على سداد قسط هذا الشهر لتجنب الغرامات المزعجة." 
                else "You've covered ${String.format("%.1f", progressPercentage)}% of the loan. Focus on paying this month's EMI to avoid penalties.",
                InsightType.TIP
            )
        }
        
        return LiveCalculationResult(
            pastPaidMonths = pastPaidMonths,
            remainingMonths = remainingMonths,
            progressPercentage = progressPercentage,
            remainingBalance = activeLoan.currentBalanceOverride,
            estimatedFutureInterest = futureResult.totalInterest,
            fullSchedule = futureResult.schedule,
            proactiveInsight = insight
        )
    }
}
