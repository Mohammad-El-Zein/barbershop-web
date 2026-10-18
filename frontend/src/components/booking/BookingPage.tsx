"use client";

import { useSearchParams } from "next/navigation";
import { useRouter } from "@/i18n/navigation";
import type { Booking } from "@/lib/api/types";
import { rememberStatus } from "./BookingDialog";
import { BookingFlow } from "./BookingFlow";

/** /booking?services=a,b&staff=x — the flow on its own page. */
export function BookingPage() {
  const params = useSearchParams();
  const router = useRouter();
  const services = params.get("services")?.split(",").filter(Boolean) ?? [];
  const staff = params.get("staff") ?? undefined;
  return (
    <BookingFlow
      initialServiceIds={services}
      initialStaffId={staff}
      source={params.get("source") ?? "booking-page"}
      onBooked={(b: Booking) => {
        rememberStatus(b.statusToken);
        router.push(`/booking/status/${b.statusToken}`);
      }}
    />
  );
}
