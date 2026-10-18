"use client";

import createClient, { type Middleware } from "openapi-fetch";
import { API_URL } from "./config";
import type { paths } from "./schema";

/**
 * Typed API client (types generated from the backend's OpenAPI spec by
 * `npm run api:generate`). Every request carries the current UI language
 * (localized texts and error messages) and — when logged in — the access token.
 * Cookies are sent along (credentials: include) so /api/auth/refresh can read
 * the httpOnly refresh cookie; JavaScript never sees that cookie.
 */

let accessToken: string | null = null;
let language = "de";

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export function setApiLanguage(lang: string) {
  language = lang;
}

const headers: Middleware = {
  onRequest({ request }) {
    request.headers.set("Accept-Language", language);
    if (accessToken && !request.headers.has("Authorization")) {
      request.headers.set("Authorization", `Bearer ${accessToken}`);
    }
    return request;
  },
};

export const api = createClient<paths>({ baseUrl: API_URL, credentials: "include" });
api.use(headers);

// ── errors ───────────────────────────────────────────────────────────────

/** A failed API call: the backend's RFC 9457 problem detail, unpacked. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    /** Stable backend code, e.g. "booking.slot_taken" (mapped to texts in messages → errors). */
    readonly code: string,
    /** Localized backend message (fallback when the frontend has no text for the code). */
    readonly detail: string,
    /** Per-field validation messages. */
    readonly fieldErrors: Record<string, string> = {},
    /** The whole problem body (e.g. bookingStatus / paymentStatus on 410). */
    readonly body: Record<string, unknown> = {},
  ) {
    super(code);
  }

  static network() {
    return new ApiError(0, "network", "");
  }
}

type Result<T> = { data?: T; error?: unknown; response: Response };

/** Refresh hook installed by the AuthProvider (null = not logged in, no retry). */
let refreshSession: (() => Promise<boolean>) | null = null;

export function setRefreshHandler(fn: (() => Promise<boolean>) | null) {
  refreshSession = fn;
}

/**
 * Runs a typed call and returns its data, or throws an {@link ApiError}.
 * A 401 with an (expired) access token triggers one silent refresh + retry.
 */
export async function call<T>(fn: () => Promise<Result<T>>): Promise<T> {
  let result: Result<T>;
  try {
    result = await fn();
    if (result.response.status === 401 && accessToken && refreshSession && (await refreshSession())) {
      result = await fn();
    }
  } catch {
    throw ApiError.network();
  }
  const { data, error, response } = result;
  if (response.ok) return data as T;
  const body = (error && typeof error === "object" ? error : {}) as Record<string, unknown>;
  throw new ApiError(
    response.status,
    typeof body.code === "string" ? body.code : `http.${response.status}`,
    typeof body.detail === "string" ? body.detail : "",
    (body.errors as Record<string, string>) ?? {},
    body,
  );
}

export function isApiError(e: unknown, code?: string): e is ApiError {
  return e instanceof ApiError && (code === undefined || e.code === code);
}
