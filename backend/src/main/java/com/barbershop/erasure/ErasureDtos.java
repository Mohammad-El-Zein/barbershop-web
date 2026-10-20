package com.barbershop.erasure;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ErasureDtos {

    private ErasureDtos() {}

    /**
     * What a deletion would do. {@code deleted}: bookings removed completely (no money moved);
     * {@code anonymised}: bookings with a paid / refunded / kept deposit — personal data removed,
     * the payment records stay. {@code active} / {@code history}: bookings that block a barber or
     * service deletion (or, for a customer, stay untouched).
     */
    public record DeletionPreview(boolean allowed, int deleted, int anonymised, int active, int history) {}

    /** What a deletion did; {@code keptActive}: active bookings of a customer that were left alone. */
    public record DeletionResult(int deleted, int anonymised, int keptActive) {}

    /** A customer of the admin list: an account ({@code userId}) or a guest ({@code email}) — exactly one. */
    @Schema(description = "A customer: userId for an account, email for a guest (in the body, never in a URL)")
    public record CustomerRef(UUID userId, @Email @Size(max = 254) String email) {}
}
