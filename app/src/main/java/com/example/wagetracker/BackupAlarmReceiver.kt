package com.example.wagetracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val repo = WageRepository(context)
            val data = BackupData(repo.records.toList(), repo.paidMonths.toList())
            val json = Gson().toJson(data)

            val dir = context.getExternalFilesDir("backups")
            dir?.mkdirs()

            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val file = File(dir, "wage-tracker-backup-$stamp.json")
            file.writeText(json)

            val files = dir?.listFiles()?.sortedByDescending { it.lastModified() }
            if (files != null && files.size > 30) {
                for (f in files.drop(30)) f.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val REQ_CODE = 200

        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BackupAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BackupAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pendingIntent)
        }
    }
}
