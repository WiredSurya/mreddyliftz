package com.mreddy.liftz.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Immediate acknowledgment for a widget tap, and the per-parameter "working on it" state.
 *
 * WHY THIS IS SPLIT FROM THE DRAWING:
 * A widget's pixels live in the launcher's process. This code runs in the APP's process, in the
 * action callback, the instant the tap is delivered. That difference is the whole point — a
 * vibration fires here in single-digit milliseconds, with no RemoteViews push involved, so it
 * lands long before any visual change possibly could. It is the only truly instant feedback an
 * app widget can give.
 */
object WidgetFeedback {

    /**
     * How many taps for each parameter are still waiting for their redraw to reach the screen.
     *
     * Keyed by macro name. Incremented the moment a tap is handled, and cleared when the redraw
     * that reflects them has actually been pushed — so a non-zero value means precisely "you
     * pressed this and the launcher has not caught up yet", which is exactly the state the widget
     * draws an activity indicator for.
     */
    private val inFlight = ConcurrentHashMap<String, AtomicInteger>()

    fun tapRegistered(macro: String): Int =
        inFlight.getOrPut(macro) { AtomicInteger(0) }.incrementAndGet()

    /** Called once a redraw reflecting [count] taps has been pushed. */
    fun tapsRendered(macro: String, count: Int) {
        inFlight[macro]?.addAndGet(-count)?.let { if (it < 0) inFlight[macro]?.set(0) }
    }

    fun pending(macro: String): Int = inFlight[macro]?.get()?.coerceAtLeast(0) ?: 0

    fun snapshot(): Map<String, Int> =
        inFlight.mapValues { it.value.get().coerceAtLeast(0) }.filterValues { it > 0 }

    /**
     * A short, light tick — the same weight as a keyboard press, not a notification buzz. Fired
     * on every +/- tap so the press is acknowledged even when the number lags behind.
     *
     * Uses VIBRATE, which the app already holds for the set-complete buzz.
     */
    fun tick(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return

        runCatching {
            // EFFECT_TICK is the system's own "light press" primitive where the hardware supports
            // it; the fallback is a deliberately short 12ms pulse rather than a generic buzz.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(12, 60))
            }
        }
    }
}
