import { AdminBookings } from "@/components/staff/AdminBookings";
import { AdminOnly } from "@/components/staff/StaffShell";

export default function Page() {
  return (
    <AdminOnly>
      <AdminBookings />
    </AdminOnly>
  );
}
