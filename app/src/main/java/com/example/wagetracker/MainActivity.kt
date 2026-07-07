package com.example.wagetracker

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.View
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wagetracker.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOCUS_MONTH = "focus_month"
        private const val REQ_EXPORT_MONTH = 1001
        private const val REQ_EXPORT_ALL = 1002
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private lateinit var repo: WageRepository
    private val currentMonth: Calendar = Calendar.getInstance()

    private val monthRecords: MutableList<DailyRecord> = mutableListOf()

    private val createMonthCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val monthKey = MonthSummaryCalculator.monthKey(currentMonth)
            val monthName = MonthSummaryCalculator.monthName(currentMonth)
            CsvExporter.writeMonth(this, uri, monthKey, monthName, repo.records)
            toast(getString(R.string.msg_export_done, uri.toString()))
        } catch (e: Exception) {
            toast(getString(R.string.msg_export_failed, e.message ?: ""))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        repo = WageRepository(this)
        val isDark = repo.isDarkMode()
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar with menu
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                R.id.action_dashboard -> { startActivity(Intent(this, DashboardActivity::class.java)); true }
                R.id.action_year_view -> { startActivity(Intent(this, YearViewActivity::class.java)); true }
                else -> false
            }
        }

        intent.getStringExtra(EXTRA_FOCUS_MONTH)?.let { focusOn(it) }

        adapter = RecordAdapter(
            onDeleteClick = { record -> confirmDelete(record) },
            onEditClick = { record -> showEditDialog(record) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Swipe left to delete with confirmation dialog
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                val pos = viewHolder.bindingAdapterPosition
                if (pos < 0 || pos >= adapter.currentList.size) return
                val record = adapter.currentList[pos]
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.cd_delete))
                    .setMessage("Delete ${record.date}?")
                    .setPositiveButton(android.R.string.ok) { _, _ -> deleteWithUndo(record) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> adapter.notifyItemChanged(pos) }
                    .setOnCancelListener { adapter.notifyItemChanged(pos) }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)

        binding.btnAddRecord.setOnClickListener { showAddDialog() }
        binding.btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            refresh()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            refresh()
        }
        binding.btnShare.setOnClickListener { shareMonth() }
        binding.btnExportCSV.setOnClickListener {
            val stamp = DateFormat.format("yyyyMM", currentMonth).toString()
            createMonthCsv.launch("wage-tracker-$stamp.csv")
        }
        binding.chipHeroStatus.setOnClickListener { showMarkPaidDialog() }
        binding.dashboardChin.setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)) }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        WageWidgetProvider.refreshAll(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun focusOn(monthKey: String) {
        val parts = monthKey.split("-")
        if (parts.size == 2) {
            currentMonth.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
        }
    }

    private fun refresh() {
        binding.tvCurrentMonth.text = MonthSummaryCalculator.monthName(currentMonth)
        val monthKey = MonthSummaryCalculator.monthKey(currentMonth)
        val isPaid = repo.monthPaid(monthKey)
        val totals = MonthSummaryCalculator.summarize(repo.records, monthKey, isPaid)
        val rawTotals = MonthSummaryCalculator.summarize(repo.records, monthKey)
        val worked = MonthSummaryCalculator.workedDays(repo.records, monthKey)

        // Hero
        val owed = if (isPaid) 0.0 else totals.owed
        binding.tvHeroAmount.text = "R${"%,.2f".format(java.util.Locale.ENGLISH, owed)}"
        binding.tvHeroMonth.text = getString(R.string.worked_days, worked)
        binding.chipHeroStatus.text = if (isPaid) getString(R.string.action_unmark_paid) else getString(R.string.action_mark_paid)
        binding.chipHeroStatus.setChipBackgroundColorResource(
            if (isPaid) R.color.wage_paid else android.R.color.white
        )
        binding.chipHeroStatus.setTextColor(
            if (isPaid) 0xFFFFFFFF.toInt() else getColor(R.color.brand_primary)
        )

        // Short-by chip
        val paidRecord = repo.paidRecord(monthKey)
        if (isPaid && paidRecord != null && paidRecord.shortBy > 0) {
            binding.chipShortBy.visibility = View.VISIBLE
            binding.chipShortBy.text = getString(R.string.dialog_paid_short) + " R${"%.2f".format(paidRecord.shortBy)}"
        } else {
            binding.chipShortBy.visibility = View.GONE
        }

        // Mini cards (always show raw totals regardless of paid status)
        binding.tvTotalCardTips.text = "R${"%,.0f".format(java.util.Locale.ENGLISH, rawTotals.cardTips)}"
        binding.tvTotalCashTips.text = "R${"%,.0f".format(java.util.Locale.ENGLISH, rawTotals.cashTips)}"
        binding.tvTotalAllTips.text = "R${"%,.0f".format(java.util.Locale.ENGLISH, rawTotals.tips)}"
        binding.tvTotalPaidIn.text = "R${"%,.0f".format(java.util.Locale.ENGLISH, rawTotals.cashPaidIn)}"

        // List
        val list = repo.records
            .filter { it.monthKey == monthKey }
            .sortedBy { it.isoDate }
        monthRecords.clear()
        monthRecords.addAll(list)
        adapter.submitList(monthRecords.toList())
        binding.tvEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        WageWidgetProvider.refreshAll(this)
    }

    private fun confirmDelete(record: DailyRecord) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cd_delete))
            .setMessage("Delete ${record.date}?")
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteWithUndo(record) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteWithUndo(record: DailyRecord) {
        val index = repo.records.indexOfFirst { it.id == record.id }.coerceAtLeast(0)
        repo.deleteRecord(record.id)
        refresh()
        Snackbar.make(binding.root, getString(R.string.undo_delete, record.date), Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                repo.insertRecordAt(index, record)
                refresh()
            }.show()
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_record, null)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etCardTips = view.findViewById<EditText>(R.id.etCardTips)
        val etCashTips = view.findViewById<EditText>(R.id.etCashTips)
        val etCashPaidIn = view.findViewById<EditText>(R.id.etCashPaidIn)
        val etNotes = view.findViewById<EditText>(R.id.etNotes)
        val swDayOff = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swDayOff)

        // Default to today's date
        val prefillCal = Calendar.getInstance()
        val prefillIso = DateUtils.isoFor(
            prefillCal.get(Calendar.YEAR),
            prefillCal.get(Calendar.MONTH),
            prefillCal.get(Calendar.DAY_OF_MONTH)
        )
        etDate.setText(DateUtils.displayFromIso(prefillIso))
        etDate.tag = prefillIso

        etDate.setOnClickListener { pickDateInto(etDate) }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val iso = (etDate.tag as? String) ?: prefillIso
                if (iso.isEmpty()) {
                    toast(getString(R.string.msg_pick_date))
                    return@setPositiveButton
                }
                val card = etCardTips.text.toString().toDoubleOrNull() ?: 0.0
                val cash = etCashTips.text.toString().toDoubleOrNull() ?: 0.0
                val paidIn = etCashPaidIn.text.toString().toDoubleOrNull() ?: 0.0
                if (card < 0 || cash < 0 || paidIn < 0) {
                    toast("Negative values not allowed")
                    return@setPositiveButton
                }
                val record = DailyRecord(
                    id = UUID.randomUUID().toString(),
                    date = DateUtils.displayFromIso(iso),
                    isoDate = iso,
                    cardTips = card,
                    cashTips = cash,
                    cashPaidIn = paidIn,
                    isOff = swDayOff.isChecked,
                    notes = etNotes.text.toString().trim()
                )
                repo.addRecord(record)
                // After add, switch the visible month to the one the record belongs to
                focusOn(record.monthKey)
                refresh()
                toast(getString(R.string.msg_added, record.date))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(record: DailyRecord) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_record, null)
        val etDate = view.findViewById<EditText>(R.id.etEditDate)
        val etCardTips = view.findViewById<EditText>(R.id.etEditCardTips)
        val etCashTips = view.findViewById<EditText>(R.id.etEditCashTips)
        val etCashPaidIn = view.findViewById<EditText>(R.id.etEditCashPaidIn)
        val etNotes = view.findViewById<EditText>(R.id.etEditNotes)
        val swDayOff = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swEditDayOff)

        etDate.setText(record.date)
        etDate.tag = record.isoDate
        etDate.setOnClickListener { pickDateInto(etDate) }
        etCardTips.setText(if (record.cardTips > 0) "%.2f".format(record.cardTips) else "")
        etCashTips.setText(if (record.cashTips > 0) "%.2f".format(record.cashTips) else "")
        etCashPaidIn.setText(if (record.cashPaidIn > 0) "%.2f".format(record.cashPaidIn) else "")
        etNotes.setText(record.notes)
        swDayOff.isChecked = record.isOff

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val iso = (etDate.tag as? String) ?: record.isoDate
                val card = etCardTips.text.toString().toDoubleOrNull() ?: 0.0
                val cash = etCashTips.text.toString().toDoubleOrNull() ?: 0.0
                val paidIn = etCashPaidIn.text.toString().toDoubleOrNull() ?: 0.0
                if (card < 0 || cash < 0 || paidIn < 0) {
                    toast("Negative values not allowed")
                    return@setPositiveButton
                }
                val updated = record.copy(
                    date = DateUtils.displayFromIso(iso),
                    isoDate = iso,
                    cardTips = card,
                    cashTips = cash,
                    cashPaidIn = paidIn,
                    notes = etNotes.text.toString().trim(),
                    isOff = swDayOff.isChecked
                )
                repo.updateRecord(updated)
                focusOn(updated.monthKey)
                refresh()
                toast(getString(R.string.msg_updated, updated.date))
            }
            .setNeutralButton("Reset") { _, _ ->
                repo.updateRecord(record.copy(
                    cardTips = 0.0,
                    cashTips = 0.0,
                    cashPaidIn = 0.0,
                    notes = "",
                    isOff = swDayOff.isChecked
                ))
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickDateInto(target: EditText) {
        val cal = Calendar.getInstance()
        val tag = target.tag as? String
        if (tag != null && tag.length >= 10) {
            val parts = tag.split("-")
            if (parts.size >= 3) {
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            } else {
                cal.set(Calendar.YEAR, currentMonth.get(Calendar.YEAR))
                cal.set(Calendar.MONTH, currentMonth.get(Calendar.MONTH))
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
        } else {
            cal.set(Calendar.YEAR, currentMonth.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, currentMonth.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val iso = DateUtils.isoFor(year, month, day)
                target.setText(DateUtils.displayFromIso(iso))
                target.tag = iso
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showMarkPaidDialog() {
        val monthKey = MonthSummaryCalculator.monthKey(currentMonth)
        val monthName = MonthSummaryCalculator.monthName(currentMonth)
        val isAlreadyPaid = repo.monthPaid(monthKey)
        val owed = MonthSummaryCalculator.summarize(repo.records, monthKey, isAlreadyPaid).owed

        if (!isAlreadyPaid && owed <= 0) {
            toast(getString(R.string.msg_no_amount_owed, monthName))
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_paid_month, null)
        val tvInfo = view.findViewById<android.widget.TextView>(R.id.tvMonthInfo)
        val tilPaid = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPaidAmount)
        val etPaid = view.findViewById<EditText>(R.id.etPaidAmount)

        if (isAlreadyPaid) {
            tvInfo.text = "$monthName is currently marked as PAID.\n\nTap confirm to REVERSE."
            tilPaid.visibility = View.GONE
        } else {
            tvInfo.text = "$monthName\nAmount owed: R${"%.2f".format(owed)}"
            tilPaid.visibility = View.VISIBLE
            etPaid.setText("%.2f".format(owed))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_paid_title)
            .setView(view)
            .setPositiveButton(if (isAlreadyPaid) "Reverse" else "Confirm") { _, _ ->
                if (isAlreadyPaid) {
                    repo.unmarkMonthPaid(monthKey)
                    toast(getString(R.string.msg_unpaid, monthName))
                } else {
                    val paidAmount = etPaid.text.toString().toDoubleOrNull() ?: owed
                    val totals = MonthSummaryCalculator.summarize(repo.records, monthKey)
                    val record = PaidMonthRecord(
                        monthKey = monthKey,
                        monthName = monthName,
                        paidDate = DateFormat.format("yyyy-MM-dd", java.util.Date()).toString(),
                        amountOwed = owed,
                        paidAmount = paidAmount,
                        totalCardTips = totals.cardTips,
                        totalCashTips = totals.cashTips,
                        totalCashPaidIn = totals.cashPaidIn
                    )
                    repo.markMonthPaid(record)
                    toast(getString(R.string.msg_paid, monthName))
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareMonth() {
        val monthName = MonthSummaryCalculator.monthName(currentMonth)
        val monthKey = MonthSummaryCalculator.monthKey(currentMonth)
        val recs = repo.records.filter { it.monthKey == monthKey && !it.isOff }
        if (recs.isEmpty()) {
            toast(getString(R.string.msg_no_data, monthName))
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Wage Tracker - $monthName")
        sb.appendLine()
        var card = 0.0
        var cash = 0.0
        var paid = 0.0
        for (r in recs.sortedBy { it.isoDate }) {
            sb.appendLine("${r.date}: card R${"%.0f".format(r.cardTips)} · cash R${"%.0f".format(r.cashTips)} · paid in R${"%.0f".format(r.cashPaidIn)}")
            card += r.cardTips
            cash += r.cashTips
            paid += r.cashPaidIn
        }
        sb.appendLine()
        sb.appendLine("Card: R${"%.2f".format(card)}")
        sb.appendLine("Cash: R${"%.2f".format(cash)}")
        sb.appendLine("Paid in: R${"%.2f".format(paid)}")
        sb.appendLine("You are owed: R${"%.2f".format(card + paid)}")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, "Share $monthName"))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}