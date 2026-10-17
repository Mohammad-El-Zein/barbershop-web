package com.barbershop.service;

import static com.barbershop.auth.AdminPrincipal.tenantId;

import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.service.ServiceDtos.ServiceAdminView;
import com.barbershop.service.ServiceDtos.ServiceRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/services")
@Tag(name = "Admin: services")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminServiceController {

    private final ServiceCatalog catalog;

    public AdminServiceController(ServiceCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ServiceAdminView> list(@AuthenticationPrincipal Jwt jwt) {
        return catalog.adminList(tenantId(jwt));
    }

    @GetMapping("/{id}")
    public ServiceAdminView get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return catalog.adminGet(tenantId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceAdminView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ServiceRequest req) {
        return catalog.create(tenantId(jwt), req);
    }

    @PutMapping("/{id}")
    public ServiceAdminView update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
                                   @Valid @RequestBody ServiceRequest req) {
        return catalog.update(tenantId(jwt), id, req);
    }

    /** Deactivates the service (soft delete). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        catalog.deactivate(tenantId(jwt), id);
    }
}
