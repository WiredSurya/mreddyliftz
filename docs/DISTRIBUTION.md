# Distributing mreddyLiftz

**Decision, 2026-09-04: this app is not going on the Play Store.**

It was built as a personal training app, and it is shared with friends because there is no reason
not to. That is the whole scope. Sideloading via a link covers it completely, and the Play path
is deliberately not being pursued — see the appendix if that ever changes.

---

## How it is distributed

One link, sent to whoever wants it:

**https://wiredsurya.github.io/mreddyliftz/**

- The page lives in `docs/index.html`, served by GitHub Pages off `master`.
- Its Download button points at `releases/latest/download/`, **not** a pinned version, so cutting
  a new release updates the link automatically. The page never needs editing.
- The page exists because a GitHub releases page on a phone is unusable for a non-technical
  person. It explains the "not from the Play Store" warning *before* they hit it, since that is
  where people stop and give up.

## Shipping an update

```bash
# 1. bump versionCode (must increase) and versionName in app/build.gradle.kts
# 2. build the signed release APK
./gradlew :app:assembleRelease
# 3. cut the release; the landing page picks it up with no further changes
gh release create v1.1.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "mreddyLiftz 1.1.0" --notes "what changed"
```

Friends re-open the same link and tap Download again. There is no auto-update. If that becomes
annoying, [Obtainium](https://github.com/ImranR98/Obtainium) watches a GitHub releases page and
updates automatically — worth mentioning to anyone technical enough to want it.

## THE ONE THING THAT MATTERS: do not lose the keystore

`../keystore/mreddyliftz-upload.jks`, one directory above the repo, git-ignored.

Android refuses to update an app if the new build is signed with a different key. Lose that file
and **every existing install is a dead end** — you and your friends would have to uninstall
(losing all logged data) and start over with a new key. There is no recovery, no reset, and no
support line, because there is no store account behind it.

Back it up somewhere outside this project directory. A password manager attachment or an external
drive. Today.

This is now the single most important operational fact about the project, and it got *more*
important by skipping the Play Store, not less — Play App Signing would have been the safety net.

## Things that no longer apply

Skipping the store removes all of this, permanently:

- The $25 developer account, and its identity verification
- The Data Safety form and content rating questionnaire
- Google's minimum `targetSdk` floor, which rises roughly every August. Keeping `targetSdk` current
  is still good for runtime behaviour, but nothing will now *reject* the app for lagging.
- The rule that new personal accounts must run a closed test with 12 testers for 14 days
- Store review, listing copy, and the 1024x500 feature graphic

`PLAY_STORE_LISTING.md` is kept only for the signing-key details it records. Its listing copy and
console checklist are dormant.

## Still worth doing, store or no store

- **Tell people to set a backup folder.** Settings → Choose a backup folder. Sideloaded apps have
  no cloud restore behind them, so an uninstall or a lost phone is the end of that history unless
  they picked a folder that syncs.
- **Keep `PRIVACY.md` honest.** Nobody is auditing it now, but it is still a true statement to
  people trusting you with their training log, and it stays true only while the app makes no
  network calls.
- **Screenshots** in `docs/screenshots/` are still useful for the landing page.

---

## Appendix: if the Play Store ever comes back up

The prep is done and still valid — signing config, a signed AAB that builds clean, `PRIVACY.md`
published at a public URL, and draft listing copy in `PLAY_STORE_LISTING.md`. What would be left:

1. Developer account, $25, identity verification (the slow part)
2. Data Safety form and content rating
3. A 1024x500 feature graphic, which does not exist
4. Recapture `docs/screenshots/04_progress.png` — it is currently the empty state
5. Re-check the `targetSdk` floor, which will have moved
6. Opt into Play App Signing, which makes the keystore recoverable

And the migration gotcha: a Play build signed by Google has a **different signature** than these
sideloaded ones, so existing users would have to uninstall (losing data) before installing from
the store. Anyone who set a backup folder can restore; anyone who did not, cannot.
