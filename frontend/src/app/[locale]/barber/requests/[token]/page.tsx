import { DecisionView } from "@/components/links/DecisionView";
import { TokenPage } from "@/components/links/TokenPage";

/** Email-link pages are personal: never indexed. */
export const metadata = { robots: { index: false, follow: false } };

/** Barber accept / decline page (link from the request email). */
export default function Page() {
  return (
    <TokenPage width="md">
      <DecisionView />
    </TokenPage>
  );
}
