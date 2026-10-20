package com.barbershop.staff;

import java.util.Collection;
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

    /**
     * Row lock before deleting the staff member. Plain FOR UPDATE on purpose: JPA's PESSIMISTIC_WRITE
     * is FOR NO KEY UPDATE on PostgreSQL, which does not conflict with the FOR KEY SHARE lock of a
     * booking being inserted (foreign key) — so the deletion would not wait for it.
     */
    @Query(value = "select id from staff where id = :id and tenant_id = :tenantId for update", nativeQuery = true)
    Optional<UUID> lockForDeletion(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query(value = "select service_id from staff_service where staff_id = :staffId", nativeQuery = true)
    List<UUID> findServiceIds(@Param("staffId") UUID staffId);

    @Query(value = "select staff_id, service_id from staff_service where tenant_id = :tenantId", nativeQuery = true)
    List<Object[]> findAssignments(@Param("tenantId") UUID tenantId);

    @Query(value = "select staff_id from staff_service where service_id = :serviceId", nativeQuery = true)
    List<UUID> findStaffIdsOffering(@Param("serviceId") UUID serviceId);

    /** Staff offering ALL of the given services (count = number of distinct service ids). */
    @Query(value = """
            select staff_id from staff_service where service_id in (:serviceIds)
            group by staff_id having count(distinct service_id) = :count""", nativeQuery = true)
    List<UUID> findStaffIdsOfferingAll(@Param("serviceIds") Collection<UUID> serviceIds, @Param("count") long count);

    @Modifying
    @Query(value = "delete from staff_service where staff_id = :staffId", nativeQuery = true)
    void deleteAssignments(@Param("staffId") UUID staffId);

    @Modifying
    @Query(value = "insert into staff_service (tenant_id, staff_id, service_id) values (:tenantId, :staffId, :serviceId)",
            nativeQuery = true)
    void insertAssignment(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId,
                          @Param("serviceId") UUID serviceId);
}
