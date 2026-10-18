"use client";

import { useTranslations } from "next-intl";
import { ApiError } from "./client";

/**
 * Turns any error into a user-facing, localized sentence:
 * 1. our own text for the backend code (messages → errors["booking.slot_taken"] …),
 * 2. otherwise the backend's localized `detail` (it already speaks the UI language),
 * 3. otherwise a generic message.
 */
export function useErrorText() {
  const t = useTranslations("errors");
  return (e: unknown): string => {
    if (e instanceof ApiError) {
      if (e.code === "network") return t("network");
      const key = e.code.replaceAll(".", "_");
      if (t.has(key)) return t(key);
      if (e.detail) return e.detail;
    }
    return t("generic");
  };
}
