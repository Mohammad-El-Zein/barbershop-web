import { AccountDashboard } from "@/components/account/AccountDashboard";
import { RequireAuth } from "@/components/account/RequireAuth";
import { PageShell } from "@/components/PageShell";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** My account: loyalty progress + my bookings. */
export default function Page() {
  return (
    <PageShell width="md">
      <RequireAuth roles={["CUSTOMER"]}>
        <AccountDashboard />
      </RequireAuth>
    </PageShell>
  );
}
