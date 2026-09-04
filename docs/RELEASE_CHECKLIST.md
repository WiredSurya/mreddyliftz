# Getting mreddyLiftz onto the Play Store

Everything still standing between the current repo and a live listing, split by who can
actually do it. Written 2026-09-04.

---

## 1. Things only you can do

None of these can be done from this repo. They need the Google account holder.

| # | Task | Notes |
|---|------|-------|
| 1 | **Play Developer account** | One-time $25. Needs your identity and a payment method. Verification can take a day or two, so start this first — it is the long pole. |
| 2 | **Accept the Developer Distribution Agreement** | Part of account setup. |
| 3 | **Create the app** in Play Console | Name, default language, "App", "Free". |
| 4 | **Data Safety form** | Answers in `PLAY_STORE_LISTING.md`. Today the honest answer is "collects no data" — that stops being true the moment cloud sync ships. |
| 5 | **Content rating questionnaire** | Fitness app, no objectionable content. A few minutes. |
| 6 | **Target audience** | Say 18+ or 13+. Declaring under-13 pulls in Families Policy and a lot more review. |
| 7 | **Choose a track** | Start with **Internal testing** (up to 100 testers by email, no review wait) before Closed/Open. |
| 8 | **Opt into Play App Signing** | Recommended. Google holds the real signing key; ours becomes the upload key. It makes losing the keystore recoverable instead of fatal. |

## 2. Things I can do, but need a decision from you first

| # | Task | The decision |
|---|------|--------------|
| 9 | ~~Seed data~~ | **DECIDED: blank slate.** New installs ship empty with an in-app routine builder and the AI hand-off as the two ways in. The old routine is opt-in from Settings as "the example routine". |
| 10 | **Package name is permanent** | `com.mreddy.liftz` can never be changed after first publish. Speak now. |
| 11 | **App name on the listing** | Currently "mreddyLiftz". Fine, but it is what people search for. |
| 12 | **Screenshots** | I can capture and frame real ones from the device in a few minutes. Needs the phone plugged in. |
| 13 | **Feature graphic (1024×500)** | Required. I can design one from the app's own palette and icon. |

## 3. Things I should do before you ship to testers

| # | Task | Why it matters |
|---|------|----------------|
| 14 | ~~Bump `versionCode`~~ | **DONE** — now `2`. Must still increase on every future upload. |
| 15 | ~~`versionName` → `1.0.0`~~ | **DONE.** |
| 16 | **Re-check Play's target API floor** | We are on `targetSdk = 35`. Google raises the minimum roughly every August; confirm 35 still clears it at upload time. |
| 17 | ~~Run from a RELEASE build~~ | **DONE.** Installed and launched clean at v1.0.0. Cold start **610ms vs 1280ms on debug** — 2.1x faster, which is a real chunk of the widget lag. |
| 18 | **Decide on R8/minify** | `isMinifyEnabled = false`. Keeps stack traces readable, costs ~30-40% APK size. Fine to ship, worth a deliberate call. |
| 19 | **Test the v2→v3 migration on a device with real data** | v1→v2 was verified live. v2→v3 has only been verified against SQLite on the desktop via `tools/migration_check.py`. |
| 20 | **A pass on a second device** | Everything visual has been seen on exactly one phone (OnePlus 6, Android 9), which is also old enough that `cornerRadius` silently no-ops. |

## 4. Known gaps, deliberately not fixed

Not blockers, but you should know they exist before testers find them.

- **Widget cold-start lag.** A tap on a widget when the app process is dead has to start that
  process before any of our code runs. That is a platform floor, not a bug we can code around.
  Release builds cold-start faster; that is the main remaining lever.
- **`cornerRadius` is a no-op below API 31.** Widget buttons render square on Android 11 and
  older. Cosmetic, correct on modern phones.
- **No custom user-defined parameters.** Fat was added as a real column; arbitrary user-defined
  ones are a schema rework (see `HANDOFF.md`).
- **No Compose UI tests.** Domain logic has 66 unit tests; the UI has none.
- **Cloud sync is local-only.** See `docs/CLOUD_SYNC.md`.

## 5. Order I would actually do it in

1. Start the **Developer account** today — verification is the slowest step and everything waits on it.
2. Answer the **seed data** question (#9), since it changes what ships.
3. I bump versions, run a release build on-device, capture screenshots and the feature graphic.
4. Upload to **Internal testing**. Not production. Get your testers on that track.
5. Collect a week of real use, fix what surfaces, then promote to Closed or Open testing.
6. Cloud sync after that, in its own release, with `PRIVACY.md` and the Data Safety form
   rewritten in the same change.

## 6. The one thing that must not slip

`PRIVACY.md` is published against the listing and currently states the app makes **no network
calls** and does not hold the `INTERNET` permission. That is true today.

The moment a cloud backend ships, it is false. It has to be rewritten — what is uploaded, where
it lives, who can reach it, how to delete it — and the Data Safety form updated **in the same
release**. Shipping sync without that is a false privacy declaration to both your users and to
Google, and it is the kind of thing that gets an app pulled.
