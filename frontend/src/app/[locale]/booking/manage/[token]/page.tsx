import { ManageView } from "@/components/links/ManageView";
import { TokenPage } from "@/components/links/TokenPage";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** Customer manage / cancel page (link from the emails). */
export default function Page() {
  return (
    <TokenPage width="md">
      <ManageView />
    </TokenPage>
  );
}
