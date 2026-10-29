import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker, { signToken, type Env } from "../src/index";

const BASE = "https://sync.test";
const WK_TOKEN = "11111111-2222-3333-4444-555555555555";
const testEnv = env as unknown as Env;

/** Fakes WaniKani's GET /user: each known token maps to a user id; anything else is a 401. */
function fakeWaniKani(users: Record<string, string>) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
    const url = input instanceof Request ? input.url : String(input);
    if (url !== "https://wanikani.test/v2/user") throw new Error(`unexpected fetch ${url}`);
    const auth = new Headers(init?.headers).get("Authorization") ?? "";
    const userId = users[auth.replace("Bearer ", "")];
    if (!userId) return new Response("{}", { status: 401 });
    return Response.json({ object: "user", data: { id: userId, username: "u", level: 3 } });
  });
}

function call(method: string, path: string, options: { token?: string; body?: unknown; ip?: string } = {}) {
  const headers = new Headers({ "CF-Connecting-IP": options.ip ?? "203.0.113.1" });
  if (options.token) headers.set("Authorization", `Bearer ${options.token}`);
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  const req = new Request(`${BASE}${path}`, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  return worker.fetch(req, testEnv);
}

async function tokenFor(userId: string) {
  return signToken({ sub: userId, exp: Math.floor(Date.now() / 1000) + 3600 }, "test-secret");
}

function segment(id: string, startedAtMs = 1_000) {
  return {
    type: "study_segment",
    recordId: id,
    payload: { v: 1, kind: "REVIEW", startedAtMs, durationMs: 60_000, level: 5, itemsAnswered: 12 },
  };
}

beforeEach(async () => {
  await env.DB.prepare("DELETE FROM history").run();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("POST /v1/auth", () => {
  it("swaps a valid WaniKani token for a sync token carrying the WaniKani user id", async () => {
    fakeWaniKani({ [WK_TOKEN]: "user-a" });

    const res = await call("POST", "/v1/auth", { body: { wkToken: WK_TOKEN } });

    expect(res.status).toBe(200);
    const body = await res.json<{ token: string; userId: string; expiresAtMs: number }>();
    expect(body.userId).toBe("user-a");
    expect(body.expiresAtMs).toBeGreaterThan(Date.now());
    const pull = await call("GET", "/v1/history", { token: body.token });
    expect(pull.status).toBe(200);
  });

  it("rejects a token WaniKani rejects", async () => {
    fakeWaniKani({});

    const res = await call("POST", "/v1/auth", { body: { wkToken: WK_TOKEN } });

    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "invalid_wanikani_token" });
  });

  it("reports WaniKani being down as 502, not as a bad token", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("", { status: 503 }));

    const res = await call("POST", "/v1/auth", { body: { wkToken: WK_TOKEN } });

    expect(res.status).toBe(502);
  });

  it("refuses a malformed token without calling WaniKani", async () => {
    const fetchSpy = fakeWaniKani({});

    const res = await call("POST", "/v1/auth", { body: { wkToken: "bad token\r\nX-Injected: 1" } });

    expect(res.status).toBe(400);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("rate-limits repeated attempts from one address", async () => {
    fakeWaniKani({});
    const statuses: number[] = [];
    for (let i = 0; i < 12; i++) {
      statuses.push((await call("POST", "/v1/auth", { body: { wkToken: WK_TOKEN }, ip: "198.51.100.7" })).status);
    }

    expect(statuses.slice(0, 10).every((s) => s === 401)).toBe(true);
    expect(statuses.at(-1)).toBe(429);
  });
});

describe("authentication on data routes", () => {
  it.each([
    ["missing", undefined],
    ["garbage", "not-a-jwt"],
  ])("rejects a %s token", async (_, token) => {
    const res = await call("GET", "/v1/history", { token });
    expect(res.status).toBe(401);
  });

  it("rejects a token signed with another secret", async () => {
    const forged = await signToken({ sub: "user-a", exp: Math.floor(Date.now() / 1000) + 3600 }, "wrong");
    expect((await call("GET", "/v1/history", { token: forged })).status).toBe(401);
  });

  it("rejects an expired token", async () => {
    const expired = await signToken({ sub: "user-a", exp: Math.floor(Date.now() / 1000) - 1 }, "test-secret");
    expect((await call("GET", "/v1/history", { token: expired })).status).toBe(401);
  });
});

describe("push and pull", () => {
  it("returns pushed records in order with a cursor", async () => {
    const token = await tokenFor("user-a");
    await call("POST", "/v1/history", { token, body: { records: [segment("s1"), segment("s2")] } });

    const res = await call("GET", "/v1/history?after=0", { token });
    const body = await res.json<{ records: unknown[]; cursor: number; hasMore: boolean }>();

    expect(body.records).toEqual([segment("s1"), segment("s2")]);
    expect(body.cursor).toBeGreaterThan(0);
    expect(body.hasMore).toBe(false);
  });

  it("stores a record once however many times it is pushed", async () => {
    const token = await tokenFor("user-a");
    await call("POST", "/v1/history", { token, body: { records: [segment("s1")] } });
    const retry = await call("POST", "/v1/history", { token, body: { records: [segment("s1"), segment("s2")] } });

    expect(retry.status).toBe(200);
    const body = await (await call("GET", "/v1/history", { token })).json<{ records: { recordId: string }[] }>();
    expect(body.records.map((r) => r.recordId)).toEqual(["s1", "s2"]);
  });

  it("treats the same id under different types as different records", async () => {
    const token = await tokenFor("user-a");
    const day = { type: "activity_day", recordId: "2026-10-29", payload: {} };
    const segmentWithDateId = { ...segment("2026-10-29") };
    await call("POST", "/v1/history", { token, body: { records: [day, segmentWithDateId] } });

    const body = await (await call("GET", "/v1/history", { token })).json<{ records: unknown[] }>();
    expect(body.records).toHaveLength(2);
  });

  it("pages through records and only returns what is new after the cursor", async () => {
    const token = await tokenFor("user-a");
    await call("POST", "/v1/history", { token, body: { records: ["a", "b", "c"].map((id) => segment(id)) } });

    const first = await (await call("GET", "/v1/history?after=0&limit=2", { token })).json<Page>();
    expect(first.records.map((r) => r.recordId)).toEqual(["a", "b"]);
    expect(first.hasMore).toBe(true);

    const second = await (await call("GET", `/v1/history?after=${first.cursor}&limit=2`, { token })).json<Page>();
    expect(second.records.map((r) => r.recordId)).toEqual(["c"]);
    expect(second.hasMore).toBe(false);

    await call("POST", "/v1/history", { token, body: { records: [segment("d")] } });
    const third = await (await call("GET", `/v1/history?after=${second.cursor}`, { token })).json<Page>();
    expect(third.records.map((r) => r.recordId)).toEqual(["d"]);
  });

  it("hands a late upload of old records to devices already past them by time", async () => {
    const token = await tokenFor("user-a");
    await call("POST", "/v1/history", { token, body: { records: [segment("new", 9_000_000)] } });
    const seen = await (await call("GET", "/v1/history", { token })).json<Page>();

    // An offline device finally uploads a stretch that started long before the one already seen.
    await call("POST", "/v1/history", { token, body: { records: [segment("old", 1_000)] } });

    const next = await (await call("GET", `/v1/history?after=${seen.cursor}`, { token })).json<Page>();
    expect(next.records.map((r) => r.recordId)).toEqual(["old"]);
  });

  it("returns an empty page, keeping the cursor, when nothing is new", async () => {
    const token = await tokenFor("user-a");

    const body = await (await call("GET", "/v1/history?after=42", { token })).json<Page>();

    expect(body).toEqual({ records: [], cursor: 42, hasMore: false });
  });

  it("never shows one user's records to another", async () => {
    const tokenA = await tokenFor("user-a");
    const tokenB = await tokenFor("user-b");
    await call("POST", "/v1/history", { token: tokenA, body: { records: [segment("s1")] } });
    await call("POST", "/v1/history", { token: tokenB, body: { records: [segment("s1", 5)] } });

    const a = await (await call("GET", "/v1/history", { token: tokenA })).json<Page>();
    const b = await (await call("GET", "/v1/history", { token: tokenB })).json<Page>();

    expect(a.records).toEqual([segment("s1")]);
    expect(b.records).toEqual([segment("s1", 5)]);
  });

  it.each([
    ["an unknown type", { type: "settings", recordId: "x", payload: {} }],
    ["a bad record id", { type: "study_segment", recordId: "../x", payload: {} }],
    ["a non-object payload", { type: "study_segment", recordId: "x", payload: [1] }],
    ["an oversized payload", { type: "study_segment", recordId: "x", payload: { pad: "x".repeat(3000) } }],
  ])("rejects the whole push for %s and stores nothing", async (_, bad) => {
    const token = await tokenFor("user-a");

    const res = await call("POST", "/v1/history", { token, body: { records: [segment("ok"), bad] } });

    expect(res.status).toBe(400);
    const body = await (await call("GET", "/v1/history", { token })).json<Page>();
    expect(body.records).toEqual([]);
  });

  it("rejects more than 500 records in one push", async () => {
    const token = await tokenFor("user-a");
    const records = Array.from({ length: 501 }, (_, i) => segment(`s${i}`));

    expect((await call("POST", "/v1/history", { token, body: { records } })).status).toBe(400);
  });

  it("accepts a full 500-record push in one batch", async () => {
    const token = await tokenFor("user-a");
    const records = Array.from({ length: 500 }, (_, i) => segment(`s${i}`));

    expect((await call("POST", "/v1/history", { token, body: { records } })).status).toBe(200);
    const page = await (await call("GET", "/v1/history?limit=500", { token })).json<Page>();
    expect(page.records).toHaveLength(500);
  });

  it.each(["after=-1", "after=abc", "limit=0", "limit=501"])("rejects a bad query %s", async (query) => {
    const token = await tokenFor("user-a");
    expect((await call("GET", `/v1/history?${query}`, { token })).status).toBe(400);
  });

  it("rejects a body that is not JSON", async () => {
    const token = await tokenFor("user-a");
    const req = new Request(`${BASE}/v1/history`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: "{nope",
    });
    expect((await worker.fetch(req, testEnv)).status).toBe(400);
  });
});

describe("DELETE /v1/me", () => {
  it("deletes only the caller's records", async () => {
    const tokenA = await tokenFor("user-a");
    const tokenB = await tokenFor("user-b");
    await call("POST", "/v1/history", { token: tokenA, body: { records: [segment("s1")] } });
    await call("POST", "/v1/history", { token: tokenB, body: { records: [segment("s1")] } });

    const res = await call("DELETE", "/v1/me", { token: tokenA });

    expect(res.status).toBe(204);
    expect((await (await call("GET", "/v1/history", { token: tokenA })).json<Page>()).records).toEqual([]);
    expect((await (await call("GET", "/v1/history", { token: tokenB })).json<Page>()).records).toHaveLength(1);
  });
});

it("returns 404 for an unknown route", async () => {
  const token = await tokenFor("user-a");
  expect((await call("GET", "/v1/nope", { token })).status).toBe(404);
});

interface Page {
  records: { recordId: string }[];
  cursor: number;
  hasMore: boolean;
}
