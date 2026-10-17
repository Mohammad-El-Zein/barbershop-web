package com.barbershop.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Staff + the staff_service link table. The link table carries tenant_id
 * (every table is tenant-scoped), so it is managed with native queries rather
 * than a JPA @ManyToMany.
 */
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    List<Staff> findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID tenantId);

    List<Staff> findByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    Optional<Staff> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = "select service_id from staff_service where staff_id = :staffId", nativeQuery = true)
    List<UUID> findServiceIds(@Param("staffId") UUID staffId);

    @Query(value = "select staff_id, service_id from staff_service where tenant_id = :tenantId", nativeQuery = true)
    List<Object[]> findAssignments(@Param("tenantId") UUID tenantId);

    @Query(value = "select staff_id from staff_service where service_id = :serviceId", nativeQuery = true)
    List<UUID> findStaffIdsOffering(@Param("serviceId") UUID serviceId);

    @Modifying
    @Query(value = "delete from staff_service where staff_id = :staffId", nativeQuery = true)
    void deleteAssignments(@Param("staffId") UUID staffId);

    @Modifying
    @Query(value = "insert into staff_service (tenant_id, staff_id, service_id) values (:tenantId, :staffId, :serviceId)",
            nativeQuery = true)
    void insertAssignment(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId,
                          @Param("serviceId") UUID serviceId);
}
