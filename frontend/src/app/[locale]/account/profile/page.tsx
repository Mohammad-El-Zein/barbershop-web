import { ProfileView } from "@/components/account/ProfileView";
import { RequireAuth } from "@/components/account/RequireAuth";
import { PageShell } from "@/components/PageShell";

export const metadata = { robots: { index: false } };

/** Profile, password, GDPR export / delete (also used by barbers and admins for their own profile). */
export default function Page() {
  return (
    <PageShell width="md">
      <RequireAuth>
        <ProfileView />
      </RequireAuth>
    </PageShell>
  );
}
