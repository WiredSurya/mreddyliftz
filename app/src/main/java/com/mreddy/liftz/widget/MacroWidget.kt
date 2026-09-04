package com.mreddy.liftz.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import android.widget.RemoteViews
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mreddy.liftz.MainActivity
import com.mreddy.liftz.R
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/* Widget-local palette — its own small iOS-quick-action-style surface, not a miniature of the
 * app's own screens, so it gets its own (related but distinct) colors. */
private val WidgetBg = Color(0xFF211C17)      // warm dark, matching the app's night surface
private val WidgetOrange = Color(0xFFF97316)  // same action orange as the app
private val WidgetMinusBg = Color(0xFF3A3129)
private val WidgetOnDark = Color(0xFFF3EADC)
private val WidgetMuted = Color(0xFFA79683)
private val WidgetGreen = Color(0xFF5BC45F)   // goal hit

private val SizeCompact = DpSize(250.dp, 110.dp)
private val SizeMedium = DpSize(250.dp, 180.dp)
private val SizeLarge = DpSize(250.dp, 250.dp)

/**
 * Home screen quick-add widget. Reads and writes the SAME Room tables as the app — a tap here
 * shows up in the app immediately (once the redraw lands; see [AdjustMacroAction] for why that
 * redraw is debounced).
 *
 * Responsive: taller placements reveal more of the day (Calories, then workout status) rather
 * than just stretching the same three rows.
 */
class MacroWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SizeCompact, SizeMedium, SizeLarge))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = LiftzRepository(LiftzDatabase.get(context))
        val today = LocalDate.now()
        repo.ensureDailyLog(today)

        // Direct one-shot reads, NOT Flow.first(). Collecting a Room Flow spins up an
        // observer, runs the query, emits and cancels — three times over, on a cold-started
        // process, for data we only need one snapshot of. This is the redraw path, so it runs
        // on every single tap.
        val log = repo.dailyLogOnce(today)
        val goals = repo.goalsOnce()
        val increments = repo.incrementsOnce()

        // Taps whose redraw has not reached the launcher yet, captured BEFORE drawing so the
        // count rendered is exactly the count cleared afterwards.
        val pending = WidgetFeedback.snapshot()

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val showCalories = size.height >= SizeMedium.height
                val showWorkout = size.height >= SizeLarge.height

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WidgetBg)
                        .cornerRadius(20.dp)
                        .padding(12.dp)
                        // Tapping empty space in the widget opens the app. Buttons below have
                        // their own clickable() which Glance gives priority over this one.
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier
                                .size(6.dp)
                                .background(WidgetOrange)
                                .cornerRadius(3.dp)
                        ) {}
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            "Today",
                            style = TextStyle(
                                color = ColorProvider(WidgetOnDark),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    MacroLine("Water", log?.waterMl ?: 0, goals.waterMl, "ml",
                        LiftzRepository.Macro.WATER, increments.waterMl, pending)
                    MacroLine("Protein", log?.proteinG ?: 0, goals.proteinG, "g",
                        LiftzRepository.Macro.PROTEIN, increments.proteinG, pending)
                    MacroLine("Carbs", log?.carbsG ?: 0, goals.carbsG, "g",
                        LiftzRepository.Macro.CARBS, increments.carbsG, pending)
                    MacroLine("Fat", log?.fatG ?: 0, goals.fatG, "g",
                        LiftzRepository.Macro.FAT, increments.fatG, pending)
                    if (showCalories) {
                        if (goals.autoCalcCalories) {
                            // Derived from the macros above, so it is a read-out with no buttons.
                            ReadOnlyLine(
                                "Calories",
                                repo.caloriesFor(log, goals),
                                goals.calories,
                                "kcal"
                            )
                        } else {
                            MacroLine("Calories", log?.calories ?: 0, goals.calories, "kcal",
                                LiftzRepository.Macro.CALORIES, increments.calories, pending)
                        }
                    }
                    if (showWorkout && log?.isWorkoutDay == true) {
                        Spacer(GlanceModifier.height(6.dp))
                        val done = log.workoutCompleted
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                GlanceModifier
                                    .size(8.dp)
                                    .background(if (done) WidgetGreen else WidgetMuted)
                                    .cornerRadius(4.dp)
                            ) {}
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                if (done) "Workout done" else "Workout pending",
                                style = TextStyle(
                                    color = ColorProvider(if (done) WidgetGreen else WidgetMuted),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A macro the widget shows but cannot edit, because the app computes it. */
@Composable
private fun ReadOnlyLine(label: String, current: Int, target: Int, unit: String) {
    val hit = target <= 0 || current >= target
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                label,
                style = TextStyle(color = ColorProvider(WidgetMuted), fontWeight = FontWeight.Medium)
            )
            Text(
                "$current/$target $unit",
                style = TextStyle(
                    color = ColorProvider(if (hit) WidgetGreen else WidgetOnDark),
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text("auto", style = TextStyle(color = ColorProvider(WidgetMuted)))
    }
}

@Composable
private fun MacroLine(
    label: String,
    current: Int,
    target: Int,
    unit: String,
    macro: LiftzRepository.Macro,
    step: Int,
    pending: Map<String, Int> = emptyMap()
) {
    val hit = target <= 0 || current >= target
    val waiting = pending[macro.name] ?: 0
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = TextStyle(
                        color = ColorProvider(WidgetMuted),
                        fontWeight = FontWeight.Medium
                    )
                )
                if (waiting > 0) {
                    Spacer(GlanceModifier.width(6.dp))
                    // One dot per tap the launcher has not caught up with yet, capped so a long
                    // burst does not overflow the row.
                    repeat(waiting.coerceAtMost(4)) {
                        Box(
                            GlanceModifier
                                .size(5.dp)
                                .background(WidgetOrange)
                                .cornerRadius(3.dp)
                        ) {}
                        Spacer(GlanceModifier.width(3.dp))
                    }
                }
            }
            Text(
                "$current/$target $unit",
                style = TextStyle(
                    color = ColorProvider(if (hit) WidgetGreen else WidgetOnDark),
                    fontWeight = FontWeight.Bold
                )
            )
            if (waiting > 0) {
                // Orange dots tracking left and right across the row while the update is in
                // flight. This is a ProgressBar with an AnimationDrawable behind it, embedded as
                // raw RemoteViews because Glance cannot express a custom indeterminate drawable.
                // It is the only way to get real motion into a widget: the launcher runs the
                // animation on its own clock, in its own process, with no redraw from this app.
                AndroidRemoteViews(
                    remoteViews = RemoteViews(
                        LocalContext.current.packageName,
                        R.layout.widget_bouncing_dots
                    ),
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp)
                )
            }
        }
        RoundButton(symbol = "-", background = WidgetMinusBg, macro = macro, delta = -step)
        Spacer(GlanceModifier.width(8.dp))
        RoundButton(symbol = "+", background = WidgetOrange, macro = macro, delta = step)
    }
}

@Composable
private fun RoundButton(symbol: String, background: Color, macro: LiftzRepository.Macro, delta: Int) {
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .background(background)
            .cornerRadius(15.dp) // half the box size -> a circle, iOS-quick-action style
            .clickable(
                actionRunCallback<AdjustMacroAction>(
                    actionParametersOf(
                        AdjustMacroAction.MACRO to macro.name,
                        AdjustMacroAction.DELTA to delta
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

/**
 * Applies the +/- tap straight to Room, then redraws the widget.
 *
 * THE COALESCING HAS TO HAPPEN *INSIDE* onAction, AND THAT IS THE WHOLE TRICK.
 *
 * The obvious optimisation — launch the redraw into a background scope with a delay so a burst
 * of taps collapses into one push — is wrong here, and measurably so. An ActionCallback's process
 * is only guaranteed to be alive until onAction RETURNS. Deferring the redraw into a detached
 * coroutine means the process is frequently torn down before the delay elapses, so the redraw
 * never happens at all. Measured on a OnePlus 6: five rapid taps wrote 1500 to the database
 * correctly, and the widget was still showing 750 seven seconds later, permanently stale until
 * something else forced an update.
 *
 * So the delay is awaited inside onAction instead, which keeps the process alive for it. A ticket
 * counter does the coalescing: every tap takes a ticket, waits briefly, and then only redraws if
 * no newer tap has arrived meanwhile. Superseded taps return without drawing, the last tap of a
 * burst always draws, and no update is ever silently lost.
 *
 * The Room write is never delayed or skipped — it happens immediately on every tap, before any of
 * this — so the data is correct even if a redraw is superseded.
 */
class AdjustMacroAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val macroName = parameters[MACRO] ?: return
        val delta = parameters[DELTA] ?: return

        // Instant, before any database or drawing work: this runs in the app's process, so the
        // tick lands in milliseconds regardless of how long the launcher takes to repaint.
        WidgetFeedback.tick(context)
        WidgetFeedback.tapRegistered(macroName)
        inFlightTaps.incrementAndGet()

        val repo = LiftzRepository(LiftzDatabase.get(context))
        repo.adjustMacro(LocalDate.now(), LiftzRepository.Macro.valueOf(macroName), delta)

        val myTicket = ticket.incrementAndGet()
        // An isolated tap should not pay the coalescing wait at all — only bursts need it. If
        // nothing else is in flight, draw immediately.
        if (inFlightTaps.get() > 1) {
            delay(COALESCE_MS)
            // A newer tap arrived while waiting; it will draw the final state.
            if (ticket.get() != myTicket) {
                inFlightTaps.decrementAndGet()
                return
            }
        }

        val rendered = WidgetFeedback.snapshot()
        MacroWidget().update(context, glanceId)
        rendered.forEach { (macro, count) -> WidgetFeedback.tapsRendered(macro, count) }
        inFlightTaps.decrementAndGet()
    }

    companion object {
        val MACRO = ActionParameters.Key<String>("macro")
        val DELTA = ActionParameters.Key<Int>("delta")

        /**
         * Long enough to swallow a fast double-tap, short enough to feel immediate. Every tap
         * pays this before drawing, so it is deliberately well under the ~100ms that reads as
         * instant.
         */
        private const val COALESCE_MS = 80L
        private val ticket = AtomicInteger(0)
        /** Taps currently between "handled" and "drawn", used to skip the wait for single taps. */
        private val inFlightTaps = AtomicInteger(0)
    }
}

/** Manifest entry point. */
class MacroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MacroWidget()
}
