package com.barbershop.token;

import com.barbershop.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems single-use email-link tokens.
 *
 * <ul>
 *   <li>{@link #peek} — for GET (show the page): never changes anything.</li>
 *   <li>{@link #consume} — for POST (do the action): locks the row, checks
 *       not used / not expired, marks it used in the caller's transaction, so
 *       the action and the "used" flag commit or roll back together.</li>
 * </ul>
 * Unknown → 404 {@code token.invalid}; used → 410 {@code token.used};
 * expired → 410 {@code token.expired}. All messages are localized.
 */
@Service
public class ActionTokenService {

    private final ActionTokenRepository tokens;
    private final TokenHasher hasher;
    private final Clock clock;

    public ActionTokenService(ActionTokenRepository tokens, TokenHasher hasher, Clock clock) {
        this.tokens = tokens;
        this.hasher = hasher;
        this.clock = clock;
    }

    public record Issued(String raw, ActionToken token) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issue(UUID tenantId, TokenPurpose purpose, UUID bookingId, UUID userId, UUID paymentId,
                        Instant expiresAt) {
        String raw = hasher.generate();
        ActionToken t = new ActionToken(tenantId, purpose, hasher.hash(purpose.name(), raw), bookingId, userId,
                paymentId, expiresAt, Instant.now(clock));
        tokens.save(t);
        return new Issued(raw, t);
    }

    /** Resolves a token without checking used/expired (the caller decides how to present those). */
    @Transactional(readOnly = true)
    public ActionToken find(TokenPurpose purpose, String raw) {
        if (!TokenHasher.plausible(raw)) throw ApiException.notFound("token.invalid");
        return tokens.findByTokenHash(hasher.hash(purpose.name(), raw))
                .orElseThrow(() -> ApiException.notFound("token.invalid"));
    }

    /** For GET: the token must exist, be unused and unexpired — nothing is modified. */
    @Transactional(readOnly = true)
    public ActionToken peek(TokenPurpose purpose, String raw) {
        ActionToken t = find(purpose, raw);
        requireUsable(t);
        return t;
    }

    /** For POST: locks, validates and marks the token used (in the caller's transaction). */
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionToken consume(TokenPurpose purpose, String raw) {
        if (!TokenHasher.plausible(raw)) throw ApiException.notFound("token.invalid");
        ActionToken t = tokens.findByTokenHashForUpdate(hasher.hash(purpose.name(), raw))
                .orElseThrow(() -> ApiException.notFound("token.invalid"));
        requireUsable(t);
        t.markUsed(Instant.now(clock));
        return t;
    }

    /** Locks the token row without consuming it (for actions that check state first). */
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionToken lock(TokenPurpose purpose, String raw) {
        if (!TokenHasher.plausible(raw)) throw ApiException.notFound("token.invalid");
        return tokens.findByTokenHashForUpdate(hasher.hash(purpose.name(), raw))
                .orElseThrow(() -> ApiException.notFound("token.invalid"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markUsed(ActionToken t) {
        if (!t.isUsed()) t.markUsed(Instant.now(clock));
    }

    public void requireUsable(ActionToken t) {
        if (t.isUsed()) throw ApiException.gone("token.used");
        if (t.isExpired(Instant.now(clock))) throw ApiException.gone("token.expired");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateForBooking(UUID bookingId, TokenPurpose purpose) {
        tokens.invalidateForBooking(bookingId, purpose, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateForUser(UUID userId, TokenPurpose purpose) {
        tokens.invalidateForUser(userId, purpose, Instant.now(clock));
    }
}
