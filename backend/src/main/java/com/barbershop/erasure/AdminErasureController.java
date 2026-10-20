package com.barbershop.erasure;

import com.barbershop.auth.CurrentUser;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.erasure.ErasureDtos.CustomerRef;
import com.barbershop.erasure.ErasureDtos.DeletionPreview;
import com.barbershop.erasure.ErasureDtos.DeletionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deleting bookings, barbers, services and a customer's history (admins of the shop only —
 * /api/admin/**). Rules and what is deleted vs. anonymised: {@link ErasureService}.
 */
@RestController
@Tag(name = "Admin: deletion")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminErasureController {

    private final ErasureService erasure;

    public AdminErasureController(ErasureService erasure) {
        this.erasure = erasure;
    }

    @DeleteMapping("/api/admin/bookings/{id}")
    @Operation(summary = "Deletes a booking that is not active (cancelled, declined, expired, completed, no-show). "
            + "Without money movement it is removed completely; with a paid / refunded / kept deposit its personal "
            + "data is removed and the payment records stay. Active: 409 booking.erase_active (cancel first).")
    public DeletionResult deleteBooking(CurrentUser admin, @PathVariable("id") UUID id) {
        return erasure.deleteBooking(admin, id);
    }

    @GetMapping("/api/admin/staff/{id}/deletion")
    @Operation(summary = "What deleting this barber would do (bookings deleted / anonymised, or what blocks it)")
    public DeletionPreview previewStaff(CurrentUser admin, @PathVariable("id") UUID id) {
        return erasure.previewStaff(admin, id);
    }

    @DeleteMapping("/api/admin/staff/{id}/permanently")
    @Operation(summary = "Deletes a barber for good with services, working hours, time off and login. Allowed when "
            + "every booking is cancelled, declined or expired (they are deleted / anonymised with it); otherwise "
            + "409 staff.has_active_bookings / staff.has_booking_history: deactivate instead.")
    public DeletionResult deleteStaff(CurrentUser admin, @PathVariable("id") UUID id) {
        return erasure.deleteStaff(admin, id);
    }

    @GetMapping("/api/admin/services/{id}/deletion")
    @Operation(summary = "What deleting this service would do (bookings deleted / anonymised, or what blocks it)")
    public DeletionPreview previewService(CurrentUser admin, @PathVariable("id") UUID id) {
        return erasure.previewService(admin, id);
    }

    @DeleteMapping("/api/admin/services/{id}/permanently")
    @Operation(summary = "Deletes a service for good (DELETE /{id} only deactivates it). Allowed when it is in no "
            + "booking or only in cancelled, declined or expired ones (deleted / anonymised with it); otherwise "
            + "409 service.has_active_bookings / service.has_booking_history: deactivate instead.")
    public DeletionResult deleteService(CurrentUser admin, @PathVariable("id") UUID id) {
        return erasure.deleteService(admin, id);
    }

    @PostMapping("/api/admin/customers/history/preview")
    @Operation(summary = "What deleting this customer's history would do (POST: the guest email stays out of URLs)")
    public DeletionPreview previewCustomer(CurrentUser admin, @Valid @RequestBody CustomerRef customer) {
        return erasure.previewCustomer(admin, customer);
    }

    @PostMapping("/api/admin/customers/history/delete")
    @Operation(summary = "Deletes / anonymises every booking of the customer that is not active; active bookings and "
            + "the account stay (keptActive)")
    public DeletionResult deleteCustomerHistory(CurrentUser admin, @Valid @RequestBody CustomerRef customer) {
        return erasure.deleteCustomerHistory(admin, customer);
    }
}
