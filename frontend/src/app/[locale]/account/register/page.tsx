import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { RegisterForm } from "@/components/account/AuthForms";
import { PageShell, PageTitle } from "@/components/PageShell";
import { Skeleton } from "@/components/ui/Feedback";

export const metadata = { robots: { index: false } };

export default async function Page({ params }: PageProps<"/[locale]/account/register">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "account.register" });
  return (
    <PageShell width="sm">
      <PageTitle eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")} />
      <Suspense fallback={<Skeleton className="h-80" />}>
        <RegisterForm />
      </Suspense>
    </PageShell>
  );
}
