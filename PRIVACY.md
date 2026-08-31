# Privacy Policy — mreddyLiftz

**Last updated: 2026-08-31**

mreddyLiftz is a local-only fitness and macro tracker. This policy is short because
the app does very little with data other than store it on your own phone.

## What the app collects

Nothing. mreddyLiftz does not collect, transmit, sell, or share any personal data,
usage data, or analytics, because it has no way to: the app does not request the
`INTERNET` permission and contains no network, analytics, advertising, or
crash-reporting code of any kind. There are no accounts, no sign-in, and no servers
involved anywhere in the app.

## What the app stores, and where

Everything you enter — your routine, workout logs, set history, and daily macro
totals — is stored in a local database on your device only, using Android's Room
library. It never leaves your phone through the app itself.

The only other permission the app requests is `VIBRATE`, used purely for the
completion buzz when you finish a set. It has nothing to do with data collection.

## Android's own backup, and JSON export/import

Two things are outside the app's own code but worth being explicit about:

- **Android device backup.** Like most apps, mreddyLiftz supports the Android
  operating system's standard app-data backup mechanism. If you have backup to a
  Google account enabled in your phone's own settings, your phone's OS may include
  this app's local data in that backup, the same as it does for most apps. That
  transfer is controlled by your phone's OS-level settings and Google's own backup
  service, not by mreddyLiftz, which has no code involved in it.
- **JSON export/import.** The app has a Settings screen feature to export your
  data to a JSON file, or import one, using Android's standard file picker (SAF).
  This only happens when you explicitly choose to export or import a file, and the
  file goes exactly where you tell the file picker to put it. mreddyLiftz never
  uploads this file anywhere on its own.

## Children's privacy

The app does not knowingly collect any data from anyone, including children,
because it does not collect data from anyone. It has no age gate because it needs
none.

## Changes to this policy

If this app ever changes to add a network feature, an account system, or any data
collection, this file will be updated first, and the date at the top will change.
As of the date above, none of that exists.

## Contact

This is an independently developed, solo hobby project. For questions about this
policy or the app, open an issue at the project's repository:
https://github.com/WiredSurya/mreddyliftz
