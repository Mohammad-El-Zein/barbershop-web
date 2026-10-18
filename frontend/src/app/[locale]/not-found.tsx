import { PageShell } from "@/components/PageShell";
import { ErrorView } from "@/components/errors/ErrorView";

/** 404 in the site design (Next.js adds noindex and the 404 status). */
export default function NotFound() {
  return (
    <PageShell width="md">
      <ErrorView kind="notFound" />
    </PageShell>
  );
}
