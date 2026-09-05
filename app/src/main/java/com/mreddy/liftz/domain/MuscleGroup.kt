package com.mreddy.liftz.domain

/**
 * The muscles an exercise trains.
 *
 * Pure Kotlin with no Android imports, like the rest of `domain/` — this is data about training,
 * not about the screen, and it is what both the per-exercise diagram and the weekly body map on
 * the profile read from.
 *
 * The list is deliberately COARSE. A finer split (upper vs lower pec, long vs short head) would
 * look more serious and be worse: every extra region is another judgement call when someone adds
 * their own exercise, and the whole point is that a person can classify their own movement in two
 * taps without knowing anatomy vocabulary.
 */
enum class MuscleGroup(val displayName: String, val region: BodyRegion, val onBack: Boolean) {
    CHEST("Chest", BodyRegion.UPPER, false),
    SHOULDERS("Shoulders", BodyRegion.UPPER, false),
    BICEPS("Biceps", BodyRegion.UPPER, false),
    TRICEPS("Triceps", BodyRegion.UPPER, true),
    FOREARMS("Forearms", BodyRegion.UPPER, false),
    LATS("Lats", BodyRegion.UPPER, true),
    UPPER_BACK("Upper back", BodyRegion.UPPER, true),
    LOWER_BACK("Lower back", BodyRegion.UPPER, true),
    TRAPS("Traps", BodyRegion.UPPER, true),
    NECK("Neck", BodyRegion.UPPER, false),

    ABS("Abs", BodyRegion.CORE, false),
    OBLIQUES("Obliques", BodyRegion.CORE, false),

    QUADS("Quads", BodyRegion.LOWER, false),
    HAMSTRINGS("Hamstrings", BodyRegion.LOWER, true),
    GLUTES("Glutes", BodyRegion.LOWER, true),
    CALVES("Calves", BodyRegion.LOWER, false),
    ADDUCTORS("Inner thigh", BodyRegion.LOWER, false);

    companion object {
        /** Tolerant parse for JSON import and hand-edited files. Unknown names are dropped. */
        fun parse(raw: String?): MuscleGroup? = raw?.trim()?.uppercase()?.replace(' ', '_')
            ?.let { key -> entries.firstOrNull { it.name == key } }

        fun parseList(raw: String?): List<MuscleGroup> =
            raw?.split(',').orEmpty().mapNotNull { parse(it) }.distinct()
    }
}

enum class BodyRegion(val displayName: String) {
    UPPER("Upper body"), CORE("Core"), LOWER("Lower body")
}

/**
 * How hard each muscle was hit over some window, 0f..1f, for shading the body map.
 *
 * A SET is the unit, not a session: three sessions that each brushed a muscle once should not
 * outrank one session that hammered it, and set count is the closest honest proxy for volume that
 * every exercise type can produce (a bodyweight rung has no weight to multiply by).
 *
 * Secondary muscles count for a third of a set. Not zero, because "my triceps did nothing on
 * press day" is plainly false and would draw a misleading gap; not one, because counting them
 * fully would make every muscle look trained and the map would stop showing gaps at all — which
 * is the only reason to draw it.
 */
object MuscleLoad {

    const val SECONDARY_WEIGHT = 1f / 3f

    /** Raw weighted set counts per muscle. */
    fun tally(
        entries: List<Entry>
    ): Map<MuscleGroup, Float> {
        val out = mutableMapOf<MuscleGroup, Float>()
        for (e in entries) {
            e.primary?.let { out[it] = (out[it] ?: 0f) + e.sets }
            for (m in e.secondary) {
                if (m == e.primary) continue    // never double-count a muscle listed twice
                out[m] = (out[m] ?: 0f) + e.sets * SECONDARY_WEIGHT
            }
        }
        return out
    }

    /**
     * Normalised 0f..1f for shading, scaled against a target rather than against the hardest-hit
     * muscle. Scaling to the max would paint SOMETHING full-intensity on every possible week,
     * including a week with one lazy set, and a map where the colours never mean the same thing
     * twice cannot show you a gap.
     */
    fun intensity(
        entries: List<Entry>,
        setsForFull: Float = 10f
    ): Map<MuscleGroup, Float> =
        tally(entries).mapValues { (_, sets) -> (sets / setsForFull).coerceIn(0f, 1f) }

    data class Entry(
        val primary: MuscleGroup?,
        val secondary: List<MuscleGroup>,
        val sets: Float
    )
}
