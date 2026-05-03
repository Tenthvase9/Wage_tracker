package com.example.wagetracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.wagetracker.databinding.ItemRecordBinding

class RecordAdapter(
    private var records: List<DailyRecord>,
    private val onDeleteClick: (DailyRecord) -> Unit
) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.binding.apply {
            tvDate.text = record.date
            tvCard.text = if (record.isOff) "OFF" else String.format("R%.0f", record.tipsToPay)
            tvCash.text = if (record.isOff) "" else String.format("R%.0f", record.cashPaidIn)

            btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }

    override fun getItemCount() = records.size

    fun updateRecords(newRecords: List<DailyRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}