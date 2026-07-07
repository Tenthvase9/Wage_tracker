package com.example.wagetracker

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val backupLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                BackupManager.backup(requireContext(), uri)
                Toast.makeText(requireContext(), "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        private val restoreLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                val ok = BackupManager.restore(requireContext(), uri)
                if (ok) {
                    Toast.makeText(requireContext(), "Data restored", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Restore failed: invalid file", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        private val notifPermLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val prefs = preferenceManager.sharedPreferences!!
                val h = prefs.getInt("reminder_hour", 20)
                val m = prefs.getInt("reminder_minute", 0)
                ReminderReceiver.schedule(requireContext(), h, m)
                findPreference<androidx.preference.SwitchPreferenceCompat>("reminder_enabled")?.isChecked = true
            } else {
                Toast.makeText(requireContext(), "Notification permission required for reminders", Toast.LENGTH_LONG).show()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<androidx.preference.SwitchPreferenceCompat>("dark_mode")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    val mode = if (enabled) {
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    }
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                    WageRepository(requireContext()).setDarkMode(enabled)
                    true
                }

            val ctx = requireContext()
            val prefs = preferenceManager.sharedPreferences!!
            updateReminderTimeSummary(prefs.getInt("reminder_hour", 20), prefs.getInt("reminder_minute", 0))

            // Daily reminder
            findPreference<androidx.preference.SwitchPreferenceCompat>("reminder_enabled")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@setOnPreferenceChangeListener false
                        }
                        val h = prefs.getInt("reminder_hour", 20)
                        val m = prefs.getInt("reminder_minute", 0)
                        ReminderReceiver.schedule(ctx, h, m)
                    } else {
                        ReminderReceiver.cancel(ctx)
                    }
                    true
                }

            findPreference<androidx.preference.Preference>("reminder_time")
                ?.setOnPreferenceClickListener {
                    val h = prefs.getInt("reminder_hour", 20)
                    val m = prefs.getInt("reminder_minute", 0)
                    TimePickerDialog(ctx, { _, hour, minute ->
                        prefs.edit().putInt("reminder_hour", hour).putInt("reminder_minute", minute).apply()
                        updateReminderTimeSummary(hour, minute)
                        if (prefs.getBoolean("reminder_enabled", false)) {
                            ReminderReceiver.schedule(ctx, hour, minute)
                        }
                    }, h, m, false).show()
                    true
                }

            // Auto backup
            findPreference<androidx.preference.SwitchPreferenceCompat>("auto_backup_enabled")
                ?.setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    if (enabled) {
                        BackupAlarmReceiver.schedule(ctx)
                    } else {
                        BackupAlarmReceiver.cancel(ctx)
                    }
                    true
                }

            findPreference<androidx.preference.Preference>("backup_now")
                ?.setOnPreferenceClickListener {
                    performBackupNow(ctx)
                    true
                }

            findPreference<androidx.preference.Preference>("backup")
                ?.setOnPreferenceClickListener {
                    backupLauncher.launch("wage-tracker-backup.json")
                    true
                }

            findPreference<androidx.preference.Preference>("restore")
                ?.setOnPreferenceClickListener {
                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                    true
                }

            findPreference<androidx.preference.Preference>("reset_data")
                ?.setOnPreferenceClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Reset all data")
                        .setMessage("This will permanently delete all records and paid-month history. This cannot be undone.")
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            WageRepository(requireContext()).clearAll()
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Done")
                                .setMessage("All data has been cleared.")
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
        }

        private fun updateReminderTimeSummary(hour: Int, minute: Int) {
            val ampm = if (hour < 12) "AM" else "PM"
            val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val mStr = "%02d".format(minute)
            val summary = "$h12:$mStr $ampm"
            findPreference<androidx.preference.Preference>("reminder_time")?.summary = summary
        }

        private fun performBackupNow(context: Context) {
            try {
                val repo = WageRepository(context)
                val data = BackupData(repo.records.toList(), repo.paidMonths.toList())
                val json = com.google.gson.Gson().toJson(data)
                val dir = context.getExternalFilesDir("backups")
                dir?.mkdirs()
                val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "wage-tracker-backup-$stamp.json")
                file.writeText(json)
                Toast.makeText(context, R.string.backup_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "${getString(R.string.backup_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}