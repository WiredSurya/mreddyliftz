# mreddyLiftz — HANDOFF / RESUME LOG

**Purpose:** if the build stops mid-run (usage limit, crash, machine sleep), anyone (you, a friend,
or a fresh AI session with zero context) reads THIS FILE FIRST and picks up exactly where it left off.

Keep this file updated. Every completed piece = one git commit + one line in the Status table below.

---

## How to resume in 60 seconds

1. `cd mreddyLiftz && git log --oneline` — last commit tells you the last finished piece.
2. Read the **Status** table below: `[x]` = done and committed, `[~]` = partially written, `[ ]` = not started.
3. Read **Next actions** at the bottom. Do the top unchecked item.
4. Read `README.md` for what the app is; read this file for where the build stopped.

## Project facts a fresh session needs

- **App:** mreddyLiftz — native Android fitness + macro tracker. Solo hobby project, sideload only, no Play Store.
- **Stack:** Kotlin, Jetpack Compose, Room (single source of truth), Jetpack Glance (Phase 2 widget).
  No Firebase, no backend, no network calls anywhere.
- **Package:** `com.mreddy.liftz` — sources under `app/src/main/java/com/mreddy/liftz/`.
- **Gradle:** Kotlin DSL + version catalog at `gradle/libs.versions.toml`. AGP 8.7.3, Kotlin 2.0.21, KSP, Room 2.6.1.
- **minSdk 26, compileSdk/targetSdk 35, JVM target 17.**
- **Build env caveat:** the sandbox this was written in had **no Android SDK and no Maven access**, so
  NOTHING has been compiled. Everything is written to be correct on first open in Android Studio, but
  expect to fix a few import/API nits on first sync. That is normal, not a sign the design is wrong.
- Target dev machine is Linux with 8GB RAM: `gradle.properties` caps the daemon at 1536m on purpose.

## Architecture map (where things live)

```
app/src/main/java/com/mreddy/liftz/
  LiftzApp.kt            Application class, builds the repository singleton
  MainActivity.kt        Single activity, hosts the Compose nav graph
  data/db/               Room: Enums, Entities, Relations, Daos, Converters, LiftzDatabase
  data/seed/SeedData.kt  The real starting routine, written on first DB create
  data/repo/             LiftzRepository — the one place UI talks to for data
  data/json/             Export/import models + JsonPort (kotlinx.serialization)
  domain/                Pure logic, no Android deps, unit testable:
                           ProgressionEngine.kt  the if-statement adaptive engine
                           TimeEstimator.kt      rolling-window time to completion
                           DayCompletion.kt      calendar green-fill maths (4 or 5 denominator)
  ui/                    theme/, nav/, calendar/, workout/, exercise/, settings/, common/
  widget/                Phase 2 Glance widget
app/src/test/java/...    JVM unit tests for the domain layer
```

## Status

| # | Piece | State | Commit |
|---|-------|-------|--------|
| 1 | Gradle scaffold, manifest, resources, adaptive icon | [x] | `Scaffold Gradle/Compose Android project...` |
| 2 | Room schema: enums, entities, relations, DAOs, database, seed | [x] | in HANDOFF commit |
| 3 | Progression engine + time estimator + day completion (domain) | [x] | `Domain layer ... + LiftzRepository` |
| 4 | Repository layer | [x] | `Domain layer ... + LiftzRepository` |
| 5 | Compose theme + navigation skeleton | [ ] | |
| 6 | Calendar screen | [ ] | |
| 7 | Workout screen | [ ] | |
| 8 | Exercise screen | [ ] | |
| 9 | Settings screen | [ ] | |
| 10 | JSON import/export + template file | [ ] | |
| 11 | Unit tests for domain layer | [ ] | |
| 12 | Glance widget (stretch) | [ ] | |
| 13 | README | [ ] | |

## Next actions

- [ ] (see Status table — do the topmost `[ ]` row)

