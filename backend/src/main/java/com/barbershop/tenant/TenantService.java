package com.barbershop.tenant;

import com.barbershop.common.error.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves tenants from the public URL slug or from the admin token's tenant id. */
@Service
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public Tenant requireBySlug(String slug) {
        return tenants.findBySlug(slug).orElseThrow(() -> ApiException.notFound("tenant.not_found", slug));
    }

    public Tenant require(UUID id) {
        return tenants.findById(id).orElseThrow(() -> ApiException.notFound("tenant.not_found", id));
    }
}
