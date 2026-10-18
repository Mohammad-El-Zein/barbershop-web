"use client";

import { useEffect, useState } from "react";
import { api, call } from "@/lib/api/client";
import { TENANT } from "@/lib/api/config";
import type { DayView, Service, StaffMember } from "@/lib/api/types";

const path = { tenant: TENANT };

type Load<T> = { data: T | null; error: unknown; loading: boolean; reload: () => void };

/** Tiny fetch hook: re-runs when `key` changes; ignores stale responses. */
function useLoad<T>(key: string | null, fn: () => Promise<T>): Load<T> {
  const [state, setState] = useState<{ key: string | null; data: T | null; error: unknown }>({
    key: null,
    data: null,
    error: null,
  });
  const [nonce, setNonce] = useState(0);
  useEffect(() => {
    if (key === null) return;
    let alive = true;
    fn().then(
      (data) => alive && setState({ key, data, error: null }),
      (error) => alive && setState({ key, data: null, error }),
    );
    return () => {
      alive = false;
    };
    // fn is recreated every render; the key captures everything it depends on
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, nonce]);
  const fresh = state.key === key;
  return {
    data: fresh ? state.data : null,
    error: fresh ? state.error : null,
    loading: key !== null && !fresh,
    reload: () => setNonce((n) => n + 1),
  };
}

/** Active services in the UI language. */
export function useServices(locale: string) {
  return useLoad(`services:${locale}`, async () =>
    (await call(() => api.GET("/api/public/{tenant}/services", { params: { path } }))) as Service[],
  );
}

/** Active barbers (photo, name, bio). */
export function useStaff(locale: string) {
  return useLoad(`staff:${locale}`, async () =>
    (await call(() => api.GET("/api/public/{tenant}/staff", { params: { path } }))) as StaffMember[],
  );
}

/** One day for all barbers (columns + "any barber"). */
export function useDay(serviceIds: string[], day: string | null, nonce: number) {
  const key = serviceIds.length && day ? `day:${serviceIds.join(",")}:${day}:${nonce}` : null;
  return useLoad(key, async () =>
    (await call(() =>
      api.GET("/api/public/{tenant}/availability", {
        params: { path, query: { services: serviceIds, date: day! } },
      }),
    )) as DayView,
  );
}

/** Days with at least one free slot in [from, to] (max 62 days). */
export function useDays(serviceIds: string[], from: string, to: string, nonce: number) {
  const key = serviceIds.length ? `days:${serviceIds.join(",")}:${from}:${to}:${nonce}` : null;
  return useLoad(key, async () => {
    const r = await call(() =>
      api.GET("/api/public/{tenant}/availability/days", {
        params: { path, query: { services: serviceIds, from, to } },
      }),
    );
    return new Set(r?.days ?? []);
  });
}
