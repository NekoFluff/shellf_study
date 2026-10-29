# Shellf Study privacy policy

_Last updated: 29 October 2026_

Shellf Study is an independent, unofficial WaniKani client for Android and iOS. It is not created by,
affiliated with, or endorsed by Tofugu LLC. This policy explains what the app does with your data. In
short: almost everything stays on your device, the app talks to WaniKani on your behalf, and the only
things it can send anywhere else, your study history and study settings, are sent only if you turn
sync on.

There are no ads, no accounts with Shellf Study itself, and nothing is sold or shared for marketing.

## Data that stays on your device

- **Your WaniKani API token.** Stored encrypted, using the Android Keystore or the iOS Keychain. It is
  sent to WaniKani's API with every request and, if you turn sync on, to the sync server about once a
  month to renew its sign-in (see below). It is never stored anywhere but your device.
- **Your WaniKani data**: assignments, review statistics, study materials and your level, plus
  WaniKani's subject content. Downloaded from WaniKani and cached so the app works offline. Your
  account's data is deleted when you log out; the subject content, which is the same for everyone,
  may stay cached.
- **Your study history**: how long you studied, when, and which days you studied (your streak).
  WaniKani doesn't record this, so the app does, on your device. It is kept when you log out and
  sign back in with the same WaniKani account, and deleted when a different account signs in.
- **Your settings, in-progress sessions and notification preferences.**
- **Friends' API tokens**, if you add friends to the leaderboard. Stored encrypted and used only to
  read their progress from WaniKani's API.

## Data sent to WaniKani

The app is a client for WaniKani's API (`api.wanikani.com`). It sends your API token with each request,
reads your WaniKani data, and submits your lessons and reviews. Pronunciation audio and subject images
are downloaded from WaniKani's servers, without your token. WaniKani's handling of that data is
covered by [its own privacy policy](https://www.wanikani.com/privacy).

## Sync across devices (optional, off by default)

If you turn on **Settings → Sync → Sync across devices**, the app keeps your study history and study
settings the same on every device signed in to your WaniKani account. To do that, it sends data to a sync server run by the
developer on [Cloudflare](https://www.cloudflare.com/privacypolicy/) (Cloudflare Workers and D1, stored
in the United States).

**What is stored on the sync server:**

- Your **WaniKani user ID** (a random identifier WaniKani assigns, not your username or email).
- Each **stretch of study time**: when it started, how long it lasted, whether it was lessons or
  reviews, how many items you answered, and your WaniKani level at the time.
- Each **day you studied**, as a date.
- Each **change to a study setting** you've changed from its default — your daily goals, lesson batch
  size, review order, and answering, quiz-screen, subject-page and audio-autoplay options — with the
  time you changed it. Your theme, audio muting, MP3-only audio, Japanese-keyboard and notification
  settings are never sent.

**What is not stored:** your API token. The app sends it to the sync server only to sign in (about once
a month); the server asks WaniKani whose token it is and then discards it. The server issues its own sign-in token, valid for 30 days,
which the app keeps encrypted on your device. Your username, email, reviews, answers and WaniKani data
are never sent to the sync server.

**Retention and deletion:** synced data is kept until you delete it. **Settings → Sync → Delete
synced data** deletes everything stored for your account and turns sync off; nothing on your devices
is affected. Logging out does not delete the synced copy, so you can sign back in on any device and get
it back.

**Operational logs:** like any web service, Cloudflare processes request details such as your IP
address to deliver requests and protect against abuse (for example, the server limits how often an IP
address can sign in). The sync server's own logs record errors only, never request contents or tokens,
and are kept for a few days.

## Crash reports and usage statistics (Android only)

The Android app includes **Firebase Crashlytics** and **Firebase Analytics** from Google. They
collect:

- **Crash reports**: what went wrong in the app's code, your device model, Android version and app
  version, tagged with a random installation ID.
- **Basic usage events**: that the app was installed and opened, and session starts, with device and
  app information and an approximate location derived from your IP address.

The app sends no custom events, no WaniKani data and no identifiers of its own to Firebase. Google
keeps this data under its [Firebase privacy terms](https://firebase.google.com/support/privacy). The
iOS app includes neither.

## Children

Shellf Study is not directed at children under 13 and does not knowingly collect their data.

## Changes

If this policy changes, the new version will be posted here with a new date. Changes are tracked in
this file's history on GitHub.

## Contact

Questions or requests, including deleting synced data you can no longer reach from the app: open an
issue at <https://github.com/NekoFluff/shellf_study/issues>.
