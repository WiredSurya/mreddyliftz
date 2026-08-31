package com.mreddy.liftz.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.material3.Text

/**
 * A number that rolls like a dial when it changes: the old value slides out and the new one
 * slides in from the direction of travel — up when counting up, down when counting down.
 *
 * This is the "make it obvious the number changed" treatment. It lives in the app, not the
 * widget: an app widget is RemoteViews shipped to the launcher's process and has no frame loop,
 * so this kind of animation is not expressible there at all.
 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit(0f, TextUnitType.Unspecified),
    durationMillis: Int = 220,
    format: (Int) -> String = { it.toString() }
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val goingUp = targetState > initialState
            val enter = slideInVertically(tween(durationMillis)) { height ->
                // Entering from below when counting up, from above when counting down.
                if (goingUp) height else -height
            }
            val exit = slideOutVertically(tween(durationMillis)) { height ->
                if (goingUp) -height else height
            }
            enter togetherWith exit
        },
        modifier = modifier,
        label = "rolling-number"
    ) { shown ->
        Text(text = format(shown), style = style, color = color, fontSize = fontSize)
    }
}
