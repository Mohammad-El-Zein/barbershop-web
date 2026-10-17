package com.barbershop.account;

import java.util.UUID;

/** Account emails, sent after commit (raw tokens only exist here). */
public final class AccountEvents {

    private AccountEvents() {}

    public record VerificationRequested(UUID userId, String rawToken) {}

    /** invite = a new barber account ("set your password"), otherwise "forgot password". */
    public record PasswordResetRequested(UUID userId, String rawToken, boolean invite) {}

    /** Someone tried to register an existing email: the owner is told (the response looks the same). */
    public record AlreadyRegistered(UUID userId) {}
}
