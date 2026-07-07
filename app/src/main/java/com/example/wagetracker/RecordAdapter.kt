package com.example.wagetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wagetracker.databinding.ItemRecordBinding

class RecordAdapter(
    private val onDeleteClick: (DailyRecord) -> Unit,
    private val onEditClick: (DailyRecord) -> Unit
) : ListAdapter<DailyRecord, RecordAdapter.ViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

    inner class ViewHolder(val binding: ItemRecordBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        holder.binding.apply {
            tvDate.text = if (record.isOff) "${record.date} (OFF)" else record.date

            if (record.isOff) {
                tvCardTips.text = "-"
                tvCashTips.text = "-"
                tvCashPaidIn.text = "-"
                tvDayTotal.text = "-"
                tvDayTotal.visibility = View.VISIBLE
            } else {
                tvCardTips.text = String.format("R%.0f", record.cardTips)
                tvCashTips.text = String.format("R%.0f", record.cashTips)
                tvCashPaidIn.text = String.format("R%.0f", record.cashPaidIn)
                tvDayTotal.text = String.format("R%.0f", record.dayTotal)
            }

            if (record.notes.isNotBlank()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = record.notes
            } else {
                tvNotes.visibility = View.GONE
            }

            root.setOnClickListener { onEditClick(record) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DailyRecord>() {
            override fun areItemsTheSame(a: DailyRecord, b: DailyRecord) = a.id == b.id
            override fun areContentsTheSame(a: DailyRecord, b: DailyRecord) = a == b
        }
    }
}