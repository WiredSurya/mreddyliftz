package com.mreddy.liftz.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class TrainingGoal(val label: String) {
    BUILD_MUSCLE("Build muscle"), GAIN_STRENGTH("Gain strength"), FAT_LOSS("Fat loss")
}

enum class ExperienceLevel(val label: String, val detail: String) {
    BEGINNER("Beginner", "0-1 year"),
    INTERMEDIATE("Intermediate", "1-3 years"),
    ADVANCED("Advanced", "3+ years")
}

enum class TrainingPlace(val label: String, val detail: String) {
    LARGE_GYM("Large gym", "Full fitness club"),
    COMMERCIAL_GYM("Commercial gym", "Basic machines and free weights"),
    GARAGE_GYM("Garage gym", "Barbell, rack, dumbbells, bench"),
    AT_HOME("At home", "Dumbbells, pull-up bar, bench"),
    BODYWEIGHT("Bodyweight only", "No equipment")
}

/** Everything the setup quiz collects. Nulls mean "not answered yet". */
data class TrainingProfile(
    val completed: Boolean = false,
    val goal: TrainingGoal? = null,
    val experience: ExperienceLevel? = null,
    val daysPerWeek: Int? = null,
    val place: TrainingPlace? = null,
    val bodyWeightKg: Double? = null,
    val focus: String? = null
)

private val Context.profileDataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "training_profile")

/**
 * Who the person is and how they train.
 *
 * In DataStore rather than Room for the same reason the theme is: it is not training DATA, it is
 * context about the trainee. It never belongs in an exported workout snapshot, and putting it in
 * Room would mean a schema migration every time the quiz gains a question.
 *
 * Every answer is used for something concrete — see `seedGoalsFrom` in the repository. A quiz
 * that collects answers and changes nothing is a form, not onboarding, and people can tell.
 */
class ProfilePrefs(private val context: Context) {

    val profile: Flow<TrainingProfile> = context.profileDataStore.data.map { p ->
        TrainingProfile(
            completed = p[KEY_COMPLETED] ?: false,
            goal = p[KEY_GOAL]?.let { runCatching { TrainingGoal.valueOf(it) }.getOrNull() },
            experience = p[KEY_EXPERIENCE]?.let {
                runCatching { ExperienceLevel.valueOf(it) }.getOrNull()
            },
            daysPerWeek = p[KEY_DAYS],
            place = p[KEY_PLACE]?.let { runCatching { TrainingPlace.valueOf(it) }.getOrNull() },
            bodyWeightKg = p[KEY_WEIGHT]?.toDoubleOrNull(),
            focus = p[KEY_FOCUS]
        )
    }

    suspend fun once(): TrainingProfile = profile.first()

    suspend fun save(p: TrainingProfile) = context.profileDataStore.edit { store ->
        store[KEY_COMPLETED] = p.completed
        p.goal?.let { store[KEY_GOAL] = it.name }
        p.experience?.let { store[KEY_EXPERIENCE] = it.name }
        p.daysPerWeek?.let { store[KEY_DAYS] = it }
        p.place?.let { store[KEY_PLACE] = it.name }
        p.bodyWeightKg?.let { store[KEY_WEIGHT] = it.toString() }
        p.focus?.let { store[KEY_FOCUS] = it }
    }

    /** Lets someone redo the quiz from Settings without wiping their training data. */
    suspend fun reset() = context.profileDataStore.edit { it[KEY_COMPLETED] = false }

    private companion object {
        val KEY_COMPLETED = booleanPreferencesKey("profile_completed")
        val KEY_GOAL = stringPreferencesKey("profile_goal")
        val KEY_EXPERIENCE = stringPreferencesKey("profile_experience")
        val KEY_DAYS = intPreferencesKey("profile_days_per_week")
        val KEY_PLACE = stringPreferencesKey("profile_place")
        val KEY_WEIGHT = stringPreferencesKey("profile_weight_kg")
        val KEY_FOCUS = stringPreferencesKey("profile_focus")
    }
}
