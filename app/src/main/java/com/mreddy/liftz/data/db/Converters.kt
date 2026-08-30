package com.mreddy.liftz.data.db

import androidx.room.TypeConverter

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
}
