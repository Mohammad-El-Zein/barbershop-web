import { NextIntlClientProvider } from "next-intl";
import { getMessages } from "next-intl/server";
import { Nav } from "@/components/Nav";
import { StaffShell } from "@/components/staff/StaffShell";
import { PRIVATE_PAGE } from "@/lib/seo";

/** Personal / private page: never indexed. */
export const metadata = PRIVATE_PAGE;

/** Staff area (barbers + admins): own nav, wide layout, no marketing footer. */
export default async function AdminLayout({ children, params }: LayoutProps<"/[locale]/admin">) {
  const { locale } = await params;
  // all messages incl. "staff" (the public layout leaves that namespace out to keep pages light)
  const messages = await getMessages();
  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <Nav hasScene={false} />
      <main id="main" className="mx-auto w-full max-w-7xl px-4 pb-20 pt-[calc(var(--nav-h)+2rem)] sm:px-6">
        <StaffShell>{children}</StaffShell>
      </main>
    </NextIntlClientProvider>
  );
}
