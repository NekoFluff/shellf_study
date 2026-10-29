/**
 * Shellf Study history sync: stores the history WaniKani can't give back (study-time segments,
 * study-streak days), plus changes to the app's synced settings, so they follow a learner across
 * devices.
 *
 * Identity is the WaniKani user id, proven once by exchanging a WaniKani API token at /v1/auth for
 * a token signed here. The WaniKani token is used for that one request and never stored or logged.
 * Every other route trusts only the user id inside a verified token — never anything in the
 * request body — which is what keeps one learner's history out of another's.
 *
 * Records are opaque to the server: it stores and returns `payload` without reading it, so the app
 * can evolve the record shape without a backend deploy.
 */

export interface Env {
  DB: D1Database;
  JWT_SECRET: string;
  AUTH_LIMITER: RateLimit;
  /** Overridable for tests; defaults to WaniKani's production API. */
  WANIKANI_API_URL?: string;
}

const RECORD_TYPES = new Set(["study_segment", "activity_day", "setting"]);
const RECORD_ID_PATTERN = /^[A-Za-z0-9-]{1,64}$/;
const WANIKANI_TOKEN_PATTERN = /^[A-Za-z0-9-]{1,64}$/;
const MAX_RECORDS_PER_PUSH = 500;
const MAX_PAYLOAD_BYTES = 2048;
const MAX_BODY_BYTES = 1_500_000;
const MAX_PAGE_SIZE = 500;
const TOKEN_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000;

export interface HistoryRecord {
  type: string;
  recordId: string;
  payload: Record<string, unknown>;
}

interface HistoryRow {
  seq: number;
  type: string;
  record_id: string;
  payload: string;
}

class BadRequest extends Error {}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    try {
      if (req.method === "POST" && url.pathname === "/v1/auth") return await auth(req, env);

      const userId = await verifyToken(req.headers.get("Authorization"), env.JWT_SECRET);
      if (!userId) return json({ error: "unauthorized" }, 401);

      switch (`${req.method} ${url.pathname}`) {
        case "POST /v1/history":
          return await push(req, env, userId);
        case "GET /v1/history":
          return await pull(url, env, userId);
        case "DELETE /v1/me":
          return await deleteMe(env, userId);
      }
      return json({ error: "not_found" }, 404);
    } catch (e) {
      if (e instanceof BadRequest) return json({ error: "bad_request", detail: e.message }, 400);
      // Message only: a stack or the request could carry a WaniKani token.
      console.error("unhandled", e instanceof Error ? e.message : "unknown");
      return json({ error: "internal" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;

/** Swaps a WaniKani API token for one of ours, after WaniKani confirms whose it is. */
async function auth(req: Request, env: Env): Promise<Response> {
  const ip = req.headers.get("CF-Connecting-IP") ?? "unknown";
  const { success } = await env.AUTH_LIMITER.limit({ key: ip });
  if (!success) return json({ error: "rate_limited" }, 429);

  const body = await readJson(req);
  const wkToken = (body as { wkToken?: unknown }).wkToken;
  if (typeof wkToken !== "string" || !WANIKANI_TOKEN_PATTERN.test(wkToken)) {
    throw new BadRequest("wkToken");
  }

  const wk = await fetch(`${env.WANIKANI_API_URL ?? "https://api.wanikani.com/v2"}/user`, {
    headers: { Authorization: `Bearer ${wkToken}`, "Wanikani-Revision": "20170710" },
  });
  if (wk.status === 401) return json({ error: "invalid_wanikani_token" }, 401);
  if (!wk.ok) return json({ error: "wanikani_unavailable" }, 502);

  const userId = ((await wk.json()) as { data?: { id?: unknown } }).data?.id;
  if (typeof userId !== "string" || userId.length === 0) {
    return json({ error: "wanikani_unavailable" }, 502);
  }

  const expiresAtMs = Date.now() + TOKEN_LIFETIME_MS;
  const token = await signToken({ sub: userId, exp: Math.floor(expiresAtMs / 1000) }, env.JWT_SECRET);
  return json({ token, userId, expiresAtMs });
}

/** Stores records, skipping ones already stored, so a retried upload is harmless. */
async function push(req: Request, env: Env, userId: string): Promise<Response> {
  const body = await readJson(req);
  const records = (body as { records?: unknown }).records;
  if (!Array.isArray(records) || records.length > MAX_RECORDS_PER_PUSH) throw new BadRequest("records");
  records.forEach((record, i) => validateRecord(record, i));
  if (records.length === 0) return json({ accepted: 0 });

  const insert = env.DB.prepare(
    `INSERT INTO history (user_id, type, record_id, payload, received_at)
     VALUES (?1, ?2, ?3, ?4, ?5)
     ON CONFLICT (user_id, type, record_id) DO NOTHING`,
  );
  const now = Date.now();
  // One single-row statement per record, sent as one batch: a multi-row VALUES would pass D1's
  // 100-bound-parameter cap, while a batch counts as one query and runs as one transaction.
  await env.DB.batch(
    (records as HistoryRecord[]).map((r) =>
      insert.bind(userId, r.type, r.recordId, JSON.stringify(r.payload), now),
    ),
  );
  return json({ accepted: records.length });
}

/** Records after the client's cursor, in server order, one page at a time. */
async function pull(url: URL, env: Env, userId: string): Promise<Response> {
  const after = parseIntParam(url, "after", 0);
  const limit = parseIntParam(url, "limit", MAX_PAGE_SIZE);
  if (after < 0) throw new BadRequest("after");
  if (limit < 1 || limit > MAX_PAGE_SIZE) throw new BadRequest("limit");

  // One extra row tells us whether another page exists without a COUNT.
  const { results } = await env.DB.prepare(
    `SELECT seq, type, record_id, payload FROM history
     WHERE user_id = ?1 AND seq > ?2
     ORDER BY seq LIMIT ?3`,
  )
    .bind(userId, after, limit + 1)
    .all<HistoryRow>();

  const page = results.slice(0, limit);
  return json({
    records: page.map((r) => ({ type: r.type, recordId: r.record_id, payload: JSON.parse(r.payload) })),
    cursor: page.at(-1)?.seq ?? after,
    hasMore: results.length > limit,
  });
}

async function deleteMe(env: Env, userId: string): Promise<Response> {
  await env.DB.prepare(`DELETE FROM history WHERE user_id = ?1`).bind(userId).run();
  return new Response(null, { status: 204 });
}

function validateRecord(record: unknown, index: number): asserts record is HistoryRecord {
  const r = record as Partial<HistoryRecord> | null;
  if (typeof r !== "object" || r === null) throw new BadRequest(`records[${index}]`);
  if (typeof r.type !== "string" || !RECORD_TYPES.has(r.type)) throw new BadRequest(`records[${index}].type`);
  if (typeof r.recordId !== "string" || !RECORD_ID_PATTERN.test(r.recordId)) {
    throw new BadRequest(`records[${index}].recordId`);
  }
  if (typeof r.payload !== "object" || r.payload === null || Array.isArray(r.payload)) {
    throw new BadRequest(`records[${index}].payload`);
  }
  if (new TextEncoder().encode(JSON.stringify(r.payload)).length > MAX_PAYLOAD_BYTES) {
    throw new BadRequest(`records[${index}].payload`);
  }
}

async function readJson(req: Request): Promise<object> {
  const length = Number(req.headers.get("Content-Length") ?? 0);
  if (length > MAX_BODY_BYTES) throw new BadRequest("body too large");
  const text = await req.text();
  if (text.length > MAX_BODY_BYTES) throw new BadRequest("body too large");
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new BadRequest("body is not JSON");
  }
  if (typeof parsed !== "object" || parsed === null) throw new BadRequest("body is not an object");
  return parsed;
}

function parseIntParam(url: URL, name: string, fallback: number): number {
  const raw = url.searchParams.get(name);
  if (raw === null) return fallback;
  if (!/^\d+$/.test(raw)) throw new BadRequest(name);
  return Number(raw);
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

// --- Tokens: a standard HS256 JWT, signed and checked with Web Crypto. ---

interface TokenClaims {
  sub: string;
  exp: number;
}

const encoder = new TextEncoder();

export async function signToken(claims: TokenClaims, secret: string): Promise<string> {
  const header = base64UrlEncode(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = base64UrlEncode(encoder.encode(JSON.stringify(claims)));
  const signature = await crypto.subtle.sign("HMAC", await hmacKey(secret), encoder.encode(`${header}.${payload}`));
  return `${header}.${payload}.${base64UrlEncode(new Uint8Array(signature))}`;
}

/** The token's user id, or null if it is missing, malformed, forged or expired. */
export async function verifyToken(authorization: string | null, secret: string): Promise<string | null> {
  const token = authorization?.startsWith("Bearer ") ? authorization.slice("Bearer ".length) : null;
  const parts = token?.split(".");
  if (!parts || parts.length !== 3) return null;
  const [header, payload, signature] = parts;

  let signatureBytes: Uint8Array;
  let claims: Partial<TokenClaims>;
  try {
    signatureBytes = base64UrlDecode(signature);
    const headerJson = JSON.parse(new TextDecoder().decode(base64UrlDecode(header)));
    if (headerJson.alg !== "HS256") return null;
    claims = JSON.parse(new TextDecoder().decode(base64UrlDecode(payload)));
  } catch {
    return null;
  }

  // crypto.subtle.verify compares in constant time.
  const valid = await crypto.subtle.verify(
    "HMAC",
    await hmacKey(secret),
    signatureBytes,
    encoder.encode(`${header}.${payload}`),
  );
  if (!valid) return null;
  if (typeof claims.sub !== "string" || typeof claims.exp !== "number") return null;
  if (claims.exp * 1000 <= Date.now()) return null;
  return claims.sub;
}

function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDecode(text: string): Uint8Array {
  const base64 = text.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(text.length / 4) * 4, "=");
  const binary = atob(base64);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}
