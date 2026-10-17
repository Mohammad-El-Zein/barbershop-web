package com.barbershop.staff;

import com.barbershop.staff.StaffDtos.StaffView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/{tenant}/staff")
@Tag(name = "Public: staff")
public class PublicStaffController {

    private final StaffDirectory directory;

    public PublicStaffController(StaffDirectory directory) {
        this.directory = directory;
    }

    @GetMapping
    @Operation(summary = "Active barbers, localized bio; ?service= filters to those offering it")
    public List<StaffView> list(@PathVariable("tenant") String tenant,
                                @RequestParam(name = "service", required = false) UUID serviceId) {
        return directory.listActive(tenant, serviceId);
    }
}
