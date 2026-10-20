"use client";

import { useEffect, useId, useRef } from "react";
import { useTranslations } from "next-intl";
import { getLenis } from "@/lib/scroll";

/**
 * Accessible modal on top of the native <dialog> element:
 * - showModal() makes the page behind inert (focus can't leave the dialog),
 * - Tab / Shift+Tab additionally wrap inside the dialog (explicit focus trap),
 * - Escape closes, focus returns to the element that opened it,
 * - page scrolling (incl. Lenis smooth scroll) is paused while open.
 */
export function Dialog({
  open,
  onClose,
  title,
  children,
  size = "md",
  hideTitle = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  size?: "sm" | "md" | "lg";
  hideTitle?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const opener = useRef<Element | null>(null);
  const titleId = useId();
  const t = useTranslations("common");

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      opener.current = document.activeElement;
      d.showModal();
      document.documentElement.style.overflow = "hidden";
      getLenis()?.stop();
    } else if (!open && d.open) {
      d.close();
    }
  }, [open]);

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    const onClosed = () => {
      document.documentElement.style.overflow = "";
      getLenis()?.start();
      (opener.current as HTMLElement | null)?.focus?.();
    };
    const onCancel = (e: Event) => {
      e.preventDefault(); // we control closing (state stays in sync)
      onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Tab") return;
      const focusables = d.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!focusables.length) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    d.addEventListener("close", onClosed);
    d.addEventListener("cancel", onCancel);
    d.addEventListener("keydown", onKey);
    return () => {
      d.removeEventListener("close", onClosed);
      d.removeEventListener("cancel", onCancel);
      d.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  const width = size === "sm" ? "sm:max-w-md" : size === "lg" ? "sm:max-w-5xl" : "sm:max-w-2xl";

  return (
    <dialog ref={ref} className={`modal ${width}`} aria-labelledby={titleId} data-lenis-prevent>
      {open && (
        <div className="modal-panel relative flex max-h-[100dvh] flex-col sm:max-h-[92dvh]">
          <div className="flex items-center justify-between gap-4 border-b border-line px-5 py-4 sm:px-7">
            <h2 id={titleId} className={hideTitle ? "sr-only" : "display text-2xl"}>
              {title}
            </h2>
            <button
              type="button"
              onClick={onClose}
              aria-label={t("close")}
              className="-mr-2 ml-auto flex h-10 w-10 items-center justify-center rounded-full text-ink/70 hover:text-gold-text focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold-text"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 5l14 14M19 5L5 19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-5 sm:px-7 sm:py-6">{children}</div>
        </div>
      )}
    </dialog>
  );
}

/** Yes / no confirmation built on {@link Dialog}. The confirm action is the POST. */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  danger = false,
  busy = false,
  confirmDisabled = false,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  children?: React.ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  /** Only the confirm button off (e.g. the action is not possible); "back" stays usable. */
  confirmDisabled?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const t = useTranslations("common");
  return (
    <Dialog open={open} onClose={onClose} title={title} size="sm">
      <div className="space-y-4 text-sm leading-relaxed text-ink/85">{children}</div>
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={busy}>
          {t("back")}
        </button>
        <button
          type="button"
          className={`btn ${danger ? "btn-danger" : "btn-primary"}`}
          onClick={onConfirm}
          disabled={busy || confirmDisabled}
          aria-busy={busy}
        >
          {confirmLabel}
        </button>
      </div>
    </Dialog>
  );
}
