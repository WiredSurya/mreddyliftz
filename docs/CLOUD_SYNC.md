# Cloud sync — what is built, and what needs you

## Where this stands

The sync **pipeline is built and working**, with one deliberate limitation: the only backend
right now writes to this device's private storage. Nothing goes off the phone yet.

That is not a stub. `LocalFileBackend` is a real backup target — it saves you from a bad import
or a wrong edit today — and more importantly it means the export → upload → download → restore
path is exercised for real. When a cloud backend is added it is not being proven for the first
time in production; only the four functions in `SyncBackend` are new.

```
data/sync/
  SyncBackend.kt      four functions: displayName, isAvailable, upload, download
  LocalFileBackend.kt writes to app-private storage. Real, working, on-device only.
  SyncManager.kt      all the actual logic: snapshot, restore, schema guard, state
data/prefs/UiPrefs.kt SyncPrefs: last backup/restore, last error, device id
```

## Why snapshots and not per-record sync

This is a single-person app. A whole-database snapshot with last-write-wins is the honest fit:

- The export is a few KB. There is nothing to optimise.
- It reuses `JsonPort`, which is already tested and already the documented portable format.
- It has no merge semantics to get subtly wrong.

True multi-device merging needs an `updatedAt` on every row, a change log, and a per-table
conflict policy. That is a much bigger commitment and would be the wrong thing to rush for one
user with one phone. `SyncManager` is the seam if it is ever wanted; `SyncBackend` would not move.

Restore is **never automatic**. It overwrites, so it is always an explicit action behind a
confirmation that says what will be lost.

## What only you can do

Everything below needs the Google account holder. None of it can be done from this repo.

1. **Create a Firebase project** at console.firebase.google.com.
2. **Register the Android app** with package `com.mreddy.liftz`.
3. **Add the SHA-1 of the signing key**, which OAuth requires. The upload key's SHA-1 is in
   `PLAY_STORE_LISTING.md`; if Play App Signing is enabled, Google's own key SHA-1 must be added
   too or sign-in breaks for Play-installed builds while working fine on sideloads.
4. **Download `google-services.json`** into `app/`. **Git-ignore it** — it identifies your project.
5. **Enable** Google sign-in under Authentication, and create a Firestore or Storage bucket.
6. **Write security rules** so a user can only read and write their own document. Without this,
   the default rules are open and anyone can read everyone's training data.

## What I do once you have that

Roughly a day, and contained:

- Add the `google-services` Gradle plugin and Firebase dependencies. **Note:** that plugin fails
  the build outright if `google-services.json` is missing, which is exactly why it is not added
  yet — it would break the build for you and for every beta tester until the file exists.
- Add `FirebaseBackend : SyncBackend`, roughly 60 lines against the existing interface.
- Add a sign-in row to Settings and gate the backup buttons on it.
- Point `LiftzApp.syncManager` at the new backend when signed in, falling back to local when not.
- Flip `UiPrefs.showOfflineIndicator` to default ON, since the offline banner will then describe
  something real.

## PRIVACY.md must be rewritten first

`PRIVACY.md` currently states the app makes no network calls and does not hold the INTERNET
permission. That is **true today** and it is published against the Play listing.

The moment a cloud backend ships, that document becomes false. It has to be rewritten — covering
what is uploaded, where it is stored, who can access it, how to delete it — and the Play Console
Data Safety form has to be updated in the same release. Shipping sync without doing that is a
false privacy declaration to users and to Google, so treat it as part of the work, not paperwork
afterwards.
