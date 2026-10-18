import { VerifyView } from "@/components/account/VerifyView";
import { TokenPage } from "@/components/links/TokenPage";

/** Email-link pages are personal: never indexed. */
export const metadata = { robots: { index: false, follow: false } };

/** Confirm the email address (link from the verification email). */
export default function Page() {
  return (
    <TokenPage width="sm">
      <VerifyView />
    </TokenPage>
  );
}
