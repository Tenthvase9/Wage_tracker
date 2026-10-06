# Wages Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an hourly-wage tracking system (settings rate, per-day hours, auto-calculated locked wages, separate wages-paid flow) plus a wages line across main/list/dashboard/year/widget/CSV, and fix the edit-dialog amount-wiping bug (already fixed — see spec).

**Architecture:** Wages are stored per `DailyRecord` (`hours`, `wageAmount` locked at entry). `MonthSummaryCalculator.Totals` gains `hours`/`wages` sums. `PaidMonthRecord` gains wage fields so a month's wages can be marked paid independently of tips. UI follows existing patterns (dialogs in MainActivity, RecyclerView adapter, PreferenceFragmentCompat).

**Tech Stack:** Kotlin, Android Views + XML, Material Components, SharedPreferences + Gson, androidx.preference, JUnit 4 (unit tests).

## Global Constraints

- All money/hours formatting uses `Locale.ENGLISH` (comma-decimal locales break parsing — this was the edit-dialog bug).
- Amount parsing goes through `MainActivity.parseAmount` (trims, `,` → `.`).
- Day off ⇒ hours = 0 and wage = 0, always.
- Wage is locked at entry: changing the rate in settings never recalculates stored `wageAmount`.
- Min SDK 24, target/compile SDK 36, ViewBinding enabled, no new dependencies.
- Package: `com.example.wagetracker`

---

### Task 1: Data model + calculator hours/wages

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/DailyRecord.kt`
- Modify: `app/src/main/java/com/example/wagetracker/PaidMonthRecord.kt`
- Modify: `app/src/main/java/com/example/wagetracker/MonthSummaryCalculator.kt`
- Create: `app/src/test/java/com/example/wagetracker/MonthSummaryCalculatorTest.kt`

**Interfaces:**
- Produces: `DailyRecord(hours: Double, wageAmount: Double)`, `PaidMonthRecord(totalHours, wagesOwed, wagesPaid, wagesPaidDate, wagesShortBy)`, `MonthSummaryCalculator.Totals.hours: Double`, `Totals.wages: Double`, `MonthSummaryCalculator.formatHours(Double): String`

- [ ] **Step 1: Extend `DailyRecord`**

Add two fields after `cashPaidIn`:

```kotlin
    var hours: Double = 0.0,
    var wageAmount: Double = 0.0
```

- [ ] **Step 2: Extend `PaidMonthRecord`**

Add fields after `totalCashPaidIn` and a helper:

```kotlin
    val totalHours: Double = 0.0,
    val wagesOwed: Double = 0.0,
    val wagesPaid: Double = 0.0,
    val wagesPaidDate: String = ""
) {
    val shortBy: Double
        get() = (amountOwed - paidAmount).coerceAtLeast(0.0)

    val wagesShortBy: Double
        get() = (wagesOwed - wagesPaid).coerceAtLeast(0.0)
}
```

- [ ] **Step 3: Extend `MonthSummaryCalculator`**

`Totals` becomes:

```kotlin
    data class Totals(
        val cardTips: Double,
        val cashTips: Double,
        val cashPaidIn: Double,
        val hours: Double,
        val wages: Double
    ) {
        val tips: Double get() = cardTips + cashTips
        val owed: Double get() = cardTips + cashPaidIn
    }
```

`summarize` accumulates and returns:

```kotlin
        var hours = 0.0
        var wages = 0.0
        for (r in records) {
            if (r.monthKey != monthKey) continue
            if (r.isOff) continue
            card += r.cardTips
            cash += r.cashTips
            paidIn += r.cashPaidIn
            hours += r.hours
            wages += r.wageAmount
        }
        return if (isPaid) Totals(0.0, 0.0, 0.0, 0.0, 0.0)
        else Totals(card, cash, paidIn, hours, wages)
```

Add at end of object:

```kotlin
    fun formatHours(h: Double): String =
        if (h >= 0 && h == h.toLong().toDouble()) "${h.toLong()} hours"
        else "%.1f hours".format(Locale.ENGLISH, h)
```

- [ ] **Step 4: Write the failing test**

Create `app/src/test/java/com/example/wagetracker/MonthSummaryCalculatorTest.kt`:

```kotlin
package com.example.wagetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthSummaryCalculatorTest {

    private fun record(
        monthKey: String,
        hours: Double = 0.0,
        wage: Double = 0.0,
        isOff: Boolean = false
    ) = DailyRecord(
        id = "$monthKey-$hours-$wage",
        date = "Mon 1 Jan",
        isoDate = "${monthKey}-01",
        hours = hours,
        wageAmount = wage,
        isOff = isOff
    )

    @Test
    fun sums_hours_and_wages() {
        val records = listOf(
            record("2026-04", hours = 8.0, wage = 960.0),
            record("2026-04", hours = 7.5, wage = 900.0),
            record("2026-05", hours = 4.0, wage = 480.0)
        )
        val totals = MonthSummaryCalculator.summarize(records, "2026-04")
        assertEquals(15.5, totals.hours, 0.001)
        assertEquals(1860.0, totals.wages, 0.001)
    }

    @Test
    fun day_off_excluded_from_hours_and_wages() {
        val totals = MonthSummaryCalculator.summarize(
            listOf(record("2026-04", hours = 8.0, wage = 960.0, isOff = true)),
            "2026-04"
        )
        assertEquals(0.0, totals.hours, 0.001)
        assertEquals(0.0, totals.wages, 0.001)
    }

    @Test
    fun paid_month_zeroes_hours_and_wages() {
        val totals = MonthSummaryCalculator.summarize(
            listOf(record("2026-04", hours = 8.0, wage = 960.0)),
            "2026-04",
            isPaid = true
        )
        assertEquals(0.0, totals.hours, 0.001)
        assertEquals(0.0, totals.wages, 0.001)
    }

    @Test
    fun formatHours_whole_and_decimal() {
        assertEquals("8 hours", MonthSummaryCalculator.formatHours(8.0))
        assertEquals("7.5 hours", MonthSummaryCalculator.formatHours(7.5))
        assertEquals("0 hours", MonthSummaryCalculator.formatHours(0.0))
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: FAIL — `Totals` constructor arity mismatch / `hours` / `wages` / `formatHours` unresolved.

- [ ] **Step 6: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/wagetracker/DailyRecord.kt app/src/main/java/com/example/wagetracker/PaidMonthRecord.kt app/src/main/java/com/example/wagetracker/MonthSummaryCalculator.kt app/src/test/java/com/example/wagetracker/MonthSummaryCalculatorTest.kt
git commit -m "feat: add hours and wages to data model and month totals"
```

---

### Task 2: Settings hourly rate

**Files:**
- Modify: `app/src/main/res/xml/preferences.xml`
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt`

**Interfaces:**
- Produces: `MainActivity.hourlyRate(): Double` (private; reads `hourly_rate` pref, comma-tolerant, default 0)

- [ ] **Step 1: Add the preference**

In `preferences.xml`, insert a new category before the "Appearance" category:

```xml
    <PreferenceCategory app:title="Wages">
        <EditTextPreference
            app:key="hourly_rate"
            app:title="Hourly rate (R)"
            app:summary="Used to calculate wages from hours worked"
            app:defaultValue="0" />
    </PreferenceCategory>
```

- [ ] **Step 2: Add the rate helper to MainActivity**

Add import `androidx.preference.PreferenceManager` (with the other imports), and add next to `parseAmount`:

```kotlin
    private fun hourlyRate(): Double {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return parseAmount(prefs.getString("hourly_rate", "0") ?: "0")
    }
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/preferences.xml app/src/main/java/com/example/wagetracker/MainActivity.kt
git commit -m "feat: add hourly rate setting"
```

---

### Task 3: Add/Edit dialogs — hours, auto-calculated wage, manual override

**Files:**
- Modify: `app/src/main/res/layout/dialog_add_record.xml`
- Modify: `app/src/main/res/layout/dialog_edit_record.xml`
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (`showAddDialog`, `showEditDialog`)

**Interfaces:**
- Consumes: `MainActivity.hourlyRate()`, `MainActivity.parseAmount(String): Double`, `DailyRecord.hours`, `DailyRecord.wageAmount`
- Produces: add/edit dialogs persist `hours` and `wageAmount` on save; day off forces both to 0

- [ ] **Step 1: Add fields to `dialog_add_record.xml`**

After the `etCashPaidIn` TextInputLayout (before the notes one), insert:

```xml
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            style="@style/Widget.Material3.TextInputLayout.OutlinedBox">
            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etHours"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Hours worked"
                android:inputType="numberDecimal" />
        </com.google.android.material.textfield.TextInputLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="12dp">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    style="@style/Widget.Material3.TextInputLayout.OutlinedBox">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etWages"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:hint="Wages (R)"
                        android:inputType="numberDecimal"
                        android:enabled="false" />
                </com.google.android.material.textfield.TextInputLayout>

                <TextView
                    android:id="@+id/tvWageRate"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textSize="12sp"
                    android:textColor="?attr/colorOnSurfaceVariant" />
            </LinearLayout>

            <com.google.android.material.checkbox.MaterialCheckBox
                android:id="@+id/cbManualWage"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Manual amount"
                android:layout_marginStart="8dp" />
        </LinearLayout>
```

- [ ] **Step 2: Add the same fields to `dialog_edit_record.xml`**

Same block as Step 1, with IDs `etEditHours`, `etEditWages`, `tvEditWageRate`, `cbEditManualWage`.

- [ ] **Step 3: Wire the add dialog in `showAddDialog`**

After the existing `val swDayOff = ...` line, add:

```kotlin
        val etHours = view.findViewById<EditText>(R.id.etHours)
        val etWages = view.findViewById<EditText>(R.id.etWages)
        val cbManual = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbManualWage)
        val tvRate = view.findViewById<android.widget.TextView>(R.id.tvWageRate)

        val rate = hourlyRate()
        tvRate.text = "Rate: R${"%.2f".format(java.util.Locale.ENGLISH, rate)}/h"

        fun recalcWage() {
            if (!cbManual.isChecked) {
                etWages.setText("%.2f".format(java.util.Locale.ENGLISH, parseAmount(etHours.text.toString()) * rate))
            }
        }
        etHours.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = recalcWage()
        })
        cbManual.setOnCheckedChangeListener { _, checked ->
            etWages.isEnabled = checked
            if (!checked) recalcWage()
        }
        swDayOff.setOnCheckedChangeListener { _, checked ->
            if (checked) { etHours.setText(""); etWages.setText("") }
        }
```

In the OK handler, change the validation and record creation to include hours/wages:

```kotlin
                val hours = if (swDayOff.isChecked) 0.0 else parseAmount(etHours.text.toString())
                val wage = if (swDayOff.isChecked) 0.0 else parseAmount(etWages.text.toString())
                if (card < 0 || cash < 0 || paidIn < 0 || hours < 0 || wage < 0) {
                    toast("Negative values not allowed")
                    return@setPositiveButton
                }
```

and add `hours = hours, wageAmount = wage` to the `DailyRecord(...)` constructor call.

- [ ] **Step 4: Wire the edit dialog in `showEditDialog`**

After the existing `val swDayOff = ...` line, add:

```kotlin
        val etHours = view.findViewById<EditText>(R.id.etEditHours)
        val etWages = view.findViewById<EditText>(R.id.etEditWages)
        val cbManual = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbEditManualWage)
        val tvRate = view.findViewById<android.widget.TextView>(R.id.tvEditWageRate)

        val rate = hourlyRate()
        val lockedRate = if (record.hours > 0) record.wageAmount / record.hours else rate
        tvRate.text = "Rate: R${"%.2f".format(java.util.Locale.ENGLISH, lockedRate)}/h"

        etHours.setText(if (!record.isOff && record.hours > 0) "%.2f".format(java.util.Locale.ENGLISH, record.hours) else "")
        etWages.setText(if (!record.isOff && record.wageAmount > 0) "%.2f".format(java.util.Locale.ENGLISH, record.wageAmount) else "")
        cbManual.isChecked = Math.abs(record.wageAmount - record.hours * rate) > 0.001
        etWages.isEnabled = cbManual.isChecked

        fun recalcWage() {
            if (!cbManual.isChecked) {
                etWages.setText("%.2f".format(java.util.Locale.ENGLISH, parseAmount(etHours.text.toString()) * rate))
            }
        }
        etHours.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = recalcWage()
        })
        cbManual.setOnCheckedChangeListener { _, checked ->
            etWages.isEnabled = checked
            if (!checked) recalcWage()
        }
        swDayOff.setOnCheckedChangeListener { _, checked ->
            if (checked) { etHours.setText(""); etWages.setText("") }
        }
```

In the OK handler, change the validation and copy to include hours/wages:

```kotlin
                val hours = if (swDayOff.isChecked) 0.0 else parseAmount(etHours.text.toString())
                val wage = if (swDayOff.isChecked) 0.0 else parseAmount(etWages.text.toString())
                if (card < 0 || cash < 0 || paidIn < 0 || hours < 0 || wage < 0) {
                    toast("Negative values not allowed")
                    return@setPositiveButton
                }
```

and add `hours = hours, wageAmount = wage` to `record.copy(...)`.

In the Reset neutral button, add `hours = 0.0, wageAmount = 0.0` to `record.copy(...)`.

- [ ] **Step 5: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/dialog_add_record.xml app/src/main/res/layout/dialog_edit_record.xml app/src/main/java/com/example/wagetracker/MainActivity.kt
git commit -m "feat: hours and auto-calculated wages in add/edit dialogs"
```

---

### Task 4: Main screen wages card + per-day wages line

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/item_record.xml`
- Modify: `app/src/main/java/com/example/wagetracker/RecordAdapter.kt`
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (`refresh`)

**Interfaces:**
- Consumes: `Totals.hours`, `Totals.wages`, `MonthSummaryCalculator.formatHours`, `DailyRecord.hours`, `DailyRecord.wageAmount`, `WageRepository.wagesPaid(monthKey): Boolean` (Task 5 — for the chip; if Task 5 not yet run, still compiles because the call site is added in Task 5's refresh edit — see note)

> Note: `refresh()` references `repo.wagesPaid(...)` and `binding.chipWagesStatus`/`tvWagesAmount`/`tvWagesHours`. Add the `wagesPaid` repo method in Task 5 BEFORE building this task, or build only after Task 5. Order: do Task 5's repository step first if building incrementally.

- [ ] **Step 1: Add the wages card to `activity_main.xml`**

Insert between the hero card closing `</com.google.android.material.card.MaterialCardView>` and the `<!-- Four mini cards -->` comment:

```xml
    <!-- Wages card (separate from tips owed) -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/wagesCard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:layout_marginTop="8dp"
        app:cardCornerRadius="16dp"
        app:cardElevation="2dp"
        app:cardBackgroundColor="?attr/colorSurface"
        app:strokeColor="?attr/colorOutline"
        app:strokeWidth="1dp"
        app:contentPadding="14dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="WAGES"
                    android:textAllCaps="true"
                    android:letterSpacing="0.08"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:textSize="11sp" />

                <TextView
                    android:id="@+id/tvWagesAmount"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="R0"
                    android:textColor="?attr/colorOnSurface"
                    android:textSize="24sp"
                    android:textStyle="bold"
                    android:fontFeatureSettings="tnum" />

                <TextView
                    android:id="@+id/tvWagesHours"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="0 hours"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:textSize="12sp" />
            </LinearLayout>

            <com.google.android.material.chip.Chip
                android:id="@+id/chipWagesStatus"
                style="@style/Widget.Material3.Chip.Assist"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Mark wages paid"
                app:chipIcon="@drawable/ic_check"
                app:chipBackgroundColor="#FFFFFF"
                android:textColor="?attr/colorPrimary"
                app:chipIconTint="?attr/colorPrimary" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Add the wages line to `item_record.xml`**

After the `tvNotes` TextView, add:

```xml
    <TextView
        android:id="@+id/tvWages"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textColor="@color/wage_owed"
        android:textSize="12sp"
        android:visibility="gone" />
```

- [ ] **Step 3: Bind wages in `RecordAdapter.onBindViewHolder`**

In the `isOff` branch add `tvWages.text = "-"` and `tvWages.visibility = View.VISIBLE` alongside the other "-" assignments. In the else branch, after the existing assignments, add:

```kotlin
                if (record.hours > 0 || record.wageAmount > 0) {
                    tvWages.visibility = View.VISIBLE
                    tvWages.text = "Wages: R${"%.0f".format(record.wageAmount)} · ${MonthSummaryCalculator.formatHours(record.hours)}"
                } else {
                    tvWages.visibility = View.GONE
                }
```

- [ ] **Step 4: Populate the wages card in `MainActivity.refresh`**

After the mini-cards block (after `binding.tvTotalPaidIn.text = ...`), add:

```kotlin
        // Wages card
        val wagesIsPaid = repo.wagesPaid(monthKey)
        binding.tvWagesAmount.text = "R${"%,.0f".format(java.util.Locale.ENGLISH, if (wagesIsPaid) 0.0 else rawTotals.wages)}"
        binding.tvWagesHours.text = MonthSummaryCalculator.formatHours(rawTotals.hours)
        binding.chipWagesStatus.text = if (wagesIsPaid) "Wages paid · tap to reverse" else "Mark wages paid"
        binding.chipWagesStatus.setChipBackgroundColorResource(
            if (wagesIsPaid) R.color.wage_paid else android.R.color.white
        )
        binding.chipWagesStatus.setTextColor(
            if (wagesIsPaid) 0xFFFFFFFF.toInt() else getColor(R.color.brand_primary)
        )
        binding.chipWagesStatus.setOnClickListener { showWagesPaidDialog() }
```

Also add `invalidateOptionsMenu()` at the end of `refresh()` (before `WageWidgetProvider.refreshAll(this)`).

- [ ] **Step 5: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/res/layout/item_record.xml app/src/main/java/com/example/wagetracker/RecordAdapter.kt app/src/main/java/com/example/wagetracker/MainActivity.kt
git commit -m "feat: wages card on main screen and wages line per day"
```

---

### Task 5: Wages paid flow (repository + dialog + menu)

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/WageRepository.kt`
- Modify: `app/src/main/res/menu/menu_main.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt`

**Interfaces:**
- Consumes: `PaidMonthRecord.wagesOwed/wagesPaid/wagesPaidDate/totalHours`
- Produces: `WageRepository.wagesPaid(monthKey): Boolean`, `WageRepository.markWagesPaid(monthKey, monthName, wagesOwed, wagesPaid, totalHours)`, `WageRepository.unmarkWagesPaid(monthKey)`, `MainActivity.showWagesPaidDialog()` (private), menu item `R.id.action_mark_wages_paid`

- [ ] **Step 1: Update `WageRepository`**

Change `monthPaid` to require a tips paid date:

```kotlin
    fun monthPaid(monthKey: String): Boolean =
        _paid.any { it.monthKey == monthKey && it.paidDate.isNotEmpty() }
```

Add after `paidRecord`:

```kotlin
    fun wagesPaid(monthKey: String): Boolean =
        _paid.any { it.monthKey == monthKey && it.wagesPaidDate.isNotEmpty() }

    fun markWagesPaid(monthKey: String, monthName: String, wagesOwed: Double, wagesPaid: Double, totalHours: Double) {
        val idx = _paid.indexOfFirst { it.monthKey == monthKey }
        val existing = if (idx >= 0) _paid[idx] else PaidMonthRecord(
            monthKey = monthKey,
            monthName = monthName,
            paidDate = "",
            amountOwed = 0.0,
            paidAmount = 0.0,
            totalCardTips = 0.0,
            totalCashTips = 0.0,
            totalCashPaidIn = 0.0
        )
        val updated = existing.copy(
            totalHours = totalHours,
            wagesOwed = wagesOwed,
            wagesPaid = wagesPaid,
            wagesPaidDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        )
        if (idx >= 0) _paid[idx] = updated else _paid.add(updated)
        persistPaid()
    }

    fun unmarkWagesPaid(monthKey: String) {
        val idx = _paid.indexOfFirst { it.monthKey == monthKey }
        if (idx < 0) return
        val updated = _paid[idx].copy(
            totalHours = 0.0,
            wagesOwed = 0.0,
            wagesPaid = 0.0,
            wagesPaidDate = ""
        )
        if (updated.paidDate.isEmpty()) _paid.removeAt(idx) else _paid[idx] = updated
        persistPaid()
    }
```

- [ ] **Step 2: Add menu item and strings**

In `menu_main.xml`, after the `action_year_view` item add:

```xml
    <item
        android:id="@+id/action_mark_wages_paid"
        android:title="Mark wages paid" />
```

In `strings.xml` add:

```xml
    <string name="action_mark_wages_paid">Mark wages paid</string>
    <string name="dialog_wages_paid_title">Mark wages paid</string>
    <string name="msg_wages_paid">%1$s wages marked as paid</string>
    <string name="msg_wages_unpaid">%1$s wages marked as unpaid</string>
```

- [ ] **Step 3: Add `showWagesPaidDialog` and menu handling to MainActivity**

Add the dialog method (after `showMarkPaidDialog`):

```kotlin
    private fun showWagesPaidDialog() {
        val monthKey = MonthSummaryCalculator.monthKey(currentMonth)
        val monthName = MonthSummaryCalculator.monthName(currentMonth)
        val isAlreadyPaid = repo.wagesPaid(monthKey)
        val totals = MonthSummaryCalculator.summarize(repo.records, monthKey)

        val view = layoutInflater.inflate(R.layout.dialog_paid_month, null)
        val tvInfo = view.findViewById<android.widget.TextView>(R.id.tvMonthInfo)
        val tilPaid = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPaidAmount)
        val etPaid = view.findViewById<android.widget.EditText>(R.id.etPaidAmount)

        if (isAlreadyPaid) {
            tvInfo.text = "$monthName wages are currently marked as PAID.\n\nTap confirm to REVERSE."
            tilPaid.visibility = View.GONE
        } else {
            tvInfo.text = "$monthName\nWages owed: R${"%.2f".format(java.util.Locale.ENGLISH, totals.wages)}"
            tilPaid.visibility = View.VISIBLE
            etPaid.setText("%.2f".format(java.util.Locale.ENGLISH, totals.wages))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_wages_paid_title)
            .setView(view)
            .setPositiveButton(if (isAlreadyPaid) "Reverse" else "Confirm") { _, _ ->
                if (isAlreadyPaid) {
                    repo.unmarkWagesPaid(monthKey)
                    toast(getString(R.string.msg_wages_unpaid, monthName))
                } else {
                    val paidAmount = parseAmount(etPaid.text.toString())
                    repo.markWagesPaid(monthKey, monthName, totals.wages, paidAmount, totals.hours)
                    toast(getString(R.string.msg_wages_paid, monthName))
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
```

In the toolbar menu `when` block add:

```kotlin
                R.id.action_mark_wages_paid -> { showWagesPaidDialog(); true }
```

Add `onPrepareOptionsMenu`:

```kotlin
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val monthKey = MonthSummaryCalculator.monthKey(currentMonth)
        val wagesOwed = MonthSummaryCalculator.summarize(repo.records, monthKey).wages
        menu.findItem(R.id.action_mark_wages_paid)?.isEnabled =
            wagesOwed > 0 && !repo.wagesPaid(monthKey)
        return super.onPrepareOptionsMenu(menu)
    }
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/wagetracker/WageRepository.kt app/src/main/res/menu/menu_main.xml app/src/main/res/values/strings.xml app/src/main/java/com/example/wagetracker/MainActivity.kt
git commit -m "feat: wages paid flow separate from tips paid"
```

---

### Task 6: Dashboard + Year view wages

**Files:**
- Modify: `app/src/main/res/layout/activity_dashboard.xml`
- Modify: `app/src/main/java/com/example/wagetracker/DashboardActivity.kt`
- Modify: `app/src/main/java/com/example/wagetracker/YearAdapter.kt` (`YearMonthRow`)
- Modify: `app/src/main/java/com/example/wagetracker/YearViewActivity.kt`
- Modify: `app/src/main/res/layout/item_year_month.xml`

**Interfaces:**
- Consumes: `DailyRecord.wageAmount`, `PaidMonthRecord.wagesPaidDate`
- Produces: `YearMonthRow.wages: Double`

- [ ] **Step 1: Dashboard — add Wages column**

In `activity_dashboard.xml`, inside the all-time totals row (after the "Paid in" column, before the "Days" column), insert:

```xml
                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical"
                            android:gravity="center">
                            <TextView android:id="@+id/tvTotalWages" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="R0" android:textColor="@color/wage_owed" android:textSize="20sp" android:textStyle="bold" />
                            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Wages" android:textColor="?attr/colorOnSurfaceVariant" android:textSize="11sp" />
                        </LinearLayout>
```

In `DashboardActivity.buildStats`, after `val allPaidIn = ...` add `val allWages = records.sumOf { it.wageAmount }`, set `binding.tvTotalWages.text = formatRand(allWages)` in both the empty-state branch and the main branch.

- [ ] **Step 2: Year view — extend row and adapter**

`YearMonthRow` gains `val wages: Double` (after `short`).

`YearViewActivity.rebuild` — compute and pass wages:

```kotlin
                val wagesSum = recs.sumOf { it.wageAmount }
                YearMonthRow(
                    ...,
                    wages = if (paid?.wagesPaidDate?.isNotEmpty() == true) 0.0 else wagesSum
                )
```

`item_year_month.xml` — add a 4th column after the "Paid" column:

```xml
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Wages"
                    android:textColor="?attr/colorOnSurfaceVariant"
                    android:textSize="11sp" />
                <TextView
                    android:id="@+id/tvYearWages"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="R0"
                    android:textColor="@color/wage_owed"
                    android:textSize="15sp"
                    android:textStyle="bold" />
            </LinearLayout>
```

`YearAdapter.onBindViewHolder` — add `tvYearWages.text = "R${"%.0f".format(row.wages)}"`.

- [ ] **Step 3: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_dashboard.xml app/src/main/java/com/example/wagetracker/DashboardActivity.kt app/src/main/java/com/example/wagetracker/YearAdapter.kt app/src/main/java/com/example/wagetracker/YearViewActivity.kt app/src/main/res/layout/item_year_month.xml
git commit -m "feat: wages totals in dashboard and year view"
```

---

### Task 7: Widget wages line

**Files:**
- Modify: `app/src/main/res/layout/widget_wage.xml`
- Modify: `app/src/main/java/com/example/wagetracker/WageWidgetProvider.kt`

**Interfaces:**
- Consumes: `Totals.wages`, `Totals.hours`, `WageRepository.wagesPaid(monthKey)`, `MonthSummaryCalculator.formatHours`

- [ ] **Step 1: Add the wages TextView to `widget_wage.xml`**

After `widgetSub`:

```xml
    <TextView
        android:id="@+id/widgetWages"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="?attr/colorOnPrimary"
        android:alpha="0.85"
        android:textSize="14sp"
        android:layout_marginTop="2dp" />
```

- [ ] **Step 2: Populate it in `WageWidgetProvider.updateOne`**

After the `if (repo.records.any ...)` block's existing text sets, inside the same branch add:

```kotlin
                val wagesIsPaid = repo.wagesPaid(monthKey)
                views.setTextViewText(
                    R.id.widgetWages,
                    if (wagesIsPaid) "Wages: paid" else "Wages R${"%.0f".format(totals.wages)} · ${MonthSummaryCalculator.formatHours(totals.hours)}"
                )
```

and in the else branch: `views.setTextViewText(R.id.widgetWages, "")`.

- [ ] **Step 3: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/widget_wage.xml app/src/main/java/com/example/wagetracker/WageWidgetProvider.kt
git commit -m "feat: wages line on widget"
```

---

### Task 8: CSV export hours/wages columns

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/CsvExporter.kt`

**Interfaces:**
- Consumes: `DailyRecord.hours`, `DailyRecord.wageAmount`, `Totals.hours`, `Totals.wages`

- [ ] **Step 1: Update `writeMonth`**

Header becomes:

```kotlin
        sb.appendLine("Date,Card Tips,Cash Tips,Cash Paid In,Hours,Wages,Total,Notes")
```

OFF row becomes `"${csv(r.date)},OFF,OFF,OFF,OFF,OFF,OFF,"`.

Data row becomes:

```kotlin
            sb.appendLine(
                "${csv(r.date)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.cardTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.cashTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.cashPaidIn)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.hours)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, r.wageAmount)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, total)}," +
                "${csv(r.notes)}"
            )
```

Totals line becomes:

```kotlin
        sb.appendLine("Totals,${"%.2f".format(java.util.Locale.ENGLISH, totals.cardTips)},${"%.2f".format(java.util.Locale.ENGLISH, totals.cashTips)},${"%.2f".format(java.util.Locale.ENGLISH, totals.cashPaidIn)},${"%.2f".format(java.util.Locale.ENGLISH, totals.hours)},${"%.2f".format(java.util.Locale.ENGLISH, totals.wages)},")
```

- [ ] **Step 2: Update `writeAllMonths`**

Header becomes:

```kotlin
        sb.appendLine("Month,Status,Worked Days,Card Tips,Cash Tips,Cash Paid In,Hours,Wages,Owed,Paid,Short By,Paid Date")
```

Month row becomes:

```kotlin
            sb.appendLine(
                "$monthName,$status,$days," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.cardTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.cashTips)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.cashPaidIn)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.hours)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, totals.wages)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, owed)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, paidAmount)}," +
                "${"%.2f".format(java.util.Locale.ENGLISH, short)}," +
                paidDate
            )
```

OFF day row becomes `"  ${r.date},OFF,,,,,,,"` and the normal day row gains hours/wages:

```kotlin
                sb.appendLine(
                    "  ${r.date},," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.cardTips)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.cashTips)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.cashPaidIn)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.hours)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.wageAmount)}," +
                    "${"%.2f".format(java.util.Locale.ENGLISH, r.dayTotal)}," +
                    "${csv(r.notes)}"
                )
```

(The `Locale.ENGLISH` on existing formats is a drive-by fix for the same comma-locale bug as the edit dialog.)

- [ ] **Step 3: Build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/wagetracker/CsvExporter.kt
git commit -m "feat: hours and wages columns in CSV export"
```

---

### Task 9: Full verification

**Files:** none

- [ ] **Step 1: Unit tests**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS (Task 1 tests + example test).

- [ ] **Step 2: Full build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual test checklist (on device/emulator)**

1. Settings → Hourly rate: enter `120`.
2. Add entry: hours `8` → wages auto-fills `960.00`; card/cash/paid-in untouched.
3. Check "Manual amount" → wages editable; uncheck → reverts to auto.
4. Edit that entry: hours/wages prefilled, rate shown as `R120.00/h`; change card tips only → save → cash/paid-in/hours/wages all preserved (bug fix verification).
5. Day off entry: hours/wages empty, list shows `-`.
6. Main screen: wages card shows `R960` + `8 hours` under the hero owed amount.
7. Menu → Mark wages paid → confirm → chip shows "Wages paid · tap to reverse", wages amount shows R0; reverse works.
8. Tips mark-paid still works independently of wages paid.
9. Dashboard: Wages column shows total; Year view: Wages column per month.
10. Widget: wages line under owed, smaller text.
11. Export CSV: Hours/Wages columns present.
12. Change rate to `150` → old entry still shows R960 locked; new entry with 8h auto-fills `1200.00`.
