package com.example.wagetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean("reminder_enabled", false)) {
            val hour = prefs.getInt("reminder_hour", 20)
            val minute = prefs.getInt("reminder_minute", 0)
            ReminderReceiver.schedule(context, hour, minute)
        }
        if (prefs.getBoolean("auto_backup_enabled", false)) {
            BackupAlarmReceiver.schedule(context)
        }
    }
}
