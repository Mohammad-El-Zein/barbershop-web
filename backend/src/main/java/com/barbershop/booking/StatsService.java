package com.barbershop.booking;

import com.barbershop.booking.BookingDtos.CampaignRow;
import com.barbershop.booking.BookingDtos.CampaignStatsView;
import com.barbershop.booking.BookingDtos.DayCount;
import com.barbershop.booking.BookingDtos.ServiceCount;
import com.barbershop.booking.BookingDtos.StatsView;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard numbers: bookings per local day (shop timezone) and per service in a
 * date range, split into booked (slot-blocking: requested, awaiting payment, confirmed,
 * completed) and lost (declined, expired, cancelled, no-show). Plain SQL
 * because grouping by local day needs AT TIME ZONE.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final JdbcClient jdbc;
    private final TenantService tenants;
    private final Clock clock;

    public StatsService(JdbcClient jdbc, TenantService tenants, Clock clock) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.clock = clock;
    }

    public StatsView stats(UUID tenantId, LocalDate from, LocalDate to) {
        Tenant tenant = tenants.require(tenantId);
        ZoneId zone = tenant.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(29);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 366) {
            throw ApiException.unprocessable("stats.invalid_range");
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();

        List<DayCount> perDay = jdbc.sql("""
                        select (b.start_time at time zone :tz)::date as day,
                               count(*) filter (where b.status in ('PENDING_BARBER','AWAITING_PAYMENT','CONFIRMED','COMPLETED')) as booked,
                               count(*) filter (where b.status not in ('PENDING_BARBER','AWAITING_PAYMENT','CONFIRMED','COMPLETED')) as cancelled
                        from booking b
                        where b.tenant_id = :tenant and b.start_time >= :from and b.start_time < :to
                        group by 1 order by 1""")
                .param("tz", tenant.getTimezone())
                .param("tenant", tenantId)
                .param("from", java.sql.Timestamp.from(fromInstant))
                .param("to", java.sql.Timestamp.from(toInstant))
                .query((rs, i) -> new DayCount(((Date) rs.getObject("day")).toLocalDate(),
                        rs.getLong("booked"), rs.getLong("cancelled")))
                .list();

        Lang lang = Messages.currentLang();
        List<ServiceCount> perService = jdbc.sql("""
                        select s.id, s.name_de, s.name_en,
                               count(*) filter (where b.status in ('PENDING_BARBER','AWAITING_PAYMENT','CONFIRMED','COMPLETED')) as booked,
                               count(*) filter (where b.status not in ('PENDING_BARBER','AWAITING_PAYMENT','CONFIRMED','COMPLETED')) as cancelled
                        from booking b join booking_item i on i.booking_id = b.id join service s on s.id = i.service_id
                        where b.tenant_id = :tenant and b.start_time >= :from and b.start_time < :to
                        group by s.id, s.name_de, s.name_en
                        order by booked desc, s.name_de""")
                .param("tenant", tenantId)
                .param("from", java.sql.Timestamp.from(fromInstant))
                .param("to", java.sql.Timestamp.from(toInstant))
                .query((rs, i) -> new ServiceCount(rs.getObject("id", UUID.class),
                        lang.pick(rs.getString("name_de"), rs.getString("name_en")),
                        rs.getLong("booked"), rs.getLong("cancelled")))
                .list();

        long booked = perDay.stream().mapToLong(DayCount::booked).sum();
        long cancelled = perDay.stream().mapToLong(DayCount::cancelled).sum();
        return new StatsView(start, end, tenant.getTimezone(), booked, cancelled, perDay, perService);
    }

    /** Online bookings created in [from, to] (shop-local dates), grouped by first-touch campaign. */
    public CampaignStatsView campaigns(UUID tenantId, LocalDate from, LocalDate to) {
        Tenant tenant = tenants.require(tenantId);
        ZoneId zone = tenant.zone();
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : end.minusDays(29);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 366) {
            throw ApiException.unprocessable("stats.invalid_range");
        }
        List<CampaignRow> rows = jdbc.sql("""
                        select coalesce(b.attr_source,
                                        case when b.attr_gclid is not null then 'google'
                                             when b.attr_fbclid is not null then 'facebook' end,
                                        '(direct)') as source,
                               coalesce(b.attr_medium,
                                        case when b.attr_gclid is not null then 'cpc'
                                             when b.attr_fbclid is not null then 'paid_social' end,
                                        '(none)') as medium,
                               coalesce(b.attr_campaign,
                                        case when b.attr_landing_path ~ '^/[a-z]{2}/lp/[^/?#]+'
                                             then 'lp:' || split_part(b.attr_landing_path, '/', 4) end,
                                        '(not set)') as campaign,
                               count(*) as bookings,
                               count(*) filter (where b.confirmed_at is not null) as confirmed,
                               coalesce(sum(b.price_total_cents - b.loyalty_discount_cents)
                                        filter (where b.confirmed_at is not null), 0) as revenue
                        from booking b
                        where b.tenant_id = :tenant and b.source = 'ONLINE'
                          and b.created_at >= :from and b.created_at < :to
                        group by 1, 2, 3
                        order by bookings desc, confirmed desc, 1, 2, 3""")
                .param("tenant", tenantId)
                .param("from", java.sql.Timestamp.from(start.atStartOfDay(zone).toInstant()))
                .param("to", java.sql.Timestamp.from(end.plusDays(1).atStartOfDay(zone).toInstant()))
                .query((rs, i) -> new CampaignRow(rs.getString("source"), rs.getString("medium"),
                        rs.getString("campaign"), rs.getLong("bookings"), rs.getLong("confirmed"),
                        rs.getLong("revenue")))
                .list();
        long total = rows.stream().mapToLong(CampaignRow::bookings).sum();
        long direct = rows.stream().filter(r -> "(direct)".equals(r.source()) && "(not set)".equals(r.campaign()))
                .mapToLong(CampaignRow::bookings).sum();
        return new CampaignStatsView(start, end, tenant.getTimezone(), tenant.getCurrency(), total, total - direct, rows);
    }
}
