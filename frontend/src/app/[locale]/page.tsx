import { Footer } from "@/components/Footer";
import { Nav } from "@/components/Nav";
import { Preloader } from "@/components/Preloader";
import { ScrollProgress } from "@/components/ScrollProgress";
import { SmoothScroll } from "@/components/SmoothScroll";
import { Experience } from "@/components/scene/Experience";

export default function Home() {
  return (
    <>
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
