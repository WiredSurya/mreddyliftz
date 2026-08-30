package com.mreddy.liftz.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.mreddy.liftz.data.db.LiftzDatabase
import com.mreddy.liftz.data.repo.LiftzRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * PHASE 2 STRETCH GOAL: home screen quick-add widget.
 *
 * Reads and writes the SAME Room tables as the app, so a tap here shows up on the calendar
 * immediately. No separate state, no sync, no backend.
 */
class MacroWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = LiftzRepository(LiftzDatabase.get(context))
        val today = LocalDate.now()
        repo.ensureDailyLog(today)

        val log = repo.observeDailyLog(today).first()
        val goals = repo.observeGoals().first()
        val increments = repo.observeIncrements().first()

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B1F))
                        .padding(10.dp)
                ) {
                    Text(
                        "Today",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFE7EDF2)),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    MacroLine("Water", log?.waterMl ?: 0, goals.waterMl, "ml",
                        LiftzRepository.Macro.WATER, increments.waterMl)
                    MacroLine("Protein", log?.proteinG ?: 0, goals.proteinG, "g",
                        LiftzRepository.Macro.PROTEIN, increments.proteinG)
                    MacroLine("Carbs", log?.carbsG ?: 0, goals.carbsG, "g",
                        LiftzRepository.Macro.CARBS, increments.carbsG)
                }
            }
        }
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
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label  $current/$target $unit",
            style = TextStyle(
                color = ColorProvider(if (hit) Color(0xFF2ECC71) else Color(0xFF8A97A3))
            ),
            modifier = GlanceModifier.defaultWeight()
        )
        WidgetButton("-", macro, -step)
        Spacer(GlanceModifier.width(6.dp))
        WidgetButton("+", macro, step)
    }
}

@Composable
private fun WidgetButton(symbol: String, macro: LiftzRepository.Macro, delta: Int) {
    Text(
        symbol,
        style = TextStyle(
            color = ColorProvider(Color(0xFFE7EDF2)),
            fontWeight = FontWeight.Bold
        ),
        modifier = GlanceModifier
            .background(Color(0xFF1F262B))
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clickable(
                actionRunCallback<AdjustMacroAction>(
                    actionParametersOf(
                        AdjustMacroAction.MACRO to macro.name,
                        AdjustMacroAction.DELTA to delta
                    )
                )
            )
    )
}

/** Applies the +/- tap straight to Room, then asks Glance to redraw. */
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
        MacroWidget().update(context, glanceId)
    }

    companion object {
        val MACRO = ActionParameters.Key<String>("macro")
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}

/** Manifest entry point. */
class MacroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MacroWidget()
}
