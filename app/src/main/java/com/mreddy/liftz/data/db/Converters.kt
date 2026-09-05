package com.mreddy.liftz.data.db

import androidx.room.TypeConverter
import com.mreddy.liftz.domain.MuscleGroup

/** Room stores enums as their name string, which keeps the DB dump readable. */
class Converters {
    @TypeConverter fun exerciseTypeTo(v: ExerciseType): String = v.name
    @TypeConverter fun exerciseTypeFrom(v: String): ExerciseType = ExerciseType.valueOf(v)

    @TypeConverter fun setTypeTo(v: SetType): String = v.name
    @TypeConverter fun setTypeFrom(v: String): SetType = SetType.valueOf(v)

    @TypeConverter fun suggestionStatusTo(v: SuggestionStatus): String = v.name
    @TypeConverter fun suggestionStatusFrom(v: String): SuggestionStatus = SuggestionStatus.valueOf(v)

    @TypeConverter fun suggestionKindTo(v: SuggestionKind): String = v.name
    @TypeConverter fun suggestionKindFrom(v: String): SuggestionKind = SuggestionKind.valueOf(v)

    @TypeConverter fun muscleTo(v: MuscleGroup?): String? = v?.name
    @TypeConverter fun muscleFrom(v: String?): MuscleGroup? = MuscleGroup.parse(v)

    /**
     * Comma-separated rather than JSON. It keeps a hand-inspected database dump readable, and
     * MuscleGroup.parseList drops anything it does not recognise, so a stale or misspelt name in
     * an imported file degrades to "one fewer muscle" instead of throwing on read.
     */
    @TypeConverter fun musclesTo(v: List<MuscleGroup>): String = v.joinToString(",") { it.name }
    @TypeConverter fun musclesFrom(v: String?): List<MuscleGroup> = MuscleGroup.parseList(v)
}
