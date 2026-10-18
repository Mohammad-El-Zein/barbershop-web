"use client";

import { useEffect, useState } from "react";
import { api, call } from "./api/client";
import { TENANT } from "./api/config";
import type { Shop } from "./api/types";

export { DEFAULT_RULES, rulesOf } from "./rules";

let cached: Promise<Shop> | null = null;

/** Shop info + customer-facing booking rules (deposit, cutoffs, loyalty), loaded once per page load. */
export function useShop(): Shop | null {
  const [shop, setShop] = useState<Shop | null>(null);
  useEffect(() => {
    cached ??= call(() => api.GET("/api/public/{tenant}", { params: { path: { tenant: TENANT } } })).catch((e) => {
      cached = null;
      throw e;
    }) as Promise<Shop>;
    let alive = true;
    cached.then((s) => alive && setShop(s)).catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);
  return shop;
}
