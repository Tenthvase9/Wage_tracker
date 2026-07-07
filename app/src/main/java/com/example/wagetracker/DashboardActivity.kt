package com.example.wagetracker

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wagetracker.databinding.ActivityDashboardBinding
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private data class MonthStats(val key: String, val tips: Double, val days: Int)

    private lateinit var binding: ActivityDashboardBinding
    private val repo by lazy { WageRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.totalsCard.setOnClickListener { finish() }

        buildStats()
    }

    private fun buildStats() {
        val records = repo.records
        if (records.isEmpty()) {
            binding.tvTotalCardTips.text = "R0"
            binding.tvTotalCashTips.text = "R0"
            binding.tvTotalPaidIn.text = "R0"
            binding.tvTotalDays.text = "0"
            binding.tvAvgTips.text = "R0"
            binding.tvBestMonth.text = "-"
            binding.tvWorstMonth.text = "-"
            return
        }

        // Group by month
        val byMonth = records.groupBy { it.monthKey }
        val monthKeys = byMonth.keys.sorted()

        // All-time totals
        val allCard = records.sumOf { it.cardTips }
        val allCash = records.sumOf { it.cashTips }
        val allPaidIn = records.sumOf { it.cashPaidIn }
        val allDays = records.count { !it.isOff }

        binding.tvTotalCardTips.text = formatRand(allCard)
        binding.tvTotalCashTips.text = formatRand(allCash)
        binding.tvTotalPaidIn.text = formatRand(allPaidIn)
        binding.tvTotalDays.text = allDays.toString()

        // Per-month totals
        val monthStats = monthKeys.map { key ->
            val recs = byMonth[key]!!
            MonthStats(key, recs.sumOf { it.cardTips + it.cashTips }, recs.count { !it.isOff })
        }

        // Averages
        val avgTips = if (monthStats.isNotEmpty()) monthStats.map { it.tips }.average() else 0.0
        binding.tvAvgTips.text = formatRand(avgTips)

        // Best / worst
        val best = monthStats.maxByOrNull { it.tips }
        val worst = monthStats.minByOrNull { it.tips }
        binding.tvBestMonth.text = if (best != null) "${best.key.takeLast(2)} R${"%.0f".format(best.tips)}" else "-"
        binding.tvWorstMonth.text = if (worst != null) "${worst.key.takeLast(2)} R${"%.0f".format(worst.tips)}" else "-"

        // Build bar chart
        buildChart(monthStats)
    }

    private fun buildChart(stats: List<MonthStats>) {
        val maxTips = stats.maxOfOrNull { it.tips } ?: return
        val barMaxHeight = 120
        val barWidth = 56
        val padding = 8
        val context = this

        for (s in stats) {
            val barHeight = if (maxTips > 0) ((s.tips / maxTips) * barMaxHeight).toInt().coerceAtLeast(4) else 4

            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(barWidth + padding * 2, LinearLayout.LayoutParams.MATCH_PARENT)
                gravity = Gravity.BOTTOM
                setPadding(padding, 0, padding, 0)
            }

            val tvValue = TextView(context).apply {
                text = "R${"%.0f".format(s.tips)}"
                textSize = 10f
                setTextColor(ContextCompat.getColor(context, R.color.wage_card))
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val bar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(barWidth, barHeight)
                background = ContextCompat.getDrawable(context, R.drawable.chart_bar_bg)
            }

            val tvLabel = TextView(context).apply {
                text = s.key.takeLast(2)
                textSize = 9f
                setTextColor(ContextCompat.getColor(context, R.color.wage_off))
                gravity = Gravity.CENTER_HORIZONTAL
            }

            column.addView(tvValue)
            column.addView(bar)
            column.addView(tvLabel)
            binding.chartContainer.addView(column)
        }
    }

    private fun formatRand(v: Double) = "R${"%,.0f".format(Locale.ENGLISH, v)}"
}
