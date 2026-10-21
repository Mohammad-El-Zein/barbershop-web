"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useLocale } from "next-intl";
import { useRouter } from "@/i18n/navigation";
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
 * - Logout is complete: the backend revokes the refresh token family and clears the cookie;
 *   here the access token, the user, the refresh timer, the session hint and the booking
 *   status link of the session go. Other tabs follow at once (BroadcastChannel): a logout
 *   there ends the session here too, a login with another account there switches this tab
 *   to that account. Views keyed by the user id (StaffShell, RequireAuth) remount, so
 *   nothing of the previous account stays on screen.
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

const refreshCall = () => call(() => api.POST("/api/auth/refresh")).then((s) => s as Session);

/**
 * One refresh at a time per tab. Tabs share the cookie: when another tab rotated it a moment
 * earlier, our request still carried the old one and is refused — the browser has the new
 * cookie by then, so one retry a moment later succeeds.
 */
function refreshOnce(): Promise<Session | null> {
  inflight ??= refreshCall()
    .catch(() => new Promise<void>((r) => setTimeout(r, 300 + Math.random() * 300)).then(refreshCall))
    .catch(() => null)
    .finally(() => {
      inflight = null;
    });
  return inflight;
}

/**
 * Set by a logout started in this tab (useLogout), until the next login: the protected page it
 * was started on (RequireAuth) then doesn't send the visitor to the login — the logout leads
 * to its own target.
 */
let leavingAfterLogout = false;
export function isLeavingAfterLogout() {
  return leavingAfterLogout;
}

/** Tells the other tabs of this browser about a login or logout. */
type TabMessage = { type: "logout" } | { type: "login"; userId: string };
const CHANNEL = "auth";

function tellOtherTabs(msg: TabMessage) {
  try {
    const ch = new BroadcastChannel(CHANNEL);
    ch.postMessage(msg);
    ch.close();
  } catch {
    /* very old browser: the other tabs notice at their next refresh */
  }
}

/** Data that belongs to the session, not to the device (theme, language and consent stay). */
function forgetSessionData() {
  try {
    sessionStorage.removeItem("booking:status"); // the status link of the last booking
  } catch {
    /* storage blocked */
  }
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
  const userIdRef = useRef<string | undefined>(undefined);
  useEffect(() => {
    userIdRef.current = user?.id;
  }, [user]);

  const applySession = useCallback(
    (s: Session) => {
      leavingAfterLogout = false;
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
      if (userIdRef.current && userIdRef.current !== s.user?.id) forgetSessionData();
      applySession(s);
      tellOtherTabs({ type: "login", userId: s.user!.id! });
      return s.user!;
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    try {
      // revokes the refresh token family and clears the cookie; locally we end the session
      // either way (offline: the cookie stays, but without the hint it is not used again)
      await call(() => api.POST("/api/auth/logout"));
    } catch {
      /* see above */
    } finally {
      clear();
      forgetSessionData();
      tellOtherTabs({ type: "logout" });
    }
  }, [clear]);

  // the other tabs: a logout there ends the session here; a login with another account there
  // switches this tab to it (the cookie is shared, so this tab's next refresh would anyway)
  useEffect(() => {
    let ch: BroadcastChannel;
    try {
      ch = new BroadcastChannel(CHANNEL);
    } catch {
      return;
    }
    ch.onmessage = async (e: MessageEvent<TabMessage>) => {
      if (e.data.type === "logout") {
        clear();
        forgetSessionData();
      } else if (e.data.type === "login" && e.data.userId !== userIdRef.current) {
        forgetSessionData();
        const s = await refreshOnce();
        if (s) applySession(s);
        else clear();
      }
    };
    return () => ch.close();
  }, [applySession, clear]);

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

/** Log out, then go to {@code to} (replace: "back" does not return to the signed-in view). */
export function useLogout(to: string) {
  const { logout } = useAuth();
  const router = useRouter();
  return useCallback(async () => {
    leavingAfterLogout = true;
    await logout();
    router.replace(to);
  }, [logout, router, to]);
}
