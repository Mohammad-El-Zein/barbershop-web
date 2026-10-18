import { ManageView } from "@/components/links/ManageView";
import { TokenPage } from "@/components/links/TokenPage";

/** Email-link pages are personal: never indexed. */
export const metadata = { robots: { index: false, follow: false } };

/** Customer manage / cancel page (link from the emails). */
export default function Page() {
  return (
    <TokenPage width="md">
      <ManageView />
    </TokenPage>
  );
}
