import { useLocale, useTranslations } from "next-intl";
import { siteConfig } from "@/config/site.config";
import { CopyrightYear } from "./CopyrightYear";
import { Logo } from "./Logo";
import { NewsletterForm } from "./NewsletterForm";

const { footer, contact, brand } = siteConfig;

/**
 * Regular (unpinned) footer after the scroll scene. Section titles are h2
 * (top-level page sections), styled as eyebrows. Follows the site theme
 * (ivory in light mode), uses the shared page gutter, and marks where the nav
 * switches from "over the scene" to "over the page" (data-after-scene).
 */
export function Footer({ onLanding = true }: { onLanding?: boolean } = {}) {
  const t = useTranslations();
  const locale = useLocale();
  // "#anchor" links scroll on the landing page; elsewhere they lead back to it
  const hrefOf = (href: string) => (onLanding || !href.startsWith("#") ? href : `/${locale}${href === "#home" ? "" : href}`);

  return (
    <footer data-after-scene className="relative z-10 border-t border-gold/25 bg-surface text-ink">
      {/* gold hairline glow */}
      <div className="pointer-events-none absolute inset-x-0 -top-px mx-auto h-px max-w-3xl bg-gradient-to-r from-transparent via-gold to-transparent" />

      <div className="grid gap-12 px-[var(--gutter)] py-16 md:grid-cols-12 md:py-24">
        <div className="md:col-span-4">
          <Logo />
          <p className="mt-5 max-w-xs text-sm leading-relaxed text-muted">{t("footer.description")}</p>
          <ul className="mt-6 flex gap-3" aria-label={t("footer.social")}>
            {footer.socials.map((s) => (
              <li key={s.label}>
                <a
                  href={s.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={s.label}
                  className="flex h-10 w-10 items-center justify-center rounded-full border border-ink/20 text-ink/80 transition-colors hover:border-gold-text hover:text-gold-text"
                >
                  <SocialIcon name={s.icon} />
                </a>
              </li>
            ))}
          </ul>
        </div>

        {footer.columns.map((col) => (
          <nav key={col.titleKey} aria-label={t(col.titleKey)} className="md:col-span-2">
            <h2 className="eyebrow">{t(col.titleKey)}</h2>
            <ul className="mt-5 space-y-3">
              {col.links.map((l) => (
                <li key={l.labelKey}>
                  <a href={hrefOf(l.href)} className="text-sm text-ink/80 transition-colors hover:text-gold-text">
                    {t(l.labelKey)}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
        ))}

        <div id={contact.anchor} className="scroll-mt-24 md:col-span-4">
          <h2 className="eyebrow">{t("footer.visit")}</h2>
          <address className="mt-5 space-y-1 text-sm not-italic text-ink/85">
            {contact.addressLines.map((l) => (
              <div key={l}>{l}</div>
            ))}
            <div className="pt-2">
              <a href={`tel:${contact.phone.replace(/\s/g, "")}`} className="hover:text-gold-text">
                {contact.phone}
              </a>
            </div>
            <div>
              <a href={`mailto:${contact.email}`} className="hover:text-gold-text">
                {contact.email}
              </a>
            </div>
          </address>
          <ul className="mt-4 space-y-1 text-xs text-muted">
            {(t.raw("footer.hours") as string[]).map((h) => (
              <li key={h}>{h}</li>
            ))}
          </ul>

          <h2 className="eyebrow mt-10">{t("footer.newsletter.title")}</h2>
          <p className="mt-2 text-sm text-muted">{t("footer.newsletter.text")}</p>
          <NewsletterForm />
        </div>
      </div>

      <div className="border-t border-line">
        <div className="flex flex-col items-start justify-between gap-3 px-[var(--gutter)] py-6 text-xs text-muted md:flex-row md:items-center">
          <p>
            © <CopyrightYear /> {brand.name}. {t("footer.rights")}
          </p>
          <ul className="flex gap-6">
            {footer.legal.map((l) => (
              <li key={l.labelKey}>
                <a href={`/${locale}${l.href}`} className="hover:text-gold-text">
                  {t(l.labelKey)}
                </a>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </footer>
  );
}

function SocialIcon({ name }: { name: "instagram" | "facebook" | "tiktok" }) {
  const common = { width: 16, height: 16, viewBox: "0 0 24 24", fill: "none", "aria-hidden": true } as const;
  if (name === "instagram")
    return (
      <svg {...common}>
        <rect x="3" y="3" width="18" height="18" rx="5" stroke="currentColor" strokeWidth="1.6" />
        <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.6" />
        <circle cx="17.5" cy="6.5" r="1" fill="currentColor" />
      </svg>
    );
  if (name === "facebook")
    return (
      <svg {...common}>
        <path d="M14 8h3V4h-3a4 4 0 0 0-4 4v3H7v4h3v6h4v-6h3l1-4h-4V8z" fill="currentColor" />
      </svg>
    );
  return (
    <svg {...common}>
      <path d="M15 3c.4 2.6 2 4.2 5 4.4V11a8.6 8.6 0 0 1-5-1.6V16a6 6 0 1 1-6-6v3.6A2.5 2.5 0 1 0 11.5 16V3H15z" fill="currentColor" />
    </svg>
  );
}
