import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { ForgotForm } from "@/components/account/AuthForms";
import { PageShell, PageTitle } from "@/components/PageShell";
import { Skeleton } from "@/components/ui/Feedback";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

export default async function Page({ params }: PageProps<"/[locale]/account/forgot-password">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "account.forgot" });
  return (
    <PageShell width="sm">
      <PageTitle eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")} />
      <Suspense fallback={<Skeleton className="h-80" />}>
        <ForgotForm />
      </Suspense>
    </PageShell>
  );
}
