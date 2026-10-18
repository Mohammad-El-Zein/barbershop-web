"use client";

import { useTranslations } from "next-intl";
import type { BookingStatus } from "@/lib/api/types";

type Tone = "info" | "success" | "warn" | "error";

const toneCls: Record<Tone, string> = {
  info: "border-line bg-[color-mix(in_srgb,var(--text)_4%,transparent)]",
  success: "border-[#5d8a5a]/60 bg-[#5d8a5a]/12",
  warn: "border-gold/50 bg-gold/10",
  error: "border-[#c25a45]/60 bg-[#c25a45]/12",
};

/** Message box. Errors are announced (role=alert), the rest politely (role=status). */
export function Alert({
  tone = "info",
  title,
  children,
  className = "",
}: {
  tone?: Tone;
  title?: string;
  children?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={`rounded-xl border px-4 py-3 text-sm leading-relaxed ${toneCls[tone]} ${className}`}
    >
      {title && <p className="font-semibold text-ink">{title}</p>}
      {children && <div className={title ? "mt-1 text-ink/85" : "text-ink/90"}>{children}</div>}
    </div>
  );
}

/** "DEMO MODE: no real payment" — always visible on payment screens. */
export function DemoBanner({ className = "" }: { className?: string }) {
  const t = useTranslations("pay");
  return (
    <div
      role="note"
      className={`flex items-center gap-3 rounded-xl border-2 border-dashed border-gold px-4 py-3 text-sm font-semibold uppercase tracking-[0.12em] text-gold-text ${className}`}
    >
      <span aria-hidden="true" className="text-lg">⚠</span>
      <span>{t("demoBanner")}</span>
    </div>
  );
}

const statusTone: Record<BookingStatus, string> = {
  PENDING_BARBER: "border-gold/50 text-gold-text",
  AWAITING_PAYMENT: "border-[#d39b3a] text-[#d39b3a] light:text-[#8a5a10] light:border-[#8a5a10]",
  CONFIRMED: "border-[#6fa86b] text-[#7fbb7a] light:text-[#2f6b2c] light:border-[#2f6b2c]",
  COMPLETED: "border-ink/30 text-ink/70",
  DECLINED: "border-[#c25a45]/70 text-[#e0806b] light:text-[#9a3523]",
  EXPIRED: "border-ink/25 text-ink/55",
  CANCELLED: "border-ink/25 text-ink/55",
  NO_SHOW: "border-[#c25a45]/70 text-[#e0806b] light:text-[#9a3523]",
};

export function StatusBadge({ status, className = "" }: { status?: string; className?: string }) {
  const t = useTranslations("status");
  const s = (status ?? "PENDING_BARBER") as BookingStatus;
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[0.68rem] font-semibold uppercase tracking-[0.12em] ${statusTone[s] ?? ""} ${className}`}
    >
      {t(s)}
    </span>
  );
}

export function Spinner({ label, className = "" }: { label?: string; className?: string }) {
  return (
    <span role="status" className={`inline-flex items-center gap-2 text-sm text-muted ${className}`}>
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-gold/30 border-t-gold motion-reduce:animate-none" />
      {label && <span>{label}</span>}
    </span>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden="true" className={`skeleton ${className}`} />;
}

/** Initials avatar, or the photo when the staff profile has one. */
export function Avatar({ name, photoUrl, size = 48 }: { name: string; photoUrl?: string | null; size?: number }) {
  const initials = name
    .split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
  return photoUrl ? (
    // eslint-disable-next-line @next/next/no-img-element -- staff photos come from arbitrary admin-provided URLs
    <img src={photoUrl} alt="" width={size} height={size} className="shrink-0 rounded-full object-cover" style={{ width: size, height: size }} />
  ) : (
    <span
      aria-hidden="true"
      className="display flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-gold to-gold-deep text-[#120d05]"
      style={{ width: size, height: size, fontSize: size * 0.4 }}
    >
      {initials}
    </span>
  );
}

/** Label / value row for summaries. */
export function Row({ label, children, strong = false }: { label: string; children: React.ReactNode; strong?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-line py-2.5 last:border-b-0">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className={`text-right text-sm ${strong ? "font-semibold text-gold-text" : "text-ink"}`}>{children}</dd>
    </div>
  );
}
