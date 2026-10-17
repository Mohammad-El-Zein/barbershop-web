package com.barbershop.service;

import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.service.ServiceDtos.ServiceAdminView;
import com.barbershop.service.ServiceDtos.ServiceRequest;
import com.barbershop.service.ServiceDtos.ServiceView;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for barber services (public listing + admin CRUD). */
@Service
@Transactional(readOnly = true)
public class ServiceCatalog {

    private final BarberServiceRepository services;
    private final TenantService tenants;
    private final PriceFormatter prices;

    public ServiceCatalog(BarberServiceRepository services, TenantService tenants, PriceFormatter prices) {
        this.services = services;
        this.tenants = tenants;
        this.prices = prices;
    }

    public List<ServiceView> listActive(String tenantSlug) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        Lang lang = Messages.currentLang();
        return services.findByTenantIdAndActiveTrueOrderBySortOrderAscNameDeAsc(tenant.getId()).stream()
                .map(s -> view(s, tenant, lang))
                .toList();
    }

    public ServiceView view(BarberService s, Tenant tenant, Lang lang) {
        return new ServiceView(s.getId(), s.name(lang), s.description(lang), s.getDurationMin(),
                prices.view(s.getPriceCents(), tenant.getCurrency(), s.isPriceFrom(), lang));
    }

    /** An active service of this tenant, or 404. */
    public BarberService requireActive(UUID tenantId, UUID serviceId) {
        return services.findByIdAndTenantId(serviceId, tenantId)
                .filter(BarberService::isActive)
                .orElseThrow(() -> ApiException.notFound("service.not_found"));
    }

    /**
     * The chosen services of one booking (e.g. haircut + beard), in the given order.
     * 1 … {@code max} distinct active services of this tenant, otherwise 404/422.
     */
    public List<BarberService> requireActiveAll(UUID tenantId, List<UUID> serviceIds, int max) {
        if (serviceIds == null || serviceIds.isEmpty()) throw ApiException.unprocessable("service.none_selected");
        if (serviceIds.size() > max) throw ApiException.unprocessable("service.too_many", max);
        if (new HashSet<>(serviceIds).size() != serviceIds.size()) {
            throw ApiException.unprocessable("service.duplicate");
        }
        return serviceIds.stream().map(id -> requireActive(tenantId, id)).toList();
    }

    // ── admin ────────────────────────────────────────────────────────────────

    public List<ServiceAdminView> adminList(UUID tenantId) {
        return services.findByTenantIdOrderBySortOrderAscNameDeAsc(tenantId).stream().map(ServiceAdminView::of).toList();
    }

    public ServiceAdminView adminGet(UUID tenantId, UUID id) {
        return ServiceAdminView.of(require(tenantId, id));
    }

    @Transactional
    public ServiceAdminView create(UUID tenantId, ServiceRequest req) {
        BarberService s = new BarberService(tenantId);
        apply(s, req);
        return ServiceAdminView.of(services.save(s));
    }

    @Transactional
    public ServiceAdminView update(UUID tenantId, UUID id, ServiceRequest req) {
        BarberService s = require(tenantId, id);
        apply(s, req);
        return ServiceAdminView.of(s);
    }

    /** Soft delete: past bookings keep referencing the service. */
    @Transactional
    public void deactivate(UUID tenantId, UUID id) {
        require(tenantId, id).setActive(false);
    }

    private BarberService require(UUID tenantId, UUID id) {
        return services.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("service.not_found"));
    }

    private static void apply(BarberService s, ServiceRequest req) {
        s.setNameDe(req.nameDe().trim());
        s.setNameEn(req.nameEn().trim());
        s.setDescriptionDe(req.descriptionDe());
        s.setDescriptionEn(req.descriptionEn());
        s.setDurationMin(req.durationMin());
        s.setPriceCents(req.priceCents());
        s.setPriceFrom(req.priceFrom());
        if (req.active() != null) s.setActive(req.active());
        if (req.sortOrder() != null) s.setSortOrder(req.sortOrder());
    }
}
