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

export default async function Home({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  return (
    <>
      <ShopJsonLd locale={locale} />
      {/* landing page only: the scene starts locked behind the preloader */}
      <SmoothScroll />
      <Preloader />
      <ScrollProgress />
      <Nav />
      <main id="main">
        <Experience />
      </main>
      <Footer />
    </>
  );
}
