package com.example.wagetracker

import android.content.Context
import android.net.Uri
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes CSV to a user-chosen Uri via SAF. No external storage permissions.
 */
object CsvExporter {

    fun writeMonth(context: Context, uri: Uri, monthKey: String, monthName: String, records: List<DailyRecord>) {
        val monthRecords = records.filter { it.monthKey == monthKey }.sortedBy { it.isoDate }
        val totals = MonthSummaryCalculator.summarize(records, monthKey)

        val sb = StringBuilder()
        sb.appendLine("Wage Tracker - $monthName")
        sb.appendLine()
        sb.appendLine("Date,Card Tips,Cash Tips,Cash Paid In,Hours,Wages,Total,Notes")
        for (r in monthRecords) {
            if (r.isOff) {
                sb.appendLine("${csv(r.date)},OFF,OFF,OFF,OFF,OFF,OFF,")
                continue
            }
            val total = r.dayTotal
            sb.appendLine(
                "${csv(r.date)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.cardTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.cashTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.cashPaidIn)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.hours)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.wageAmount)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, total)}," +
                "${csv(r.notes)}"
            )
        }
        sb.appendLine()
        sb.appendLine("Totals,${"%.2f".format(java.util.Locale.ENGLISH, totals.cardTips)},${"%.2f".format(java.util.Locale.ENGLISH, totals.cashTips)},${"%.2f".format(java.util.Locale.ENGLISH, totals.cashPaidIn)},${"%.2f".format(java.util.Locale.ENGLISH, totals.hours)},${"%.2f".format(java.util.Locale.ENGLISH, totals.wages)},,")
        sb.appendLine("You are owed,${"%.2f".format(java.util.Locale.ENGLISH, totals.owed)},,,")
        writeUri(context, uri, sb.toString())
    }

    fun writeAllMonths(context: Context, uri: Uri, records: List<DailyRecord>, paidMonths: List<PaidMonthRecord>) {
        val grouped = records.groupBy { it.monthKey }.toSortedMap()
        val sb = StringBuilder()
        sb.appendLine("Wage Tracker - All months")
        sb.appendLine("Exported,${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
        sb.appendLine()
        sb.appendLine("Month,Status,Worked Days,Card Tips,Cash Tips,Cash Paid In,Hours,Wages,Owed,Paid,Short By,Paid Date")
        for ((monthKey, recs) in grouped) {
            val totals = MonthSummaryCalculator.summarize(records, monthKey)
            val days = MonthSummaryCalculator.workedDays(records, monthKey)
            val paid = paidMonths.firstOrNull { it.monthKey == monthKey }
            val tipsPaid = paid?.paidDate?.isNotEmpty() == true
            val status = if (tipsPaid) "PAID" else "UNPAID"
            val owed = if (tipsPaid) 0.0 else totals.owed
            val paidAmount = paid?.paidAmount ?: 0.0
            val short = paid?.shortBy ?: 0.0
            val paidDate = paid?.paidDate ?: ""
            val monthName = monthNameFor(monthKey)
            sb.appendLine(
                "$monthName,$status,$days," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.cardTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.cashTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.cashPaidIn)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.hours)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.wages)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, owed)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, paidAmount)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, short)}," +
                paidDate
            )
            // Per-day rows for the month
            for (r in recs.sortedBy { it.isoDate }) {
                if (r.isOff) {
                    sb.appendLine("  ${r.date},OFF,,,,,,,")
                    continue
                }
                sb.appendLine(
                    "  ${r.date},," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.cardTips)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.cashTips)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.cashPaidIn)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.hours)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.wageAmount)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.dayTotal)}," +
                    "${csv(r.notes)}"
                )
            }
        }
        writeUri(context, uri, sb.toString())
    }

    private fun writeUri(context: Context, uri: Uri, text: String) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            OutputStreamWriter(out).use { writer -> writer.write(text) }
        }
    }

    private fun csv(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n')
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private fun monthNameFor(monthKey: String): String {
        val parts = monthKey.split("-")
        if (parts.size != 2) return monthKey
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val m = parts[1].toIntOrNull() ?: return monthKey
        val y = parts[0].toIntOrNull() ?: return monthKey
        if (m !in 1..12) return monthKey
        return "${months[m - 1]} $y"
    }
}