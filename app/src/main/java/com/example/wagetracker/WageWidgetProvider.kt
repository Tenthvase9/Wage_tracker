package com.example.wagetracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale

class WageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WageWidgetProvider::class.java))
            ids.forEach { id -> updateOne(context, mgr, id) }
        }

        private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val repo = WageRepository(context)
            val cal = Calendar.getInstance()
            val monthKey = MonthSummaryCalculator.monthKey(cal)
            val totals = MonthSummaryCalculator.summarize(repo.records, monthKey)
            val isPaid = repo.monthPaid(monthKey)

            val views = RemoteViews(context.packageName, R.layout.widget_wage)
            views.setTextViewText(R.id.widgetMonth, MonthSummaryCalculator.monthName(cal))
            if (repo.records.any { it.monthKey == monthKey }) {
                val amount = if (isPaid) 0.0 else totals.owed
                views.setTextViewText(
                    R.id.widgetOwed,
                    context.getString(R.string.widget_owed, "%,.2f".format(Locale.ENGLISH, amount))
                )
                views.setTextViewText(
                    R.id.widgetSub,
                    if (isPaid) "Paid in full" else "Card ${"%.0f".format(totals.cardTips)} + Paid in ${"%.0f".format(totals.cashPaidIn)}"
                )
            } else {
                views.setTextViewText(R.id.widgetOwed, context.getString(R.string.widget_empty))
                views.setTextViewText(R.id.widgetSub, "Tap to open")
            }

            val openIntent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetOwed, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}