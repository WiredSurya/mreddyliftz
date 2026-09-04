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

- **App:** mreddyLiftz — native Android fitness + macro tracker. A personal app, shared with
  friends. **The Play Store is deliberately NOT the target** (decided 2026-09-04): it ships as a
  signed APK from https://wiredsurya.github.io/mreddyliftz/, and `docs/DISTRIBUTION.md` is the
  process. Do not restart Play Console work without being asked — the store prep that exists is
  dormant on purpose, not unfinished.
- **THE ONE THING THAT CAN BREAK EVERYTHING:** the release keystore at
  `../keystore/mreddyliftz-upload.jks` (git-ignored, one directory above the repo). Android will
  not update an app signed with a different key, so losing it strands every existing install with
  no recovery — and with no store account behind it, there is no Play App Signing safety net.
- **Stack:** Kotlin, Jetpack Compose, Room (single source of truth), Jetpack Glance (widget),
  DataStore (UI prefs only). No Firebase, no backend, and no network calls: the app holds
  ACCESS_NETWORK_STATE (read-only, for the offline indicator) but deliberately does NOT hold
  INTERNET, so it is incapable of sending anything off the device.
- **Package:** `com.mreddy.liftz` — sources under `app/src/main/java/com/mreddy/liftz/`.
- **Gradle:** Kotlin DSL + version catalog at `gradle/libs.versions.toml`. AGP 8.7.3, Kotlin 2.0.21, KSP, Room 2.6.1.
- **minSdk 26, compileSdk/targetSdk 35, JVM target 17.**
- **Build status — verified 2026-08-30 on the real Linux dev machine (no longer "never compiled"):**
  - `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL, 37 tasks, 0 errors.
  - `./gradlew :app:testDebugUnitTest` -> **23/23 pass, 0 failures, 0 errors**
    (DayCompletionTest 5, ProgressionEngineTest 14, TimeEstimatorTest 4).
  - `python3 tools/engine_sim.py` -> **28/28 passed.**
  - A full `--rerun-tasks` clean rebuild is warning-free and green end to end.
  - Totals are now **49/49 Kotlin unit tests**, **45/45 `engine_sim.py`**, and
    **2/2 migrations** verified by `tools/migration_check.py`.
  - The two known warnings (JsonPort opt-in, deprecated `Icons.Filled.Undo`) are fixed, so a forced
    `./gradlew :app:compileDebugKotlin --rerun` now emits **zero `w:` lines**. Keep it that way.
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
  data/prefs/UiPrefs.kt  DataStore: theme mode, offline-indicator toggle (NOT training data)
  data/net/              Connectivity Flow for the offline indicator (read-only, no requests)
  domain/                Pure logic, no Android deps, unit testable:
                           ProgressionEngine.kt  the if-statement adaptive engine
                           TimeEstimator.kt      rolling-window time to completion
                           DayCompletion.kt      calendar green-fill maths (4 or 5 denominator)
                           Calories.kt           Atwater 4/4/9, derived calories
                           Coach.kt              rule-based insights over your own numbers
  ui/                    theme/, nav/, calendar/, workout/, exercise/, summary/,
                         coach/, stats/, settings/, common/
  widget/                Glance macro widget
app/src/test/java/...    JVM unit tests for the domain layer
tools/engine_sim.py      Second implementation of the engine rules (cross-check)
tools/migration_check.py Replays every Room migration against real SQLite, no device needed
```

## Status

Phase 1 and the widget are complete and compile clean. Items 21-26 are the 2026-09-04 redesign
session: none of that has been rendered on a phone yet (see Next actions).

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
| 15 | Clear the two compiler warnings (serialization opt-in, mirrored Undo icon) | [x] | Clear the last two compiler warnings: build is now warning-clean |
| 16 | Room migration scaffold + version/chain guard test | [x] | Room migration scaffold so a future schema change cannot wipe the training log |
| 17 | Domain-layer audit against the spec; per-set rung attribution fix | [x] | Fix per-(exercise,level) tracking for mixed-rung sessions: pull-up could never progress |
| 18 | Post-workout summary screen (optional polish) | [x] | Post-workout summary screen |
| 19 | Real-device verification: fresh install, mixed-rung logging, v1->v2 migration | [x] | (see Next actions — verified live, not a separate commit) |
| 20 | Play Store prep: release signing config, signed AAB, privacy policy, listing copy | [x] | Play Store submission prep: release signing, privacy policy, listing copy |
| 21 | Widget rework: tap-to-open, orange controls, responsive sizing, debounced redraw | [x] | Widget: tap-to-open, orange iOS-style controls... |
| 22 | Swipe navigation, theme toggle, rolling numbers, template access, support email | [x] | Swipe navigation, theme toggle, rolling macro numbers... |
| 23 | Warm paper visual identity (palette + Plus Jakarta Sans) | [x] | Warm paper visual identity... |
| 24 | Fat tracking + auto-calculated calories (schema v3) + desktop migration checker | [x] | Fat tracking and calories that calculate themselves (schema v3) |
| 25 | Progress page + rule-based Coach + bring-your-own-LLM hand-off | [x] | Progress page and Coach: the two missing screens |
| 26 | Offline indicator (banner, pulsing icon, connectivity Flow) | [x] | Offline indicator: Spotify-style banner... |

## Next actions (in order)

- [x] Compile it. DONE — `./gradlew :app:assembleDebug` is BUILD SUCCESSFUL with zero errors.
- [x] Run `./gradlew :app:testDebugUnitTest` — DONE, **23/23 green, no failures, no errors**, no
      device needed. `python3 tools/engine_sim.py` independently confirms the same rules, **28/28**.
      Neither needed a fix; nothing was failing.
- [ ] Install on a phone over USB. Do NOT bother with an emulator on an 8GB machine.
- [ ] Set your real weekly split: edit `SeedData.routineDays` / `routineDayExercises`, or import a
      JSON file with your own `routine_days`. Current seed assumes full body Mon/Wed/Fri.
- [x] Room migration path. DONE — see `data/db/Migrations.kt`. There is still nothing to migrate
      (version 1 is the first release), but the scaffolding is in place, so the next entity change
      is a two-line edit instead of a data-loss incident. To change an entity now:
      bump `Migrations.SCHEMA_VERSION`, append a `MIGRATION_N_N+1` to `Migrations.ALL`, rebuild,
      and commit the newly generated `app/schemas/<db>/<version>.json`. `MigrationsTest` fails the
      build if you bump the version and forget the migration.
- [x] Audit the domain layer against the spec. DONE, and it found a real bug — see the
      per-set-rung note under Gotchas. `DayCompletion` (5 vs 4 denominator) and `TimeEstimator`
      both matched the spec exactly and needed no change.
- [x] Install on a phone and confirm the v1 -> v2 migration runs clean. DONE, for real, on
      hardware (OnePlus 6, Android 9 / API 28), the same night — a second window opened up after
      the initial handoff. This was not a quick smoke test: to actually exercise `MIGRATION_1_2`
      rather than just trust it, the pre-fix commit (`9bdd73b`, schema v1, no `levelKey` column)
      was checked out, built, and installed FRESH; a real pull-up session (all 5 sets, sessionId 1)
      was logged through the UI so `set_logs` held real v1-shaped rows; then `master` (schema v2)
      was installed IN PLACE over it — a genuine upgrade install, not a wipe. Confirmed by pulling
      the on-device `mreddyliftz.db` before and after with `run-as` + `sqlite3`:
        - `PRAGMA user_version` went 1 -> 2 automatically.
        - `set_logs` gained the `levelKey` column via the real `ALTER TABLE`.
        - All 5 pre-migration rows survived with their original ids, reps, and setType untouched —
          zero data loss.
        - The migrated rows' `levelKey` is NULL, exactly as documented, and the app read them back
          without crashing: reopening the Pull-up exercise screen showed "PR at this level: 8 reps"
          and the already-completed 5/5 gold ring, via the session-level fallback in `historyFor()`.
      No `IllegalStateException`, no "migration...not found", no crash to home screen. This was the
      one flagged open gap and it is now closed.
- [x] Post-workout summary screen — DONE, and now also confirmed on a real screen (see above): the
      "Workout summary" card renders correctly at the bottom of the workout queue with the right
      label state ("See how the day is going so far" before completion).
- [ ] Optional polish still on the table: per-set weight logging, Compose UI tests.
      Per-set weight logging was deliberately NOT started: `set_logs` already has a per-set
      `weightKg` column, but making it editable per set means the load stops being constant within
      a session, and `evaluateWeighted` currently filters history on the SESSION's weight. Doing it
      properly means making weight a per-set rung the way `levelKey` now is. That is a real design
      change, not a UI tweak — do not do it as a quick win, or you will reintroduce the exact bug
      that commit 9e62919 fixed.
- [ ] Only tested on one device (OnePlus 6, Android 9). Layout/behaviour on other screen sizes,
      Android versions, and especially light-vs-dark theme switching is unverified — the phone used
      happened to be in light mode; dark mode was never seen live.
- [x] Device verification of the 2026-09-04 session. DONE on the OnePlus 6 (Android 9):
      upgrade-installed over a real schema v2 database holding 6 daily logs and 5 set logs.
      `PRAGMA user_version` went 2 -> 3, fatG/autoCalcCalories were added with correct defaults,
      every row survived, no crash. Verified live: swipe navigation between tabs, the fat row,
      auto-calculated calories (30g protein + 10g fat rendered exactly 210 kcal), the Coach page
      reading real averages, and the Progress page. One real bug was found and fixed only because
      it was seen on hardware — see the lavender-bottom-bar commit.
- [ ] Still unrendered: the WIDGET rework (needs adding to a home screen by hand), dark mode, the
      offline banner (toggle it on in Settings to see it), and the summary screen.
- [ ] **Most of the 2026-09-04 session is now verified, but not all of it.** The device was
      disconnected for all of it. Everything compiles, 49/49 tests and both simulators are green,
      and the migrations are verified against real SQLite — but the entire redesign, all five
      tabs, the widget rework and the offline banner are unrendered. This is the top priority
      before shipping to beta testers.
- [ ] Custom user-defined parameters (beyond the fixed five macros) — asked for, NOT built. Fat
      was added as a real column instead, because it was the specific thing blocking calorie
      auto-calculation. Fully user-defined parameters means a `macro_params` + `macro_logs` pair
      replacing the fixed columns, and reworking `DayCompletion`, the widget and the JSON schema
      around them. Deliberately not started under deadline rather than half-built.
- [ ] Cloud sync + Google sign-in. The offline UI is built and waiting; flip the default in
      `UiPrefs.showOfflineIndicator` when it lands. `PRIVACY.md` currently states the app makes
      no network calls, which is TRUE today — it must be rewritten before any sync ships, and
      before that version reaches Play Console.
- [ ] Play Store submission. See `PLAY_STORE_LISTING.md` for the full checklist — code-side prep
      (signing, listing copy, privacy policy) is done, but account creation, screenshots, the Data
      Safety form, and the actual Console upload all need a human with the Google account.

## Where the design went (2026-09-04)

The app was restyled end to end and four features landed in one session. Read this before
touching the UI or the macro model.

- **Palette is warm paper, not dark slate.** `ui/theme/Theme.kt` is the only place colours are
  defined. Light is the design target (aged paper `#FBF7EF`, orange `#F97316` for action, yellow
  for highlight); dark is a *warm* dark, not blue-grey. Use theme roles
  (`MaterialTheme.colorScheme.*`, `goalGreen()`, `crownGold()`) rather than hardcoding, or things
  break in one of the two themes.
- **Type is Plus Jakarta Sans**, one variable font file at `res/font/plus_jakarta_sans.ttf`,
  SIL OFL, attribution at `licenses/PlusJakartaSans-OFL.txt`. Anthropic's own fonts (Styrene,
  Tiempos) are commercially licensed and cannot be bundled — this is the closest open equivalent.
- **Five pager tabs, not three:** Calendar / Today / Coach / Progress / Profile, all swipeable.
  They are pages of one `HorizontalPager` behind the single `home` route, NOT separate NavHost
  destinations — that is what makes swiping work. Detail screens are still pushed normally.
- **Calories are derived by default.** See the gotcha below. This is the biggest behavioural
  change in the session.
- **There is no AI model in the app and that is intentional.** The Coach screen has two halves: a
  rule-based engine (`domain/Coach.kt`) over your own logged numbers, and an export that bundles
  a written briefing + your JSON so you can hand it to any LLM and import the answer back. A
  bundled model would have meant an API key, a running cost, and history leaving the device. Do
  not "upgrade" this to a built-in model without deciding those three things first.

## Gotchas a fresh session should know

- **Adding fat retroactively un-crowns some past days, and that is correct.** Verified on the
  real device during the v2->v3 upgrade: a logged rest day that hit water/protein/carbs/calories
  was a 4/4 crown under v2. Under v3 the fourth slot is FAT, which is 0 on every pre-migration
  day, so it scores 3/4 and the crown disappears. Nothing was lost or miscounted — the day
  genuinely has no fat recorded — but expect the crown count to drop after upgrading, and do not
  go hunting for a bug. Editing fat on an old day restores its crown.
- **Calories are computed, not logged, unless you turn that off.** `goals.autoCalcCalories`
  defaults ON and makes calories `4*protein + 4*carbs + 9*fat` via `domain/Calories.kt`. Every
  caller must go through `LiftzRepository.caloriesFor()`; reading `daily_logs.calories` directly
  gives you a stale hand-entered number that is ignored in auto mode.
- **The calendar denominators are still 5 and 4, and adding a macro must not change that.** The
  fourth macro slot holds EITHER fat (auto-calc on) OR calories (off), never both. If you add a
  sixth macro, decide deliberately whether it is scored — silently pushing the denominator to 6
  changes the fill of every day already logged. Two tests pin this in both modes.
- **`tools/migration_check.py` replays every migration against real SQLite on the desktop.** Run
  it after ANY entity change, before touching a phone. Room only validates a migration when a
  real device opens a real database, which turns a bad `ALTER TABLE` into a crash-on-launch for
  whoever updates first. This catches it in a second, with no device.
- **The widget cannot animate.** It is RemoteViews shipped to the launcher's process: no frame
  loop, no touch-down callback. The rolling-number effect is in-app only. Widget redraws are
  debounced (250ms) because Android rate-limits how fast RemoteViews can be pushed, which is what
  caused the multi-second lag on rapid taps; the Room write itself is never debounced.


- **Release keystore lives OUTSIDE this repo**, at `../keystore/mreddyliftz-upload.jks` (one
  directory above the repo root), with credentials in `keystore.properties` at the repo root.
  Both are git-ignored — check `.gitignore` before ever touching either, and NEVER commit them;
  this repo is public. `keystore.properties` is easy to regenerate (just new random passwords for
  the same key). The keystore FILE is not: losing it means the Play Store upload key is gone.
  Back it up somewhere outside this project directory (password manager, external drive) — it is
  not part of the git history and never should be.
- Do NOT add Firebase, a backend, or any network call. Explicitly out of scope.
- Room is the ONLY source of truth. The Glance widget writes the same tables; nothing is cached.
- The progression engine (`domain/ProgressionEngine.kt`) has zero Android/Room imports on purpose.
  Keep it that way so it stays unit-testable on the JVM.
- **Never add `fallbackToDestructiveMigration()` to the Room builder.** It is the one-line "fix"
  Android Studio and most StackOverflow answers suggest when a schema change crashes on open, and
  it deletes every row in the database. This DB is the only copy of the training history that
  exists anywhere — no backend, no cloud. A crash on open is recoverable; a wipe is not. Write the
  migration instead. `data/db/Migrations.kt` documents how.
- `app/schemas/**` is committed on purpose. It is the migration audit trail, not build output —
  do not add it to `.gitignore`.
- PRs are per `(exercise, level)` pair, never global per exercise. That is load bearing for the
  "regress after missed workouts" behaviour.
- **A session can mix rungs, so the rung lives on the SET, not just the session.** The seeded
  pull-up does sets 0-1 unassisted at `standard` and sets 2-4 at `band_assisted` in one session.
  Originally every set inherited the session's level, which meant a 4-rep unassisted set counted
  as a band-assisted set: `sessionQualifies` takes the MINIMUM across the sets being judged, so
  pull-up's minimum was always the unassisted set and **pull-up could never progress, ever**. Its
  displayed PR was polluted the same way. Fixed by `set_logs.levelKey` (schema v2) plus
  `SessionSummary.setsAt(level)`. If you add another mixed-rung exercise, set `levelKeyOverride`
  on its planned sets and this all keeps working.
- For a WEIGHTED exercise the load IS the rung: PR and baseline group by `weightKg`, exactly as
  `evaluateWeighted` already refused to count lighter sessions toward a jump. A 10 kg PR is not a
  12 kg PR.
- `tools/engine_sim.py` is a second independent implementation of the same rules. Change one, change
  the other — if they disagree, one of them is wrong. That is the whole point of it.
- Rep increment is fixed at 1 and must NOT become a setting.
- Calendar denominators (5 workout / 4 rest) come from the routine plan upfront, not from what got
  logged. `daily_logs.isWorkoutDay` is written when the day is first touched.
- Git: a remote already exists (`origin` -> github.com/WiredSurya/mreddyliftz, branch `master`).
  Commit locally as much as you like, but **do not `git push`** — pushing is the human's call, done
  by hand after review. Do not add or re-point remotes either.
