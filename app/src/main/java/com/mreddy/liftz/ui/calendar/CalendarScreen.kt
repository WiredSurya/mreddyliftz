package com.mreddy.liftz.ui.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.data.repo.LiftzRepository
import com.mreddy.liftz.ui.common.CrownReveal
import com.mreddy.liftz.ui.common.factoryOf
import com.mreddy.liftz.ui.theme.LiftzGreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * HOME SCREEN.
 *
 * Google-Calendar-style month grid showing EVERY day, not just workout days. Each cell fills green
 * from the bottom in proportion to the fraction of that day's goals hit. Denominator is 5 on a
 * workout day and 4 otherwise, and is known upfront from the routine plan.
 */
@Composable
fun CalendarScreen(
    onDayClick: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = factoryOf { CalendarViewModel(LiftzApp.repo()) })
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        /* ---- month header ---- */
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::previousMonth) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = viewModel::nextMonth) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
            }
        }

        /* ---- weekday header ---- */
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        /* ---- the grid ----
         * Leading blanks so the 1st lands under the right weekday column.
         * DayOfWeek.value is 1 = Monday, so the grid starts on Monday.
         */
        val leadingBlanks = days.firstOrNull()?.date?.dayOfWeek?.value?.minus(1) ?: 0
        val cells: List<LiftzRepository.CalendarDay?> = List(leadingBlanks) { null } + days

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(cells) { day ->
                if (day == null) {
                    Spacer(Modifier.aspectRatio(0.85f))
                } else {
                    DayCell(
                        day = day,
                        isToday = day.date == today,
                        onClick = { onDayClick(day.date) }
                    )
                }
            }
        }

        TextButton(
            onClick = viewModel::jumpToToday,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Today") }
    }
}

/**
 * One day cell.
 *
 * Layering, bottom to top:
 *   1. dark cell background
 *   2. the crown (only drawn when the day is 100%) so it literally sits UNDER the fill
 *   3. the green fill, animated to its fraction, growing from the bottom
 *   4. the day number
 *
 * At 100% the fill covers the whole cell and the crown does a radial reveal on top of it.
 */
@Composable
private fun DayCell(
    day: LiftzRepository.CalendarDay,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val fraction by animateFloatAsState(
        targetValue = day.completion.fraction,
        animationSpec = tween(450),
        label = "dayFill"
    )
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.secondary, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        // 3. green fill, anchored to the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceIn(0f, 1f))
                .background(LiftzGreen.copy(alpha = 0.85f))
        )

        // 2 + 4. crown reveal over the full fill, then the date number
        if (day.completion.isCrown) {
            CrownReveal(
                visible = true,
                modifier = Modifier.align(Alignment.Center).size(20.dp)
            )
        }

        Text(
            text = day.date.dayOfMonth.toString(),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp),
            fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (fraction > 0.6f) Color(0xFF06210F) else MaterialTheme.colorScheme.onSurface
        )

        // Tiny dumbbell dot marks planned workout days that are not yet complete.
        if (day.isWorkoutDay && !day.completion.isCrown) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}
