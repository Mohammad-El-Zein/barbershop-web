import { isPlaceholder, legalConfig } from "@/config/legal.config";
import { PageShell, PageTitle } from "../PageShell";

/**
 * Building blocks of the legal pages (Impressum, Datenschutz, AGB): page frame with
 * "last updated" date and the template notice, numbered sections, and <V> for
 * config values — placeholders ("[…]") are highlighted so none is missed.
 */
export function LegalPage({
  eyebrow,
  title,
  lead,
  locale,
  children,
}: {
  eyebrow: string;
  title: string;
  lead?: string;
  locale: string;
  children: React.ReactNode;
}) {
  const de = locale === "de";
  const updated = new Intl.DateTimeFormat(de ? "de-DE" : "en-GB", { dateStyle: "long", timeZone: "UTC" }).format(
    new Date(`${legalConfig.lastUpdated}T12:00:00Z`),
  );
  return (
    <PageShell width="md">
      <PageTitle eyebrow={eyebrow} title={title} lead={lead} />
      {!legalConfig.reviewed && (
        <div role="note" className="mb-8 rounded-2xl border-2 border-dashed border-gold/70 bg-gold/10 p-4 text-sm leading-relaxed">
          <strong className="font-semibold">{de ? "Vorlage – nicht rechtlich geprüft." : "Template – not legally reviewed."}</strong>{" "}
          {de
            ? "Dieser Text ist ein Muster. Markierte Platzhalter [ … ] müssen ersetzt und der gesamte Text von einer Rechtsanwältin / einem Rechtsanwalt geprüft werden, bevor die Website live geht."
            : "This text is a sample. Highlighted placeholders [ … ] must be replaced and the whole text reviewed by a lawyer before the website goes live."}
        </div>
      )}
      <div className="legal space-y-10 text-sm leading-relaxed text-ink/85 [&_a]:text-gold-text [&_a]:underline [&_a]:underline-offset-2 [&_h3]:mt-5 [&_h3]:font-semibold [&_h3]:text-ink [&_li]:ml-5 [&_li]:list-disc [&_p]:mt-3 [&_ul]:mt-3 [&_ul]:space-y-1">
        {children}
      </div>
      <p className="mt-12 text-xs text-muted">
        {de ? "Stand" : "Last updated"}: {updated}
      </p>
    </PageShell>
  );
}

export function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} aria-labelledby={`h-${id}`} className="scroll-mt-28">
      <h2 id={`h-${id}`} className="display text-2xl text-ink">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** A value from legal.config: highlighted while it is still a "[…]" placeholder. */
export function V({ children }: { children: string }) {
  if (!isPlaceholder(children)) return <>{children}</>;
  return (
    <mark className="rounded bg-gold/25 px-1 text-ink ring-1 ring-gold/60" title="Platzhalter / placeholder">
      {children}
    </mark>
  );
}
