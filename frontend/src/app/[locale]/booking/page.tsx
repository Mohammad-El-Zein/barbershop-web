import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { BookingPage } from "@/components/booking/BookingPage";
import { PageShell, PageTitle } from "@/components/PageShell";
import { Skeleton } from "@/components/ui/Feedback";

export async function generateMetadata({ params }: PageProps<"/[locale]/booking">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "booking" });
  return { title: t("title"), robots: { index: true } };
}

/** Full-page booking flow (also the fallback target of every "Book" button). */
export default async function Page({ params }: PageProps<"/[locale]/booking">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "booking" });
  return (
    <PageShell width="lg">
      <PageTitle eyebrow={t("eyebrow")} title={t("title")} lead={t("lead")} />
      <div className="card px-5 py-6 sm:px-7">
        {/* ?services= / ?staff= presets are URL data → client-rendered inside Suspense */}
        <Suspense fallback={<Skeleton className="h-[32rem]" />}>
          <BookingPage />
        </Suspense>
      </div>
    </PageShell>
  );
}
