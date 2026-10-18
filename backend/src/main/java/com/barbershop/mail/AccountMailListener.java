package com.barbershop.mail;

import com.barbershop.account.AccountEvents.AlreadyRegistered;
import com.barbershop.account.AccountEvents.PasswordResetRequested;
import com.barbershop.account.AccountEvents.VerificationRequested;
import com.barbershop.auth.AppUser;
import com.barbershop.auth.AppUserRepository;
import com.barbershop.tenant.Tenant;
import com.barbershop.tenant.TenantRepository;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Account emails into the {@link MailOutbox} in the account change's transaction (as {@link BookingMailListener}). */
@Component
public class AccountMailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountMailListener.class);

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final AccountMails mails;
    private final MailOutbox outbox;

    public AccountMailListener(AppUserRepository users, TenantRepository tenants, AccountMails mails, MailOutbox outbox) {
        this.users = users;
        this.tenants = tenants;
        this.mails = mails;
        this.outbox = outbox;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(VerificationRequested e) {
        send(e.userId(), "verify", (u, t) -> mails.verify(u, t, e.rawToken()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(PasswordResetRequested e) {
        send(e.userId(), e.invite() ? "invite" : "reset",
                (u, t) -> e.invite() ? mails.invite(u, t, e.rawToken()) : mails.reset(u, t, e.rawToken()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void on(AlreadyRegistered e) {
        send(e.userId(), "already-registered", mails::alreadyRegistered);
    }

    private void send(UUID userId, String kind, BiFunction<AppUser, Tenant, MailContent> build) {
        AppUser u;
        Tenant t;
        MailContent m;
        try {
            u = users.findById(userId).orElseThrow();
            t = tenants.findById(u.getTenantId()).orElseThrow();
            m = build.apply(u, t);
        } catch (Exception ex) {
            // a mail that can't be built must not fail the account change
            log.error("Could not build {} mail for account {}: {}", kind, userId, ex.getMessage(), ex);
            return;
        }
        UUID id = outbox.enqueue(m, kind, t.getId(), null, u.getId()); // storing fails the transaction (outbox)
        if (id != null) log.info("Queued {} mail {} for account {}", kind, id, userId);
    }
}
