import { Footer } from "@/components/Footer";
import { Nav } from "@/components/Nav";
import { Preloader } from "@/components/Preloader";
import { ScrollProgress } from "@/components/ScrollProgress";
import { Experience } from "@/components/scene/Experience";

export default function Home() {
  return (
    <>
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
