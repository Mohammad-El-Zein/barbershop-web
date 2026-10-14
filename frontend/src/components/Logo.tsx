import { siteConfig } from "@/config/site.config";

/** Wordmark + scissors glyph. Swap for an <img> logo per client if needed. */
export function Logo({ className = "" }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="text-gold">
        <circle cx="6" cy="18" r="3" stroke="currentColor" strokeWidth="1.6" />
        <circle cx="18" cy="18" r="3" stroke="currentColor" strokeWidth="1.6" />
        <path d="M8.2 16 19 3M15.8 16 5 3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
      <span className="display text-[1.15rem] tracking-[0.14em] text-ink">{siteConfig.brand.name}</span>
    </span>
  );
}
