"use client";

import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { BOOKING_OPEN_EVENT, type BookingOpenDetail } from "@/lib/booking";
import type { Booking } from "@/lib/api/types";
import { whenSceneReady } from "@/lib/sceneReady";
import { Dialog } from "../ui/Dialog";
import { Skeleton } from "../ui/Feedback";

/**
 * The flow (steps, API client, validation) is loaded on demand: not part of the
 * initial JavaScript of any page, prefetched when the browser is idle after the
 * first screen, so the first click still opens instantly.
 */
const loadFlow = () => import("./BookingFlow").then((m) => ({ default: m.BookingFlow }));
const BookingFlow = lazy(loadFlow);

/** Remember the latest status link so the payment page can lead back to it. */
export function rememberStatus(token?: string) {
  if (!token) return;
  try {
    sessionStorage.setItem("booking:status", token);
  } catch {
    /* private mode — the status page still works from its URL */
  }
}

/**
 * The booking modal, mounted once in the locale layout. Every "Book" button
 * dispatches BOOKING_OPEN_EVENT (lib/booking.ts); service cards preselect their
 * service. After confirming, we go to the live status page.
 */
export function BookingDialog() {
  const t = useTranslations("booking");
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [preset, setPreset] = useState<BookingOpenDetail | null>(null);
  // a new key per opening: the flow starts fresh every time
  const [session, setSession] = useState(0);

  useEffect(() => {
    const onOpen = (e: Event) => {
      e.preventDefault(); // handled — no fallback navigation
      setPreset((e as CustomEvent<BookingOpenDetail>).detail);
      setSession((s) => s + 1);
      setOpen(true);
    };
    window.addEventListener(BOOKING_OPEN_EVENT, onOpen);
    let idleId = 0;
    const stop = whenSceneReady(() => {
      const idle = window.requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 2000));
      idleId = idle(() => void loadFlow()) as number;
    });
    return () => {
      window.removeEventListener(BOOKING_OPEN_EVENT, onOpen);
      stop();
      window.cancelIdleCallback?.(idleId);
    };
  }, []);

  const close = useCallback(() => setOpen(false), []);
  const onBooked = (b: Booking) => {
    rememberStatus(b.statusToken);
    setOpen(false);
    router.push(`/booking/status/${b.statusToken}`);
  };

  return (
    <Dialog open={open} onClose={close} title={t("title")} size="lg">
      {open && (
        <Suspense fallback={<Skeleton className="h-[28rem]" />}>
          <BookingFlow
            key={session}
            initialServiceIds={preset?.serviceIds}
            initialStaffId={preset?.staffId}
            onBooked={onBooked}
            source={preset?.source}
          />
        </Suspense>
      )}
    </Dialog>
  );
}
