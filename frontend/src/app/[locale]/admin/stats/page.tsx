import { AdminStats } from "@/components/staff/AdminSections";
import { AdminOnly } from "@/components/staff/StaffShell";

export default function Page() {
  return (
    <AdminOnly>
      <AdminStats />
    </AdminOnly>
  );
}
