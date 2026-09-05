# Privacy Policy — mreddyLiftz

**Last updated: 2026-09-05**

mreddyLiftz is a fitness and macro tracker that stores your data on your own phone.
It has one optional feature that sends data off the device: signing in with Google
to sync your training history between your own devices. Nothing else leaves your
phone, and if you never sign in, nothing leaves it at all.

> **What changed on 2026-09-05.** Until this date the app had no network
> capability whatsoever — it did not even hold the `INTERNET` permission, and this
> policy said so. Optional Google sign-in and cloud sync were added, so the app now
> does hold that permission. This section is here rather than in a changelog because
> a privacy policy quietly becoming less true is exactly the thing nobody notices.

## The short version

| | Signed out (the default) | Signed in |
|---|---|---|
| Training data leaves the phone | No | Yes, to your own Google-linked storage |
| Anyone else can read it | No | No |
| Analytics / ads / tracking | None | None |
| App works fully | Yes | Yes |

## If you never sign in

The app does not transmit anything. Your routine, workout logs, set history, and
daily macro totals live in a local database on your device (Android's Room library)
and stay there. There is no analytics, no advertising, no crash reporting, and no
telemetry of any kind in this app — not disabled by default, simply not present.

## If you sign in with Google

Signing in is entirely optional and exists for one purpose: so you can install the
app on a new phone, sign in, and find your history waiting.

**What is sent.** A single snapshot of your app data — the same JSON the export
feature produces: your exercises, routine, logged sets and sessions, daily macro
totals, and your goal settings. It is written to one document belonging to your
account and overwritten each time it syncs. No previous versions are kept.

**What Google receives.** Sign-in is handled by Firebase Authentication, so Google
receives the Google account you choose (its email address, display name, and an
account identifier). Storage is Firebase Cloud Firestore, hosted in Google's
`asia-south1` (Mumbai) region. Google processes this data as the infrastructure
provider, under Google's own privacy policy. The developer of this app has access
to the Firebase project's administrative console.

**Who else can read it.** Nobody. Access rules (`firestore.rules` in the source
repository) permit an account to read and write only documents under its own
account identifier, and deny everything else by default. Another signed-in user
cannot reach your data.

**What is never sent.** Your device's theme, chosen backup folder, and sync
bookkeeping stay local. There is still no analytics, advertising, tracking, or
crash reporting.

## Deleting your data

- **On the phone:** uninstalling the app removes its local database.
- **In the cloud:** Settings → Account → **Delete cloud copy**. This erases the
  stored snapshot immediately and leaves your phone's data untouched.
- **Signing out does not delete anything,** in either place. It stops syncing.

To remove the Google account record itself, ask via the contact address below.

## Permissions

- `INTERNET` — cloud sync only. Used solely when you are signed in and syncing.
- `ACCESS_NETWORK_STATE` — to show the offline indicator. Read-only; grants no
  ability to transmit anything.
- `VIBRATE` — the completion buzz when you finish a set.

## Android's own backup, and JSON export/import

Two things sit outside the app's own code but are worth being explicit about:

- **Android device backup.** Like most apps, mreddyLiftz supports the Android
  operating system's standard app-data backup. If you have backup to a Google
  account enabled in your phone's settings, the OS may include this app's local data
  in that backup, as it does for most apps. That transfer is controlled by your
  phone's OS settings and Google's backup service, not by mreddyLiftz, which has no
  code involved in it.
- **JSON export/import.** You can export your data to a JSON file, or import one,
  through Android's standard file picker. This happens only when you explicitly
  choose to, and the file goes exactly where you tell the picker to put it. The app
  never uploads that file on its own.

## Children's privacy

The app is not directed at children and has no age gate. Signed out it collects
nothing from anyone. Signed in, it requires a Google account, which carries Google's
own age requirements.

## Changes to this policy

If the app changes what it collects or transmits, this file is updated first and the
date at the top changes, as happened on 2026-09-05.

## Contact

This is an independently developed, solo hobby project.

- Email: suryapatrimath@gmail.com
- Repository: https://github.com/WiredSurya/mreddyliftz
