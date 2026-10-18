import { AdminServices } from "@/components/staff/AdminSections";
import { AdminOnly } from "@/components/staff/StaffShell";

export default function Page() {
  return (
    <AdminOnly>
      <AdminServices />
    </AdminOnly>
  );
}
