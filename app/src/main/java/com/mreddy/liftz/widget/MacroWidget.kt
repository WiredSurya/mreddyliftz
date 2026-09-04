package com.mreddy.liftz.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

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

        val log = repo.observeDailyLog(today).first()
        val goals = repo.observeGoals().first()
        val increments = repo.observeIncrements().first()

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
                        LiftzRepository.Macro.WATER, increments.waterMl)
                    MacroLine("Protein", log?.proteinG ?: 0, goals.proteinG, "g",
                        LiftzRepository.Macro.PROTEIN, increments.proteinG)
                    MacroLine("Carbs", log?.carbsG ?: 0, goals.carbsG, "g",
                        LiftzRepository.Macro.CARBS, increments.carbsG)
                    MacroLine("Fat", log?.fatG ?: 0, goals.fatG, "g",
                        LiftzRepository.Macro.FAT, increments.fatG)
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
                                LiftzRepository.Macro.CALORIES, increments.calories)
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
    step: Int
) {
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
                    // Bigger and bolder than a plain label: the closest a RemoteViews-based
                    // widget can get to "make the changed number noticeable" without a real
                    // animation API (Glance/RemoteViews has none — see the widget lag note in
                    // HANDOFF.md for why an odometer-roll animation isn't possible here at all;
                    // that lives on the in-app macro card instead).
                    color = ColorProvider(if (hit) WidgetGreen else WidgetOnDark),
                    fontWeight = FontWeight.Bold
                )
            )
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
 * Applies the +/- tap straight to Room (always, immediately — that write is never delayed or
 * skipped), then asks Glance to redraw the widget.
 *
 * The REDRAW is debounced, and that is the fix for the "10-30 second lag after a burst of taps"
 * bug: every tap used to trigger its own full [MacroWidget.update] — a full re-provideGlance,
 * i.e. three fresh Room Flow reads plus a full RemoteViews rebuild and push through
 * AppWidgetManager. Firing that once per rapid tap queues up a backlog, and Android (more
 * aggressively under some OEMs' battery-optimization builds, OnePlus/OxygenOS included) rate-
 * limits how often a widget's RemoteViews can actually be pushed to the launcher — so a burst of
 * 10-20 taps could genuinely take that long to fully drain. Coalescing to ONE redraw per burst
 * (the trailing tap, after a short quiet period) fixes the app-side half of that: data is always
 * correct immediately, and the widget converges to the right on-screen number promptly once you
 * stop tapping, instead of visibly lagging behind for the length of the whole burst.
 *
 * The other, non-code half: if this is still slow on a specific phone, check
 * Settings -> Apps -> mreddyLiftz -> Battery -> Unrestricted. OxygenOS in particular is known to
 * throttle background app-widget updates hard under its default battery management.
 */
class AdjustMacroAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val macroName = parameters[MACRO] ?: return
        val delta = parameters[DELTA] ?: return
        val repo = LiftzRepository(LiftzDatabase.get(context))
        repo.adjustMacro(LocalDate.now(), LiftzRepository.Macro.valueOf(macroName), delta)
        scheduleDebouncedRedraw(context, glanceId)
    }

    companion object {
        val MACRO = ActionParameters.Key<String>("macro")
        val DELTA = ActionParameters.Key<Int>("delta")

        private const val DEBOUNCE_MS = 250L
        private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val pendingRedraws = ConcurrentHashMap<GlanceId, Job>()

        private fun scheduleDebouncedRedraw(context: Context, glanceId: GlanceId) {
            pendingRedraws[glanceId]?.cancel()
            pendingRedraws[glanceId] = debounceScope.launch {
                delay(DEBOUNCE_MS)
                MacroWidget().update(context, glanceId)
                pendingRedraws.remove(glanceId)
            }
        }
    }
}

/** Manifest entry point. */
class MacroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MacroWidget()
}
