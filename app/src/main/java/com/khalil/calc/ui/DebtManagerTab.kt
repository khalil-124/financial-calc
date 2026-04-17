package com.khalil.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khalil.calc.logic.LoanDao
import com.khalil.calc.logic.DebtManagerEngine
import com.khalil.calc.logic.DebtManagerResult
import java.text.DecimalFormat

@Composable
fun DebtManagerTab(dao: LoanDao, currentLang: String) {
    val isArabic = currentLang == "ar"
    val activeLoans by dao.getAllActiveLoans().collectAsState(initial = emptyList())
    var extraBudgetInput by remember { mutableStateOf("0") }

    val extraBudget = extraBudgetInput.toDoubleOrNull() ?: 0.0
    val simulationResult = remember(activeLoans, extraBudget, isArabic) {
        DebtManagerEngine.simulatePayoff(activeLoans, extraBudget, isArabic)
    }

    val formatter = remember { DecimalFormat("#,##0") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if(isArabic) "إدارة الديون الذكية" else "Smart Debt Manager", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CalcColors.textPrimary())
        Spacer(Modifier.height(8.dp))
        Text(if(isArabic) "كم تستطيع أن تدفع إضافياً كل شهر فوق أقساطك الأساسية؟" else "How much extra can you pay monthly on top of your EMIs?", color = CalcColors.textMuted(), fontSize = 14.sp)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = extraBudgetInput,
            onValueChange = { extraBudgetInput = it },
            label = { Text(if(isArabic) "الميزانية الإضافية الشهرية" else "Extra Monthly Budget") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        if (activeLoans.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if(isArabic) "لا يوجد لديك قروض حية مسجلة لإدارتها." else "No active live loans to manage.", color = CalcColors.textMuted())
            }
        } else if (simulationResult != null) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    // Base Scenario Info
                    Surface(
                        color = CalcColors.surface(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CalcColors.border()),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if(isArabic) "الوضع الحالي (بدون تسريع)" else "Current Status (No Acceleration)", fontWeight = FontWeight.Bold, color = CalcColors.textPrimary())
                            Spacer(Modifier.height(8.dp))
                            Text(if(isArabic) "الوقت للانتهاء: ${simulationResult.baseMonthsToPayoff} شهر" else "Time to Payoff: ${simulationResult.baseMonthsToPayoff} months", color = CalcColors.textMuted())
                            Text(if(isArabic) "إجمالي الفوائد المدفوعة: ${formatter.format(simulationResult.baseInterestPaid)}" else "Total Interest Paid: ${formatter.format(simulationResult.baseInterestPaid)}", color = CalcColors.textMuted())
                        }
                    }
                }

                if (extraBudget > 0) {
                    item {
                        Text(if(isArabic) "مقارنة استراتيجيات السداد المسرع" else "Acceleration Strategy Comparison", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CalcColors.accent(), modifier = Modifier.padding(bottom = 16.dp))
                    }

                    item {
                        StrategyCard(
                            title = if(isArabic) "كرة الثلج (الأصغر أولاً)" else "Snowball (Smallest First)",
                            desc = if(isArabic) "تركز على تسديد القرض الأصغر أولاً للشعور بالإنجاز." else "Focuses on paying the smallest balance first for psychological wins.",
                            sim = simulationResult.snowballSimulation,
                            formatter = formatter,
                            isArabic = isArabic,
                            recommended = simulationResult.snowballSimulation.totalInterestPaid <= simulationResult.avalancheSimulation.totalInterestPaid
                        )
                    }

                    item {
                        StrategyCard(
                            title = if(isArabic) "الانهيار الجليدي (الأعلى فائدة أولاً)" else "Avalanche (Highest Interest First)",
                            desc = if(isArabic) "تركز على تسديد القرض ذو الفائدة الأعلى أولاً لتوفير أكبر قدر من المال." else "Focuses on the highest interest rate first to save the most money.",
                            sim = simulationResult.avalancheSimulation,
                            formatter = formatter,
                            isArabic = isArabic,
                            recommended = simulationResult.avalancheSimulation.totalInterestPaid <= simulationResult.snowballSimulation.totalInterestPaid
                        )
                    }
                } else {
                    item {
                         Text(if(isArabic) "أدخل ميزانية إضافية لرؤية سحر استراتيجيات السداد!" else "Enter an extra budget to see the magic of payoff strategies!", color = CalcColors.accent(), modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyCard(title: String, desc: String, sim: com.khalil.calc.logic.DebtStrategySimulation, formatter: DecimalFormat, isArabic: Boolean, recommended: Boolean) {
    Surface(
        color = CalcColors.surface(),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (recommended) Color(0xFF4CAF50) else CalcColors.border()),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CalcColors.textPrimary(), modifier = Modifier.weight(1f))
                if (recommended) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                }
            }
            Text(desc, fontSize = 12.sp, color = CalcColors.textMuted(), modifier = Modifier.padding(vertical = 4.dp))

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                 Column {
                    Text(if(isArabic) "الوقت للانتهاء" else "Payoff Time", fontSize = 12.sp, color = CalcColors.textMuted())
                    Text("${sim.totalMonthsToPayoff} ${if(isArabic) "شهر" else "mo"}", fontWeight = FontWeight.Bold, color = CalcColors.textPrimary())
                    if(sim.savedMonthsComparedToBaseline > 0) {
                         Text("${if(isArabic) "وفرت" else "Saved"} ${sim.savedMonthsComparedToBaseline} ${if(isArabic) "شهور" else "mo"}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                 }
                 Column(horizontalAlignment = Alignment.End) {
                    Text(if(isArabic) "الفوائد المدفوعة" else "Interest Paid", fontSize = 12.sp, color = CalcColors.textMuted())
                    Text(formatter.format(sim.totalInterestPaid), fontWeight = FontWeight.Bold, color = CalcColors.textPrimary())
                     if(sim.savedInterestComparedToBaseline > 0) {
                         Text("${if(isArabic) "وفرت" else "Saved"} ${formatter.format(sim.savedInterestComparedToBaseline)}", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                 }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = CalcColors.border())
            Spacer(Modifier.height(8.dp))
            Text(if(isArabic) "ترتيب السداد الموصى به:" else "Recommended Payoff Order:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CalcColors.textMuted())
            sim.payoffOrder.forEachIndexed { index, loanName ->
                Text("${index + 1}. $loanName", fontSize = 14.sp, color = CalcColors.textPrimary())
            }
        }
    }
}
