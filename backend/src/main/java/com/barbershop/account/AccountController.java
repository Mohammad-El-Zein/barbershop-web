package com.barbershop.account;

import com.barbershop.account.AccountDtos.AcceptedResponse;
import com.barbershop.account.AccountDtos.ChangePasswordRequest;
import com.barbershop.account.AccountDtos.EmailRequest;
import com.barbershop.account.AccountDtos.RegisterRequest;
import com.barbershop.account.AccountDtos.ResetPasswordRequest;
import com.barbershop.account.AccountDtos.TokenRequest;
import com.barbershop.account.AccountDtos.VerifyResult;
import com.barbershop.auth.AuthDtos.SessionResponse;
import com.barbershop.auth.CurrentUser;
import com.barbershop.auth.SessionResponses;
import com.barbershop.common.config.OpenApiConfig;
import com.barbershop.common.i18n.Messages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public account endpoints. register / resend / forgot always answer 202 with the
 * same message (no user enumeration). Email links open frontend pages; only the
 * page's button calls verify / reset (POST).
 */
@RestController
@Tag(name = "Auth: account")
public class AccountController {

    private final AccountService accounts;
    private final SessionResponses sessions;
    private final Messages messages;

    public AccountController(AccountService accounts, SessionResponses sessions, Messages messages) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.messages = messages;
    }

    @PostMapping("/api/public/{tenant}/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Register a customer account (consent required). Always 202 — check your email.")
    public AcceptedResponse register(@PathVariable("tenant") String tenant, @Valid @RequestBody RegisterRequest req,
                                     Locale locale) {
        accounts.register(tenant, req, locale);
        return new AcceptedResponse(messages.get("account.check_email"));
    }

    @PostMapping("/api/public/{tenant}/auth/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponse resend(@PathVariable("tenant") String tenant, @Valid @RequestBody EmailRequest req) {
        accounts.resendVerification(tenant, req.email());
        return new AcceptedResponse(messages.get("account.check_email"));
    }

    @PostMapping("/api/public/auth/verify-email")
    @Operation(summary = "Confirm the email (single-use link token). Links earlier guest bookings with that email.")
    public VerifyResult verify(@Valid @RequestBody TokenRequest req) {
        return accounts.verifyEmail(req.token());
    }

    @PostMapping("/api/public/{tenant}/auth/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedResponse forgot(@PathVariable("tenant") String tenant, @Valid @RequestBody EmailRequest req) {
        accounts.forgotPassword(tenant, req.email());
        return new AcceptedResponse(messages.get("account.reset_sent"));
    }

    @PostMapping("/api/public/auth/reset-password")
    @Operation(summary = "Set a new password with the reset/invite link token (single use). Ends all sessions.")
    public AcceptedResponse reset(@Valid @RequestBody ResetPasswordRequest req) {
        accounts.resetPassword(req.token(), req.newPassword());
        return new AcceptedResponse(messages.get("account.password_changed"));
    }

    /** Under /api/auth because it sets the refresh cookie (Bearer token required, see SecurityConfig). */
    @PostMapping("/api/auth/password")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Change password: all other sessions end, this device gets a fresh session + cookie")
    public ResponseEntity<SessionResponse> changePassword(CurrentUser me, @Valid @RequestBody ChangePasswordRequest req) {
        return sessions.of(accounts.changePassword(me, req.currentPassword(), req.newPassword()));
    }
}
