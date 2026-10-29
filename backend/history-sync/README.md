# history-sync

The Cloudflare Worker + D1 backend behind the app's opt-in "Sync across devices" setting. It stores the
history WaniKani can't give back — study-time segments and study-streak days — and every change to a
synced setting, keyed by WaniKani user id, so they follow a learner across devices. Records are opaque JSON to the server; the app owns their
shape (`shared/.../data/historysync/HistoryRecords.kt`).

| Route               | What it does                                                                 |
| ------------------- | ---------------------------------------------------------------------------- |
| `POST /v1/auth`     | Checks a WaniKani token against `GET /v2/user`, returns a 30-day HS256 token |
| `POST /v1/history`  | Stores up to 500 records; one already stored is skipped                      |
| `GET /v1/history`   | Records after `?after=<cursor>`, oldest first, up to 500 a page              |
| `DELETE /v1/me`     | Deletes the caller's records                                                 |

The WaniKani token is used for the one `/v1/auth` request and never stored or logged.

## Develop

```bash
npm install
npm test              # vitest inside the Workers runtime, against a local D1
npm run typecheck
npm run dev           # local server; put JWT_SECRET=... in an untracked .dev.vars first
```

## Deploy

One-time setup (already done for the `shellf-history` database):

```bash
npx wrangler login
npx wrangler d1 create shellf-history     # put the printed id in wrangler.jsonc
npm run migrate:remote
```

Deployed at `https://sync-shellf-study.nekofluff.workers.dev`, which the app has as
`HISTORY_SYNC_BASE_URL` in `shared/.../network/historysync/HistorySyncApi.kt`. Renaming the account's
workers.dev subdomain changes that URL and breaks sync for installed builds.

Each deploy: `npm run deploy`. `JWT_SECRET` was uploaded with the first deploy (`--secrets-file`) and
persists across deploys. Rotating it signs every device out of sync until its next pass, which
re-authenticates on its own.

A schema change is a new numbered file in `migrations/`, applied with `npm run migrate:remote` before
deploying the code that needs it.
