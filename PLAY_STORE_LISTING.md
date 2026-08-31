# Play Store submission — mreddyLiftz

Draft listing copy plus the checklist for what's left. Everything code-side that
doesn't require a Play Console account is done; the rest needs you at
play.google.com/console.

## Status

| Piece | State |
|---|---|
| Release signing key (`../keystore/mreddyliftz-upload.jks`) | [x] Generated, git-ignored |
| `keystore.properties` + `signingConfigs.release` wiring | [x] Done, in `app/build.gradle.kts` |
| Signed release AAB | [x] Builds clean: `./gradlew :app:bundleRelease`, verified with `jarsigner -verify` |
| Privacy policy | [x] `PRIVACY.md`, public at the URL below once pushed |
| Store listing copy (this file) | [x] Drafted below — read it, it's your voice not mine |
| Google Play Developer account | [ ] You — $25 one-time, your identity/payment |
| App signed up for Play App Signing | [ ] You, in Console, first upload |
| Screenshots | [ ] See note below |
| Feature graphic (1024x500) | [ ] Not made |
| Data Safety form | [ ] You, in Console — answers are straightforward, see note below |
| Content rating questionnaire | [ ] You, in Console |
| First upload + closed testing track | [ ] You |

## Privacy policy URL

Once this commit is pushed, the policy is publicly readable at:

```
https://github.com/WiredSurya/mreddyliftz/blob/master/PRIVACY.md
```

Paste that into Play Console → App content → Privacy policy. Double-check it loads
in a private/incognito window before submitting — GitHub renders `.md` files
automatically for a public repo, which this one is, but confirm rather than trust.

## Before you upload: the seed data is *your* real routine

Worth deciding deliberately, not by default: `SeedData.kt` ships with a real
routine — specific exercises, your current pull-up level (`band_assisted`), your
actual working weights (10kg standing press, 8kg single-leg RDL), rep targets.
Anyone who installs the app from the Play Store gets that as their starting
point, visible in the app itself before they touch anything. It's not sensitive
in a data-breach sense — it's just workout numbers — but it is *your* numbers,
shipped as the default for strangers. Options if it matters to you:
1. Ship it as-is. It's a reasonable, plausible starting routine for anyone, and
   editing it away is one JSON import.
2. Swap it for a generic/neutral placeholder routine before the first public
   upload, and keep your real one as a JSON file you import locally instead
   (`Settings → Routine data → Import`, using the same schema this file already
   supports).
No code change needed either way unless you pick option 2 — flagging it so it's
a choice, not an oversight.

## Data Safety form — what to answer

Since the app requests no `INTERNET` permission and has no analytics/ads/network
code (see `PRIVACY.md`), the Play Console Data Safety form should be close to the
simplest case:
- **Does your app collect or share any of the required user data types?** No.
- **Is all user data encrypted in transit?** N/A — nothing is transmitted.
- **Do you provide a way for users to request data deletion?** N/A, or: yes,
  uninstalling the app deletes it, since nothing exists outside the device.

Re-check the exact current wording of these questions in Console when you get
there — Google revises this form's exact language periodically, and this note
was written 2026-08-31 against what the app's manifest and dependencies actually
contain, not against a live look at the form.

## Target API level

`compileSdk`/`targetSdk` are both 35 (Android 15) as of this commit. Play
Console enforces a minimum target API level that Google bumps roughly once a
year (usually around August, tracking the previous year's Android release).
Check Console's current requirement at upload time — if it's moved past 35 by
the time you submit, that's a `targetSdk` bump in `app/build.gradle.kts`, not a
bigger change, but do check before assuming 35 still clears the bar.

## Screenshots

None exist yet as polished marketing assets. Real screenshots of the running app
were captured during device testing the night this file was written (calendar,
workout queue, exercise screen mid-session) — they're accurate to the real UI
but include the phone status bar and aren't cropped/framed for a store listing.
Play requires at least 2 phone screenshots (1080x1920 minimum, PNG or JPEG) and
a 1024x500 feature graphic; neither is built here. Ask for these explicitly when
you're ready — the running app makes them cheap to produce (same screencap flow
as the device testing session), but a deliberate ask keeps this from ballooning
into unrequested design work tonight.

## Draft listing copy

**App name** (30 char max): `mreddyLiftz`

**Short description** (80 char max):
> Local-first workout tracker with adaptive progression. No account, no cloud.

**Full description** (4000 char max):

```
mreddyLiftz is a workout and macro tracker built around one idea: your training
data belongs on your phone, not in someone else's cloud. Everything — your
routine, every set you log, every macro you track — stays in a local database on
your device. No account, no sign-in, no network access of any kind. The app
doesn't even request the internet permission.

WHAT IT TRACKS
- A weekly routine of your own exercises, with rest-day macro tracking built in
- Set-by-set logging, with two styles: fixed-rep sets that pre-fill your target,
  and to-failure sets that pre-fill what you did last time so you're always
  trying to beat it
- A calendar view that fills in as you hit your daily goals — water, protein,
  carbs, calories, and workout completion on training days

ADAPTIVE PROGRESSION, NOT GUESSWORK
mreddyLiftz watches your rep history at each exercise and each level or weight
you train it at, and tells you when you're consistently hitting the top of your
rep range — the standard double-progression signal that it's time to move up.
You always confirm the change yourself; nothing switches automatically. Dial
back to an easier level after a rough stretch and the app compares you against
that level's own history, not your best-ever numbers, so the target stays fair.

MACROS, KEPT SIMPLE
Quick-add buttons for water, protein, carbs, and calories, with increments you
can tune to how you actually log. A home screen widget keeps today's macro
totals one glance away.

IMPORT / EXPORT
Your full routine and history can be exported to a plain, human-readable JSON
file, and imported back in — to move to a new phone, to back your own data up
your own way, or to hand-edit your routine directly. The format is documented
in the export file itself.

WHAT THIS APP IS NOT
It's not a social network, it's not ad-supported, and it doesn't sell or share
any data, because there's no server for data to go to. It was built as a
personal tool first and is shared as-is.
```

**Category**: Health & Fitness

**Contact email**: (yours — Play Console requires one)

**Website**: `https://github.com/WiredSurya/mreddyliftz` (optional field, but the
repo is public and can serve as one)
