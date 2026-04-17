package com.khalil.calc.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.khalil.calc.logic.AppDatabase
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class LoanNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).loanDao()
        val activeLoans = dao.getAllActiveLoansSync()

        if (activeLoans.isEmpty()) {
            return Result.success()
        }

        val currentDate = LocalDate.now()

        for (loan in activeLoans) {

            // Calculate next payment date
            var nextPaymentDate = currentDate.withDayOfMonth(loan.paymentDay.coerceAtMost(currentDate.lengthOfMonth()))

            // If the payment date for this month has passed, the next payment is next month
            if (currentDate.isAfter(nextPaymentDate)) {
                val nextMonth = currentDate.plusMonths(1)
                nextPaymentDate = nextMonth.withDayOfMonth(loan.paymentDay.coerceAtMost(nextMonth.lengthOfMonth()))
            }

            val daysUntilPayment = ChronoUnit.DAYS.between(currentDate, nextPaymentDate).toInt()

            // Trigger notification 3 days before payment or on the day
            if (daysUntilPayment in 0..3) {
                // To avoid spamming, we can use a combination of loan ID and month as notification ID
                val notificationId = loan.id * 1000 + nextPaymentDate.monthValue

                val title = "تذكير بقسط القرض / Loan EMI Reminder"
                val message = if (daysUntilPayment == 0) {
                    "قسط القرض '${loan.name}' مستحق اليوم! / EMI for '${loan.name}' is due today!"
                } else {
                    "قسط القرض '${loan.name}' مستحق بعد $daysUntilPayment أيام. / EMI for '${loan.name}' is due in $daysUntilPayment days."
                }

                NotificationHelper.showNotification(applicationContext, title, message, notificationId)
            }
        }

        return Result.success()
    }
}
