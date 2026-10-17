package com.barbershop.service;

import com.barbershop.service.ServiceDtos.ServiceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenant}/services")
@Tag(name = "Public: services")
public class PublicServiceController {

    private final ServiceCatalog catalog;

    public PublicServiceController(ServiceCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Active services, localized (?lang=de|en or Accept-Language)")
    public List<ServiceView> list(@PathVariable("tenant") String tenant) {
        return catalog.listActive(tenant);
    }
}
