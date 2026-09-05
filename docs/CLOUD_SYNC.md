# Cloud sync

## Where this stands

**Built and wired.** Optional Google sign-in, with the signed-in account's training history
stored in Firestore and pulled back down on a new device.

There are now three backends behind one interface, picked automatically:

| Backend | When it is used | Survives |
|---|---|---|
| `FirestoreBackend` | Signed in, cloud sync on | A lost phone |
| `FolderBackend` | Signed out (or sync paused) with a folder chosen | A lost phone, via Drive/Dropbox/etc |
| `LocalFileBackend` | Nothing else configured | A bad import, but not an uninstall |

```
data/auth/AuthManager.kt      Google sign-in via Credential Manager; state, uid, sign-out
data/sync/
  SyncBackend.kt              four functions: displayName, isAvailable, upload, download
  FirestoreBackend.kt         users/{uid}/snapshots/latest, one overwritten document
  FolderBackend.kt            SAF folder of your choosing
  LocalFileBackend.kt         app-private fallback
  SyncManager.kt              backend selection, snapshot/restore, schema guard, launch sync
firestore.rules               the deployed access rules, source of truth
```

## Why snapshots and not per-record sync

This is a single-person app. A whole-database snapshot with last-write-wins is the honest fit:

- The export is a few KB. There is nothing to optimise.
- It reuses `JsonPort`, which is already tested and already the documented portable format.
- It has no merge semantics to get subtly wrong.

True multi-device merging needs an `updatedAt` on every row, a change log, and a per-table
conflict policy. That is a much bigger commitment and would be the wrong thing to rush for one
user with one phone. `SyncManager` is the seam if it is ever wanted; `SyncBackend` would not move.

## The one rule that keeps automatic sync safe

`SyncManager.syncOnLaunch()` runs once per app start. It will **only ever restore automatically
into a blank install** — no exercises and no sessions.

That is the entire safety model, and it is worth stating plainly: a snapshot restore is a
wholesale replacement, so if this phone has real history *and* the cloud copy is newer, the two
cannot both survive. Rather than pick, the app stops and asks (`LaunchSync.RemoteIsNewer`),
showing a dialog that says exactly what each choice costs. Silently overwriting a logged session
because another device synced later would be the worst bug this app could ship.

The blank case has no such dilemma — there is nothing to lose — so "install on a new phone, sign
in, everything is there" just works.

## Access rules

`firestore.rules` is the source of truth; the Firebase console is only where it gets applied.

```
match /users/{uid}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
match /{document=**} { allow read, write: if false; }
```

The uid comes from the verified ID token, not from anything the client sends, which is what stops
one account reading another's data. The default-deny at the bottom means a future feature cannot
accidentally ship world-readable because nobody wrote a rule for it.

**Deploy:** Firestore Database → Rules → paste the file → Publish.

## Setting this up in a fresh Firebase project

Only needed if you are rebuilding this from scratch or forking it. The account holder must do all
of it; none can be done from the repo.

1. Create a project at console.firebase.google.com.
2. Register an Android app with package `com.mreddy.liftz`.
3. Add the SHA-1 of **every** signing key — release *and* debug, or sign-in breaks in Android
   Studio while working on sideloads. Get them with:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```
4. Enable **Google** under Authentication → Sign-in method, with a support email.
5. **Publish the OAuth consent screen** (Google Cloud console → APIs & Services → OAuth consent
   screen), or only whitelisted test users can sign in — capped at 100. This is the single most
   commonly missed step and it fails with an unhelpful error.
6. Create a **Firestore** database in production mode. The region is permanent.
7. Download `google-services.json` into `app/` — **after** steps 3 and 4, or it will be missing
   its `oauth_client` block and sign-in cannot work. A complete file is ~1.6 KB and contains a
   `client_type: 3` entry; a broken one is ~700 bytes with no `oauth_client` at all. Check with:
   ```bash
   python3 -c "import json;d=json.load(open('app/google-services.json'));print(d['client'][0].get('oauth_client'))"
   ```
8. Publish `firestore.rules`.

`google-services.json` **is** committed to this repo. That is deliberate and safe: it contains no
secret, the API key in it is restricted by package name and signing fingerprint, and Firestore
access is gated by the rules above rather than by the file's obscurity. Committing it keeps the
build working across machines. A fork will still need its own project, because the fingerprints
will not match.

## Cost

Firebase Spark (free) tier. Auth is unlimited; Firestore allows 50k reads and 20k writes per day.
This app performs roughly one read and one write per launch, so a handful of users is around
three orders of magnitude below the limit. Do not upgrade to Blaze — there is no need.

## Privacy

`PRIVACY.md` was rewritten on 2026-09-05, in the same change that added this. It now states what
is uploaded, where it is stored, who can read it, and how to delete it, and the app has a
**Delete cloud copy** button so that answer is a button rather than an email address.
