# mreddyLiftz

Native Android fitness + macro tracker. Solo hobby project, sideload only, no Play Store, no backend,
no network calls anywhere.

**Stack:** Kotlin, Jetpack Compose, Room (single source of truth), Jetpack Glance (widget),
kotlinx.serialization (import/export). Gradle Kotlin DSL with a version catalog.
minSdk 26, compileSdk/targetSdk 35, JVM target 17.

> **Read `HANDOFF.md` first if you are picking this up mid-build.** It tracks what is done,
> what is stubbed, and what to do next, with a git commit per completed piece.

---

## Open it

1. Android Studio > Open > pick this folder.
2. Let it sync. It will fetch AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1, Glance 1.1.1.
3. Run on a device over USB. **You do not need an emulator** — that matters on an 8GB machine.
   `gradle.properties` caps the Gradle daemon at 1536m for the same reason.
4. `./gradlew :app:testDebugUnitTest` runs the domain unit tests on the JVM, no device needed.

**This code has never been compiled.** It was written in a sandbox with no Android SDK and no Maven
access, so expect a handful of import or API nits on first sync. The logic, schema, and structure are
the parts that took the thinking; those are covered by tests and by `tools/engine_sim.py`.

---

## What is built (Phase 1)

### 1. Calendar screen (home)
Google-Calendar-style month grid showing **every** day, not just workout days. Each cell fills green
from the bottom in proportion to the fraction of that day's goals hit.

- Workout day: denominator **5** (water, protein, carbs, calories, workout)
- Non-workout day: denominator **4**

The denominator comes from the routine plan and is known upfront (`daily_logs.isWorkoutDay` is written
when the day is first touched, seeded from `routine_days`), not computed after the fact.
A 100% day draws a gold crown under the fill with a radial wipe reveal. Tapping a day opens it.

### 2. Day / workout screen
Macro card with +/- steppers on top, then the exercise list as a Spotify-style queue
(upcoming / in-progress / completed), an overall sets progress bar, and an estimated time to
completion built from rolling-window per-exercise time estimates. Rest days show the macro card
and nothing else, which is why the calendar can open every day.

### 3. Exercise screen
Full screen on tap. Contains:

- Traditional record and current progression level (PR is **per (exercise, level) pair**)
- Collapsible form description
- Level ladder as chips: any rung is selectable, including regressing after missed workouts
- Set-by-set logging with two set types:
  - `FIXED_REP` — input pre-fills with the planned goal reps, +/- bumps it by 1
  - `TO_FAILURE` — input pre-fills with what was logged **last time this exercise occurred**
    (same set index, not the previous set). The number to beat is shown on the row.
- Pie-chart ring that fills as sets complete. Full ring on the last set triggers a gold flash,
  confetti, a haptic buzz, then an automatic return to the workout screen.
- One **cumulative** rest timer for the whole exercise (`plannedSets * restSecondsPerSet`),
  not a per-set countdown.

### 4. Settings screen (profile icon in the bottom nav)
Editable per-tap increments (water 250 ml, protein 10 g, carbs 10 g placeholder, calories),
daily goals, per-exercise rolling window, and JSON import/export through the system file picker.
**Rep increment is fixed at 1 and deliberately not editable.**

### 5. Adaptive progression engine
`domain/ProgressionEngine.kt`. Pure if-statements, no ML, no Android or Room imports, fully unit
tested on the JVM.

- **Case A, `bodyweight_progression`** — ordered `levels` ladder plus a `hypertrophy_range`
  (default `[8,12]`). Hit the top of the range on **every set** for `rolling_window` consecutive
  sessions **at the current level** and the engine suggests the next rung. The user confirms;
  nothing switches on its own.
- **Case B, `weighted`** — same trigger, but suggests `current_weight_kg + weight_increment_kg`.
- PRs and baselines are tracked per `(exercise, level)` pair. Changing level in either direction
  moves the comparison target to that level's own history, so the first session at a new rung is
  simply that rung's baseline.
- Core exercises are not progression tracked at all.
- The same rolling window drives time estimates, so old slow sessions do not drag current ones down.

### 6. JSON import / export
`data/json/`. Schema mirrors `mreddyliftz_export_template.json` in the repo root (also shipped in
`app/src/main/assets/`). Top-level keys: `exercises`, `core_exercises`, `goals`, `increments`,
`routine_days`, `daily_logs`, `sessions`, `schema_version`. Unknown keys are ignored, so `_readme`
comment arrays are legal and the format is forward compatible. Import has an OVERWRITE mode
(replace the routine) and a MERGE mode.

The format is written to be readable by a human **or by an AI session with zero prior context** —
hand it one export and it can describe the whole app state, or write a new file to change the routine.

### 7. Icon
Adaptive icon, vector only: `ic_launcher_foreground.xml` (minimal dumbbell with a diagonal shine
streak, drawn inside the 66dp safe zone) over `ic_launcher_background.xml` (dark slate plus a soft
sheen band). Also wired as the monochrome layer for themed icons.

### 8. Glance widget (Phase 2 stretch, done)
`widget/MacroWidget.kt`. Home-screen water/protein/carbs quick-add with +/- buttons that read and
write the **same Room tables**. No separate state, no sync.

---

## Seed routine

Written on first DB create (`data/seed/SeedData.kt`):

| Exercise | Type | Set type | Range | Sets | Start |
|---|---|---|---|---|---|
| Pull-up | bodyweight_progression | mixed | 8-12 | 5 | `band_assisted` (2 unassisted to failure + 3 band assisted) |
| Ring dip | bodyweight_progression | TO_FAILURE | 8-12 | 3 | `negative` |
| Standing DB press | weighted | FIXED_REP | 8-12 | 3 | 10 kg, +2 kg |
| Single-leg RDL | weighted | FIXED_REP | 8-12 | 3 | 8 kg, +2 kg |
| Nordic curl negative | bodyweight_progression | TO_FAILURE | 6-10 | 3 | `partial_rom` |
| Plank / hanging knee raise / side plank | core | not tracked | - | 3/3/2 | - |

**Assumption to change if wrong:** the weekly split was not specified, so the seed puts a full-body
session on Monday, Wednesday and Friday with rest on the other days. Edit `SeedData.routineDays` /
`routineDayExercises`, or just import a JSON file with your own `routine_days`.

---

## Project layout

```
app/src/main/java/com/mreddy/liftz/
  LiftzApp.kt            Application, owns the DB + repository singletons (no DI framework)
  MainActivity.kt        Single activity hosting the Compose nav graph
  data/db/               Room: Enums, Entities, Relations, Daos, Converters, LiftzDatabase,
                         Migrations (schema version + the migration chain)
  data/seed/             The starting routine
  data/repo/             LiftzRepository, the only thing the UI talks to
  data/json/             Portable format + Room<->JSON port
  domain/                Pure, testable: ProgressionEngine, TimeEstimator, DayCompletion
  ui/                    theme, nav, calendar, workout, exercise, summary, settings, common
  widget/                Glance macro widget
app/schemas/             Room's exported schema JSON, one per version. Committed on purpose:
                         it is the migration audit trail, not build output.
app/src/test/            JVM unit tests for the domain layer and the migration chain
tools/engine_sim.py      Second independent implementation of the engine rules. If it and the
                         Kotlin disagree, one of them has a bug.
mreddyliftz_export_template.json
```

---

## What is stubbed or deliberately left out

- **Never compiled.** No Android SDK in the authoring sandbox. First sync may need small fixes.
- **No Room migrations.** Schema version 1 only. Add a migration (or uninstall/reinstall) before
  changing entities on a device that already holds data.
- **Weekly split is a guess.** See the assumption note above.
- **Calories increment** is editable in Settings even though the brief only listed water/protein/carbs.
  The JSON `increments` block still exports only the three specified keys.
- **Carbs** are a placeholder throughout (default 10 g/tap, 250 g goal) since there is no carb history yet.
- Out of scope by request: Firebase or any backend, Play Store packaging, meal photo capture,
  Zepp/sleep/HR/PAI integration.
- No instrumented (device) tests, no Compose UI tests.

## Next steps if you want to keep going

It compiles clean (`./gradlew :app:assembleDebug`, no warnings) and the domain layer is green:
35 JVM unit tests plus 40 checks in `tools/engine_sim.py`. It has also run on real hardware
(OnePlus 6, Android 9): fresh install, a full pull-up session logged through the UI, and the
v1 -> v2 Room migration verified by upgrading an app holding a real pre-migration database in
place and confirming the schema, the data, and the app's own read path all came through intact.
Details in `HANDOFF.md`. What is left:

1. Sharpen the routine: set your real weekly split, or import a JSON file.
2. Optional: per-set weight logging, exercise-level notes history, Compose UI tests.
   Per-set weight is not the quick win it looks like — see the note in `HANDOFF.md`, because a
   naive version reintroduces a progression bug that was already fixed once.
3. Only tested on one device and in light theme. Other screen sizes, Android versions, and dark
   mode are unverified.
## License

This repository is public for portfolio/viewing purposes only. All rights reserved — no permission is granted to use, modify, or distribute this code.
