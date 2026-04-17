package com.khalil.calc.pdf

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.khalil.calc.logic.CalculationResult
import com.khalil.calc.logic.LoanInput
import java.text.DecimalFormat

object PdfGenerator {

    fun generateAndPrint(
        context: Context,
        input: LoanInput,
        result: CalculationResult,
        isArabic: Boolean,
        isYearly: Boolean
    ) {
        try {
            val webView = WebView(context)
            val html = buildHtml(input, result, isArabic, isYearly)
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                        if (printManager == null) {
                            android.util.Log.e("PdfGenerator", "PrintManager not available")
                            return
                        }
                        val jobName = if(isArabic) "تقرير_القرض_${System.currentTimeMillis()}" else "Loan_Report_${System.currentTimeMillis()}"
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                    } catch (e: Exception) {
                        android.util.Log.e("PdfGenerator", "Error during print process: ${e.message}")
                        e.printStackTrace()
                    }
                }

                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    android.util.Log.e("PdfGenerator", "WebView Error: ${error?.description}")
                }
            }
            
            // Ensure loadData is called on the main thread
            webView.post {
                webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "Failed to initialize WebView for PDF: ${e.message}")
        }
    }

    private fun buildHtml(input: LoanInput, result: CalculationResult, isArabic: Boolean, isYearly: Boolean): String {
        val f = DecimalFormat("#,##0.00")
        val dir = if (isArabic) "rtl" else "ltr"
        val align = if (isArabic) "right" else "left"

        val title = if (isArabic) "تقرير محاكاة القرض المتقدم" else "Advanced Loan Simulation Report"
        val subtitle = if (isArabic) "التفاصيل المالية وجدول السداد" else "Financial Details & Repayment Schedule"
        
        // Summary Headers
        val strPrincipal = if(isArabic) "الرصيد الأساسي:" else "Principal Amount:"
        val strEMI = if(isArabic) "القسط الشهري:" else "Monthly EMI:"
        val strInterest = if(isArabic) "إجمالي الفوائد:" else "Total Interest:"
        val strTotal = if(isArabic) "إجمالي المدفوعات:" else "Total Payments:"
        val strAPR = if(isArabic) "النسبة الحقيقية (APR):" else "True APR:"
        // val strType = if(isArabic) "طريقة العرض:" else "Report Type:"
        // val valType = if(isYearly) (if(isArabic) "سنوي مجمع" else "Yearly Consolidated") else (if(isArabic) "شهري مفصل" else "Monthly Detailed")

        val html = StringBuilder()
        html.append("""
            <!DOCTYPE html>
            <html lang="${if(isArabic) "ar" else "en"}" dir="$dir">
            <head>
                <meta charset="UTF-8">
                <title>$title</title>
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #333; margin: 40px; font-size: 14px; }
                    .header { text-align: center; border-bottom: 2px solid #003366; padding-bottom: 20px; margin-bottom: 30px; }
                    .header h1 { color: #003366; margin: 0; font-size: 26px; }
                    .header p { color: #D4AF37; margin: 5px 0 0 0; font-weight: bold; font-size: 14px; }
                    .summary-box { background-color: #F4F7FA; border: 1px solid #ECEFF1; border-radius: 8px; padding: 20px; margin-bottom: 30px; display: flex; flex-wrap: wrap; justify-content: space-between; }
                    .summary-item { width: 30%; margin-bottom: 15px; }
                    .summary-label { font-size: 12px; color: #607D8B; display: block; }
                    .summary-val { font-size: 16px; font-weight: bold; color: #003366; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 12px; }
                    th { background-color: #003366; color: #FFF; padding: 12px; text-align: center; border: 1px solid #002244; }
                    td { padding: 10px; text-align: center; border: 1px solid #ECEFF1; }
                    tr:nth-child(even) { background-color: #F8FAFC; }
                    .num { text-align: $align; font-family: monospace; font-size: 13px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>$title</h1>
                    <p>$subtitle</p>
                </div>
                <div class="summary-box">
                    <div class="summary-item"><span class="summary-label">$strPrincipal</span><span class="summary-val">JOD ${f.format(input.assetPrice - input.downPayment)}</span></div>
                    <div class="summary-item"><span class="summary-label">$strEMI</span><span class="summary-val">JOD ${f.format(result.monthlyEMI)}</span></div>
                    <div class="summary-item"><span class="summary-label">$strInterest</span><span class="summary-val" style="color:#E53935">JOD ${f.format(result.totalInterest)}</span></div>
                    <div class="summary-item"><span class="summary-label">$strTotal</span><span class="summary-val">JOD ${f.format(result.totalPayment)}</span></div>
                    <div class="summary-item"><span class="summary-label">$strAPR</span><span class="summary-val">${String.format("%.2f", result.trueAPR)}%</span></div>
                    <div class="summary-item"><span class="summary-label">${if(isArabic) "إجمالي التأمين/الرسوم:" else "Total Ins/Fees:"}</span><span class="summary-val" style="color:#795548">JOD ${f.format(result.totalInsurance)}</span></div>
                    <div class="summary-item"><span class="summary-label">${if(isArabic) "نوع الفائدة:" else "Rate Type:"}</span><span class="summary-val">${input.rateType.name}</span></div>
                    <div class="summary-item"><span class="summary-label">${if(isArabic) "مدة القرض:" else "Loan Term:"}</span><span class="summary-val">${input.months} ${if(isArabic) "أشهر" else "Mo"}</span></div>
                    <div class="summary-item"><span class="summary-label">${if(isArabic) "فترة السماح:" else "Grace Period:"}</span><span class="summary-val">${input.graceMonths} ${if(isArabic) "أشهر" else "Mo"}</span></div>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>${if(isArabic) (if(isYearly) "السنة" else "الشهر") else (if(isYearly) "Year" else "Month")}</th>
                            <th>${if(isArabic) "رصيد الافتتاح" else "Opening Bal."}</th>
                            <th>${if(isArabic) "إجمالي الدفعة" else "Total Payment"}</th>
                            <th>${if(isArabic) "أصل القرض" else "Principal"}</th>
                            <th>${if(isArabic) "الفائدة" else "Interest"}</th>
                            <th>${if(isArabic) "تأمين/رسوم" else "Ins/Fees"}</th>
                            <th>${if(isArabic) "غرامة السداد" else "Penalty"}</th>
                            <th>${if(isArabic) "دفعات ذكية" else "Smart Payments"}</th>
                            <th>${if(isArabic) "الرصيد المتبقي" else "Remaining Bal."}</th>
                        </tr>
                    </thead>
                    <tbody>
        """)

        if (isYearly) {
            val groupedByYear = result.schedule.groupBy { (it.monthNumber - 1) / 12 + 1 }
            groupedByYear.forEach { (year, months) ->
                val opening = months.first().openingBalance
                val closing = months.last().remainingBalance
                val payment = months.sumOf { it.payment }
                val principal = months.sumOf { it.principalPart }
                val interest = months.sumOf { it.interestPart }
                val smart = months.sumOf { it.extraPaid + it.balloonPaid }
                val fees = months.sumOf { it.insurancePart + it.recurringFeesPart }
                val penalty = months.sumOf { it.earlySettlementFeePaid }
                html.append("""
                    <tr>
                        <td><strong>$year</strong></td>
                        <td class="num">${f.format(opening)}</td>
                        <td class="num">${f.format(payment)}</td>
                        <td class="num" style="color: #2E7D32;">${f.format(principal)}</td>
                        <td class="num" style="color: #E53935;">${f.format(interest)}</td>
                        <td class="num" style="color: #795548;">${f.format(fees)}</td>
                        <td class="num" style="color: #B71C1C;">${f.format(penalty)}</td>
                        <td class="num" style="color: #2196F3;">${f.format(smart)}</td>
                        <td class="num"><strong>${f.format(closing)}</strong></td>
                    </tr>
                """)
            }
        } else {
            result.schedule.forEach { m ->
                val smart = m.extraPaid + m.balloonPaid
                val fees = m.insurancePart + m.recurringFeesPart
                val penalty = m.earlySettlementFeePaid
                html.append("""
                    <tr>
                        <td>${m.monthNumber}</td>
                        <td class="num">${f.format(m.openingBalance)}</td>
                        <td class="num">${f.format(m.payment)}</td>
                        <td class="num" style="color: #2E7D32;">${f.format(m.principalPart)}</td>
                        <td class="num" style="color: #E53935;">${f.format(m.interestPart)}</td>
                        <td class="num" style="color: #795548;">${f.format(fees)}</td>
                        <td class="num" style="color: #B71C1C;">${f.format(penalty)}</td>
                        <td class="num" style="color: #2196F3;">${f.format(smart)}</td>
                        <td class="num"><strong>${f.format(m.remainingBalance)}</strong></td>
                    </tr>
                """)
            }
        }

        html.append("""
                    </tbody>
                </table>
                <div style="margin-top: 40px; text-align: center; border-top: 1px solid #ECEFF1; padding-top: 20px; color: #607D8B; font-size: 11px;">
                    Produced by Khalil Badarin | تم التطوير بواسطة خليل بدارين
                </div>
            </body>
            </html>
        """)
        return html.toString()
    }
}
