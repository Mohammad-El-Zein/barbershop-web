"use client";

import { useEffect, useState } from "react";
import { api, call } from "./api/client";
import { TENANT } from "./api/config";
import type { BookingRules, Shop } from "./api/types";

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

/** Defaults matching the backend's, used until /api/public/{tenant} has loaded. */
export const DEFAULT_RULES: Required<BookingRules> = {
  depositCents: 1000,
  refundCutoffMin: 1440,
  onlineCancelCutoffMin: 120,
  barberResponseMin: 720,
  paymentDeadlineMin: 1440,
  minLeadMin: 240,
  maxDaysAhead: 60,
  slotStepMin: 15,
  loyaltyThreshold: 10,
  loyaltyPercent: 50,
  demoPayments: true,
};

export function rulesOf(shop: Shop | null): Required<BookingRules> {
  return { ...DEFAULT_RULES, ...(shop?.bookingRules ?? {}) } as Required<BookingRules>;
}
