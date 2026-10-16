"use client";

import { useTranslations } from "next-intl";
import type { Chapter } from "@/config/site.config";
import { ActionButton } from "../ActionButton";

/**
 * Text block of one chapter (copy from messages/<locale>.json → chapter.messageKey).
 * Headline lines are wrapped in masks so the timeline can slide them in/out line
 * by line (vertical only). Elements marked data-fade fade up after the headline.
 * The headline auto-fits its longest line to the column (.headline-fit + --fit),
 * so long German lines never overflow.
 */
export function ChapterText({ chapter, index, animated }: { chapter: Chapter; index: number; animated: boolean }) {
  const t = useTranslations(chapter.messageKey);
  const headline = t.raw("headline") as string[];
  const longest = Math.max(...headline.map((l) => l.length));
  const Heading = index === 0 ? "h1" : "h2";
  // Non-first chapters start hidden in the pinned version (GSAP takes over after hydration).
  const hidden = animated && index > 0 ? "invisible opacity-0" : "";

  return (
    <div data-chapter={chapter.id} className={`min-w-0 ${hidden}`}>
      <p data-fade className="eyebrow mb-3 flex items-center gap-3 md:mb-5">
        <span className="h-px w-8 shrink-0 bg-gold" aria-hidden="true" />
        {t("eyebrow")}
      </p>
      <Heading className="display headline-fit" style={{ "--fit": longest } as React.CSSProperties}>
        {headline.map((line, i) => (
          <span key={i} className="line-mask">
            <span className={`line ${i === chapter.accentLine ? "text-gold-gradient" : ""}`}>{line}</span>
          </span>
        ))}
      </Heading>
      <p data-fade className="mt-4 max-w-md text-[0.9rem] leading-relaxed text-muted md:mt-6 md:text-base">
        {t("body")}
      </p>
      {chapter.ctas && chapter.ctas.length > 0 && (
        <div data-fade className="mt-6 flex flex-wrap gap-2.5 md:mt-8 md:gap-3">
          {chapter.ctas.map((cta) => (
            <ActionButton
              key={cta.labelKey}
              cta={cta}
              size={chapter.stage === "cta" ? "lg" : "md"}
              source={`chapter-${chapter.id}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
