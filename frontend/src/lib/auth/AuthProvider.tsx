"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useLocale } from "next-intl";
import { api, call, setAccessToken, setApiLanguage, setRefreshHandler } from "@/lib/api/client";
import { TENANT } from "@/lib/api/config";
import type { Session, User } from "@/lib/api/types";

/**
 * Session handling for customers, barbers and admins.
 *
 * - The access token (15 min) lives in memory only (module variable in the API client).
 * - The refresh token is an httpOnly cookie on /api/auth — JavaScript never sees it.
 *   On load we try one refresh to restore the session; before the access token
 *   expires it is refreshed again in the background.
 * - Concurrent refreshes are de-duplicated (the backend rotates the cookie on every
 *   refresh and treats a second use of an old cookie as theft).
 */

type Status = "loading" | "anonymous" | "authenticated";

type AuthContextValue = {
  status: Status;
  user: User | null;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  /** Apply a session returned by the API (e.g. after a password change). */
  applySession: (s: Session) => void;
  /** Re-read the profile (after editing it). */
  setUser: (u: User) => void;
  /** Forget the session locally (after account deletion). */
  clear: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

let inflight: Promise<Session | null> | null = null;

/**
 * Non-secret hint "this browser had a session" (the refresh token itself is an httpOnly
 * cookie JS can't see). Without it we skip the restore call — no 401 noise for visitors
 * who never logged in, and no wasted refresh rate-limit budget.
 */
const HINT = "auth:session";
function setHint(on: boolean) {
  try {
    if (on) localStorage.setItem(HINT, "1");
    else localStorage.removeItem(HINT);
  } catch {
    /* private mode: we just always try */
  }
}
function hasHint() {
  try {
    return localStorage.getItem(HINT) === "1";
  } catch {
    return true;
  }
}

/** One refresh at a time, app-wide. */
function refreshOnce(): Promise<Session | null> {
  inflight ??= call(() => api.POST("/api/auth/refresh"))
    .then((s) => s as Session)
    .catch(() => null)
    .finally(() => {
      inflight = null;
    });
  return inflight;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const locale = useLocale();
  const [status, setStatus] = useState<Status>("loading");
  const [user, setUserState] = useState<User | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // the UI language goes along with every request
  setApiLanguage(locale);

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    setAccessToken(null);
    setUserState(null);
    setStatus("anonymous");
    setHint(false);
  }, []);

  // the background refresh re-applies the next session through this ref (no self-reference in the callback)
  const applyRef = useRef<(s: Session) => void>(() => undefined);

  const applySession = useCallback(
    (s: Session) => {
      setAccessToken(s.accessToken ?? null);
      setUserState(s.user ?? null);
      setStatus("authenticated");
      setHint(true);
      if (timer.current) clearTimeout(timer.current);
      const expires = s.expiresAt ? new Date(s.expiresAt).getTime() : Date.now() + 14 * 60_000;
      const inMs = Math.max(30_000, expires - Date.now() - 60_000);
      timer.current = setTimeout(async () => {
        const next = await refreshOnce();
        if (next) applyRef.current(next);
        else clear();
      }, inMs);
    },
    [clear],
  );

  useEffect(() => {
    applyRef.current = applySession;
  }, [applySession]);

  // restore the session once on load
  useEffect(() => {
    let alive = true;
    (hasHint() ? refreshOnce() : Promise.resolve(null)).then((s) => {
      if (!alive) return;
      if (s) applySession(s);
      else setStatus("anonymous");
    });
    return () => {
      alive = false;
    };
  }, [applySession]);

  // silent refresh + retry for API calls that hit an expired token
  useEffect(() => {
    setRefreshHandler(async () => {
      const s = await refreshOnce();
      if (s) {
        applySession(s);
        return true;
      }
      clear();
      return false;
    });
    return () => setRefreshHandler(null);
  }, [applySession, clear]);

  const login = useCallback(
    async (email: string, password: string) => {
      const s = (await call(() =>
        api.POST("/api/auth/{tenant}/login", { params: { path: { tenant: TENANT } }, body: { email, password } }),
      )) as Session;
      applySession(s);
      return s.user!;
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    try {
      await call(() => api.POST("/api/auth/logout"));
    } finally {
      clear();
    }
  }, [clear]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, applySession, setUser: setUserState, clear }),
    [status, user, login, logout, applySession, clear],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth outside <AuthProvider>");
  return ctx;
}
