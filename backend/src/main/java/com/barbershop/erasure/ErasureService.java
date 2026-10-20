package com.barbershop.erasure;

import com.barbershop.auth.AppUser;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.auth.CurrentUser;
import com.barbershop.auth.Role;
import com.barbershop.booking.BookingStatus;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Messages;
import com.barbershop.common.retention.BookingAnonymisation;
import com.barbershop.erasure.ErasureDtos.CustomerRef;
import com.barbershop.erasure.ErasureDtos.DeletionPreview;
import com.barbershop.erasure.ErasureDtos.DeletionResult;
import com.barbershop.staff.Staff;
import com.barbershop.staff.StaffRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place where an admin deletes bookings — alone, with a barber, with a service or as a
 * customer's history. Each call is one transaction, tenant-scoped, and logs an audit row
 * (admin_audit: ids and counts, never personal data).
 *
 * <p>Status groups: <b>active</b> (PENDING_BARBER, AWAITING_PAYMENT, CONFIRMED) are never deleted
 * — cancel first; <b>dead ends</b> (CANCELLED, DECLINED, EXPIRED); <b>history</b> (COMPLETED,
 * NO_SHOW). A single booking and a customer's history may delete dead ends and history; a barber
 * or a service only goes when every booking with it is a dead end — history means real work and
 * stays (deactivate instead).
 *
 * <p>Erasing a booking:
 * <ul>
 *   <li>no money moved (no payment, or one still UNPAID): deleted completely — items, the unpaid
 *       payment, its tokens (database cascade) and its mails;</li>
 *   <li>a deposit was paid (PAID, REFUNDED, FORFEITED): the payment and its events are the
 *       bookkeeping record and must stay, so the booking is anonymised like the retention job
 *       does ({@link BookingAnonymisation}), unlinked from the customer's account, marked
 *       {@code deleted_at} (hidden from every view, see Booking) and its email-link tokens and
 *       mails are removed.</li>
 * </ul>
 * Mails of the booking still in the outbox are deleted (their content is personal data), except
 * one being sent this very moment: the worker finishes it, unlinked from the booking (a plain
 * delivery log row, removed by the retention job).
 *
 * <p>Locking: the barber / service row first ({@code FOR UPDATE}), then the bookings in id order.
 * A new booking has to key-share-lock its barber and services (foreign keys), so it either waits
 * and then fails on the deleted row, or commits first and is seen by the check below.
 */
@Service
public class ErasureService {

    private static final Logger log = LoggerFactory.getLogger(ErasureService.class);

    static final Set<BookingStatus> ACTIVE =
            EnumSet.of(BookingStatus.PENDING_BARBER, BookingStatus.AWAITING_PAYMENT, BookingStatus.CONFIRMED);
    static final Set<BookingStatus> HISTORY = EnumSet.of(BookingStatus.COMPLETED, BookingStatus.NO_SHOW);

    /** A live booking: its status and whether money moved (a payment that is not UNPAID). */
    private record Row(UUID id, BookingStatus status, boolean money) {
        boolean active() { return ACTIVE.contains(status); }
        boolean history() { return HISTORY.contains(status); }
    }

    private record Scope(String where, Map<String, Object> params) {}

    private record Counts(int deleted, int anonymised) {}

    private final JdbcClient jdbc;
    private final StaffRepository staff;
    private final AppUserRepository users;
    private final Messages messages;
    private final Clock clock;

    public ErasureService(JdbcClient jdbc, StaffRepository staff, AppUserRepository users, Messages messages,
                          Clock clock) {
        this.jdbc = jdbc;
        this.staff = staff;
        this.users = users;
        this.messages = messages;
        this.clock = clock;
    }

    // ── a single booking ─────────────────────────────────────────────────────

    @Transactional
    public DeletionResult deleteBooking(CurrentUser admin, UUID bookingId) {
        List<Row> rows = bookings(scope(admin, "b.id = :id", "id", bookingId), true);
        if (rows.isEmpty()) throw ApiException.notFound("booking.not_found");
        if (rows.get(0).active()) throw ApiException.conflict("booking.erase_active");
        Counts c = erase(rows);
        audit(admin, "BOOKING_DELETED", "BOOKING", bookingId, c);
        return new DeletionResult(c.deleted(), c.anonymised(), 0);
    }

    // ── a barber ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeletionPreview previewStaff(CurrentUser admin, UUID staffId) {
        staff.findByIdAndTenantId(staffId, admin.tenantId()).orElseThrow(() -> ApiException.notFound("staff.not_found"));
        return preview(bookings(scope(admin, "b.staff_id = :staff", "staff", staffId), false), true);
    }

    /**
     * Deletes the barber with services, working hours, time off (cascade) and the barber's login;
     * only when all its bookings are dead ends (erased first). Anonymised bookings that outlive
     * it lose the reference (staff_id NULL). 409 staff.has_active_bookings / has_booking_history.
     */
    @Transactional
    public DeletionResult deleteStaff(CurrentUser admin, UUID staffId) {
        staff.lockForDeletion(staffId, admin.tenantId()).orElseThrow(() -> ApiException.notFound("staff.not_found"));
        Staff s = staff.findById(staffId).orElseThrow();
        List<Row> rows = bookings(scope(admin, "b.staff_id = :staff", "staff", staffId), true);
        requireOnlyDeadEnds(rows, "staff");
        Counts c = erase(rows);
        jdbc.sql("update booking set staff_id = null where staff_id = :staff and deleted_at is not null")
                .param("staff", staffId).update();
        // refresh / action tokens of the login cascade; a non-barber account is only unlinked (FK)
        users.findByStaffId(s.getId()).stream().filter(u -> u.getRole() == Role.BARBER).forEach(this::deleteLogin);
        staff.delete(s);
        staff.flush();
        audit(admin, "STAFF_DELETED", "STAFF", staffId, c);
        return new DeletionResult(c.deleted(), c.anonymised(), 0);
    }

    private void deleteLogin(AppUser u) {
        users.delete(u);
        log.info("Barber account {} deleted with its staff member", u.getId());
    }

    // ── a service ────────────────────────────────────────────────────────────

    private static final String WITH_SERVICE =
            "exists (select 1 from booking_item i where i.booking_id = b.id and i.service_id = :service)";

    @Transactional(readOnly = true)
    public DeletionPreview previewService(CurrentUser admin, UUID serviceId) {
        requireService(admin, serviceId, false);
        return preview(bookings(scope(admin, WITH_SERVICE, "service", serviceId), false), true);
    }

    /**
     * Deletes the service (also from the barbers offering it) when every booking containing it
     * is a dead end (erased first). Items of anonymised bookings keep their frozen name and
     * price, only the reference goes (service_id NULL). 409 service.has_active_bookings / has_booking_history.
     */
    @Transactional
    public DeletionResult deleteService(CurrentUser admin, UUID serviceId) {
        requireService(admin, serviceId, true);
        List<Row> rows = bookings(scope(admin, WITH_SERVICE, "service", serviceId), true);
        requireOnlyDeadEnds(rows, "service");
        Counts c = erase(rows);
        jdbc.sql("update booking_item set service_id = null where service_id = :service")
                .param("service", serviceId).update();
        jdbc.sql("delete from service where id = :service").param("service", serviceId).update();
        audit(admin, "SERVICE_DELETED", "SERVICE", serviceId, c);
        return new DeletionResult(c.deleted(), c.anonymised(), 0);
    }

    private void requireService(CurrentUser admin, UUID serviceId, boolean lock) {
        boolean found = jdbc.sql("select id from service where id = :id and tenant_id = :tenant"
                        + (lock ? " for update" : ""))
                .param("id", serviceId).param("tenant", admin.tenantId())
                .query(UUID.class).optional().isPresent();
        if (!found) throw ApiException.notFound("service.not_found");
    }

    // ── a customer's history ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeletionPreview previewCustomer(CurrentUser admin, CustomerRef customer) {
        List<Row> rows = bookings(customerScope(admin, customer), false);
        DeletionPreview p = preview(rows, false);
        if (rows.isEmpty() && customer.userId() == null) throw ApiException.notFound("customer.not_found");
        return p;
    }

    /** Erases every booking of the customer that is not active; the account itself stays. */
    @Transactional
    public DeletionResult deleteCustomerHistory(CurrentUser admin, CustomerRef customer) {
        List<Row> rows = bookings(customerScope(admin, customer), true);
        if (rows.isEmpty() && customer.userId() == null) throw ApiException.notFound("customer.not_found");
        List<Row> erasable = rows.stream().filter(r -> !r.active()).toList();
        Counts c = erase(erasable);
        audit(admin, "CUSTOMER_HISTORY_DELETED", customer.userId() != null ? "CUSTOMER" : "GUEST",
                customer.userId(), c);
        return new DeletionResult(c.deleted(), c.anonymised(), rows.size() - erasable.size());
    }

    private Scope customerScope(CurrentUser admin, CustomerRef customer) {
        boolean byAccount = customer.userId() != null;
        boolean byEmail = customer.email() != null && !customer.email().isBlank();
        if (byAccount == byEmail) throw ApiException.badRequest("customer.ref_invalid");
        if (byAccount) {
            boolean exists = jdbc.sql("""
                            select count(*) > 0 from app_user
                            where id = :user and tenant_id = :tenant and role = 'CUSTOMER'""")
                    .param("user", customer.userId()).param("tenant", admin.tenantId())
                    .query(Boolean.class).single();
            if (!exists) throw ApiException.notFound("customer.not_found");
            return scope(admin, "b.user_id = :user", "user", customer.userId());
        }
        // a guest = the bookings without account under this email (as in the customer list)
        return scope(admin, "b.user_id is null and lower(b.customer_email) = lower(:email)", "email",
                customer.email().trim());
    }

    // ── shared ───────────────────────────────────────────────────────────────

    private static Scope scope(CurrentUser admin, String where, String name, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenant", admin.tenantId());
        params.put(name, value);
        return new Scope(where, params);
    }

    /** The live bookings of the scope (this tenant only); locked in id order when {@code lock}. */
    private List<Row> bookings(Scope scope, boolean lock) {
        return jdbc.sql("""
                        select b.id, b.status,
                               exists (select 1 from payment p where p.booking_id = b.id
                                       and (p.status <> 'UNPAID' or p.paid_at is not null)) as money
                        from booking b
                        where b.tenant_id = :tenant and b.deleted_at is null and (%s)
                        order by b.id%s""".formatted(scope.where(), lock ? " for update of b" : ""))
                .params(scope.params())
                .query((rs, i) -> new Row(rs.getObject("id", UUID.class), BookingStatus.valueOf(rs.getString("status")),
                        rs.getBoolean("money")))
                .list();
    }

    /** Barber / service: allowed only with dead ends; a customer: everything but active bookings. */
    private static DeletionPreview preview(List<Row> rows, boolean onlyDeadEnds) {
        int active = (int) rows.stream().filter(Row::active).count();
        int history = (int) rows.stream().filter(Row::history).count();
        List<Row> erasable = rows.stream().filter(r -> !r.active() && !(onlyDeadEnds && r.history())).toList();
        int anonymised = (int) erasable.stream().filter(Row::money).count();
        boolean allowed = onlyDeadEnds ? active == 0 && history == 0 : !erasable.isEmpty();
        return new DeletionPreview(allowed, erasable.size() - anonymised, anonymised, active, history);
    }

    private static void requireOnlyDeadEnds(List<Row> rows, String what) {
        long active = rows.stream().filter(Row::active).count();
        if (active > 0) throw ApiException.conflict(what + ".has_active_bookings", active);
        long history = rows.stream().filter(Row::history).count();
        if (history > 0) throw ApiException.conflict(what + ".has_booking_history", history);
    }

    /** Deletes bookings without money movement, anonymises the others (see the class comment). */
    private Counts erase(List<Row> rows) {
        if (rows.isEmpty()) return new Counts(0, 0);
        List<UUID> all = rows.stream().map(Row::id).toList();
        List<UUID> delete = rows.stream().filter(r -> !r.money()).map(Row::id).toList();
        List<UUID> anonymise = rows.stream().filter(Row::money).map(Row::id).toList();

        jdbc.sql("delete from mail_outbox where booking_id in (:ids) and status <> 'SENDING'")
                .param("ids", all).update();
        // a mail being sent right now: the worker finishes it; it stays only as a delivery log row
        jdbc.sql("update mail_outbox set booking_id = null where booking_id in (:ids)").param("ids", all).update();
        if (!delete.isEmpty()) {
            // cascades: booking_item, payment (UNPAID) + payment_event, action_token
            jdbc.sql("delete from booking where id in (:ids)").param("ids", delete).update();
        }
        if (!anonymise.isEmpty()) {
            Instant now = Instant.now(clock);
            jdbc.sql("""
                            delete from action_token
                            where booking_id in (:ids)
                               or payment_id in (select p.id from payment p where p.booking_id in (:ids))""")
                    .param("ids", anonymise).update();
            jdbc.sql("update loyalty_account set reserved_booking_id = null where reserved_booking_id in (:ids)")
                    .param("ids", anonymise).update();
            jdbc.sql("update booking set " + BookingAnonymisation.SET_CLAUSE + """
                            , user_id = null, consent_at = null, deleted_at = :now, updated_at = :now
                            where id in (:ids)""")
                    .param("placeholder", messages.get(Locale.GERMAN, BookingAnonymisation.PLACEHOLDER_KEY))
                    .param("now", Timestamp.from(now))
                    .param("ids", anonymise)
                    .update();
        }
        return new Counts(delete.size(), anonymise.size());
    }

    private void audit(CurrentUser admin, String action, String targetType, UUID targetId, Counts c) {
        jdbc.sql("""
                        insert into admin_audit (id, tenant_id, actor_user_id, action, target_type, target_id,
                                                 bookings_deleted, bookings_anonymised, created_at)
                        values (:id, :tenant, :actor, :action, :type, :target, :deleted, :anonymised, :now)""")
                .param("id", UUID.randomUUID())
                .param("tenant", admin.tenantId())
                .param("actor", admin.userId())
                .param("action", action)
                .param("type", targetType)
                .param("target", targetId)
                .param("deleted", c.deleted())
                .param("anonymised", c.anonymised())
                .param("now", Timestamp.from(Instant.now(clock)))
                .update();
        // ids and counts only — never names, emails or phone numbers
        log.info("Admin {} {} {} {}: {} booking(s) deleted, {} anonymised", admin.userId(), action, targetType,
                targetId, c.deleted(), c.anonymised());
    }
}
