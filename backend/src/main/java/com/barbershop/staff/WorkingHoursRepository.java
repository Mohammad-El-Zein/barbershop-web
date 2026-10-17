package com.barbershop.staff;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

    List<WorkingHours> findByStaffIdInOrderByWeekdayAscStartTimeAsc(Collection<UUID> staffIds);

    List<WorkingHours> findByStaffIdOrderByWeekdayAscStartTimeAsc(UUID staffId);

    @Modifying
    @Query("delete from WorkingHours w where w.staffId = :staffId")
    void deleteByStaffId(@Param("staffId") UUID staffId);
}
