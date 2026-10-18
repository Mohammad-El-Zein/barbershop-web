import { DecisionView } from "@/components/links/DecisionView";
import { TokenPage } from "@/components/links/TokenPage";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** Barber accept / decline page (link from the request email). */
export default function Page() {
  return (
    <TokenPage width="md">
      <DecisionView />
    </TokenPage>
  );
}
