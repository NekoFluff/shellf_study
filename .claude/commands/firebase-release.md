---
description: Build and release the Android app to Firebase App Distribution with auto-generated release notes, then commit and push the version bump.
argument-hint: [version] [release notes]
---

Release the app to Firebase App Distribution. Arguments (both optional): $ARGUMENTS

- If the first argument looks like a version (e.g. `v1.4`, `1.4`), use it as the new version.
- Anything else in the arguments is release notes text supplied by the user — use it verbatim
  instead of generating notes.

This repo distributes builds through the Firebase App Distribution Gradle plugin, configured in
[app/build.gradle.kts](../../app/build.gradle.kts). Only the `debug` build type has a
`firebaseAppDistribution` block (`groups = "friends"`), so that's the variant to build and upload —
confirm this is still true by checking the file rather than assuming, since it's the kind of thing
that can change.

## 1. Work out the version

Read the current `versionCode` / `versionName` from `defaultConfig` in `app/build.gradle.kts`.

- **An explicit version was given**: use that as the new `versionName`, and set `versionCode` to
  current + 1.
- **No version given**: bump the patch component of `versionName` (e.g. `1.3` → `1.4`) and
  increment `versionCode` by 1. Always bump — don't re-upload the same version number unless
  explicitly asked to (e.g. "re-upload the same build" or "try that release again").

Edit the two lines in `defaultConfig` together so they never drift out of sync.

## 2. Write the release notes

Unless release notes text was supplied in the arguments, generate them:

1. Find the previous release commit: `git log --oneline --grep="^Bump version to" -1` gives you the
   boundary. If there isn't one yet, use the repo's first commit.
2. List what changed since then: `git log <that-commit>..HEAD --oneline`.
3. Turn that into 2–5 short bullet points **for testers, not for developers** — describe what
   changed about the app's behavior, not implementation details, file names, or commit hygiene.
   Skip merge commits and anything purely internal (refactors, test-only changes, CI tweaks) unless
   nothing else is left to say. If genuinely nothing user-visible changed since the last release,
   say so plainly (e.g. "Internal changes only, no visible differences") rather than padding it out.
4. Keep it to plain text — the `--releaseNotes` flag doesn't render markdown, so use line breaks or
   a simple `- ` prefix per bullet, not `**bold**` or headers.

## 3. Build and upload

```bash
./gradlew :app:assembleDebug :app:appDistributionUploadDebug --releaseNotes="<the notes>"
```

Report both links from the task output back to the user (Firebase console link and tester share
link) — don't just say "done".

If the build or upload fails, stop there and report the error. Don't touch git.

## 4. Commit and push the version bump

Once the upload succeeds, always commit and push the version change — no need to ask first:

```bash
git add app/build.gradle.kts
git commit -m "Bump version to <versionName>"
git push
```

Stage only `app/build.gradle.kts`, not `git add -A` — this commit should be exactly the version
bump, nothing else that happens to be sitting in the working tree.

## Notes on judgment calls

- If there are unrelated uncommitted changes in the working tree when you go to commit, don't sweep
  them into the release commit — mention them to the user instead.
- "Release notes" here means the tester-facing changelog text, not a commit message — keep the two
  separate even though they cover similar ground.
