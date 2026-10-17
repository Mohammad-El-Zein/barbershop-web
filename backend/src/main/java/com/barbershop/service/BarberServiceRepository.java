package com.barbershop.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarberServiceRepository extends JpaRepository<BarberService, UUID> {

    List<BarberService> findByTenantIdAndActiveTrueOrderBySortOrderAscNameDeAsc(UUID tenantId);

    List<BarberService> findByTenantIdOrderBySortOrderAscNameDeAsc(UUID tenantId);

    Optional<BarberService> findByIdAndTenantId(UUID id, UUID tenantId);
}
