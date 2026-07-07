package com.example.wagetracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wagetracker.databinding.ActivityYearViewBinding

class YearViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYearViewBinding
    private lateinit var adapter: YearAdapter
    private val repo by lazy { WageRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYearViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = YearAdapter { row ->
            // Tapping a month opens the main screen focused on that month
            val cal = java.util.Calendar.getInstance()
            val parts = row.monthKey.split("-")
            if (parts.size == 2) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
            }
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_FOCUS_MONTH, row.monthKey)
            }
            startActivity(intent)
            finish()
        }
        binding.yearRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.yearRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun rebuild() {
        val rows = repo.records
            .groupBy { it.monthKey }
            .map { (monthKey, recs) ->
                val totals = MonthSummaryCalculator.summarize(recs, monthKey)
                val paid = repo.paidRecord(monthKey)
                YearMonthRow(
                    monthKey = monthKey,
                    monthName = nameFor(monthKey),
                    tips = totals.tips,
                    owed = if (paid != null) 0.0 else totals.owed,
                    paid = paid?.paidAmount ?: 0.0,
                    short = paid?.shortBy ?: 0.0,
                    isPaid = paid != null
                )
            }
            .sortedByDescending { it.monthKey }
        adapter.submitList(rows)
    }

    private fun nameFor(monthKey: String): String {
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