import { ResetView } from "@/components/account/ResetView";
import { TokenPage } from "@/components/links/TokenPage";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** Set a new password (reset / barber invite link). */
export default function Page() {
  return (
    <TokenPage width="sm">
      <ResetView />
    </TokenPage>
  );
}
