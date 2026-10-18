import { Footer } from "./Footer";
import { Nav } from "./Nav";

/**
 * Layout for the app pages (booking, email-link pages, account, privacy):
 * the regular nav (in page mode), a centered content column and the footer.
 * `width` = max content width.
 */
export function PageShell({
  children,
  width = "md",
  footer = true,
}: {
  children: React.ReactNode;
  width?: "sm" | "md" | "lg" | "xl";
  footer?: boolean;
}) {
  const max = { sm: "max-w-xl", md: "max-w-3xl", lg: "max-w-5xl", xl: "max-w-7xl" }[width];
  return (
    <>
      <Nav hasScene={false} />
      <main id="main" className={`mx-auto w-full ${max} px-4 pb-20 pt-[calc(var(--nav-h)+2rem)] sm:px-6 md:pt-[calc(var(--nav-h)+3rem)]`}>
        {children}
      </main>
      {footer && <Footer onLanding={false} />}
    </>
  );
}

/** Page title block: eyebrow + display headline + optional lead text. */
export function PageTitle({ eyebrow, title, lead }: { eyebrow?: string; title: string; lead?: React.ReactNode }) {
  return (
    <header className="mb-8 md:mb-10">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <h1 className="display mt-3 text-4xl leading-[0.95] sm:text-5xl md:text-6xl">{title}</h1>
      {lead && <div className="mt-4 max-w-2xl text-base leading-relaxed text-muted">{lead}</div>}
    </header>
  );
}
