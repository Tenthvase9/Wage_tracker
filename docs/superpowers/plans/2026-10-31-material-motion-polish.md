# Material Motion Polish (v2.4.2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app feel alive and deliberate: animated totals, screen/card transitions, list life, and micro-interactions - main screen first, then dashboard/year view/settings.

**Architecture:** One shared `Motion.kt` helper (count-up animator, staggered entrance, month slide, press scale) built on `ViewPropertyAnimator` + material transition classes from the existing `com.google.android.material` dependency. Activities call helpers from their existing refresh/binding code; a touch-handler on views wires press scale. No window-level choreography, no MotionLayout, no new dependencies.

**Tech Stack:** `com.google.android.material.transition` (MaterialFadeThrough, MaterialSharedAxis, MaterialFade), `ViewPropertyAnimator`, RecyclerView `DefaultItemAnimator`/custom tweaks. Views/XML only.

**Spec:** `docs/superpowers/specs/2026-10-31-material-motion-polish-design.md`

## Global Constraints

- No new dependencies - material/transition/viewPropertyAnimator only.
- All money/hours strings render with `Locale.ENGLISH` through the app's existing format style (`"R${"%,.0f".format(java.util.Locale.ENGLISH, x)}"` / `MonthSummaryCalculator.formatHours(h)`); animated interim text is display-only, never parsed.
- Dark mode safe: no hardcoded colors; use theme attrs (`?attr/colorSurface`, etc.) - current theme already handles day/night.
- Motion timing: single animations at most 400ms total; entrance stagger delay base 40ms/view, total at most 250ms; month slide at most 200ms. Motions must be cancelable and must cancel in-flight runs before starting new ones.
- Entrance animations run only on first data display for that screen instance (returning from dashboard/dialog to a live MainActivity must NOT re-run entrance).
- Widget (`WageWidgetProvider`) untouched.
- Colors and card styles untouched (no theme redesign).
- minSdk 24: every animated call site must run on API 24+; material transition classes are viewlib compat (no extra guards needed).
- Verified helper precision: count-up interpolates between `from` and `to` and clamps `t` to 0..1; unit test pins clamp + a stable midpoint (see Task 1).

## Review Focus

1. Re-entering MainActivity (onResume after dialog/dashboard) must not replay entrance stagger - only first refresh animates. Test: Task 8 manual checklist line 1.
2. Flipping a month to paid sets owed to 0.0: count-up must land exactly on "R0" and never render "R-,1" style garbage (clamp mid-draw + final `to` render). Test: Task 1 unit test asserts clamp; Task 8 manual checklist line 2 covers paid flip.
3. Fast repeated month-arrow taps must not stack letters/garbled text - cancel in-flight slide before starting. Test: Task 8 manual checklist line 3.
4. Swipe-delete with undo still animates a sane re-insertion after undo - list remains stable. Test: Task 8 manual checklist line 4.
5. Dark mode: transitions don't paint wrong surfaces - no hardcoded colors slip in. Test: Task 8 manual dark-mode sweep.

---

### Task 1: Motion helper object (`Motion.kt`)

**Files:**
- Create: `app/src/main/java/com/example/wagetracker/Motion.kt`
- Create: `app/src/test/java/com/example/wagetracker/MotionTest.kt`

**Interfaces:**
- Consumes: nothing (standalone).
- Produces: `object Motion` with exactly these five functions, used by Tasks 2-7:
  - `fun lerp(from: Double, to: Double, t: Float): Double` - pure; clamps `t` to 0..1.
  - `fun animateCount(view: TextView, from: Double, to: Double, format: (Double) -> String, durationMs: Long = 350L)` - ValueAnimator 0..1, per-view cancel (tag = the animator), every frame `view.text = format(lerp(from, to, animatedFraction))`, final frame exactly `format(to)`.
  - `fun staggerIn(views: List<View>, baseDelayMs: Int = 40)` - per view (index-ordered): alpha 0->1, translationY 24dp->0, startDelay `baseDelayMs * index`, duration 200ms, ease-out; visible views only, in screen order.
  - `fun slideMonth(views: List<View>, forward: Boolean, durationMs: Long = 180)` - alpha 0.0->1 + translationX (24dp toward old position, sign by direction) settling to 0; cancels prior per-view animator first.
  - `fun pressScale(view: View, scale: Float = 0.94f)` - sets an `OnTouchListener`: on `ACTION_DOWN` scale to `scale` (100ms), on `ACTION_UP`/`CANCEL` scale back with slight overshoot (140ms); returns `false` so normal click handling is untouched.

- [ ] **Step 1: Write the failing test**

```kotlin
class MotionTest {
    @Test fun `lerp clamps t`() {
        assertEquals(10.0, Motion.lerp(10.0, 20.0, -1f))
        assertEquals(20.0, Motion.lerp(10.0, 20.0, 2f))
    }
    @Test fun `lerp midpoint`() {
        assertEquals(15.0, Motion.lerp(10.0, 20.0, 0.5f), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** - `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.wagetracker.MotionTest"` -> FAIL (Motion unresolved).
- [ ] **Step 3: Implement `Motion.kt` per the signatures above.**
- [ ] **Step 4: Run test to verify it passes** (`BUILD SUCCESSFUL`, 2 tests).
- [ ] **Step 5: Commit** - `git commit -m "Add Motion helpers for polish pass"`.

### Task 2: Main screen animated totals

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (refresh() lines ~150-205)

**Interfaces:**
- Consumes: `Motion.animateCount` (Task 1).
- Produces: MainActivity keeps private property caches of previous Double values for: hero owed, wages amount, wages hours, totalCardTips, totalCashTips, totalAllTips, totalPaidIn.

- [ ] **Step 1: Implement** - `private var firstRefresh = true`; in refresh(), for each money TextView compare the cached value: on first pass render directly + set cache; afterwards `Motion.animateCount(...)` with the exact format lambdas already used in the current hardcoded lines (copy verbatim into the lambda - e.g. `{ x -> "R${"%,.0f".format(java.util.Locale.ENGLISH, x)}"` } for tvTotal*, for tvHeroAmount `"R${"%,.2f".format(java.util.Locale.ENGLISH, x)}"`, tvWagesHours via `MonthSummaryCalculator::formatHours`).
- [ ] **Step 2: Verify** - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** - `git commit -m "Animate main screen totals"`.

### Task 3: Main screen entrance + month-arrow slide

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (onCreate lines ~74-130, refresh() ~150-205)

**Interfaces:**
- Consumes: `Motion.staggerIn`, `Motion.slideMonth` (Task 1).
- Produces: private `entranceDone` flag; arrows pass direction to slideMonth once per tap (consumed flag).

- [ ] **Step 1: Implement** - On onCreate after `refresh()`: `Motion.staggerIn(listOf(binding.heroCard, binding.wagesCard, binding.tvEmptyState, binding.dashboardChin))` (visible views only - tvEmptyState hides when the list is populated, so it naturally stays quiet). Wrap month arrows: `btnPrevMonth` -> direction forward=false, `btnNextMonth` -> forward=true, then refresh(). Inside refresh(), when `!firstRefresh` and direction was set (reset after consume): `Motion.slideMonth(listOf(binding.tvCurrentMonth, binding.heroCard, binding.wagesCard, binding.tvTotalCardTips, binding.tvTotalCashTips, binding.tvTotalAllTips, binding.tvTotalPaidIn), forward)`.
- [ ] **Step 2: Verify** - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** - `git commit -m "Animate main screen entrance and month nav"`.

### Task 4: Dialog transitions (add/edit/paid dialogs)

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (showAddDialog ~230, showEditDialog ~310, showMarkPaidDialog ~440, showWagesPaidDialog ~496)

**Interfaces:**
- Consumes: `com.google.android.material.transition.MaterialSharedAxis` / `MaterialFade` (already on classpath - no added dependency).
- Produces: subtle dialog enter/exit. Implementation via dialog-window `window.setWindowAnimations(R.style.DialogMotion)` OR per-dialog `MaterialSharedAxis` attached to the dialog root view - implementer's pick (builder call), constrained to: runs on API 24+, no new resources beyond one anim/style XML if chosen, and all four dialog call sites use one consistent mechanism.

- [ ] **Step 1: Implement** uniform dialog enter/exit mechanism across all four dialogs above.
- [ ] **Step 2: Verify** - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** - `git commit -m "Add dialog enter/exit transitions"`.

### Task 5: List life (recycler rows)

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/RecordAdapter.kt` (line ~53 region + item callbacks)
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (recyclerView ~85-110)

**Interfaces:**
- Consumes: existing `RecordAdapter.onDeleteClick/onEditClick` + MainActivity's add/deleteWithUndo/edit paths (WageRepository unchanged).
- Produces: rows animate on data changes.

- [ ] **Step 1: Implement** - `binding.recyclerView.itemAnimator = DefaultItemAnimator().apply { addDuration = 250; removeDuration = 200 }`; on fresh add (showAddDialog success path) dispatch `adapter.notifyItemInserted(position)` and run a one-shot alpha/slide-in on that position via `recyclerView.findViewHolderForAdapterPosition(...)?.itemView` (50ms grace window; skip silently when null); on undo re-insertion (deleteWithUndo) apply the same one-shot entrance.
- [ ] **Step 2: Verify** - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** - `git commit -m "Animate record list add/edit/delete"`.

### Task 6: Micro-interactions (chips + press scale)

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (chipHeroStatus line ~126, chipWagesStatus ~196 and paid flip block ~162-193)
- Consumes: `Motion.pressScale` (Task 1).

- [ ] **Step 1: Implement** - `Motion.pressScale()` on the two chips; where chip text/bg changes on paid/unpaid flip in refresh(), wrap the change in a short ViewPropertyAnimator (scale 1 -> 1.06 -> 1 over 120ms total) only when the paid-state string actually changes from the previous render.
- [ ] **Step 2: Verify** - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** - `git commit -m "Add chip press scale and status flip pop"`.

### Task 7: Dashboard / year view / settings accent

**Files:**
- Modify: `app/src/main/java/com/example/wagetracker/DashboardActivity.kt` (lines 35-77)
- Modify: `app/src/main/java/com/example/wagetracker/YearViewActivity.kt` (rebuild 40-65)
- Modify: `app/src/main/java/com/example/wagetracker/MainActivity.kt` (dashboardChin launch ~127)

**Interfaces:**
- Consumes: `Motion.animateCount`, `Motion.staggerIn` (Task 1).
- Produces: lighter-treat screen accents on tier-2 screens.

- [ ] **Step 1: Dashboard** - cache previous values like Task 2; `Motion.animateCount` on tvTotalCardTips/CashTips/PaidIn/Wages + tvAvgTips.
- [ ] **Step 2: Year view** - first rebuild only: `Motion.staggerIn` on adapter rows via a first-run flag (do NOT animate per item during fast scroll).
- [ ] **Step 3: Settings entry** - MainActivity to DashboardActivity and to SettingsActivity launches get a consistent 150ms fade (anim resource pair or `overridePendingTransition`, implementer's pick, one mechanism for both).
- [ ] **Step 4: Verify** - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** - `git commit -m "Animate dashboard, year view and screen fades"`.

### Task 8: Full verification + on-device

**Files:**
- Modify: `app/build.gradle.kts` (versionNum)

- [ ] **Step 1: Bump `app/build.gradle.kts`** -> versionCode 6, versionName "2.4.2".
- [ ] **Step 2: `.\gradlew.bat testDebugUnitTest assembleRelease`** -> BUILD SUCCESSFUL (release build confirms no resource/minify breakage; 10 tests total: prior 8 + Motion 2).
- [ ] **Step 3: Install debug over v2.4.1 on S24 Ultra** (`adb install -r app\build\outputs\apk\debug\app-debug.apk`) - data stays.
- [ ] **Step 4: Manual checklist (user + adb):**
  - 1. Return to MainActivity after dashboard/dialog: entrance stagger does NOT replay.
  - 2. Flip month paid by chip -> "R0" exactly, no negative/garbage text.
  - 3. Tap month arrows 5x rapidly -> no text jitter/layers.
  - 4. Add record -> row animates in; undo delete -> row animates back; swipe delete -> collapse animates.
  - 5. Chip press has scale-down + spring-back.
  - 6. Settings -> Reminders -> toggle ON, set time ~2 min ahead -> notification fires within ~30s of chosen time.
  - 7. Dark mode sweep: animations run, no light-surface flashes.
