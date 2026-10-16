import { siteConfig } from "@/config/site.config";

/** True only in development and only when the debug flag in site.config.ts is on. */
export const showPlaceholderLabels =
  siteConfig.debug.showPlaceholderLabels && process.env.NODE_ENV !== "production";

/**
 * Small file-name badge drawn as a DOM overlay on top of a scene asset
 * (never baked into the image). Place it inside a positioned parent.
 */
export function PlaceholderBadge({ src, className = "" }: { src: string; className?: string }) {
  if (!showPlaceholderLabels) return null;
  const file = src.split("/").pop();
  return (
    <span
      aria-hidden="true"
      className={`pointer-events-none absolute left-1/2 top-1 z-50 -translate-x-1/2 whitespace-nowrap rounded-full border border-dashed border-gold bg-black/80 px-2 py-0.5 font-mono text-[10px] text-gold ${className}`}
    >
      {file}
    </span>
  );
}
