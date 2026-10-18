import { ResetView } from "@/components/account/ResetView";
import { TokenPage } from "@/components/links/TokenPage";

/** Email-link pages are personal: never indexed. */
export const metadata = { robots: { index: false, follow: false } };

/** Set a new password (reset / barber invite link). */
export default function Page() {
  return (
    <TokenPage width="sm">
      <ResetView />
    </TokenPage>
  );
}
