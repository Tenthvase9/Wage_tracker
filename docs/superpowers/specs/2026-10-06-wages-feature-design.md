# Wages Feature + Edit Dialog Bug Fix — Design

Date: 2026-10-06

## 1. Edit dialog amounts being wiped (bug fix, implemented)

**Root cause:** `MainActivity.kt` prefilled edit-dialog amounts with `"%.2f".format(value)`, which uses the default locale. On comma-decimal locales this produces `150,00`, and `toDoubleOrNull()` cannot parse commas — untouched fields silently saved as `0.0`. Same issue in the mark-paid dialog prefill.

**Fix (applied):**
- All prefill formats use `Locale.ENGLISH` (edit dialog, paid dialog).
- New `parseAmount(String)` helper in `MainActivity`: trims, replaces `,` with `.`, then `toDoubleOrNull()`. Used by all three parse sites (add dialog, edit dialog, paid dialog) so manually typed commas also work.

## 2. Data model

`DailyRecord` gains two fields (Gson defaults handle old records/backups as 0):

```kotlin
var hours: Double = 0.0,
var wageAmount: Double = 0.0   // locked at entry time
```

`PaidMonthRecord` gains (defaults keep old records working):

```kotlin
val totalHours: Double = 0.0,
val wagesOwed: Double = 0.0,
val wagesPaid: Double = 0.0,
val wagesPaidDate: String = ""
```

## 3. Settings

- New `EditTextPreference` `hourly_rate` ("Hourly rate (R)") in `preferences.xml`. Stored as string, parsed with comma-tolerant logic, default 0.
- Rate 0/unset: wages displays show `R0` / `0h` (not hidden).

## 4. Add / Edit daily entry dialogs

- New **Hours worked** field (decimal input, e.g. `8` or `7.5`).
- New **Wages (R)** field, auto-calculated live from `hours × rate` via TextWatcher.
- **Manual amount** checkbox: when checked, wage field is unlocked for editing; when unchecked, it reverts to auto-calc and is read-only.
- Edit dialog additionally shows the locked rate (e.g. `R120/h`) next to the wage field.
- Day off toggled → hours and wage forced to 0 (day off = no wages).
- On save: `hours` and `wageAmount` stored on the record. Wage is locked — later rate changes do not affect existing records.

## 5. Main screen

- Hero "You are owed" stays tips-only (unchanged).
- New separate **Wages card** under the hero: `Wages R3400` with `34 hours` in smaller text underneath.
- Card shows its own `PAID` chip when the month's wages are marked paid.

## 6. Per-day list item

- New line: `Wages: R960 · 8h`. Shown as `-` on day off.

## 7. Dashboard

- All-time wages earned total added alongside existing totals.

## 8. Year view

- Wages column added (wages owed / wages paid per month).

## 9. Widget

- Existing widget extended (no second widget): wages owed line added, rendered smaller than the tips owed line.

## 10. Wages paid flow (separate from tips paid)

- `WageRepository`: `monthPaid()` now also requires `paidDate.isNotEmpty()` so a wages-only record does not flip tips-paid state.
- New repo functions: `wagesPaid(monthKey)`, `markWagesPaid(...)`, `unmarkWagesPaid(...)` (unmarking removes the record if neither tips nor wages remain paid).
- Menu item **Mark wages paid** (enabled only when wages owed > 0 and not already paid), reusing the existing paid-dialog layout prefilled with wages owed. Supports reverse like the tips flow.
- Wages paid date stored separately — wages can be paid on a different date than tips.

## 11. CSV export

- Hours and wage columns added to month/all export.

## 12. Backup / restore

- No migration needed: Gson serializes the new fields; old backups restore with defaults (0 / "").

## Out of scope

- Second widget, wages in the paid-by-default flow, per-entry rate overrides beyond the manual amount checkbox, wages affecting the tips "owed" amount.
