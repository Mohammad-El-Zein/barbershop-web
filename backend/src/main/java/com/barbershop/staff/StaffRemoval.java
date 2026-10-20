package com.barbershop.staff;

import com.barbershop.auth.AppUser;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.auth.Role;
import com.barbershop.booking.BookingRepository;
import com.barbershop.common.error.ApiException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a staff member for good: only one who never had a booking (not even a past, declined
 * or cancelled one; bookings keep referencing their barber). Otherwise 409
 * {@code staff.has_bookings} — deactivate instead. Removed with it: service assignments,
 * working hours, time off (database cascade) and the barber's login with its sessions and
 * tokens.
 */
@Service
public class StaffRemoval {

    private static final Logger log = LoggerFactory.getLogger(StaffRemoval.class);

    private final StaffRepository staff;
    private final BookingRepository bookings;
    private final AppUserRepository users;

    public StaffRemoval(StaffRepository staff, BookingRepository bookings, AppUserRepository users) {
        this.staff = staff;
        this.bookings = bookings;
        this.users = users;
    }

    @Transactional
    public void delete(UUID tenantId, UUID staffId) {
        // locked first: a booking being created for this barber waits, then fails on the missing row
        Staff s = staff.findByIdAndTenantIdForUpdate(staffId, tenantId)
                .orElseThrow(() -> ApiException.notFound("staff.not_found"));
        if (bookings.existsForStaff(s.getId())) throw ApiException.conflict("staff.has_bookings");
        // refresh / action tokens of the login cascade; a non-barber account is only unlinked (FK)
        users.findByStaffId(s.getId()).stream().filter(u -> u.getRole() == Role.BARBER).forEach(this::deleteLogin);
        staff.delete(s);
        staff.flush();
        log.info("Staff member {} deleted (no bookings)", s.getId());
    }

    private void deleteLogin(AppUser u) {
        users.delete(u);
        log.info("Barber account {} deleted with its staff member", u.getId());
    }
}
