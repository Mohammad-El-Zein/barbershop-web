package com.barbershop.account;

import com.barbershop.account.AccountDtos.BarberAccountRequest;
import com.barbershop.account.AccountDtos.BarberAccountView;
import com.barbershop.account.AccountDtos.CustomerRow;
import com.barbershop.auth.CurrentUser;
import com.barbershop.booking.BookingDtos.PageView;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin: accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminAccountController {

    private final AccountService accounts;
    private final CustomerDirectory customers;

    public AdminAccountController(AccountService accounts, CustomerDirectory customers) {
        this.accounts = accounts;
        this.customers = customers;
    }

    @PostMapping("/api/admin/staff/{id}/account")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create the barber's own login for this staff profile; an invite email lets them set "
            + "their password")
    public BarberAccountView createBarberAccount(CurrentUser admin, @PathVariable("id") UUID staffId,
                                                 @Valid @RequestBody BarberAccountRequest req) {
        return accounts.createBarberAccount(admin.tenantId(), staffId, req.email(), req.lang());
    }

    @GetMapping("/api/admin/customers")
    @Operation(summary = "Customers (accounts + guests by email) with completed visits and loyalty state")
    public PageView<CustomerRow> customers(CurrentUser admin,
                                           @RequestParam(name = "q", required = false) String q,
                                           @RequestParam(name = "page", defaultValue = "0") int page,
                                           @RequestParam(name = "size", defaultValue = "50") int size) {
        return customers.list(admin.tenantId(), q, page, size);
    }
}
