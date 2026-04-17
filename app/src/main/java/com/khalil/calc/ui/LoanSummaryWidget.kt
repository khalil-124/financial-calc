package com.khalil.calc.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.khalil.calc.R
import com.khalil.calc.logic.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class LoanSummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_loan_summary)

    // Setup Click Intent to open App
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("OPEN_TAB", 1) // 1 could be the Live Tracker Tab index (though it's MyLoans in current UI, but it opens the app)
    }
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    // Fetch data asynchronously
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val dao = AppDatabase.getDatabase(context).loanDao()
            val activeLoans = dao.getAllActiveLoansSync()

            var totalBalance = 0.0
            var nearestPaymentDays = Int.MAX_VALUE
            var nearestPaymentAmount = 0.0

            val currentDate = java.time.LocalDate.now()

            for (loan in activeLoans) {
                totalBalance += loan.currentBalanceOverride

                var nextPaymentDate = currentDate.withDayOfMonth(loan.paymentDay.coerceAtMost(currentDate.lengthOfMonth()))
                if (currentDate.isAfter(nextPaymentDate)) {
                    val nextMonth = currentDate.plusMonths(1)
                    nextPaymentDate = nextMonth.withDayOfMonth(loan.paymentDay.coerceAtMost(nextMonth.lengthOfMonth()))
                }

                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(currentDate, nextPaymentDate).toInt()
                if (daysUntil < nearestPaymentDays) {
                    nearestPaymentDays = daysUntil
                    // EMI approximation for the widget (would need full engine calculation for exact, but this is a lightweight approximation)
                    val monthlyRate = loan.currentActiveRate / 100.0 / 12.0
                    val emi = if (monthlyRate > 0) {
                        (loan.currentBalanceOverride * monthlyRate) / (1 - java.lang.Math.pow(1 + monthlyRate, -loan.OriginalMonths.toDouble())) // Simplification for widget
                    } else {
                        loan.currentBalanceOverride / loan.OriginalMonths.toDouble()
                    }
                    nearestPaymentAmount = emi
                }
            }

            val formatter = DecimalFormat("#,##0")

            // Update UI on main thread
            CoroutineScope(Dispatchers.Main).launch {
                if (activeLoans.isEmpty()) {
                    views.setTextViewText(R.id.widget_title, "ديوني / Debt Portfolio")
                    views.setTextViewText(R.id.widget_total_balance, "0 JOD")
                    views.setTextViewText(R.id.widget_next_payment, "لا توجد قروض / No Loans")
                } else {
                    views.setTextViewText(R.id.widget_title, "ديوني / Debt Portfolio")
                    views.setTextViewText(R.id.widget_total_balance, "${formatter.format(totalBalance)} JOD")

                    val daysText = if (nearestPaymentDays == 0) "اليوم / Today" else "بعد $nearestPaymentDays أيام / in $nearestPaymentDays d"
                    views.setTextViewText(R.id.widget_next_payment, "القسط القادم: $daysText")
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
