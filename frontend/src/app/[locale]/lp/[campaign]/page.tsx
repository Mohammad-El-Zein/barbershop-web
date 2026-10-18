import Image from "next/image";
import { notFound } from "next/navigation";
import { Footer } from "@/components/Footer";
import { Nav } from "@/components/Nav";
import { LpBookButton } from "@/components/landing/LpBookButton";
import { landingPage, landingPages } from "@/config/landing-pages";
import { minutes } from "@/lib/format";
import { pageMetadata } from "@/lib/seo";
import { loadShopData } from "@/lib/shop-server";

/**
 * Ad landing page /[locale]/lp/[campaign], built from config/landing-pages.ts.
 * Made for speed on phones: no preloader, no scroll scene, no smooth scrolling;
 * a text headline and one priority hero image (responsive via next/image).
 * Services and prices come from the backend (cached). noindex by default.
 */
export function generateStaticParams() {
  return landingPages.map((p) => ({ campaign: p.slug }));
}

export async function generateMetadata({ params }: PageProps<"/[locale]/lp/[campaign]">) {
  const { locale, campaign } = await params;
  const lp = landingPage(campaign);
  if (!lp) return {};
  const copy = lp.copy[locale === "en" ? "en" : "de"];
  return pageMetadata({
    locale,
    path: `/lp/${lp.slug}`,
    title: copy.metaTitle,
    description: copy.metaDescription,
    noindex: !lp.index,
  });
}

export default async function LandingPage({ params }: PageProps<"/[locale]/lp/[campaign]">) {
  const { locale, campaign } = await params;
  const lp = landingPage(campaign);
  if (!lp) notFound();
  const lang = locale === "en" ? "en" : "de";
  const copy = lp.copy[lang];
  const { services } = await loadShopData(locale);
  const listed = lp.services.length ? lp.services.map((id) => services.find((s) => s.id === id)).filter((s) => !!s) : services;

  return (
    <>
      <Nav hasScene={false} />
      <main id="main" className="pt-[var(--nav-h)]">
        {/* hero: text first (paints immediately), image with priority + responsive sizes */}
        <section className="mx-auto grid max-w-6xl items-center gap-8 px-4 py-10 sm:px-6 md:grid-cols-2 md:py-16">
          <div>
            <p className="eyebrow">{copy.eyebrow}</p>
            <h1 className="display mt-3 text-[clamp(2.6rem,9vw,4.75rem)] leading-[0.95]">{copy.headline}</h1>
            <p className="mt-5 max-w-lg text-base leading-relaxed text-muted md:text-lg">{copy.subline}</p>
            <div className="mt-8">
              <LpBookButton label={copy.cta} slug={lp.slug} serviceIds={lp.preselect} />
            </div>
          </div>
          <div className="relative aspect-[16/10] overflow-hidden rounded-2xl border border-line">
            <Image
              src={lp.hero.src}
              alt={lp.hero.alt[lang]}
              width={lp.hero.width}
              height={lp.hero.height}
              priority
              fetchPriority="high"
              sizes="(min-width: 768px) 50vw, 100vw"
              className="h-full w-full object-cover"
            />
          </div>
        </section>

        <section aria-label={copy.eyebrow} className="mx-auto max-w-6xl px-4 pb-14 sm:px-6">
          <ul className="grid gap-4 md:grid-cols-3">
            {copy.benefits.map((b) => (
              <li key={b.title} className="card p-5">
                <h2 className="display text-xl">{b.title}</h2>
                <p className="mt-2 text-sm leading-relaxed text-ink/80">{b.text}</p>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="lp-services" className="mx-auto max-w-6xl px-4 pb-14 sm:px-6">
          <h2 id="lp-services" className="display text-3xl md:text-4xl">
            {copy.servicesTitle}
          </h2>
          <ul className="mt-6 grid gap-3 md:grid-cols-2">
            {listed.map((s) => (
              <li key={s.id} className="card flex items-center justify-between gap-4 p-5">
                <div className="min-w-0">
                  <p className="display text-xl">{s.name}</p>
                  <p className="text-xs text-muted">{minutes(s.durationMin ?? 0, locale)}</p>
                  {s.description && <p className="mt-1 text-sm text-ink/75">{s.description}</p>}
                </div>
                <div className="flex shrink-0 flex-col items-end gap-2">
                  {/* catalogue price, like the landing cards ("ab" / "from" where applicable) */}
                  <span className="text-sm font-semibold text-gold-text">{s.price?.formatted}</span>
                  <LpBookButton label={lang === "de" ? "Buchen" : "Book"} slug={lp.slug} serviceIds={[s.id!]} variant="ghost" />
                </div>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="lp-reviews" className="mx-auto max-w-6xl px-4 pb-14 sm:px-6">
          <h2 id="lp-reviews" className="display text-3xl md:text-4xl">
            {copy.reviewsTitle}
          </h2>
          {/* PLACEHOLDER — real reviews only (with source), never invented ones */}
          <div role="note" className="mt-6 rounded-2xl border-2 border-dashed border-gold/60 p-6 text-sm leading-relaxed text-ink/80">
            <strong className="font-semibold">{lang === "de" ? "Platzhalter: Kundenbewertungen" : "Placeholder: customer reviews"}</strong>
            <p className="mt-2">
              {lang === "de"
                ? "Hier echte Bewertungen einbinden, z. B. Google-Bewertungen mit Quelle und Datum. Keine erfundenen oder gekauften Bewertungen verwenden (UWG § 5b)."
                : "Embed real reviews here, e.g. Google reviews with source and date. Never use invented or bought reviews."}
            </p>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 pb-20 text-center sm:px-6">
          <h2 className="display text-3xl md:text-4xl">{copy.finalTitle}</h2>
          <div className="mt-6">
            <LpBookButton label={copy.cta} slug={lp.slug} serviceIds={lp.preselect} />
          </div>
        </section>
      </main>
      <Footer onLanding={false} />
    </>
  );
}
