import { Nav } from "@/components/Nav";
import { StaffShell } from "@/components/staff/StaffShell";

export const metadata = { robots: { index: false, follow: false } };

/** Staff area (barbers + admins): own nav, wide layout, no marketing footer. */
export default function AdminLayout({ children }: LayoutProps<"/[locale]/admin">) {
  return (
    <>
      <Nav hasScene={false} />
      <main id="main" className="mx-auto w-full max-w-7xl px-4 pb-20 pt-[calc(var(--nav-h)+2rem)] sm:px-6">
        <StaffShell>{children}</StaffShell>
      </main>
    </>
  );
}
