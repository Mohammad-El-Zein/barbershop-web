package com.barbershop.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenant}")
@Tag(name = "Public: shop")
public class TenantController {

    private final TenantService tenants;

    public TenantController(TenantService tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    @Operation(summary = "Shop info (name, timezone, currency, theme)")
    public TenantView get(@PathVariable("tenant") String slug) {
        return TenantView.of(tenants.requireBySlug(slug));
    }

    public record TenantView(String slug, String name, String timezone, String defaultLocale, String currency,
                             String logoUrl, Map<String, Object> theme) {
        static TenantView of(Tenant t) {
            return new TenantView(t.getSlug(), t.getName(), t.getTimezone(), t.getDefaultLocale(), t.getCurrency(),
                    t.getLogoUrl(), t.getTheme());
        }
    }
}
