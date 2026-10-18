import type { BookingRules, Shop } from "./api/types";

/** Defaults matching the backend's, used until /api/public/{tenant} has loaded (or if it can't be reached). */
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
