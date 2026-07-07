package com.example.wagetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wagetracker.databinding.ItemYearMonthBinding

data class YearMonthRow(
    val monthKey: String,
    val monthName: String,
    val tips: Double,
    val owed: Double,
    val paid: Double,
    val short: Double,
    val isPaid: Boolean
)

class YearAdapter(
    private val onClick: (YearMonthRow) -> Unit
) : ListAdapter<YearMonthRow, YearAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemYearMonthBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemYearMonthBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        holder.binding.apply {
            tvYearMonth.text = row.monthName
            tvYearTips.text = "R${"%.0f".format(row.tips)}"
            tvYearOwed.text = if (row.isPaid) "R0" else "R${"%.0f".format(row.owed)}"
            tvYearPaid.text = "R${"%.0f".format(row.paid)}"
            chipYearStatus.text = if (row.isPaid) "PAID" else "UNPAID"
            chipYearStatus.setChipBackgroundColorResource(
                if (row.isPaid) R.color.wage_paid else R.color.wage_short
            )
            chipYearStatus.setTextColor(0xFFFFFFFF.toInt())
            if (row.short > 0) {
                tvYearShort.visibility = View.VISIBLE
                tvYearShort.text = "Short by R${"%.0f".format(row.short)}"
            } else {
                tvYearShort.visibility = View.GONE
            }
            root.setOnClickListener { onClick(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<YearMonthRow>() {
            override fun areItemsTheSame(a: YearMonthRow, b: YearMonthRow) = a.monthKey == b.monthKey
            override fun areContentsTheSame(a: YearMonthRow, b: YearMonthRow) = a == b
        }
    }
}