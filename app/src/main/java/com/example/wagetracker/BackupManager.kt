package com.example.wagetracker

import android.content.Context
import android.net.Uri
import com.google.gson.Gson

data class BackupData(
    val records: List<DailyRecord>,
    val paidMonths: List<PaidMonthRecord>
)

object BackupManager {

    private val gson = Gson()

    fun backup(context: Context, uri: Uri) {
        val repo = WageRepository(context)
        val data = BackupData(repo.records.toList(), repo.paidMonths.toList())
        val json = gson.toJson(data)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.toByteArray())
        }
    }

    fun restore(context: Context, uri: Uri): Boolean {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return false
            val data = gson.fromJson(json, BackupData::class.java) ?: return false
            val repo = WageRepository(context)
            repo.clearAll()
            for (r in data.records) repo.addRecord(r)
            for (p in data.paidMonths) repo.markMonthPaid(p)
            true
        } catch (e: Exception) {
            false
        }
    }
}
