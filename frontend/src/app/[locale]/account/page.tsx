import { AccountDashboard } from "@/components/account/AccountDashboard";
import { RequireAuth } from "@/components/account/RequireAuth";
import { PageShell } from "@/components/PageShell";

export const metadata = { robots: { index: false } };

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
