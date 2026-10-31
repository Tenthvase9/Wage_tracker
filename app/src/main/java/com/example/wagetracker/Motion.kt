package com.example.wagetracker

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/**
 * Shared motion helpers for the polish pass. All animations cancel in-flight
 * work on the view before starting; durations are short (<=400ms) and money
 * strings are produced by caller-supplied formatters (Locale.ENGLISH, never parsed).
 */
object Motion {

    /** Pure interpolation with fraction clamped to 0..1. */
    fun lerp(from: Double, to: Double, t: Float): Double {
        val c = t.coerceIn(0f, 1f).toDouble()
        return from + (to - from) * c
    }

    /** Count the text number from `from` to `to`; final frame renders format(to) exactly. */
    fun animateCount(
        view: TextView,
        from: Double,
        to: Double,
        format: (Double) -> String,
        durationMs: Long = 350L
    ) {
        // Display-only interim text; never parsed anywhere (see plan constraints).
        if (from == to) { view.text = format(to); return }
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                view.text = format(
                    if (t >= 1f) to else lerp(from, to, t)
                )
            }
        }
        (view.tag as? android.animation.ValueAnimator)?.cancel()
        view.tag = animator
        animator.start()
    }

    /** Slide-up + fade entrance for the first data display only; callers guard re-runs via tags. */
    fun staggerIn(views: List<View>, baseDelayMs: Int = 40) {
        val visible = views.filter { it.visibility == View.VISIBLE }
        visible.forEachIndexed { i, v -> motionIn(v, i, baseDelayMs) }
    }

    /** Single-view entrance used by staggerIn and list adapters. */
    fun motionIn(view: View, step: Int = 0, baseDelayMs: Int = 40) {
        if (view.tag == "motion_in") return
        val density = view.context.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = 24f * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(baseDelayMs.toLong() * step)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { view.tag = "motion_in" }
            .start()
    }

    /** Quick directional settle for content that changes (e.g. month arrows). */
    fun slideMonth(views: List<View>, forward: Boolean, durationMs: Long = 180L) {
        val density = views.firstOrNull()?.context?.resources?.displayMetrics?.density ?: 1f
        // Cancel previous runs of this same slide (per-view ViewPropertyAnimator).
        // Do NOT cancel ValueAnimators from view.tag: a cross-family cancel here
        // would freeze in-flight count-ups on the old month's text.
        views.forEach { v ->
            v.animate()?.cancel()
        }
        val dx = 24f * density * (if (forward) 1f else -1f)
        views.forEach { v ->
            v.alpha = 0f
            v.translationX = dx
            v.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Press feedback: scale down on touch, spring back on release. */
    fun pressScale(view: View, scale: Float = 0.94f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale)
                    .setDuration(100L).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(140L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1f))
                    .start()
            }
            // Returning false lets the view's own touch handling deliver the click once.
            false
        }
    }
}
