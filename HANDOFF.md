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
- **Build status — verified 2026-08-30 on the real Linux dev machine (no longer "never compiled"):**
  - `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL, 37 tasks, 0 errors.
  - `./gradlew :app:testDebugUnitTest` -> **23/23 pass, 0 failures, 0 errors**
    (DayCompletionTest 5, ProgressionEngineTest 14, TimeEstimatorTest 4).
  - `python3 tools/engine_sim.py` -> **28/28 passed.**
  The old "nothing has ever been compiled, expect import nits" caveat is obsolete. It compiles and
  the domain layer is green.
- **Android SDK is at `/home/surz/Android/Sdk`, and `local.properties` is NOT in the repo** (it is
  gitignored). A fresh shell therefore needs `export ANDROID_HOME=/home/surz/Android/Sdk` before any
  Gradle command, or every task dies with "SDK location not found". Alternatively write
  `sdk.dir=/home/surz/Android/Sdk` into `local.properties` once.
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

Phase 1 is COMPLETE, plus the Phase 2 widget stretch goal. Nothing has been compiled (no Android SDK
in the authoring sandbox), so the remaining work is "open it, sync, fix compiler nits, run it".

| # | Piece | State | Commit message |
|---|-------|-------|----------------|
| 1 | Gradle scaffold, manifest, resources, adaptive icon | [x] | Scaffold Gradle/Compose Android project... |
| 2 | Room schema: enums, entities, relations, DAOs, database, seed | [x] | Add HANDOFF resume log... (files landed in this commit) |
| 3 | Progression engine + time estimator + day completion | [x] | Domain layer... + LiftzRepository |
| 4 | Repository layer | [x] | Domain layer... + LiftzRepository |
| 5 | Compose theme + navigation skeleton | [x] | Compose theme, navigation skeleton, MainActivity... |
| 6 | Calendar screen (green fill + crown reveal) | [x] | ...and calendar screen with proportional green fill |
| 7 | Workout / day screen (queue, progress, ETA, macros) | [x] | Workout/day screen: Spotify-style exercise queue... |
| 8 | Exercise screen (ring, confetti, haptic, rest timer) | [x] | Exercise screen: record/level header... |
| 9 | Settings screen | [x] | JSON import/export: portable schema... |
| 10 | JSON import/export + template file | [x] | JSON import/export: portable schema... |
| 11 | Unit tests for domain layer + Python rule simulator | [x] | JVM unit tests... (28/28 green in the simulator) |
| 12 | Glance widget (stretch) | [x] | Glance macro widget (Phase 2 stretch) and README |
| 13 | README | [x] | Glance macro widget (Phase 2 stretch) and README |
| 14 | Verification run: JVM unit tests + Python simulator | [x] | Verify domain layer: 23/23 unit tests and 28/28 engine_sim green |

## Next actions (in order)

- [x] Compile it. DONE — `./gradlew :app:assembleDebug` is BUILD SUCCESSFUL with zero errors.
- [x] Run `./gradlew :app:testDebugUnitTest` — DONE, **23/23 green, no failures, no errors**, no
      device needed. `python3 tools/engine_sim.py` independently confirms the same rules, **28/28**.
      Neither needed a fix; nothing was failing.
- [ ] Install on a phone over USB. Do NOT bother with an emulator on an 8GB machine.
- [ ] Set your real weekly split: edit `SeedData.routineDays` / `routineDayExercises`, or import a
      JSON file with your own `routine_days`. Current seed assumes full body Mon/Wed/Fri.
- [ ] Add a Room migration before you ever change an entity on a phone that already holds data.
      Schema is version 1 with no migrations written.
- [ ] Optional polish: per-set weight logging, a post-workout summary screen, Compose UI tests.

## Gotchas a fresh session should know

- Do NOT add Firebase, a backend, or any network call. Explicitly out of scope.
- Room is the ONLY source of truth. The Glance widget writes the same tables; nothing is cached.
- The progression engine (`domain/ProgressionEngine.kt`) has zero Android/Room imports on purpose.
  Keep it that way so it stays unit-testable on the JVM.
- PRs are per `(exercise, level)` pair, never global per exercise. That is load bearing for the
  "regress after missed workouts" behaviour.
- Rep increment is fixed at 1 and must NOT become a setting.
- Calendar denominators (5 workout / 4 rest) come from the routine plan upfront, not from what got
  logged. `daily_logs.isWorkoutDay` is written when the day is first touched.
- Git: a remote already exists (`origin` -> github.com/WiredSurya/mreddyliftz, branch `master`).
  Commit locally as much as you like, but **do not `git push`** — pushing is the human's call, done
  by hand after review. Do not add or re-point remotes either.
