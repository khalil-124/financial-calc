package com.khalil.calc.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.GridLines
import co.yml.charts.ui.linechart.model.IntersectionPoint
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.LineType
import co.yml.charts.ui.linechart.model.SelectionHighlightPoint
import co.yml.charts.ui.linechart.model.SelectionHighlightPopUp
import co.yml.charts.ui.linechart.model.ShadowUnderLine
import co.yml.charts.axis.AxisData
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khalil.calc.R
import com.khalil.calc.logic.*
import com.khalil.calc.pdf.PdfGenerator
import java.text.DecimalFormat
import java.util.*
import kotlinx.coroutines.launch
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

val LocalIsDarkTheme = compositionLocalOf { false }
val LocalActivity = staticCompositionLocalOf<ComponentActivity> { error("No Activity provided") }

object CalcColors {
    val RoyalBlue = Color(0xFF003366) 
    val SoftGold = Color(0xFFD4AF37)  
    val SnowWhite = Color(0xFFF4F7FA)
    val PureWhite = Color(0xFFFFFFFF)
    val SlateGray = Color(0xFF607D8B)
    val MidnightBlack = Color(0xFF0F1113)
    val CardDark = Color(0xFF1A1D21)
    val BorderDark = Color(0xFF2C3135)

    @Composable fun background() = if (LocalIsDarkTheme.current) MidnightBlack else SnowWhite
    @Composable fun surface() = if (LocalIsDarkTheme.current) CardDark else PureWhite
    @Composable fun textPrimary() = if (LocalIsDarkTheme.current) Color.White else RoyalBlue
    @Composable fun textMuted() = if (LocalIsDarkTheme.current) Color.White.copy(alpha = 0.5f) else SlateGray
    @Composable fun accent() = if (LocalIsDarkTheme.current) SoftGold else RoyalBlue
    @Composable fun border() = if (LocalIsDarkTheme.current) BorderDark else Color(0xFFECEFF1)
}

@Composable
fun CalcTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(primary = CalcColors.SoftGold, background = CalcColors.MidnightBlack, surface = CalcColors.CardDark)
    } else {
        lightColorScheme(primary = CalcColors.RoyalBlue, background = CalcColors.SnowWhite, surface = CalcColors.PureWhite)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var language by remember { mutableStateOf("ar") }
            var isDark by remember { mutableStateOf(false) } // Default to false or track system once if needed
            val locale = Locale(language)
            val configuration = LocalConfiguration.current
            configuration.setLocale(locale)
            val context = LocalContext.current
            val configContext = context.createConfigurationContext(configuration)

            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides if (language == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
                LocalIsDarkTheme provides isDark,
                LocalContext provides configContext,
                LocalActivity provides this@MainActivity
            ) {
                CalcTheme(darkTheme = isDark) { MainScreen(currentLang = language, onLanguageChange = { language = if (language == "en") "ar" else "en" }, isDark = isDark, onThemeToggle = { isDark = !isDark }) }
            }
        }
    }
}

@Composable
fun MainScreen(currentLang: String, onLanguageChange: () -> Unit, isDark: Boolean, onThemeToggle: () -> Unit, viewModel: LoanViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }

    val currentInput by viewModel.input.collectAsState()

    var financeProfile by remember { mutableStateOf(PersonalFinanceProfile()) }
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).loanDao() }
    
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = CalcColors.surface()) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, alwaysShowLabel = true, icon = { Icon(Icons.Default.Calculate, null) }, label = { Text(if(currentLang=="ar") "الحاسبة" else "Calc", maxLines = 1, fontSize = 9.sp) })
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, alwaysShowLabel = true, icon = { Icon(Icons.Default.TrackChanges, null) }, label = { Text(if(currentLang=="ar") "الحالي" else "Live", maxLines = 1, fontSize = 9.sp) })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, alwaysShowLabel = true, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text(if(currentLang=="ar") "محفظتي" else "Portfolio", maxLines = 1, fontSize = 9.sp) })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, alwaysShowLabel = true, icon = { Icon(Icons.Default.CompareArrows, null) }, label = { Text(if(currentLang=="ar") "مقارنة" else "Compare", maxLines = 1, fontSize = 9.sp) })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, alwaysShowLabel = true, icon = { Icon(Icons.Default.Person, null) }, label = { Text(if(currentLang=="ar") "ملفي" else "Profile", maxLines = 1, fontSize = 9.sp) })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(CalcColors.background()).padding(padding)) {
            when (selectedTab) {
                0 -> CalculatorTab(currentInput, currentLang, onLanguageChange, { viewModel.updateInput(it) }, dao, isDark, onThemeToggle, financeProfile, viewModel)
                1 -> MyLoansTab(dao, currentLang) { viewModel.updateInput(LoanInput(assetPrice = it.assetPrice, downPayment = it.downPayment, months = it.months, annualRate = it.annualRate, rateType = it.rateType)); selectedTab = 0 }
                2 -> CompareTab(dao, currentLang)
                3 -> FinancialProfileTab(financeProfile, currentLang, currentInput) { financeProfile = it }
                4 -> LiveTrackerTab(dao, currentLang)
            }
        }
    }
}

@Composable
fun CalculatorTab(input: LoanInput, currentLang: String, onLanguageChange: () -> Unit, onInputChanged: (LoanInput) -> Unit, dao: LoanDao, isDark: Boolean, onThemeToggle: () -> Unit, financeProfile: PersonalFinanceProfile = PersonalFinanceProfile(), viewModel: LoanViewModel) {
    var showAdvanced by remember { mutableStateOf(false) }

    val resultArabic by viewModel.resultArabic.collectAsState()
    val resultEnglish by viewModel.resultEnglish.collectAsState()

    val result = if (currentLang == "ar") resultArabic else resultEnglish
    if (result == null) return

    val formatter = remember { DecimalFormat("#,##0.00") }
    
    // Live Rate State
    var fedRate by remember { mutableStateOf<LiveRatesEngine.LiveRate?>(null) }
    var cbjRate by remember { mutableStateOf<LiveRatesEngine.LiveRate?>(null) }
    var fetchingRate by remember { mutableStateOf(true) }
    // val coroutineScope = rememberCoroutineScope() // removed to prevent unused variable warning
    
    LaunchedEffect(Unit) {
        fetchingRate = true
        fedRate = LiveRatesEngine.fetchFedRate()
        cbjRate = LiveRatesEngine.fetchCBJRate()
        fetchingRate = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        // ... (كود الهيدر والقيم الأساسية يبقى كما هو)
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(if(currentLang=="ar") "حاسبة القروض الذكية" else "SMART LOAN CALC", fontSize = 22.sp, fontWeight = FontWeight.Black, color = CalcColors.textPrimary())
                    Text(if(currentLang=="ar") "محرك السداد الديناميكي" else "DYNAMIC REPAYMENT ENGINE", fontSize = 10.sp, color = CalcColors.accent(), letterSpacing = 1.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onThemeToggle) { Text(if(isDark) "☀️" else "🌙", fontSize = 20.sp) }
                    TextButton(onClick = onLanguageChange) { Text(if(currentLang=="ar") "English" else "العربية", color = CalcColors.accent()) }
                }
            }
            Spacer(Modifier.height(20.dp))
            
            // --- LIVE FED RATE WIDGET ---
            androidx.compose.animation.AnimatedVisibility(visible = !fetchingRate && fedRate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CalcColors.surface().copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, CalcColors.accent().copy(alpha = 0.3f))
                ) {
                    val rate = fedRate
                    if (rate != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = "Live Rate", tint = CalcColors.accent(), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (currentLang == "ar") "سعر الفائدة الفيدرالي (لايف)" else "Live US Fed Rate (SOFR)",
                                        color = CalcColors.textPrimary(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (currentLang == "ar") "حُدث في: ${rate.date}" else "Updated: ${rate.date}",
                                    color = CalcColors.textMuted(),
                                    fontSize = 11.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${rate.rate}%",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                                TextButton(
                                    onClick = { 
                                        onInputChanged(input.copy(annualRate = rate.rate)) 
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        if (currentLang == "ar") "استخدم هذه الفائدة" else "Apply to Calc",
                                        fontSize = 11.sp,
                                        color = CalcColors.accent()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- LIVE CBJ RATE WIDGET ---
            androidx.compose.animation.AnimatedVisibility(visible = !fetchingRate && cbjRate != null) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).clickable { uriHandler.openUri("https://www.cbj.gov.jo") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CalcColors.surface().copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, CalcColors.accent().copy(alpha = 0.3f))
                ) {
                    val rate = cbjRate
                    if (rate != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = "Live Rate", tint = CalcColors.accent(), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (currentLang == "ar") "البنك المركزي الأردني" else "Central Bank of Jordan",
                                        color = CalcColors.textPrimary(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (currentLang == "ar") "سقف العبء (انقر للتفاصيل)" else "DTI Regs (Click for details)",
                                    color = CalcColors.textMuted(),
                                    fontSize = 11.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${rate.rate}%",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                                TextButton(
                                    onClick = { 
                                        onInputChanged(input.copy(annualRate = rate.rate)) 
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        if (currentLang == "ar") "استخدم هذه الفائدة" else "Apply to Calc",
                                        fontSize = 11.sp,
                                        color = CalcColors.accent()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            PremiumCard {
                SectionHeader(if(currentLang=="ar") "تفاصيل القرض الأساسية" else "Core Loan Details")
                
                // Loan Type Chips
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = if(currentLang == "ar") listOf("شخصي","سيارة","عقاري") else listOf("Personal","Auto","Mortgage")
                    types.forEachIndexed { index, type ->
                        Button(
                            onClick = {
                                val newMonths = when(index) { 0 -> 84; 1 -> 72; 2 -> 240; else -> input.months }
                                val newRateType = when(index) { 1 -> RateType.FLAT; else -> RateType.REDUCING }
                                val newPrice = when(index) { 0 -> 15000.0; 1 -> 22000.0; 2 -> 100000.0; else -> input.assetPrice }
                                val newDown = when(index) { 0 -> 0.0; 1 -> 4000.0; 2 -> 15000.0; else -> input.downPayment }
                                val newRate = when(index) { 0 -> 9.5; 1 -> 4.5; 2 -> 7.5; else -> input.annualRate }
                                onInputChanged(input.copy(months = newMonths, rateType = newRateType, downPayment = newDown, assetPrice = newPrice, annualRate = newRate))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CalcColors.accent().copy(alpha = 0.1f), contentColor = CalcColors.accent()),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(type, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                InField(
                    if(currentLang=="ar") "سعر الأصل الإجمالي" else "Total Asset Price",
                    input.assetPrice
                ) { onInputChanged(input.copy(assetPrice = it)) }
                
                InField(if(currentLang=="ar") "الدفعة الأولى" else "Down Payment", input.downPayment) { onInputChanged(input.copy(downPayment = it)) }
                
                
                var showRateInfoDialog by remember { mutableStateOf(false) }

                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionHeader(if(currentLang=="ar") "نوع الفائدة" else "Interest Rate Type")
                    IconButton(onClick = { showRateInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = CalcColors.accent())
                    }
                }

                if (showRateInfoDialog) {
                    Dialog(onDismissRequest = { showRateInfoDialog = false }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CalcColors.surface(),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(Modifier.padding(24.dp)) {
                                Text(
                                    text = stringResource(R.string.rate_type_info_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = CalcColors.textPrimary(),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(stringResource(R.string.rate_type_reducing_desc), fontSize = 14.sp, color = CalcColors.textMuted(), modifier = Modifier.padding(bottom = 8.dp))
                                Text(stringResource(R.string.rate_type_flat_desc), fontSize = 14.sp, color = CalcColors.textMuted(), modifier = Modifier.padding(bottom = 8.dp))
                                Text(stringResource(R.string.rate_type_murabaha_desc), fontSize = 14.sp, color = CalcColors.textMuted(), modifier = Modifier.padding(bottom = 8.dp))
                                Text(stringResource(R.string.rate_type_rule78_desc), fontSize = 14.sp, color = CalcColors.textMuted(), modifier = Modifier.padding(bottom = 16.dp))

                                Button(
                                    onClick = { showRateInfoDialog = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CalcColors.accent())
                                ) {
                                    Text(stringResource(R.string.close), color = Color.White)
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rateTypes = listOf(
                        RateType.REDUCING to (if(currentLang=="ar") "متناقصة" else "Reducing"), 
                        RateType.FLAT to (if(currentLang=="ar") "ثابتة" else "Flat"), 
                        RateType.MURABAHA to (if(currentLang=="ar") "مرابحة" else "Murabaha"),
                        RateType.RULE_OF_78 to (if(currentLang=="ar") "قاعدة 78" else "Rule 78")
                    )
                    rateTypes.forEach { (type, label) ->
                        val isSelected = input.rateType == type
                        Button(
                            onClick = { onInputChanged(input.copy(rateType = type)) },
                            colors = ButtonDefaults.buttonColors(containerColor = if(isSelected) CalcColors.accent() else CalcColors.surface(), contentColor = if(isSelected) Color.White else CalcColors.textPrimary()),
                            border = if(!isSelected) BorderStroke(1.dp, CalcColors.border()) else null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                InField(if(currentLang=="ar") "نسبة الفائدة السنوية (%)" else "Annual Interest Rate (%)", input.annualRate) { onInputChanged(input.copy(annualRate = it)) }
                
                InField(
                    if(currentLang=="ar") "مدة القرض (بالأشهر)" else "Loan Term (Months)",
                    input.months.toDouble()
                ) { onInputChanged(input.copy(months = it.toInt())) }
                
                Button(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = CalcColors.accent().copy(alpha = 0.1f), contentColor = CalcColors.accent())) {
                    Text(if(showAdvanced) (if(currentLang=="ar") "إخفاء الإعدادات المتقدمة" else "HIDE PRO SETTINGS") else (if(currentLang=="ar") "إظهار الإعدادات المتقدمة" else "SHOW PRO SETTINGS"), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (showAdvanced) {
            item {
                PremiumCard {
                    SectionHeader(if(currentLang=="ar") "رسوم المعاملة (Upfront)" else "Processing Fees")
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = if(input.feePercent == 0.0) "" else input.feePercent.toString(),
                            onValueChange = { onInputChanged(input.copy(feePercent = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "نسبة الرسوم (%)" else "Fee (%)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                        TextField(
                            value = if(input.feeFixed == 0.0) "" else input.feeFixed.toString(),
                            onValueChange = { onInputChanged(input.copy(feeFixed = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "رسوم ثابتة (JOD)" else "Fixed Fee (JOD)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                    }
                    
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if(currentLang=="ar") "خصم الرسوم من مبلغ القرض؟" else "Deduct Fees from Loan?", color = CalcColors.textPrimary(), fontSize = 12.sp)
                        Switch(
                            checked = input.deductFeesFromLoan,
                            onCheckedChange = { onInputChanged(input.copy(deductFeesFromLoan = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CalcColors.accent())
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    SectionHeader(if(currentLang=="ar") "غرامات السداد المبكر" else "Early Settlement Penalty")
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = if(input.earlySettlementFeePercent == 0.0) "" else input.earlySettlementFeePercent.toString(),
                            onValueChange = { onInputChanged(input.copy(earlySettlementFeePercent = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "غرامة (%)" else "Penalty (%)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                        TextField(
                            value = if(input.earlySettlementFeeFixed == 0.0) "" else input.earlySettlementFeeFixed.toString(),
                            onValueChange = { onInputChanged(input.copy(earlySettlementFeeFixed = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "غرامة ثابتة (JOD)" else "Fixed Penalty (JOD)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    SectionHeader(if(currentLang=="ar") "رسوم سنوية وتأمينات" else "Insurance & Annual Fees")
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = if(input.insuranceUpfrontPercent == 0.0) "" else input.insuranceUpfrontPercent.toString(),
                            onValueChange = { onInputChanged(input.copy(insuranceUpfrontPercent = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "تأمين ابتدائي (%)" else "Upfront Ins (%)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                        TextField(
                            value = if(input.insuranceUpfrontFixed == 0.0) "" else input.insuranceUpfrontFixed.toString(),
                            onValueChange = { onInputChanged(input.copy(insuranceUpfrontFixed = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "تأمين مقدم (JOD)" else "Upfront Ins (JOD)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                    }
                    
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if(currentLang=="ar") "خصم التأمين من القرض؟" else "Deduct Ins from Loan?", color = CalcColors.textPrimary(), fontSize = 12.sp)
                        Switch(
                            checked = input.deductInsuranceFromLoan,
                            onCheckedChange = { onInputChanged(input.copy(deductInsuranceFromLoan = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CalcColors.accent())
                        )
                    }

                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = if(input.monthlyInsurance == 0.0) "" else input.monthlyInsurance.toString(),
                            onValueChange = { onInputChanged(input.copy(monthlyInsurance = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "تأمين شهري (JOD)" else "Monthly Ins (JOD)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                        TextField(
                            value = if(input.annualTax == 0.0) "" else input.annualTax.toString(),
                            onValueChange = { onInputChanged(input.copy(annualTax = it.toDoubleOrNull() ?: 0.0)) },
                            label = { Text(if(currentLang=="ar") "ضريبة/رسوم سنوية" else "Annual Fee/Tax", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                    }

                    TextField(
                        value = if(input.mandatoryCardFee == 0.0) "" else input.mandatoryCardFee.toString(),
                        onValueChange = { onInputChanged(input.copy(mandatoryCardFee = it.toDoubleOrNull() ?: 0.0)) },
                        label = { Text(if(currentLang=="ar") "رسوم البطاقة الإجبارية (سنوية)" else "Annual Mandatory Card Fee", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )

                    Divider(Modifier.padding(vertical = 16.dp), color = CalcColors.border())

                    SectionHeader(if(currentLang=="ar") "فترات السداد الذكية" else "Smart Repayment Periods")
                    var pStart by remember { mutableStateOf("") }
                    var pEnd by remember { mutableStateOf("") }
                    var pAmt by remember { mutableStateOf("") }
                    input.extraPaymentPeriods.forEachIndexed { index, period ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${if(currentLang=="ar") "شهر" else "Mo"} ${period.startMonth}-${period.endMonth}: +${period.amountPerMonth} JOD", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    // زر تبديل الإستراتيجية لكل فترة
                                    Surface(
                                        modifier = Modifier.clickable {
                                            val newStrat = if (period.strategy == ExtraPaymentStrategy.REDUCE_TERM) ExtraPaymentStrategy.REDUCE_EMI else ExtraPaymentStrategy.REDUCE_TERM
                                            val newList = input.extraPaymentPeriods.toMutableList()
                                            newList[index] = period.copy(strategy = newStrat)
                                            onInputChanged(input.copy(extraPaymentPeriods = newList))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (period.strategy == ExtraPaymentStrategy.REDUCE_TERM) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFF1565C0).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            if (period.strategy == ExtraPaymentStrategy.REDUCE_TERM)
                                                (if(currentLang=="ar") "↓ مدة" else "↓ Term")
                                            else
                                                (if(currentLang=="ar") "↓ قسط" else "↓ EMI"),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (period.strategy == ExtraPaymentStrategy.REDUCE_TERM) Color(0xFF2E7D32) else Color(0xFF1565C0)
                                        )
                                    }
                                    IconButton(onClick = {
                                        pStart = period.startMonth.toString()
                                        pEnd = period.endMonth.toString()
                                        pAmt = period.amountPerMonth.toString()
                                        val newList = input.extraPaymentPeriods.toMutableList().apply { removeAt(index) }
                                        onInputChanged(input.copy(extraPaymentPeriods = newList))
                                    }) { Icon(Icons.Default.Edit, null, tint = CalcColors.accent(), modifier = Modifier.size(20.dp)) }
                                    IconButton(onClick = { 
                                        val newList = input.extraPaymentPeriods.toMutableList().apply { removeAt(index) }
                                        onInputChanged(input.copy(extraPaymentPeriods = newList))
                                    }) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                    
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pStart, 
                            onValueChange = { if(it.all { c -> c.isDigit() }) pStart = it }, 
                            label = { Text(if(currentLang=="ar") "من" else "From", fontSize = 10.sp) }, 
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pEnd, 
                            onValueChange = { if(it.all { c -> c.isDigit() }) pEnd = it }, 
                            label = { Text(if(currentLang=="ar") "إلى" else "To", fontSize = 10.sp) }, 
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pAmt, 
                            onValueChange = { if(it.all { c -> c.isDigit() || c == '.' }) pAmt = it }, 
                            label = { Text(if(currentLang=="ar") "إضافي" else "Extra", fontSize = 10.sp) }, 
                            modifier = Modifier.weight(1.3f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                val sRaw = pStart.toIntOrNull() ?: 0
                                val eRaw = pEnd.toIntOrNull() ?: 0
                                val a = pAmt.toDoubleOrNull() ?: 0.0
                                
                                if (sRaw > 0 && eRaw > 0 && a > 0) {
                                    // تصحيح ذكي: تبديل القيم إذا كانت البداية أكبر من النهاية
                                    val s = minOf(sRaw, eRaw)
                                    val e = maxOf(sRaw, eRaw)
                                    val newList = input.extraPaymentPeriods + ExtraPaymentPeriod(s, e, a)
                                    onInputChanged(input.copy(extraPaymentPeriods = newList))
                                    pStart = ""; pEnd = ""; pAmt = ""
                                }
                            }
                        ) { 
                            Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = CalcColors.accent(), modifier = Modifier.size(38.dp))
                        }
                    }
                    
                    Divider(Modifier.padding(vertical = 16.dp), color = CalcColors.border())
                    
                    SectionHeader(if(currentLang=="ar") "دفعات السداد المبكر" else "Early Repayments")
                    var bMo by remember { mutableStateOf("") }
                    var bAmt by remember { mutableStateOf("") }
                    input.balloonPayments.forEachIndexed { index, b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${if(currentLang=="ar") "شهر" else "Mo"} ${b.month}: ${b.amount} JOD", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            // زر تبديل الإستراتيجية لكل دفعة
                            Surface(
                                modifier = Modifier.clickable {
                                    val newStrat = if (b.strategy == ExtraPaymentStrategy.REDUCE_TERM) ExtraPaymentStrategy.REDUCE_EMI else ExtraPaymentStrategy.REDUCE_TERM
                                    val newList = input.balloonPayments.toMutableList()
                                    newList[index] = b.copy(strategy = newStrat)
                                    onInputChanged(input.copy(balloonPayments = newList))
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (b.strategy == ExtraPaymentStrategy.REDUCE_TERM) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFF1565C0).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    if (b.strategy == ExtraPaymentStrategy.REDUCE_TERM)
                                        (if(currentLang=="ar") "↓ مدة" else "↓ Term")
                                    else
                                        (if(currentLang=="ar") "↓ قسط" else "↓ EMI"),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (b.strategy == ExtraPaymentStrategy.REDUCE_TERM) Color(0xFF2E7D32) else Color(0xFF1565C0)
                                )
                            }
                            IconButton(onClick = {
                                bMo = b.month.toString()
                                bAmt = b.amount.toString()
                                val newList = input.balloonPayments.toMutableList().apply { removeAt(index) }
                                onInputChanged(input.copy(balloonPayments = newList))
                            }) { Icon(Icons.Default.Edit, null, tint = CalcColors.accent(), modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = {
                                val newList = input.balloonPayments.toMutableList().apply { removeAt(index) }
                                onInputChanged(input.copy(balloonPayments = newList))
                            }) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        TextField(value = bMo, onValueChange = { bMo = it }, placeholder = { Text(if(currentLang=="ar") "شهر" else "Month", fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                        TextField(value = bAmt, onValueChange = { bAmt = it }, placeholder = { Text(if(currentLang=="ar") "المبلغ" else "Amount", fontSize = 10.sp) }, modifier = Modifier.weight(2f))
                        IconButton(onClick = {
                            val m = bMo.toIntOrNull() ?: 0; val a = bAmt.toDoubleOrNull() ?: 0.0
                            if (m > 0 && a > 0) {
                                onInputChanged(input.copy(balloonPayments = input.balloonPayments + BalloonPayment(m, a)))
                                bMo = ""; bAmt = ""
                            }
                        }) { Icon(Icons.Default.AddCircle, null, tint = CalcColors.accent()) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            PremiumCard(gradient = true) {
                SectionHeader(if(currentLang=="ar") "الأثر المالي" else "Financial Impact", light = true)
                LoanPie(result, true)
                Spacer(Modifier.height(16.dp))
                
                Spacer(Modifier.height(16.dp))
                
                // Highlighted Dashboard Focus
                val animatedEMI by animateFloatAsState(targetValue = result.monthlyEMI.toFloat(), animationSpec = tween(1000))
                val animatedInterest by animateFloatAsState(targetValue = result.totalInterest.toFloat(), animationSpec = tween(1000))
                val animatedTotal by animateFloatAsState(targetValue = result.totalPayment.toFloat(), animationSpec = tween(1000))

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(if(currentLang=="ar") "القسط الشهري (EMI)" else "Monthly EMI", fontSize = 14.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(animatedEMI)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(if(currentLang=="ar") "إجمالي الفوائد المستحقة" else "Total Interest to Pay", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(animatedInterest)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if(currentLang=="ar") "إجمالي الالتزام" else "Total Obligation", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(animatedTotal)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Divider(color = Color.White.copy(0.1f))
                
                var showDetailedSummary by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { showDetailedSummary = !showDetailedSummary }.padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(if(currentLang=="ar") "التفاصيل الدقيقة والرسوم" else "Detailed Breakdown & Fees", fontSize = 12.sp, color = CalcColors.SoftGold, fontWeight = FontWeight.Bold)
                    Icon(if(showDetailedSummary) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = CalcColors.SoftGold)
                }

                androidx.compose.animation.AnimatedVisibility(visible = showDetailedSummary) {
                    Column(Modifier.fillMaxWidth()) {
                        val monthlyFees = input.monthlyInsurance + (input.annualTax / 12.0) + (input.mandatoryCardFee / 12.0)
                        if (monthlyFees > 0) {
                            ResItem(if(currentLang=="ar") "يشمل رسوم وتأمين شهري بقيمة" else "Incl. monthly fees of", "JOD ${formatter.format(monthlyFees)}", Color.White.copy(0.6f))
                        }

                        val loanPrincipal = (input.assetPrice - input.downPayment)
                        val upfrontFees = loanPrincipal * (input.feePercent / 100.0) + input.feeFixed
                        val upfrontIns = loanPrincipal * (input.insuranceUpfrontPercent / 100.0) + input.insuranceUpfrontFixed

                        if (upfrontFees > 0 || upfrontIns > 0) {
                            if (upfrontFees > 0) ResItem(if(currentLang=="ar") "رسوم معاملات (إبتدائية)" else "Upfront Processing Fees", "JOD ${formatter.format(upfrontFees)}", Color.White.copy(0.7f))
                            if (upfrontIns > 0) ResItem(if(currentLang=="ar") "تأمين حياة (ابتدائي)" else "Upfront Life Insurance", "JOD ${formatter.format(upfrontIns)}", Color.White.copy(0.7f))

                            ResItem(if(currentLang=="ar") "صافي المبلغ المستلم" else "Net Amount Received", "JOD ${formatter.format(result.netReceived)}", Color.White, isBold = true)
                            Divider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(0.05f))
                        }

                        if (result.totalInsurance > 0) {
                            ResItem(if(currentLang=="ar") "إجمالي تكاليف التأمين" else "Total Insurance Cost", "JOD ${formatter.format(result.totalInsurance)}", Color.White.copy(0.7f))
                        }
                        if (result.earlySettlementFeesTotal > 0) {
                            ResItem(if(currentLang=="ar") "رسوم السداد المبكر (للإضافي)" else "Early Settlement Fees", "JOD ${formatter.format(result.earlySettlementFeesTotal)}", Color(0xFFFFB74D))
                        }

                        ResItem(if(currentLang=="ar") "قيمة القرض الأساسية (رأس المال)" else "Original Loan Value", "JOD ${formatter.format(input.assetPrice)}", Color.White.copy(0.7f))
                        ResItem(if(currentLang=="ar") "نسبة التكلفة لأصل القرض" else "Total Cost of Borrowing Ratio", String.format("%.1f%%", result.interestToPrincipalRatio), Color.White.copy(0.7f))

                        Divider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.2f))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(if(currentLang=="ar") "النسبة السنوية الحقيقية (APR)" else "True APR (Annual Percentage Rate)", fontSize = 11.sp, color = CalcColors.SoftGold)
                            Text(String.format("%.2f%%", result.trueAPR), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        if (kotlin.math.abs(result.trueAPR - result.nominalAPR) > 0.01) {
                            Row(Modifier.fillMaxWidth(), Arrangement.End) {
                                Text(if(currentLang=="ar") "الفائدة الاسمية كانت ${result.nominalAPR}%" else "Nominal rate was ${result.nominalAPR}%", fontSize = 9.sp, color = Color.White.copy(0.6f))
                            }
                        }
                    }
                }

                if (result.monthsSaved > 0 || result.interestSaved > 0) {
                    Divider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.2f))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(if(currentLang=="ar") "التوفير المالي" else "FINANCIAL SAVINGS", fontSize = 10.sp, color = CalcColors.SoftGold, fontWeight = FontWeight.Black)
                            if (result.monthsSaved > 0) {
                                Text("${result.monthsSaved} ${if(currentLang=="ar") "شهراً مبكراً" else "Months Early"}", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            if (result.interestSaved > 0) {
                                Text("${if(currentLang=="ar") "توفير فوائد بقيمة" else "Saved"} JOD ${formatter.format(result.interestSaved)}", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(Icons.Default.TrendingDown, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            var shockValue by remember { mutableStateOf(1.5) }
            val shockResult = remember(input, currentLang, shockValue) { 
                LoanEngine().simulateRateShock(input, shockValue, currentLang == "ar") 
            }
            
            PremiumCard {
                SectionHeader(if(currentLang=="ar") "محاكي صدمات الفائدة 📉" else "Rate Shock Simulator 📉")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if(currentLang=="ar") "ماذا لو رفع البنك المركزي الفائدة؟" else "What if the Central Bank hikes rates?",
                    fontSize = 12.sp, color = CalcColors.textMuted()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = shockValue.toFloat(),
                        onValueChange = { shockValue = it.toDouble() },
                        valueRange = 0f..5f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFE53935), activeTrackColor = Color(0xFFE53935))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("+${String.format("%.1f", shockValue)}%", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                }
                if (shockValue > 0) {
                    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = shockResult.impactWarning,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ═══ شريط DTI الديناميكي ═══
        if (financeProfile.monthlySalary > 0) {
            item {
                val dtiWithLoan = ((financeProfile.totalDebtObligations + result.monthlyEMI) / financeProfile.totalIncome * 100).coerceIn(0.0, 100.0)
                val dtiColor = when {
                    dtiWithLoan > 50 -> Color(0xFFF44336)
                    dtiWithLoan > 40 -> Color(0xFFFF9800)
                    dtiWithLoan > 30 -> Color(0xFFFFC107)
                    else -> Color(0xFF4CAF50)
                }
                val disposable = financeProfile.totalIncome - financeProfile.totalDebtObligations - financeProfile.totalLivingExpenses - result.monthlyEMI

                PremiumCard {
                    SectionHeader(if(currentLang=="ar") "تحليل القدرة الشرائية" else "Affordability Analysis")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(if(currentLang=="ar") "نسبة الدين للدخل (DTI)" else "Debt-to-Income Ratio", fontSize = 12.sp, color = CalcColors.textMuted())
                        Text("${String.format("%.1f", dtiWithLoan)}%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = dtiColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(12.dp).background(CalcColors.border().copy(alpha = 0.2f), RoundedCornerShape(6.dp))) {
                        Box(Modifier.fillMaxWidth(fraction = (dtiWithLoan / 100.0).toFloat().coerceIn(0f, 1f)).height(12.dp).background(dtiColor, RoundedCornerShape(6.dp)))
                        Box(Modifier.fillMaxWidth(0.5f).height(12.dp)) {
                            Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(12.dp).background(Color.Red.copy(alpha = 0.7f)))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), Arrangement.SpaceBetween) {
                        Text("0%", fontSize = 8.sp, color = CalcColors.textMuted())
                        Text(if(currentLang=="ar") "حد العبء" else "DTI Limit", fontSize = 8.sp, color = CalcColors.textMuted().copy(0.7f))
                        Text("100%", fontSize = 8.sp, color = CalcColors.textMuted())
                    }
                    if(currentLang=="ar") {
                        Text("ملاحظة: تختلف تعليمات تحديد سقف العبء حسب نوع القرض وجهة التمويل.", fontSize = 8.sp, color = CalcColors.textMuted().copy(0.7f))
                    } else {
                        Text("Note: DTI limits vary by loan type and financial institution.", fontSize = 8.sp, color = CalcColors.textMuted().copy(0.7f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = CalcColors.border().copy(alpha = 0.15f))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(if(currentLang=="ar") "المتبقي بعد القسط" else "Disposable After EMI", fontSize = 12.sp, color = CalcColors.textMuted())
                        Text(
                            "JOD ${DecimalFormat("#,##0").format(disposable)}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (disposable >= 200) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            LoanHealthGauge(result.interestToPrincipalRatio, currentLang == "ar")
            Spacer(Modifier.height(16.dp))
        }

        item {
            LoanAmortizationChart(result.schedule, currentLang == "ar")
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionHeader(if(currentLang=="ar") "نصائح مالية ذكية" else "AI Financial Insights")
        }
        
        items(result.insights) { insight ->
            val bgColor = when(insight.type) {
                InsightType.SUCCESS -> Color(0xFFE8F5E9)
                InsightType.WARNING -> Color(0xFFFFF3E0)
                else -> CalcColors.surface()
            }
            val icon = when(insight.type) {
                InsightType.SUCCESS -> Icons.Default.CheckCircle
                InsightType.WARNING -> Icons.Default.Warning
                else -> Icons.Default.Lightbulb
            }
            val iconColor = when(insight.type) {
                InsightType.SUCCESS -> Color(0xFF2E7D32)
                InsightType.WARNING -> Color(0xFFEF6C00)
                else -> CalcColors.accent()
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = bgColor,
                border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        val textColor = if(LocalIsDarkTheme.current && bgColor == CalcColors.surface()) Color.White else Color.Black
                        Text(insight.title, fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
                        Text(insight.description, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            SectionHeader(if(currentLang=="ar") "جدول سداد القرض" else "Amortization Schedule")
            Spacer(Modifier.height(8.dp))
        }

        item {
            // حسابات تراكمية لعرض تفاصيل دقيقة في كل شهر
            var cumPrincipal = 0.0
            var cumInterest = 0.0
            var cumTotal = 0.0
            val totalInterestAll = result.schedule.sumOf { it.interestPart }
            var expandedMonth by remember { mutableStateOf(-1) }

            Column {
                result.schedule.forEach { m ->
                    cumPrincipal += m.principalPart
                    cumInterest += m.interestPart
                    cumTotal += m.payment
                    val isExpanded = expandedMonth == m.monthNumber
                    ScheduleRow(
                        m = m,
                        formatter = formatter,
                        isArabic = currentLang == "ar",
                        isExpanded = isExpanded,
                        cumPrincipal = cumPrincipal,
                        cumInterest = cumInterest,
                        cumTotal = cumTotal,
                        totalInterestAll = totalInterestAll
                    ) {
                        expandedMonth = if (expandedMonth == m.monthNumber) -1 else m.monthNumber
                    }
                }
            }
        }
        
        item {
            Spacer(Modifier.height(32.dp))
            var showSaveDialog by remember { mutableStateOf(false) }
            val saveCoroutineScope = rememberCoroutineScope()
            
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CalcColors.accent())
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (currentLang == "ar") "حفظ القرض في محفظتي" else "Save Loan to Portfolio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(16.dp))
            val ctx = LocalActivity.current as android.content.Context
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { com.khalil.calc.pdf.PdfGenerator.generateAndPrint(ctx, input, result, currentLang == "ar", isYearly = false) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CalcColors.surface(), contentColor = CalcColors.textPrimary()),
                    border = BorderStroke(1.dp, CalcColors.border())
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFD32F2F))
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentLang == "ar") "PDF (شهري)" else "PDF (Monthly)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { com.khalil.calc.pdf.PdfGenerator.generateAndPrint(ctx, input, result, currentLang == "ar", isYearly = true) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CalcColors.surface(), contentColor = CalcColors.textPrimary()),
                    border = BorderStroke(1.dp, CalcColors.border())
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFD32F2F))
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentLang == "ar") "PDF (سنوي)" else "PDF (Yearly)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            if (showSaveDialog) {
                var loanName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text(if(currentLang=="ar") "حفظ القرض" else "Save Loan", color = CalcColors.textPrimary()) },
                    text = {
                        OutlinedTextField(
                            value = loanName,
                            onValueChange = { loanName = it },
                            label = { Text(if(currentLang=="ar") "اسم القرض (مثال: سيارة مرسيدس)" else "Loan Name (e.g. Dream Car)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (loanName.isNotBlank()) {
                                saveCoroutineScope.launch {
                                    dao.insertLoan(SavedLoan(
                                        name = loanName,
                                        assetPrice = input.assetPrice,
                                        downPayment = input.downPayment,
                                        months = input.months,
                                        annualRate = input.annualRate,
                                        rateType = input.rateType,
                                        extraMonthly = input.extraMonthly,
                                        extraMonthlyStrategy = input.extraMonthlyStrategy
                                    ))
                                }
                                showSaveDialog = false
                            }
                        }) { Text(if(currentLang=="ar") "حفظ" else "Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) { Text(if(currentLang=="ar") "إلغاء" else "Cancel") }
                    }
                )
            }
        }
        
        item {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "Produced by Khalil Badarin | تم التطوير بواسطة خليل بدارين",
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = CalcColors.textMuted()
            )
        }
    }
}

@Composable
fun ScheduleTable(schedule: List<AmortizationMonth>, formatter: DecimalFormat, isArabic: Boolean) {
    // تتبع الكروت المفتوحة
    var expandedMonth by remember { mutableStateOf(-1) }
    
    var cumulativePrincipal = 0.0
    var cumulativeInterest = 0.0
    var cumulativeTotalPaid = 0.0

    // حساب إجمالي الفوائد مقدماً لمعرفة المتبقي
    val totalInterestAll: Double = schedule.sumOf { it.interestPart }

    Column {
        schedule.forEach { m ->
            cumulativePrincipal += m.principalPart
            cumulativeInterest += m.interestPart
            cumulativeTotalPaid += m.payment
            
            val totalExtra = m.extraPaid + m.balloonPaid
            val isExpanded = expandedMonth == m.monthNumber

            // لون الشريط الجانبي لتمييز نوع الدفعة
            val accentColor = when {
                m.balloonPaid > 0 && m.extraPaid > 0 -> Color(0xFF9C27B0) // بنفسجي: بالون + إضافي معاً
                m.balloonPaid > 0 -> Color(0xFF2E7D32)                    // أخضر: سداد مبكر
                m.extraPaid > 0 -> Color(0xFF2196F3)                      // أزرق: دفعة إضافية
                m.isGrace -> Color(0xFFFFC107)                            // أصفر: فترة سماح
                else -> Color(0xFF90A4AE)                                 // رمادي واضح: قسط عادي
            }

            // ═══ الكارد القابل للفتح ═══
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { expandedMonth = if (isExpanded) -1 else m.monthNumber },
                shape = RoundedCornerShape(12.dp),
                color = CalcColors.surface(),
                shadowElevation = if (isExpanded) 4.dp else 0.dp,
                border = BorderStroke(1.dp, if (isExpanded) accentColor.copy(alpha = 0.4f) else CalcColors.border().copy(alpha = 0.3f))
            ) {
                Row {
                    // شريط جانبي ملون
                    Box(Modifier
                        .width(4.dp)
                        .height(if (isExpanded) 180.dp else 48.dp)
                        .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    )
                    
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // ─── الصف المضغوط (دائماً ظاهر) ───
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            // رقم الشهر + التصنيف
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        m.monthNumber.toString(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 12.sp, fontWeight = FontWeight.Black,
                                        color = accentColor
                                    )
                                }
                                if (m.label != (if(isArabic) "قسط عادي" else "Regular")) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(m.label, fontSize = 9.sp, color = accentColor, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            // الدفعة والرصيد
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${formatter.format(m.payment)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CalcColors.textPrimary())
                                    Text("${formatter.format(m.remainingBalance)}", fontSize = 10.sp, color = CalcColors.textMuted())
                                }
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null, tint = CalcColors.textMuted(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ─── التفاصيل (تظهر فقط عند الفتح) ───
                        if (isExpanded) {
                            Spacer(Modifier.height(10.dp))
                            Divider(color = CalcColors.border().copy(alpha = 0.3f))
                            Spacer(Modifier.height(8.dp))
                            
                            // تفاصيل الدفعة
                            @Composable
                            fun DetailRow(label: String, value: String, color: Color, bold: Boolean = false) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                                    Text(label, fontSize = 12.sp, color = CalcColors.textMuted())
                                    Text(value, fontSize = 12.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
                                }
                            }

                            // ═══ القسط المتفق عليه مع البنك ═══
                            Text(
                                if(isArabic) "📋 القسط الشهري (يُقسم)" else "📋 EMI Breakdown",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = CalcColors.textPrimary()
                            )
                            DetailRow(
                                if(isArabic) "القسط المتفق عليه" else "Agreed Installment",
                                formatter.format(m.emiAmount),
                                CalcColors.textPrimary(), bold = true
                            )
                            DetailRow(
                                if(isArabic) "  🔴 منها للفائدة" else "  🔴 → Interest",
                                formatter.format(m.interestPart),
                                Color(0xFFE57373)
                            )
                            DetailRow(
                                if(isArabic) "  🟢 منها للأصل" else "  🟢 → Principal",
                                formatter.format(m.principalFromEMI),
                                Color(0xFF4CAF50)
                            )
                            if (m.insurancePart > 0) {
                                DetailRow(
                                    if(isArabic) "  🛡️ تأمين شهري" else "  🛡️ Monthly Ins.",
                                    formatter.format(m.insurancePart),
                                    Color(0xFFA1887F)
                                )
                            }
                            if (m.recurringFeesPart > 0) {
                                DetailRow(
                                    if(isArabic) "  🏦 رسوم إضافية" else "  🏦 Extra Fees",
                                    formatter.format(m.recurringFeesPart),
                                    Color(0xFFA1887F)
                                )
                            }

                            // ═══ الدفعات الذكية (100% أصل) ═══
                            if (totalExtra > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if(isArabic) "⚡ دفعات ذكية (100% أصل)" else "⚡ Smart Payments (100% Principal)",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                                if (m.extraToPrincipal > 0) {
                                    DetailRow(
                                        if(isArabic) "  🔵 إضافي → أصل" else "  🔵 Extra → Prin",
                                        "+${formatter.format(m.extraToPrincipal)}",
                                        Color(0xFF2196F3), bold = true
                                    )
                                }
                                if (m.balloonToPrincipal > 0) {
                                    DetailRow(
                                        if(isArabic) "  🟣 سداد مبكر → أصل" else "  🟣 Prepay → Prin",
                                        "+${formatter.format(m.balloonToPrincipal)}",
                                        Color(0xFF7B1FA2), bold = true
                                    )
                                }
                            }

                            // ═══ إجمالي كسر الأصل ═══
                            Spacer(Modifier.height(2.dp))
                            Divider(color = CalcColors.border().copy(alpha = 0.15f))
                            DetailRow(
                                if(isArabic) "💪 إجمالي كسر الأصل" else "💪 Total Principal Paid",
                                formatter.format(m.principalPart),
                                Color(0xFF2E7D32), bold = true
                            )

                            Spacer(Modifier.height(6.dp))
                            Divider(color = CalcColors.border().copy(alpha = 0.2f))
                            Spacer(Modifier.height(4.dp))

                            // ═══ المدفوع حتى الآن ═══
                            Text(
                                if(isArabic) "💰 المدفوع حتى الآن" else "💰 Paid So Far",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = CalcColors.accent().copy(alpha = 0.7f)
                            )
                            DetailRow(
                                if(isArabic) "فوائد مدفوعة" else "Interest Paid",
                                formatter.format(cumulativeInterest),
                                Color(0xFFE57373)
                            )
                            DetailRow(
                                if(isArabic) "أصل مسدد" else "Principal Paid",
                                formatter.format(cumulativePrincipal),
                                Color(0xFF4CAF50)
                            )
                            DetailRow(
                                if(isArabic) "إجمالي مدفوع" else "Total Paid",
                                formatter.format(cumulativeTotalPaid),
                                CalcColors.accent(), bold = true
                            )

                            Spacer(Modifier.height(4.dp))
                            Divider(color = CalcColors.border().copy(alpha = 0.15f))
                            Spacer(Modifier.height(4.dp))

                            // ═══ المتبقي ═══
                            val remainingInterest = (totalInterestAll - cumulativeInterest).coerceAtLeast(0.0)
                            val remainingTotal = m.remainingBalance + remainingInterest

                            Text(
                                if(isArabic) "⏳ المتبقي عليك" else "⏳ Remaining",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8F00).copy(alpha = 0.8f)
                            )
                            DetailRow(
                                if(isArabic) "فوائد متبقية" else "Remaining Interest",
                                formatter.format(remainingInterest),
                                Color(0xFFE57373)
                            )
                            DetailRow(
                                if(isArabic) "أصل متبقي" else "Remaining Principal",
                                formatter.format(m.remainingBalance),
                                Color(0xFF4CAF50)
                            )
                            DetailRow(
                                if(isArabic) "إجمالي متبقي" else "Total Remaining",
                                formatter.format(remainingTotal),
                                Color(0xFFFF8F00), bold = true
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Button(
        //     onClick = { com.khalil.calc.pdf.PdfGenerator.generateAndPrint(context, input, result, isArabic, isYearly = false) },
        //     modifier = Modifier.fillMaxWidth().height(50.dp),
        //     shape = RoundedCornerShape(12.dp),
        //     colors = ButtonDefaults.buttonColors(containerColor = CalcColors.accent())
        // ) {
        //     Icon(Icons.Default.PictureAsPdf, contentDescription = null)
        //     Spacer(Modifier.width(8.dp))
        //     Text(if(isArabic) "تقرير PDF شامل" else "Generate Comprehensive PDF", fontWeight = FontWeight.Bold)
        // }
    }
}

// Function exportScheduleToPdf removed.
// It is fully superseded by PdfGenerator.generateAndPrint

@Composable
fun LoanHealthGauge(ratio: Double, isArabic: Boolean) {
    val score = when {
        ratio < 15 -> if(isArabic) "قرض مثالي" else "Excellent Loan"
        ratio < 30 -> if(isArabic) "قرض جيد" else "Good Loan"
        ratio < 50 -> if(isArabic) "قرض مقبول" else "Fair Loan"
        else -> if(isArabic) "قرض مكلف جداً" else "High Cost Loan"
    }
    val color = when {
        ratio < 15 -> Color(0xFF4CAF50)
        ratio < 30 -> Color(0xFF8BC34A)
        ratio < 50 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    PremiumCard {
        SectionHeader(if(isArabic) "مقياس صحة القرض" else "Loan Health Score")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = (100 - ratio.coerceIn(0.0, 100.0)).toFloat() / 100f,
                    modifier = Modifier.size(100.dp),
                    color = color,
                    strokeWidth = 8.dp,
                    trackColor = color.copy(alpha = 0.1f)
                )
                Text("${(100 - ratio.coerceAtMost(100.0)).toInt()}%", fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(score, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if(isArabic) "نسبة الفائدة إلى أصل القرض هي ${ratio.toInt()}%" 
                else "Interest-to-Principal ratio is ${ratio.toInt()}%",
                fontSize = 12.sp, color = CalcColors.textMuted()
            )
        }
    }
}

@Composable
fun LoanAmortizationChart(schedule: List<AmortizationMonth>, isArabic: Boolean) {
    if (schedule.isEmpty()) return

    PremiumCard {
        SectionHeader(if (isArabic) "مسار إطفاء القرض (Amortization)" else "Loan Amortization Path")
        Spacer(Modifier.height(8.dp))

        val maxBalance = schedule.first().openingBalance
        var totalInterestAmount = 0f
        schedule.forEach { totalInterestAmount += it.interestPart.toFloat() }

        val principalPoints = mutableListOf<Point>()
        val interestPoints = mutableListOf<Point>()

        var cumulativeInterest = 0f

        // Month 0
        principalPoints.add(Point(0f, maxBalance.toFloat()))
        interestPoints.add(Point(0f, totalInterestAmount))

        for ((index, m) in schedule.withIndex()) {
            val monthFloat = (index + 1).toFloat()
            principalPoints.add(Point(monthFloat, m.remainingBalance.toFloat()))
            cumulativeInterest += m.interestPart.toFloat()
            var remInt = totalInterestAmount - cumulativeInterest
            if (remInt < 0f) remInt = 0f
            interestPoints.add(Point(monthFloat, remInt))
        }

        // Find maximum Y value overall for scale
        val maxPointY = maxOf(maxBalance.toFloat(), totalInterestAmount)
        val steps = 5

        val xAxisData = AxisData.Builder()
            .axisStepSize(40.dp)
            .backgroundColor(Color.Transparent)
            .steps(schedule.size)
            .labelData { i -> if (i % 12 == 0 && i > 0) "${i / 12}Y" else "" }
            .labelAndAxisLinePadding(15.dp)
            .axisLineColor(CalcColors.border())
            .axisLabelColor(CalcColors.textMuted())
            .build()

        val yAxisData = AxisData.Builder()
            .steps(steps)
            .backgroundColor(Color.Transparent)
            .labelAndAxisLinePadding(20.dp)
            .labelData { i ->
                val yVal = (i * (maxPointY / steps))
                if (yVal >= 1000) "${(yVal / 1000).toInt()}k" else yVal.toInt().toString()
            }
            .axisLineColor(CalcColors.border())
            .axisLabelColor(CalcColors.textMuted())
            .build()

        val lineChartData = LineChartData(
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        dataPoints = principalPoints,
                        LineStyle(
                            color = Color(0xFF1565C0),
                            lineType = LineType.SmoothCurve(isDotted = false)
                        ),
                        IntersectionPoint(color = Color.Transparent, radius = 0.dp),
                        SelectionHighlightPoint(color = Color(0xFF1565C0)),
                        ShadowUnderLine(
                            alpha = 0.3f,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1565C0).copy(0.3f), Color.Transparent)
                            )
                        ),
                        SelectionHighlightPopUp()
                    ),
                    Line(
                        dataPoints = interestPoints,
                        LineStyle(
                            color = Color(0xFFE53935),
                            lineType = LineType.SmoothCurve(isDotted = false)
                        ),
                        IntersectionPoint(color = Color.Transparent, radius = 0.dp),
                        SelectionHighlightPoint(color = Color(0xFFE53935)),
                        ShadowUnderLine(
                            alpha = 0.2f,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFE53935).copy(0.2f), Color.Transparent)
                            )
                        ),
                        SelectionHighlightPopUp()
                    )
                )
            ),
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            gridLines = GridLines(color = CalcColors.border().copy(0.3f)),
            backgroundColor = Color.Transparent
        )

        Box(Modifier.fillMaxWidth().height(250.dp).padding(vertical = 16.dp)) {
            LineChart(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                lineChartData = lineChartData
            )
        }
        
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFF1565C0), androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(if (isArabic) "رصيد القرض" else "Remaining Principal", fontSize = 10.sp, color = CalcColors.textPrimary(), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFFE53935), androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(if (isArabic) "الفوائد المتبقية" else "Remaining Interest", fontSize = 10.sp, color = CalcColors.textPrimary(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PremiumCard(gradient: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (gradient) Color.Transparent else CalcColors.surface(),
        shadowElevation = 4.dp,
        border = if(!gradient) BorderStroke(1.dp, CalcColors.border()) else null
    ) {
        val mod = if(gradient) Modifier.background(Brush.verticalGradient(listOf(CalcColors.RoyalBlue, Color(0xFF001A33)))) else Modifier
        Column(mod.padding(20.dp)) { content() }
    }
}

@Composable
fun LoanPie(res: CalculationResult, light: Boolean) {
    val total = res.totalPayment
    val interest = res.totalInterest
    val insurance = res.totalInsurance
    val principal = (total - interest - insurance).coerceAtLeast(0.0)

    val principalAngle = if (total > 0) (principal / total * 360f).toFloat() else 0f
    val interestAngle = if (total > 0) (interest / total * 360f).toFloat() else 0f
    val insuranceAngle = if (total > 0) (insurance / total * 360f).toFloat() else 0f

    val colorPrincipal = if(light) Color.White else Color(0xFF1565C0)
    val colorInterest = Color(0xFFE53935)
    val colorInsurance = if(light) CalcColors.SoftGold else Color(0xFFFFB74D)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                // Background track
                drawArc(if(light) Color.White.copy(0.1f) else Color.LightGray.copy(0.1f), 0f, 360f, false, style = Stroke(28f))
                
                var startAngle = -90f
                // Principal
                if (principalAngle > 0) {
                    drawArc(colorPrincipal, startAngle, principalAngle, false, style = Stroke(28f))
                    startAngle += principalAngle
                }
                // Interest
                if (interestAngle > 0) {
                    drawArc(colorInterest, startAngle, interestAngle, false, style = Stroke(28f))
                    startAngle += interestAngle
                }
                // Insurance
                if (insuranceAngle > 0) {
                    drawArc(colorInsurance, startAngle, insuranceAngle, false, style = Stroke(28f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if(light) "TOTAL" else "التكلفة الكلية", color = if(light) Color.White.copy(0.6f) else CalcColors.textMuted(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("${DecimalFormat("#,###").format(total)}", color = if(light) Color.White else CalcColors.textPrimary(), fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("JOD", color = if(light) Color.White.copy(0.5f) else CalcColors.textMuted(), fontSize = 9.sp)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            PieLegendItem(if(light) "Principal" else "الأصل", colorPrincipal, light)
            Spacer(Modifier.width(16.dp))
            PieLegendItem(if(light) "Interest" else "الفوائد", colorInterest, light)
            Spacer(Modifier.width(16.dp))
            PieLegendItem(if(light) "Fees" else "رسوم", colorInsurance, light)
        }
    }
}

@Composable
fun PieLegendItem(label: String, color: Color, light: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = if(light) Color.White.copy(0.8f) else CalcColors.textPrimary(), fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InField(label: String, valIn: Double, onValChg: (Double) -> Unit) {
    // حالة نصية داخلية للسماح بكتابة الفواصل العشرية بحرية
    var textState by remember(valIn) { 
        mutableStateOf(if(valIn == 0.0) "" else if(valIn == valIn.toInt().toDouble()) valIn.toInt().toString() else valIn.toString()) 
    }

    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, color = CalcColors.textMuted(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        TextField(
            value = textState,
            onValueChange = { newText ->
                // السماح فقط بالأرقام ونقطة عشرية واحدة
                if (newText.isEmpty() || newText.matches(Regex("""^\d*\.?\d*$"""))) {
                    textState = newText
                    newText.toDoubleOrNull()?.let { onValChg(it) } ?: if(newText.isEmpty()) onValChg(0.0) else Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, 
                unfocusedContainerColor = Color.Transparent, 
                focusedIndicatorColor = CalcColors.accent()
            ),
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CalcColors.textPrimary()),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@Composable
fun ResItem(l: String, v: String, c: Color, isBold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
        Text(l, color = c.copy(0.7f), fontSize = 13.sp)
        Text(v, color = c, fontSize = 15.sp, fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold)
    }
}

@Composable
fun SectionHeader(t: String, light: Boolean = false) {
    Text(t.uppercase(), color = if(light) Color.White.copy(0.6f) else CalcColors.accent(), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun MyLoansTab(dao: LoanDao, currentLang: String, onLoad: (SavedLoan) -> Unit) {
    val loans by dao.getAllLoans().collectAsState(initial = emptyList())
    var selectedForCompare by remember { mutableStateOf(setOf<SavedLoan>()) }
    var showCompareDialog by remember { mutableStateOf(false) }
    var refinanceTarget by remember { mutableStateOf<SavedLoan?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp, top = 20.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    text = if(currentLang=="ar") "محفظة القروض" else "Loan Portfolio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = CalcColors.textPrimary(),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (loans.isEmpty()) {
                item {
                    Text(
                        text = if(currentLang=="ar") "لا يوجد قروض محفوظة." else "No saved loans yet.",
                        color = CalcColors.textMuted(),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(loans) { l ->
                val isSelected = selectedForCompare.contains(l)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onLoad(l) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if(isSelected) CalcColors.accent().copy(alpha = 0.1f) else CalcColors.surface()
                    ),
                    border = BorderStroke(1.dp, if(isSelected) CalcColors.accent() else CalcColors.border())
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CalcColors.textPrimary())
                                Spacer(Modifier.height(4.dp))
                                Text("${if(currentLang=="ar") "القيمة:" else "Value:"} JOD ${DecimalFormat("#,##0").format(l.assetPrice)} | ${l.annualRate}% | ${l.months} ${if(currentLang=="ar") "أشهر" else "mo"}", fontSize = 12.sp, color = CalcColors.textMuted())
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { refinanceTarget = l }) {
                                    Icon(Icons.Default.Autorenew, null, tint = CalcColors.SoftGold)
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked && selectedForCompare.size < 2) {
                                            selectedForCompare = selectedForCompare + l
                                        } else if (!checked) {
                                            selectedForCompare = selectedForCompare - l
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = CalcColors.accent())
                                )
                                IconButton(onClick = { coroutineScope.launch { dao.deleteLoan(l) } }) {
                                    Icon(Icons.Default.DeleteOutline, null, tint = Color.Red.copy(0.7f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "Produced by Khalil Badarin | تم التطوير بواسطة خليل بدارين",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = CalcColors.textMuted(),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (selectedForCompare.size == 2) {
            Button(
                onClick = { showCompareDialog = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CalcColors.RoyalBlue)
            ) {
                Icon(Icons.Default.CompareArrows, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if(currentLang=="ar") "مقارنة العرضين" else "Compare Offers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showCompareDialog && selectedForCompare.size == 2) {
        val loansList = selectedForCompare.toList()
        val loan1 = loansList[0]
        val loan2 = loansList[1]
        
        val engine = LoanEngine()
        val res1 = engine.calculate(LoanInput(loan1.assetPrice, loan1.downPayment, loan1.months, loan1.annualRate, loan1.rateType, extraMonthly = loan1.extraMonthly, extraMonthlyStrategy = loan1.extraMonthlyStrategy), currentLang=="ar")
        val res2 = engine.calculate(LoanInput(loan2.assetPrice, loan2.downPayment, loan2.months, loan2.annualRate, loan2.rateType, extraMonthly = loan2.extraMonthly, extraMonthlyStrategy = loan2.extraMonthlyStrategy), currentLang=="ar")
        
        val formatter = DecimalFormat("#,##0.00")
        
        AlertDialog(
            onDismissRequest = { showCompareDialog = false },
            title = { Text(if(currentLang=="ar") "مقارنة القروض" else "Loan Comparison", fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(loan1.name, fontWeight = FontWeight.Bold, color = CalcColors.accent())
                            Spacer(Modifier.height(8.dp))
                            Text(if(currentLang=="ar") "القسط الشهري" else "Monthly EMI", fontSize = 10.sp, color = CalcColors.textMuted())
                            Text("JOD ${formatter.format(res1.monthlyEMI)}", fontWeight = FontWeight.Bold, color = if(res1.monthlyEMI <= res2.monthlyEMI) Color(0xFF4CAF50) else CalcColors.textPrimary())
                            Spacer(Modifier.height(8.dp))
                            Text(if(currentLang=="ar") "إجمالي الفوائد" else "Total Interest", fontSize = 10.sp, color = CalcColors.textMuted())
                            Text("JOD ${formatter.format(res1.totalInterest)}", fontWeight = FontWeight.Bold, color = if(res1.totalInterest <= res2.totalInterest) Color(0xFF4CAF50) else CalcColors.textPrimary())
                        }
                        Divider(Modifier.width(1.dp).height(100.dp).background(CalcColors.border()))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(loan2.name, fontWeight = FontWeight.Bold, color = CalcColors.accent())
                            Spacer(Modifier.height(8.dp))
                            Text(if(currentLang=="ar") "القسط الشهري" else "Monthly EMI", fontSize = 10.sp, color = CalcColors.textMuted())
                            Text("JOD ${formatter.format(res2.monthlyEMI)}", fontWeight = FontWeight.Bold, color = if(res2.monthlyEMI < res1.monthlyEMI) Color(0xFF4CAF50) else CalcColors.textPrimary())
                            Spacer(Modifier.height(8.dp))
                            Text(if(currentLang=="ar") "إجمالي الفوائد" else "Total Interest", fontSize = 10.sp, color = CalcColors.textMuted())
                            Text("JOD ${formatter.format(res2.totalInterest)}", fontWeight = FontWeight.Bold, color = if(res2.totalInterest < res1.totalInterest) Color(0xFF4CAF50) else CalcColors.textPrimary())
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCompareDialog = false }) { Text(if(currentLang=="ar") "إغلاق" else "Close") }
            }
        )
    }

    if (refinanceTarget != null) {
        var newRate by remember { mutableStateOf(refinanceTarget!!.annualRate.toString()) }
        var upfrontRate by remember { mutableStateOf("0.0") }
        var oldPenalty by remember { mutableStateOf("1.0") }
        
        AlertDialog(
            onDismissRequest = { refinanceTarget = null },
            title = { Text(if(currentLang=="ar") "إعادة تمويل: ${refinanceTarget!!.name}" else "Refinance: ${refinanceTarget!!.name}", fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if(currentLang=="ar") "محاكاة نقل القرض لبنك آخر مع فائدة أقل:" else "Simulate moving your loan to another bank with lower rate:", fontSize = 12.sp, color = CalcColors.textMuted())
                    
                    OutlinedTextField(
                        value = newRate, onValueChange = { newRate = it },
                        label = { Text(if(currentLang=="ar") "الفائدة الجديدة للبنك الجديد (%)" else "New Bank Rate (%)", fontSize = 10.sp) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = upfrontRate, onValueChange = { upfrontRate = it },
                        label = { Text(if(currentLang=="ar") "رسوم معاملة البنك الجديد (%)" else "New Bank Processing Fee (%)", fontSize = 10.sp) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = oldPenalty, onValueChange = { oldPenalty = it },
                        label = { Text(if(currentLang=="ar") "عمولة السداد المبكر للبنك القديم (%)" else "Old Bank Penalty (%)", fontSize = 10.sp) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    val parsedNewRate = newRate.toDoubleOrNull() ?: 0.0
                    if (parsedNewRate > 0) {
                        val engine = LoanEngine()
                        // افتراضياً البنك القديم:
                        val oldRes = engine.calculate(LoanInput(refinanceTarget!!.assetPrice, refinanceTarget!!.downPayment, refinanceTarget!!.months, refinanceTarget!!.annualRate, refinanceTarget!!.rateType, extraMonthly = refinanceTarget!!.extraMonthly, extraMonthlyStrategy = refinanceTarget!!.extraMonthlyStrategy), currentLang=="ar")
                        // بما أن القرض في البداية نعتبر أنه لم يسدد منه شيء كمحاكاة:
                        val refInput = RefinanceInput(
                            currentBalance = refinanceTarget!!.assetPrice - refinanceTarget!!.downPayment,
                            currentRemainingMonths = refinanceTarget!!.months,
                            currentEMI = oldRes.monthlyEMI,
                            currentEarlySettlementFeePercent = oldPenalty.toDoubleOrNull() ?: 0.0,
                            newBankAnnualRate = parsedNewRate,
                            newBankMonths = refinanceTarget!!.months,
                            newBankProcessingFeePercent = upfrontRate.toDoubleOrNull() ?: 0.0,
                            newBankProcessingFeeFixed = 0.0,
                            newBankRateType = RateType.REDUCING
                        )
                        val refResult = engine.analyzeRefinance(refInput, currentLang=="ar")
                        
                        Divider(Modifier.padding(vertical = 8.dp))
                        Surface(color = if (refResult.isRecommended) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(refResult.recommendationTitle, fontWeight = FontWeight.Bold, color = if (refResult.isRecommended) Color(0xFF2E7D32) else Color(0xFFC62828))
                                Text(refResult.recommendationDesc, fontSize = 11.sp, color = if (refResult.isRecommended) Color(0xFF2E7D32) else Color(0xFFC62828))
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text(if(currentLang=="ar") "قسطك الجديد:" else "New EMI:")
                                    Text("JOD ${DecimalFormat("#").format(refResult.newBankEMI)}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { refinanceTarget = null }) { Text(if(currentLang=="ar") "إغلاق" else "Close") } }
        )
    }
}

@Composable
fun ScheduleRow(
    m: AmortizationMonth,
    formatter: DecimalFormat,
    isArabic: Boolean,
    isExpanded: Boolean,
    cumPrincipal: Double = 0.0,
    cumInterest: Double = 0.0,
    cumTotal: Double = 0.0,
    totalInterestAll: Double = 0.0,
    onToggle: () -> Unit
) {
    val accentColor = when {
        m.balloonPaid > 0 && m.extraPaid > 0 -> Color(0xFF9C27B0)
        m.balloonPaid > 0 -> Color(0xFF2E7D32)
        m.extraPaid > 0 -> Color(0xFF2196F3)
        m.isGrace -> Color(0xFFFFC107)
        else -> Color(0xFF90A4AE)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = CalcColors.surface(),
        border = BorderStroke(1.dp, if (isExpanded) accentColor.copy(alpha = 0.5f) else CalcColors.border().copy(alpha = 0.15f)),
        shadowElevation = if(isExpanded) 4.dp else 0.dp
    ) {
        Row {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)))
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                // ─── السطر المضغوط ───
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = accentColor.copy(alpha = 0.12f)) {
                            Text(
                                m.monthNumber.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.Black, color = accentColor
                            )
                        }
                        if (m.label != (if(isArabic) "قسط عادي" else "Regular")) {
                            Spacer(Modifier.width(6.dp))
                            Text(m.label, fontSize = 9.sp, color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatter.format(m.payment), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CalcColors.textPrimary())
                            Text(formatter.format(m.remainingBalance), fontSize = 10.sp, color = CalcColors.textMuted())
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = CalcColors.textMuted(), modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ─── التفاصيل الكاملة ───
                if (isExpanded) {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = CalcColors.border().copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))

                    val totalExtra = m.extraPaid + m.balloonPaid

                    // ═══ القسط المتفق عليه مع البنك ═══
                    Text(
                        if(isArabic) "📋 القسط الشهري (يُقسّم)" else "📋 EMI Breakdown",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CalcColors.textPrimary()
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "القسط المتفق عليه" else "Agreed Installment", fontSize = 12.sp, color = CalcColors.textMuted())
                        Text(formatter.format(m.emiAmount), fontSize = 12.sp, color = CalcColors.textPrimary(), fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "  🔴 منها للفائدة" else "  🔴 → Interest", fontSize = 12.sp, color = CalcColors.textMuted())
                        Text(formatter.format(m.interestPart), fontSize = 12.sp, color = Color(0xFFE57373), fontWeight = FontWeight.Normal)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "  🟢 منها للأصل" else "  🟢 → Principal", fontSize = 12.sp, color = CalcColors.textMuted())
                        Text(formatter.format(m.principalFromEMI), fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Normal)
                    }
                    if (m.insurancePart > 0) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "  🛡️ تأمين شهري" else "  🛡️ Monthly Ins.", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(m.insurancePart), fontSize = 12.sp, color = Color(0xFFA1887F), fontWeight = FontWeight.Normal)
                        }
                    }
                    if (m.recurringFeesPart > 0) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "  🏦 رسوم شهرية" else "  🏦 Monthly Fees", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(m.recurringFeesPart), fontSize = 12.sp, color = Color(0xFFA1887F), fontWeight = FontWeight.Normal)
                        }
                    }

                    // ═══ الدفعات الذكية (100% أصل) ═══
                    if (totalExtra > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if(isArabic) "⚡ دفعات ذكية (100% أصل)" else "⚡ Smart Payments (100% Principal)",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3)
                        )
                        if (m.extraToPrincipal > 0) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                                Text(if(isArabic) "  🔵 إضافي → أصل" else "  🔵 Extra → Prin", fontSize = 12.sp, color = CalcColors.textMuted())
                                Text("+${formatter.format(m.extraToPrincipal)}", fontSize = 12.sp, color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (m.balloonToPrincipal > 0) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                                Text(if(isArabic) "  🟣 سداد مبكر → أصل" else "  🟣 Prepay → Prin", fontSize = 12.sp, color = CalcColors.textMuted())
                                Text("+${formatter.format(m.balloonToPrincipal)}", fontSize = 12.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // إجمالي كسر الأصل
                    Spacer(Modifier.height(2.dp))
                    Divider(color = CalcColors.border().copy(alpha = 0.15f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "💪 إجمالي كسر الأصل" else "💪 Total Principal Paid", fontSize = 12.sp, color = CalcColors.textMuted())
                        Text(formatter.format(m.principalPart), fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }

                    if (m.emiAmount != m.payment && totalExtra > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if(isArabic) "💡 100% من البالون ذهبت للأصل مباشرة" else "💡 Balloon went 100% to principal",
                            fontSize = 9.sp, color = CalcColors.accent(), fontWeight = FontWeight.Bold
                        )
                    }

                    if (cumTotal > 0) {
                        Spacer(Modifier.height(6.dp))
                        Divider(color = CalcColors.border().copy(alpha = 0.2f))
                        Spacer(Modifier.height(4.dp))

                        // ═══ المدفوع حتى الآن ═══
                        Text(
                            if(isArabic) "💰 المدفوع حتى الآن" else "💰 Paid So Far",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CalcColors.accent().copy(alpha = 0.7f)
                        )
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "فوائد مدفوعة" else "Interest Paid", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(cumInterest), fontSize = 12.sp, color = Color(0xFFE57373))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "أصل مسدد" else "Principal Paid", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(cumPrincipal), fontSize = 12.sp, color = Color(0xFF4CAF50))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "إجمالي مدفوع" else "Total Paid", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(cumTotal), fontSize = 12.sp, color = CalcColors.accent(), fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(4.dp))
                        Divider(color = CalcColors.border().copy(alpha = 0.15f))
                        Spacer(Modifier.height(4.dp))

                        // ═══ المتبقي ═══
                        val remainingInterest = (totalInterestAll - cumInterest).coerceAtLeast(0.0)
                        val remainingTotal = m.remainingBalance + remainingInterest
                        Text(
                            if(isArabic) "⏳ المتبقي عليك" else "⏳ Remaining",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00).copy(alpha = 0.8f)
                        )
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "فوائد متبقية" else "Remaining Interest", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(remainingInterest), fontSize = 12.sp, color = Color(0xFFE57373))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "أصل متبقي" else "Remaining Principal", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(m.remainingBalance), fontSize = 12.sp, color = Color(0xFF4CAF50))
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "إجمالي متبقي" else "Total Remaining", fontSize = 12.sp, color = CalcColors.textMuted())
                            Text(formatter.format(remainingTotal), fontSize = 12.sp, color = Color(0xFFFF8F00), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        // Value on left
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Black)
        // Label on right (in RTL)
        Text(label, fontSize = 11.sp, color = CalcColors.textMuted(), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CompareTab(dao: LoanDao, currentLang: String) {
    val loans by dao.getAllLoans().collectAsState(initial = emptyList())
    var input1 by remember { mutableStateOf<LoanInput?>(null) }
    var input2 by remember { mutableStateOf<LoanInput?>(null) }
    var name1 by remember { mutableStateOf("") }
    var name2 by remember { mutableStateOf("") }
    
    // States for showing detailed schedules in comparison
    var showSchedule1 by remember { mutableStateOf(false) }
    var showSchedule2 by remember { mutableStateOf(false) }
    
    val engine = LoanEngine()
    val formatter = DecimalFormat("#,##0.00")
    
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = if(currentLang=="ar") "مُحاكي المقارنة فائق الدقة" else "Ultra-Precision Simulator",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = CalcColors.textPrimary(),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                SimulationSlot(
                    label = if(currentLang=="ar") "العرض الأول (أ)" else "Offer (A)",
                    loans = loans,
                    selectedName = name1,
                    currentInput = input1,
                    currentLang = currentLang,
                    onSelect = { 
                        name1 = it.name
                        input1 = LoanInput(it.assetPrice, it.downPayment, it.months, it.annualRate, it.rateType, extraMonthly = it.extraMonthly, extraMonthlyStrategy = it.extraMonthlyStrategy)
                    },
                    onInputChanged = { input1 = it }
                )
            }
            Column(Modifier.weight(1f)) {
                SimulationSlot(
                    label = if(currentLang=="ar") "العرض الثاني (ب)" else "Offer (B)",
                    loans = loans,
                    selectedName = name2,
                    currentInput = input2,
                    currentLang = currentLang,
                    onSelect = { 
                        name2 = it.name
                        input2 = LoanInput(it.assetPrice, it.downPayment, it.months, it.annualRate, it.rateType, extraMonthly = it.extraMonthly, extraMonthlyStrategy = it.extraMonthlyStrategy)
                    },
                    onInputChanged = { input2 = it }
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        if (input1 != null && input2 != null) {
            val res1 = engine.calculate(input1!!, currentLang=="ar")
            val res2 = engine.calculate(input2!!, currentLang=="ar")
            
            PremiumCard {
                SectionHeader(if(currentLang=="ar") "مصفوفة التحليل والمفاضلة" else "Detailed Comparison Matrix")
                
                CompareItem(if(currentLang=="ar") "القسط الشهري" else "Monthly EMI", "JOD ${formatter.format(res1.monthlyEMI)}", "JOD ${formatter.format(res2.monthlyEMI)}", res1.monthlyEMI <= res2.monthlyEMI)
                CompareItem(if(currentLang=="ar") "إجمالي التكلفة" else "Total Interest", "JOD ${formatter.format(res1.totalInterest)}", "JOD ${formatter.format(res2.totalInterest)}", res1.totalInterest <= res2.totalInterest)
                CompareItem(if(currentLang=="ar") "تاريخ الانتهاء" else "Payoff Months", "${res1.schedule.size} ${if(currentLang=="ar") "شهر" else "mo"}", "${res2.schedule.size} ${if(currentLang=="ar") "شهر" else "mo"}", res1.schedule.size <= res2.schedule.size)
                CompareItem(if(currentLang=="ar") "التكلفة اليومية" else "Daily Cost", "JOD ${formatter.format(res1.totalInterest / (input1!!.months * 30.4))}", "JOD ${formatter.format(res2.totalInterest / (input2!!.months * 30.4))}", (res1.totalInterest/input1!!.months) <= (res2.totalInterest/input2!!.months))
                CompareItem(if(currentLang=="ar") "النسبة الحقيقية True APR" else "True APR", "${String.format("%.2f", res1.trueAPR)}%", "${String.format("%.2f", res2.trueAPR)}%", res1.trueAPR <= res2.trueAPR)
                
                Spacer(Modifier.height(16.dp))
                
                // Detailed Schedule Toggles (Restoring the "Very Detailed" request)
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showSchedule1 = !showSchedule1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CalcColors.accent())
                    ) {
                        Text(if(currentLang=="ar") "جدول (أ) التفصيلي" else "Schedule (A)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { showSchedule2 = !showSchedule2 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CalcColors.accent())
                    ) {
                        Text(if(currentLang=="ar") "جدول (ب) التفصيلي" else "Schedule (B)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            // Side-by-Side Schedules (When Toggled)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = showSchedule1, modifier = Modifier.weight(1f)) {
                    Column {
                        SectionHeader(if(currentLang=="ar") "توقعات (أ)" else "Schedule (A)")
                        res1.schedule.forEach { m -> 
                            CompactScheduleRow(m, formatter)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = showSchedule2, modifier = Modifier.weight(1f)) {
                    Column {
                        SectionHeader(if(currentLang=="ar") "توقعات (ب)" else "Schedule (B)")
                        res2.schedule.forEach { m -> 
                            CompactScheduleRow(m, formatter)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            
            val diff = kotlin.math.abs(res1.totalInterest - res2.totalInterest)
            val winnerName = if (res1.totalInterest <= res2.totalInterest) name1 else name2
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9).copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(0.3f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, null, tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if(currentLang=="ar") "التوصية الفنية: [$winnerName] هو الخيار الأوفر مالياً بفارق ${formatter.format(diff)} JOD."
                        else "Verdict: [$winnerName] is the winning choice, saving you JOD ${formatter.format(diff)}.",
                        fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 14.sp
                    )
                }
            }
        }
        
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Produced by Khalil Badarin | تم التطوير بواسطة خليل بدارين",
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = CalcColors.textMuted(),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CompactScheduleRow(m: AmortizationMonth, formatter: DecimalFormat) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = CalcColors.surface(),
        border = BorderStroke(1.dp, CalcColors.border().copy(0.1f))
    ) {
        Row(Modifier.padding(6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(formatter.format(m.payment), fontSize = 10.sp, fontWeight = FontWeight.Black, color = CalcColors.textPrimary())
                Text(formatter.format(m.remainingBalance), fontSize = 8.sp, color = CalcColors.textMuted())
            }
            Text(m.monthNumber.toString(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = CalcColors.accent())
        }
    }
}


@Composable
fun SimulationSlot(
    label: String,
    loans: List<SavedLoan>,
    selectedName: String,
    currentInput: LoanInput?,
    currentLang: String,
    onSelect: (SavedLoan) -> Unit,
    onInputChanged: (LoanInput) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = CalcColors.accent(), fontWeight = FontWeight.Black, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        LoanDropdown(loans, null, currentLang) { onSelect(it) }
        
        if (currentInput != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = { isEditing = !isEditing },
                shape = RoundedCornerShape(12.dp),
                color = CalcColors.surface(),
                border = BorderStroke(1.dp, if(isEditing) CalcColors.accent() else CalcColors.border().copy(0.3f))
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if(isEditing) Icons.Default.ExpandLess else Icons.Default.Edit, null, tint = CalcColors.accent(), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(selectedName.ifEmpty { "Simulation" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            
            androidx.compose.animation.AnimatedVisibility(visible = isEditing) {
                Column(Modifier.padding(vertical = 8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactSimField(if(currentLang=="ar") "السعر" else "Price", currentInput.assetPrice) { onInputChanged(currentInput.copy(assetPrice = it)) }
                    CompactSimField(if(currentLang=="ar") "الفائدة %" else "Rate %", currentInput.annualRate) { onInputChanged(currentInput.copy(annualRate = it)) }
                    CompactSimField(if(currentLang=="ar") "المدة/شهر" else "Months", currentInput.months.toDouble()) { onInputChanged(currentInput.copy(months = it.toInt())) }
                }
            }
        }
    }
}

@Composable
fun CompactSimField(label: String, value: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { 
        mutableStateOf(if(value == 0.0) "" else if(value == value.toInt().toDouble()) value.toInt().toString() else value.toString()) 
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = CalcColors.textMuted(), modifier = Modifier.width(50.dp))
        BasicTextField(
            value = text,
            onValueChange = { newText ->
                if (newText.isEmpty() || newText.matches(Regex("""^\d*\.?\d*$"""))) {
                    text = newText
                    newText.toDoubleOrNull()?.let { v -> onValueChange(v) } ?: if(newText.isEmpty()) onValueChange(0.0) else Unit
                }
            },
            textStyle = TextStyle(color = CalcColors.textPrimary(), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).background(CalcColors.surface(), RoundedCornerShape(8.dp)).padding(6.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@Composable
fun CompareItem(label: String, val1: String, val2: String, isFirstBetter: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, fontSize = 10.sp, color = CalcColors.textMuted(), fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(val1, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if(isFirstBetter) Color(0xFF2E7D32) else CalcColors.textPrimary())
                if(isFirstBetter) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                }
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if(!isFirstBetter) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(val2, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if(!isFirstBetter) Color(0xFF2E7D32) else CalcColors.textPrimary())
            }
        }
        Divider(Modifier.padding(top = 8.dp), color = CalcColors.border().copy(0.1f))
    }
}

@Composable
fun LoanDropdown(loans: List<SavedLoan>, selectedItem: SavedLoan?, currentLang: String, onSelect: (SavedLoan) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CalcColors.border()),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CalcColors.textPrimary(), containerColor = CalcColors.surface())
        ) {
            Text(selectedItem?.name ?: if(currentLang=="ar") "اختر.." else "Select..", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CalcColors.surface()).border(1.dp, CalcColors.border(), RoundedCornerShape(8.dp))
        ) {
            loans.forEach { loan ->
                DropdownMenuItem(
                    text = { Text(loan.name, color = CalcColors.textPrimary(), fontSize = 12.sp) },
                    onClick = { onSelect(loan); expanded = false }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// تاب الملف المالي الشخصي (Financial Profile Tab)
// ══════════════════════════════════════════════════════════════
@Composable
fun FinancialProfileTab(profile: PersonalFinanceProfile, currentLang: String, currentInput: LoanInput, onProfileChanged: (PersonalFinanceProfile) -> Unit) {
    val isArabic = currentLang == "ar"
    val formatter = remember { DecimalFormat("#,##0.00") }
    val engine = remember { LoanEngine() }
    
    val result = remember(currentInput, currentLang) { engine.calculate(currentInput, isArabic) }
    val proposedEMI = result.monthlyEMI
    
    val affordability = remember(profile, proposedEMI, currentInput) {
        if (profile.monthlySalary > 0) engine.analyzeAffordability(profile, proposedEMI, currentInput, isArabic) else null
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(if(isArabic) "ملفك المالي" else "FINANCIAL PROFILE", fontSize = 22.sp, fontWeight = FontWeight.Black, color = CalcColors.textPrimary())
                    Text(if(isArabic) "بياناتك الشخصية لتحليل دقيق" else "YOUR DATA FOR PRECISE ANALYSIS", fontSize = 10.sp, color = CalcColors.accent(), letterSpacing = 1.sp)
                }
                Icon(Icons.Default.Person, null, tint = CalcColors.accent(), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            PremiumCard {
                SectionHeader(if(isArabic) "💰 مصادر الدخل" else "💰 Income Sources")
                InField(if(isArabic) "الراتب الشهري" else "Monthly Salary", profile.monthlySalary) {
                    onProfileChanged(profile.copy(monthlySalary = it))
                }
                InField(if(isArabic) "دخل إضافي (إيجارات، فريلانس)" else "Other Income (Rent, Freelance)", profile.otherIncome) {
                    onProfileChanged(profile.copy(otherIncome = it))
                }
                if (profile.totalIncome > 0) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4CAF50).copy(alpha = 0.1f)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                            Text(if(isArabic) "📊 إجمالي الدخل الشهري" else "📊 Total Monthly Income", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("JOD ${formatter.format(profile.totalIncome)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            PremiumCard {
                SectionHeader(if(isArabic) "📋 الالتزامات الشهرية" else "📋 Monthly Obligations")
                InField(if(isArabic) "أقساط قروض حالية" else "Existing Loan EMIs", profile.existingLoansEMI) {
                    onProfileChanged(profile.copy(existingLoansEMI = it))
                }
                InField(if(isArabic) "بطاقات ائتمان (الحد الأدنى)" else "Credit Card Min. Payment", profile.creditCardMinPayment) {
                    onProfileChanged(profile.copy(creditCardMinPayment = it))
                }
                InField(if(isArabic) "إيجار السكن" else "Rent Expense", profile.rentExpense) {
                    onProfileChanged(profile.copy(rentExpense = it))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            PremiumCard {
                SectionHeader(if(isArabic) "🛒 المصاريف الشهرية" else "🛒 Monthly Expenses")
                InField(if(isArabic) "فواتير (كهرباء، ماء، إنترنت)" else "Utilities (Elec, Water, Internet)", profile.utilitiesExpense) {
                    onProfileChanged(profile.copy(utilitiesExpense = it))
                }
                InField(if(isArabic) "تعليم" else "Education", profile.educationExpense) {
                    onProfileChanged(profile.copy(educationExpense = it))
                }
                InField(if(isArabic) "مواصلات / وقود" else "Transport / Fuel", profile.transportExpense) {
                    onProfileChanged(profile.copy(transportExpense = it))
                }
                InField(if(isArabic) "مصاريف أخرى" else "Other Expenses", profile.otherExpenses) {
                    onProfileChanged(profile.copy(otherExpenses = it))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            PremiumCard {
                SectionHeader(if(isArabic) "🛡️ مدخرات الطوارئ" else "🛡️ Emergency Fund")
                InField(if(isArabic) "إجمالي مدخرات الطوارئ" else "Total Emergency Savings", profile.emergencyFund) {
                    onProfileChanged(profile.copy(emergencyFund = it))
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (affordability != null) {
            item {
                PremiumCard(gradient = true) {
                    SectionHeader(if(isArabic) "لوحة الصحة المالية" else "Financial Health Dashboard", light = true)
                    Spacer(Modifier.height(16.dp))

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val scoreColor = when {
                            affordability.healthScore >= 75 -> Color(0xFF4CAF50)
                            affordability.healthScore >= 50 -> Color(0xFFFFC107)
                            affordability.healthScore >= 25 -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                        val scoreLabel = when(affordability.riskLevel) {
                            RiskLevel.SAFE -> if(isArabic) "ممتاز" else "Excellent"
                            RiskLevel.MODERATE -> if(isArabic) "مقبول" else "Moderate"
                            RiskLevel.HIGH -> if(isArabic) "مرتفع" else "High Risk"
                            RiskLevel.CRITICAL -> if(isArabic) "خطر" else "Critical"
                        }

                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = affordability.healthScore / 100f,
                                modifier = Modifier.size(120.dp),
                                color = scoreColor,
                                strokeWidth = 10.dp,
                                trackColor = Color.White.copy(alpha = 0.15f)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${affordability.healthScore}", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White)
                                Text(scoreLabel, fontSize = 12.sp, color = scoreColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Divider(color = Color.White.copy(0.2f))
                    Spacer(Modifier.height(12.dp))

                    val dtiColor = when {
                        affordability.dtiWithNewLoan > 50 -> Color(0xFFF44336)
                        affordability.dtiWithNewLoan > 40 -> Color(0xFFFF9800)
                        affordability.dtiWithNewLoan > 30 -> Color(0xFFFFC107)
                        else -> Color(0xFF4CAF50)
                    }

                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "DTI الحالي (بدون القرض)" else "Current DTI (No Loan)", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("${String.format("%.1f", affordability.currentDTI)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "DTI مع القرض الجديد" else "DTI with New Loan", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("${String.format("%.1f", affordability.dtiWithNewLoan)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dtiColor)
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(0.15f), RoundedCornerShape(5.dp))) {
                        Box(Modifier.fillMaxWidth(fraction = (affordability.dtiWithNewLoan / 100.0).toFloat().coerceIn(0f, 1f)).height(10.dp).background(dtiColor, RoundedCornerShape(5.dp)))
                        Box(Modifier.fillMaxWidth(0.5f).height(10.dp)) {
                            Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(10.dp).background(Color.Red))
                        }
                    }
                    Text(
                        if(isArabic) "حد البنك المركزي الأردني: 50%" else "Jordan Central Bank Limit: 50%",
                        fontSize = 8.sp, color = Color.White.copy(0.5f), modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color.White.copy(0.2f))
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "أقصى قسط ممكن" else "Max Affordable EMI", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(affordability.maxAffordableEMI)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CalcColors.SoftGold)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "أقصى مبلغ قرض" else "Max Loan Amount", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(affordability.maxLoanAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CalcColors.SoftGold)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "القسط الحالي المتوقع" else "Current Proposed EMI", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(proposedEMI)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (proposedEMI <= affordability.maxAffordableEMI) Color(0xFF4CAF50) else Color(0xFFF44336))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "المتبقي بعد كل شيء" else "Monthly Disposable", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text("JOD ${formatter.format(affordability.monthlyDisposable)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (affordability.monthlyDisposable >= 200) Color(0xFF4CAF50) else Color(0xFFF44336))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                        Text(if(isArabic) "أشهر صندوق الطوارئ" else "Emergency Fund Months", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Text(
                            "${String.format("%.1f", affordability.emergencyMonths)} ${if(isArabic) "شهر" else "mo"}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = when { affordability.emergencyMonths >= 6 -> Color(0xFF4CAF50); affordability.emergencyMonths >= 3 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                SectionHeader(if(isArabic) "نصائح مالية مخصصة لك" else "Personalized Financial Tips")
                Spacer(Modifier.height(8.dp))
            }

            items(affordability.personalInsights.size) { index ->
                val insight = affordability.personalInsights[index]
                val bgColor = when(insight.type) {
                    InsightType.SUCCESS -> Color(0xFFE8F5E9)
                    InsightType.WARNING -> Color(0xFFFFF3E0)
                    InsightType.ALERT -> Color(0xFFFFEBEE)
                    else -> CalcColors.surface()
                }
                val icon = when(insight.type) {
                    InsightType.SUCCESS -> Icons.Default.CheckCircle
                    InsightType.WARNING -> Icons.Default.Warning
                    InsightType.ALERT -> Icons.Default.Error
                    else -> Icons.Default.Lightbulb
                }
                val iconColor = when(insight.type) {
                    InsightType.SUCCESS -> Color(0xFF2E7D32)
                    InsightType.WARNING -> Color(0xFFEF6C00)
                    InsightType.ALERT -> Color(0xFFC62828)
                    else -> CalcColors.accent()
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val textColor = if(LocalIsDarkTheme.current && bgColor == CalcColors.surface()) Color.White else Color.Black
                            Text(insight.title, fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
                            Text(insight.description, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (profile.monthlySalary <= 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CalcColors.accent().copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, CalcColors.accent().copy(alpha = 0.2f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, null, tint = CalcColors.accent(), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if(isArabic) "أدخل راتبك الشهري أعلاه لتفعيل لوحة الصحة المالية والنصائح الذكية"
                            else "Enter your monthly salary above to activate the Financial Health Dashboard and Smart Tips",
                            textAlign = TextAlign.Center, fontSize = 14.sp, color = CalcColors.textMuted()
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Produced by Khalil Badarin | تم التطوير بواسطة خليل بدارين",
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = CalcColors.textMuted()
            )
        }
    }
}
