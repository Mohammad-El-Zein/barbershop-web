package com.barbershop.booking;

import com.barbershop.common.i18n.Lang;
import com.barbershop.service.BarberService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One service of a booking. Name, duration and price are copied at booking time,
 * so later catalogue changes never alter an existing booking.
 */
@Entity
@Table(name = "booking_item")
public class BookingItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "name_de", nullable = false, updatable = false)
    private String nameDe;

    @Column(name = "name_en", nullable = false, updatable = false)
    private String nameEn;

    @Column(name = "duration_min", nullable = false, updatable = false)
    private int durationMin;

    @Column(name = "price_cents", nullable = false, updatable = false)
    private int priceCents;

    @Column(name = "price_from", nullable = false, updatable = false)
    private boolean priceFrom;

    @Column(nullable = false, updatable = false)
    private int position;

    protected BookingItem() {}

    BookingItem(Booking booking, BarberService service, int position) {
        this.id = UUID.randomUUID();
        this.booking = booking;
        this.serviceId = service.getId();
        this.nameDe = service.getNameDe();
        this.nameEn = service.getNameEn();
        this.durationMin = service.getDurationMin();
        this.priceCents = service.getPriceCents();
        this.priceFrom = service.isPriceFrom();
        this.position = position;
    }

    public String name(Lang lang) {
        return lang.pick(nameDe, nameEn);
    }

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public String getNameDe() { return nameDe; }
    public String getNameEn() { return nameEn; }
    public int getDurationMin() { return durationMin; }
    public int getPriceCents() { return priceCents; }
    public boolean isPriceFrom() { return priceFrom; }
    public int getPosition() { return position; }
}
