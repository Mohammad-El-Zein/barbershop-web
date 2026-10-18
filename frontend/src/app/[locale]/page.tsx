import { Suspense } from "react";
import { Footer } from "@/components/Footer";
import { Nav } from "@/components/Nav";
import { Preloader } from "@/components/Preloader";
import { ScrollProgress } from "@/components/ScrollProgress";
import { SmoothScroll } from "@/components/SmoothScroll";
import { Experience } from "@/components/scene/Experience";
import { ShopJsonLd } from "@/components/seo/ShopJsonLd";
import { pageMetadata } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  return pageMetadata({ locale, path: "" });
}

/**
 * Landing page. The <Suspense> boundaries around nav and footer don't wait for
 * anything (all static): they let React hydrate them as separate tasks. The scene
 * deliberately has none — React may stream a large boundary separately, and the
 * footer would then sit at the top until the scene arrives (a full-screen layout shift).
 */
export default async function Home({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  return (
    <>
      <ShopJsonLd locale={locale} />
      {/* landing page only: the scene starts locked behind the preloader */}
      <SmoothScroll />
      <Preloader />
      <ScrollProgress />
      <Suspense>
        <Nav />
      </Suspense>
      <main id="main">
        <Experience />
      </main>
      <Suspense>
        <Footer />
      </Suspense>
    </>
  );
}
