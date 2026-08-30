package com.mreddy.liftz.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** An exercise together with its ladder and its per-set plan. One query, no N+1. */
data class ExerciseWithPlan(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val levels: List<LevelEntity>,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val plannedSets: List<PlannedSetEntity>
) {
    val orderedLevels: List<LevelEntity> get() = levels.sortedBy { it.orderIndex }
    val orderedPlannedSets: List<PlannedSetEntity> get() = plannedSets.sortedBy { it.setIndex }

    val currentLevel: LevelEntity?
        get() = orderedLevels.firstOrNull { it.levelKey == exercise.currentLevelKey }
}

/** A logged session with the sets that belong to it. */
data class SessionWithSets(
    @Embedded val session: ExerciseSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SetLogEntity>
) {
    val orderedSets: List<SetLogEntity> get() = sets.sortedBy { it.setIndex }
    val topReps: Int get() = sets.maxOfOrNull { it.reps } ?: 0
    val totalReps: Int get() = sets.sumOf { it.reps }
}
