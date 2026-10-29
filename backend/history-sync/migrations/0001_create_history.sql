-- Append-only history the WaniKani API can't give back (study-time segments, study-streak days),
-- keyed by WaniKani user id. The server never reads `payload`: the app owns its shape.
CREATE TABLE history (
  -- Server-assigned and monotonic: the pull cursor. A device that uploads week-old records late
  -- still gets a fresh seq, so other devices' cursors never skip them (a startedAt cursor would).
  seq         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     TEXT    NOT NULL,
  type        TEXT    NOT NULL,
  record_id   TEXT    NOT NULL,
  payload     TEXT    NOT NULL,
  received_at INTEGER NOT NULL,
  UNIQUE (user_id, type, record_id)
);

CREATE INDEX history_user_seq ON history (user_id, seq);
