import type { Chapter } from "@/config/site.config";
import { ActionButton } from "../ActionButton";

/**
 * Text block of one chapter. Headline lines are wrapped in masks so the
 * timeline can slide them in/out line by line. Elements marked data-fade
 * fade up after the headline.
 */
export function ChapterText({ chapter, index, animated }: { chapter: Chapter; index: number; animated: boolean }) {
  const Heading = index === 0 ? "h1" : "h2";
  // Non-first chapters start hidden in the pinned version (GSAP takes over after hydration).
  const hidden = animated && index > 0 ? "invisible opacity-0" : "";

  return (
    <div data-chapter={chapter.id} className={hidden}>
      {chapter.eyebrow && (
        <p data-fade className="eyebrow mb-3 flex md:mb-5 items-center gap-3">
          <span className="h-px w-8 bg-gold" aria-hidden="true" />
          {chapter.eyebrow}
        </p>
      )}
      <Heading className="display text-[clamp(2.25rem,6vw,6.75rem)]">
        {chapter.headline.map((line, i) => (
          <span key={i} className="line-mask">
            <span className={`line ${i === chapter.accentLine ? "text-gold-gradient" : ""}`}>{line}</span>
          </span>
        ))}
      </Heading>
      {chapter.body && (
        <p data-fade className="mt-4 max-w-md text-[0.9rem] md:mt-6 leading-relaxed text-muted md:text-base">
          {chapter.body}
        </p>
      )}
      {chapter.ctas && chapter.ctas.length > 0 && (
        <div data-fade className="mt-6 flex flex-wrap gap-2.5 md:mt-8 md:gap-3">
          {chapter.ctas.map((cta) => (
            <ActionButton
              key={cta.label}
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
