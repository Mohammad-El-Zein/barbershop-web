import { getTranslations } from "next-intl/server";
import { PageShell, PageTitle } from "@/components/PageShell";
import { Alert } from "@/components/ui/Feedback";

export async function generateMetadata({ params }: PageProps<"/[locale]/datenschutz">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "privacy" });
  return { title: t("title") };
}

/**
 * Privacy policy — PLACEHOLDER. Only the booking/account part is written (it
 * describes what this system really does); the full legal text (controller,
 * hosting, cookies, rights, supervisory authority …) follows in phase 4 and must
 * be reviewed by the client / a lawyer.
 */
export default async function Page({ params }: PageProps<"/[locale]/datenschutz">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "privacy" });
  const sections = ["booking", "account", "emails", "deletion", "payments"] as const;
  return (
    <PageShell width="md">
      <PageTitle eyebrow={t("eyebrow")} title={t("title")} />
      <Alert tone="warn" className="mb-8">
        {t("placeholder")}
      </Alert>
      <div className="space-y-8">
        {sections.map((s) => (
          <section key={s} id={s} aria-labelledby={`h-${s}`}>
            <h2 id={`h-${s}`} className="display text-2xl">
              {t(`${s}.title`)}
            </h2>
            <div className="mt-3 space-y-3 text-sm leading-relaxed text-ink/85">
              {(t.raw(`${s}.text`) as string[]).map((p) => (
                <p key={p}>{p}</p>
              ))}
            </div>
          </section>
        ))}
      </div>
    </PageShell>
  );
}
