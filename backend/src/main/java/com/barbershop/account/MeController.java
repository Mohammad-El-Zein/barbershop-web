package com.barbershop.account;

import com.barbershop.account.AccountDtos.ExportView;
import com.barbershop.account.AccountDtos.PasswordConfirmation;
import com.barbershop.account.AccountDtos.UpdateProfileRequest;
import com.barbershop.auth.AuthDtos.UserView;
import com.barbershop.auth.CurrentUser;
import com.barbershop.auth.SessionResponses;
import com.barbershop.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Customer: account")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MeController {

    private final AccountService accounts;
    private final SessionResponses sessions;

    public MeController(AccountService accounts, SessionResponses sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @GetMapping
    public UserView me(CurrentUser me) {
        return accounts.me(me);
    }

    @PatchMapping
    @Operation(summary = "Update name, phone, language")
    public UserView update(CurrentUser me, @Valid @RequestBody UpdateProfileRequest req) {
        return accounts.updateProfile(me, req);
    }

    @PostMapping("/delete")
    @Operation(summary = "GDPR: delete my account (password required). Open future bookings are cancelled, "
            + "all bookings anonymised.")
    public ResponseEntity<Void> delete(CurrentUser me, @Valid @RequestBody PasswordConfirmation req) {
        accounts.deleteAccount(me, req.password());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearCookie()).build();
    }

    @GetMapping("/export")
    @Operation(summary = "GDPR: everything stored about me as JSON (download)")
    public ResponseEntity<ExportView> export(CurrentUser me) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-data.json\"")
                .body(accounts.export(me));
    }
}
