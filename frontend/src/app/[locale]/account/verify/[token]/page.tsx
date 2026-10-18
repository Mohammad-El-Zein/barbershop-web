import { VerifyView } from "@/components/account/VerifyView";
import { TokenPage } from "@/components/links/TokenPage";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** Confirm the email address (link from the verification email). */
export default function Page() {
  return (
    <TokenPage width="sm">
      <VerifyView />
    </TokenPage>
  );
}
