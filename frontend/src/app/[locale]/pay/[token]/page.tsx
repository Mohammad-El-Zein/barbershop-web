import { PayView } from "@/components/links/PayView";
import { TokenPage } from "@/components/links/TokenPage";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** DEMO deposit payment page (link from the "accepted" email / status page). */
export default function Page() {
  return (
    <TokenPage width="md">
      <PayView />
    </TokenPage>
  );
}
