package com.barbershop.account;

import com.barbershop.account.AccountDtos.CustomerRow;
import com.barbershop.booking.BookingDtos.PageView;
import com.barbershop.loyalty.LoyaltyRules;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin customer list: registered customers (with loyalty state) and guests
 * (bookings without account, grouped by email), with their completed visits.
 */
@Service
@Transactional(readOnly = true)
public class CustomerDirectory {

    private static final String CUSTOMERS = """
            select u.id as user_id, true as registered, u.name, u.email, u.phone,
                   (select count(*) from booking b where b.user_id = u.id and b.status = 'COMPLETED') as completed,
                   coalesce(l.visits_in_cycle, 0) as visits_in_cycle,
                   l.reserved_booking_id is not null as reserved,
                   (select count(*) from booking b where b.user_id = u.id) as total,
                   (select max(b.start_time) from booking b where b.user_id = u.id) as last_booking
            from app_user u left join loyalty_account l on l.user_id = u.id
            where u.tenant_id = :tenant and u.role = 'CUSTOMER'
            union all
            select null, false, max(b.customer_name), lower(b.customer_email), max(b.customer_phone),
                   count(*) filter (where b.status = 'COMPLETED'), 0, false, count(*), max(b.start_time)
            from booking b
            where b.tenant_id = :tenant and b.user_id is null and b.customer_email is not null
            group by lower(b.customer_email)""";

    private final JdbcClient jdbc;
    private final TenantService tenants;

    public CustomerDirectory(JdbcClient jdbc, TenantService tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public PageView<CustomerRow> list(UUID tenantId, String query, int page, int size) {
        Tenant tenant = tenants.require(tenantId);
        ZoneId zone = tenant.zone();
        int threshold = tenant.policy().getLoyaltyThreshold();
        int pageSize = Math.min(Math.max(size, 1), 200);
        int pageNo = Math.max(page, 0);
        String like = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String filter = " where (cast(:like as text) is null or lower(c.name) like :like or c.email like :like"
                + " or c.phone like :like)";

        long total = jdbc.sql("select count(*) from (" + CUSTOMERS + ") c" + filter)
                .param("tenant", tenantId).param("like", like)
                .query(Long.class).single();
        List<CustomerRow> rows = jdbc.sql("select * from (" + CUSTOMERS + ") c" + filter
                        + " order by c.completed desc, lower(c.name) limit :limit offset :offset")
                .param("tenant", tenantId).param("like", like)
                .param("limit", pageSize).param("offset", (long) pageNo * pageSize)
                .query((rs, i) -> {
                    Timestamp last = rs.getTimestamp("last_booking");
                    int visits = rs.getInt("visits_in_cycle");
                    boolean registered = rs.getBoolean("registered");
                    return new CustomerRow(rs.getObject("user_id", UUID.class), registered, rs.getString("name"),
                            rs.getString("email"), rs.getString("phone"), rs.getLong("completed"), visits,
                            registered && LoyaltyRules.discountAvailable(visits, threshold, rs.getBoolean("reserved")),
                            rs.getLong("total"), last == null ? null : last.toInstant().atZone(zone).toOffsetDateTime());
                })
                .list();
        int pages = (int) ((total + pageSize - 1) / pageSize);
        return new PageView<>(rows, pageNo, pageSize, total, pages);
    }
}
