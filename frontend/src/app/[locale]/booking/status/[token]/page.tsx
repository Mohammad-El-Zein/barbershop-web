import { StatusView } from "@/components/links/StatusView";
import { TokenPage } from "@/components/links/TokenPage";

/** Email-link pages are personal: never indexed. */
export const metadata = { robots: { index: false, follow: false } };

/** Live booking status right after booking (read-only link). */
export default function Page() {
  return (
    <TokenPage width="md">
      <StatusView />
    </TokenPage>
  );
}
