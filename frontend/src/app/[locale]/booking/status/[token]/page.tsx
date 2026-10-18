import { StatusView } from "@/components/links/StatusView";
import { TokenPage } from "@/components/links/TokenPage";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** Live booking status right after booking (read-only link). */
export default function Page() {
  return (
    <TokenPage width="md">
      <StatusView />
    </TokenPage>
  );
}
