package com.barbershop.account;

import com.barbershop.account.AccountDtos.ExportView;
import com.barbershop.account.AccountDtos.PaymentExport;
import com.barbershop.account.AccountDtos.RegisterRequest;
import com.barbershop.account.AccountDtos.UpdateProfileRequest;
import com.barbershop.account.AccountDtos.VerifyResult;
import com.barbershop.account.AccountEvents.AlreadyRegistered;
import com.barbershop.account.AccountEvents.PasswordResetRequested;
import com.barbershop.account.AccountEvents.VerificationRequested;
import com.barbershop.auth.AppUser;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.auth.AuthDtos.UserView;
import com.barbershop.auth.AuthService;
import com.barbershop.auth.CurrentUser;
import com.barbershop.auth.Role;
import com.barbershop.booking.Booking;
import com.barbershop.booking.BookingRepository;
import com.barbershop.booking.BookingService;
import com.barbershop.booking.BookingViews;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ApiException;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.loyalty.LoyaltyService;
import com.barbershop.payment.PaymentRepository;
import com.barbershop.staff.Staff;
import com.barbershop.staff.StaffDirectory;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantService;
import com.barbershop.token.ActionToken;
import com.barbershop.token.ActionTokenService;
import com.barbershop.token.TokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer accounts and barber invites.
 *
 * <ul>
 *   <li><b>No user enumeration:</b> register, resend-verification and forgot-password
 *       always answer the same 202, whether the email exists or not. If an email is
 *       already registered, its owner gets a notice instead of a second account.</li>
 *   <li><b>Email verification</b> is required before login. Verifying also links the
 *       customer's earlier guest bookings (same email) to the account.</li>
 *   <li><b>GDPR:</b> delete = open future bookings are cancelled, all bookings are
 *       anonymised, the account and its tokens/loyalty are removed. Export = JSON with
 *       everything we store about the customer.</li>
 * </ul>
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AppUserRepository users;
    private final TenantService tenants;
    private final PasswordEncoder passwordEncoder;
    private final ActionTokenService tokens;
    private final AuthService auth;
    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final BookingViews bookingViews;
    private final PaymentRepository payments;
    private final LoyaltyService loyalty;
    private final StaffDirectory staff;
    private final ApplicationEventPublisher events;
    private final Messages messages;
    private final AppProperties.Auth config;
    private final Clock clock;

    public AccountService(AppUserRepository users, TenantService tenants, PasswordEncoder passwordEncoder,
                          ActionTokenService tokens, AuthService auth, BookingService bookingService,
                          BookingRepository bookings, BookingViews bookingViews, PaymentRepository payments,
                          LoyaltyService loyalty, StaffDirectory staff, ApplicationEventPublisher events,
                          Messages messages, AppProperties props, Clock clock) {
        this.users = users;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.auth = auth;
        this.bookingService = bookingService;
        this.bookings = bookings;
        this.bookingViews = bookingViews;
        this.payments = payments;
        this.loyalty = loyalty;
        this.staff = staff;
        this.events = events;
        this.messages = messages;
        this.config = props.auth();
        this.clock = clock;
    }

    // ── registration & verification ──────────────────────────────────────────

    @Transactional
    public void register(String tenantSlug, RegisterRequest req, Locale requestLocale) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        String email = req.email().trim();
        requireStrongPassword(req.password(), email);
        Instant now = Instant.now(clock);

        var existing = users.findByTenantAndEmail(tenant.getId(), email);
        // hash in both branches, so the response time does not reveal whether the email exists
        String hash = passwordEncoder.encode(req.password());
        if (existing.isPresent()) {
            events.publishEvent(new AlreadyRegistered(existing.get().getId()));
            log.info("Registration attempt for existing account {}", existing.get().getId());
            return;
        }
        Lang lang = req.lang() != null ? Lang.parse(req.lang()) : Lang.of(requestLocale);
        AppUser user = new AppUser(tenant.getId(), Role.CUSTOMER, email, hash, req.name().trim(), req.phone().trim(),
                lang, now);
        user.recordConsent(now, config.consentVersion());
        users.save(user);
        sendVerification(user, now);
        log.info("Customer account {} registered (unverified)", user.getId());
    }

    @Transactional
    public void resendVerification(String tenantSlug, String email) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        users.findByTenantAndEmail(tenant.getId(), email.trim())
                .filter(u -> !u.isEmailVerified())
                .ifPresent(u -> {
                    tokens.invalidateForUser(u.getId(), TokenPurpose.EMAIL_VERIFY);
                    sendVerification(u, Instant.now(clock));
                });
    }

    @Transactional
    public VerifyResult verifyEmail(String rawToken) {
        ActionToken t = tokens.consume(TokenPurpose.EMAIL_VERIFY, rawToken);
        AppUser user = users.findById(t.getUserId()).orElseThrow(() -> ApiException.notFound("token.invalid"));
        return new VerifyResult(true, markVerified(user));
    }

    // ── passwords ────────────────────────────────────────────────────────────

    @Transactional
    public void forgotPassword(String tenantSlug, String email) {
        Tenant tenant = tenants.requireBySlug(tenantSlug);
        users.findByTenantAndEmail(tenant.getId(), email.trim()).ifPresent(u -> {
            tokens.invalidateForUser(u.getId(), TokenPurpose.PASSWORD_RESET);
            String raw = tokens.issue(u.getTenantId(), TokenPurpose.PASSWORD_RESET, null, u.getId(), null,
                    Instant.now(clock).plus(config.passwordResetTtl())).raw();
            events.publishEvent(new PasswordResetRequested(u.getId(), raw, false));
        });
    }

    /** Sets the new password, ends all sessions. Owning the inbox also verifies the email. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        ActionToken t = tokens.consume(TokenPurpose.PASSWORD_RESET, rawToken);
        AppUser user = users.findById(t.getUserId()).orElseThrow(() -> ApiException.notFound("token.invalid"));
        requireStrongPassword(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        markVerified(user);
        auth.revokeAllSessions(user.getId());
        log.info("Password reset for account {}", user.getId());
    }

    /** Changes the password, ends every other session and starts a fresh one for this device. */
    @Transactional
    public AuthService.Session changePassword(CurrentUser me, String currentPassword, String newPassword) {
        AppUser user = require(me);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.validation(Map.of("currentPassword", "account.wrong_password"));
        }
        requireStrongPassword(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        auth.revokeAllSessions(user.getId());
        return auth.startSession(user);
    }

    // ── profile ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserView me(CurrentUser me) {
        AppUser user = require(me);
        return UserView.of(user, tenants.require(user.getTenantId()));
    }

    @Transactional
    public UserView updateProfile(CurrentUser me, UpdateProfileRequest req) {
        AppUser user = require(me);
        if (req.name() != null && !req.name().isBlank()) user.setName(req.name().trim());
        if (req.phone() != null && !req.phone().isBlank()) user.setPhone(req.phone().trim());
        if (req.lang() != null) user.setLang(Lang.parse(req.lang()));
        return UserView.of(user, tenants.require(user.getTenantId()));
    }

    // ── GDPR ─────────────────────────────────────────────────────────────────

    /** Deletes a customer account (password confirmation required). */
    @Transactional
    public void deleteAccount(CurrentUser me, String password) {
        AppUser user = require(me);
        if (user.getRole() != Role.CUSTOMER) throw ApiException.forbidden("account.delete_not_allowed");
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.validation(Map.of("password", "account.wrong_password"));
        }
        String placeholder = messages.get(user.lang().locale(), "account.deleted_customer");
        int anonymised = bookingService.anonymiseForUser(user.getTenantId(), user.getId(), placeholder);
        loyalty.delete(user.getId());
        users.delete(user); // refresh/action tokens cascade
        log.info("Account {} deleted, {} booking(s) anonymised", user.getId(), anonymised);
    }

    @Transactional(readOnly = true)
    public ExportView export(CurrentUser me) {
        AppUser user = require(me);
        Tenant tenant = tenants.require(user.getTenantId());
        List<Booking> all = bookings.findByUserIdOrderByStartTimeDesc(user.getId());
        List<PaymentExport> paymentRows = payments.findByBookingIdIn(all.stream().map(Booking::getId).toList())
                .stream()
                .map(p -> new PaymentExport(p.getBookingId(), p.getStatus().name(), p.getAmountCents(), p.getCurrency(),
                        p.getPaidAt(), p.getRefundedAt(), p.getForfeitedAt()))
                .toList();
        return new ExportView(Instant.now(clock), UserView.of(user, tenant), user.getConsentAt(),
                user.getConsentVersion(), loyalty.view(user.getId(), tenant.policy()),
                bookingViews.customer(all, tenant, user.lang()), paymentRows);
    }

    // ── barber accounts (admin) ──────────────────────────────────────────────

    /** Creates the login for a staff profile and emails a "set your password" link. */
    @Transactional
    public AccountDtos.BarberAccountView createBarberAccount(UUID tenantId, UUID staffId, String email, String lang) {
        Staff s = staff.require(tenantId, staffId);
        if (!users.findByStaffId(staffId).isEmpty()) throw ApiException.conflict("account.staff_has_account");
        if (users.findByTenantAndEmail(tenantId, email.trim()).isPresent()) {
            throw ApiException.conflict("account.email_taken");
        }
        Instant now = Instant.now(clock);
        Lang l = lang != null ? Lang.parse(lang) : Lang.DEFAULT;
        // unusable random password until the barber sets one via the invite link
        AppUser user = new AppUser(tenantId, Role.BARBER, email.trim(),
                passwordEncoder.encode(UUID.randomUUID() + "" + UUID.randomUUID()), s.getName(), null, l, now);
        user.linkStaff(staffId);
        users.save(user);
        String raw = tokens.issue(tenantId, TokenPurpose.PASSWORD_RESET, null, user.getId(), null,
                now.plus(config.barberInviteTtl())).raw();
        events.publishEvent(new PasswordResetRequested(user.getId(), raw, true));
        return new AccountDtos.BarberAccountView(user.getId(), staffId, user.getEmail(), user.getName(), true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void sendVerification(AppUser user, Instant now) {
        String raw = tokens.issue(user.getTenantId(), TokenPurpose.EMAIL_VERIFY, null, user.getId(), null,
                now.plus(config.emailVerifyTtl())).raw();
        events.publishEvent(new VerificationRequested(user.getId(), raw));
    }

    /** First verification of a customer links their guest bookings. Returns how many were linked. */
    private int markVerified(AppUser user) {
        if (user.isEmailVerified()) return 0;
        user.verifyEmail(Instant.now(clock));
        tokens.invalidateForUser(user.getId(), TokenPurpose.EMAIL_VERIFY);
        if (user.getRole() != Role.CUSTOMER) return 0;
        return bookingService.linkGuestBookings(user.getTenantId(), user.getEmail(), user.getId());
    }

    private void requireStrongPassword(String password, String email) {
        if (password.length() < config.minPasswordLength()) {
            throw ApiException.validation(Map.of("password", "validation.password.too_short"));
        }
        if (password.trim().equalsIgnoreCase(email.trim())) {
            throw ApiException.validation(Map.of("password", "validation.password.equals_email"));
        }
    }

    private AppUser require(CurrentUser me) {
        return users.findByIdAndTenantId(me.userId(), me.tenantId())
                .orElseThrow(() -> ApiException.unauthorized("auth.unauthorized"));
    }
}
