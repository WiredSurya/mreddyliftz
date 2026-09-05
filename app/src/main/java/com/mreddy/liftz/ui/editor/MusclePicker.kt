package com.mreddy.liftz.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mreddy.liftz.domain.BodyRegion
import com.mreddy.liftz.domain.MuscleGroup
import com.mreddy.liftz.ui.common.BodyMap

/**
 * Pick what an exercise trains, with the diagram updating as you tap.
 *
 * The live figure is the point. Muscle names are jargon — plenty of people who train hard could
 * not confidently place "latissimus dorsi" — and watching the body light up as you choose turns
 * a vocabulary test into a recognition task.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusclePicker(
    primary: MuscleGroup?,
    secondary: Set<MuscleGroup>,
    onPrimary: (MuscleGroup?) -> Unit,
    onToggleSecondary: (MuscleGroup) -> Unit
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("What does it work?", fontWeight = FontWeight.SemiBold)
            Text(
                "Optional, but it is what puts this exercise on your weekly body map.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            // Primary at full strength, secondary at the same third-of-a-set weighting the
            // weekly map uses, so this preview matches what the profile will actually show.
            val preview = buildMap {
                primary?.let { put(it, 1f) }
                secondary.forEach { if (it != primary) put(it, 0.34f) }
            }
            BodyMap(
                intensity = preview,
                showLabels = false,
                figureHeight = 150.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Text("Main muscle", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            BodyRegion.entries.forEach { region ->
                val inRegion = MuscleGroup.entries.filter { it.region == region }
                Text(
                    region.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inRegion.forEach { m ->
                        FilterChip(
                            selected = primary == m,
                            // Tapping the selected one clears it: there is no other way back to
                            // "not sure", and forcing a guess would poison the map with a
                            // confident wrong answer.
                            onClick = { onPrimary(if (primary == m) null else m) },
                            label = { Text(m.displayName, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Also works", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "Counts for a third of a set on the map.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MuscleGroup.entries.filter { it != primary }.forEach { m ->
                    FilterChip(
                        selected = m in secondary,
                        onClick = { onToggleSecondary(m) },
                        label = { Text(m.displayName, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}
