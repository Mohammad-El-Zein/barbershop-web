import { PayView } from "@/components/links/PayView";
import { TokenPage } from "@/components/links/TokenPage";

/** Email-link pages are personal: never indexed. */
export const metadata = { robots: { index: false, follow: false } };

/** DEMO deposit payment page (link from the "accepted" email / status page). */
export default function Page() {
  return (
    <TokenPage width="md">
      <PayView />
    </TokenPage>
  );
}
