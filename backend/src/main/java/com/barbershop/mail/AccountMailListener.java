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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Account emails after commit, async (same pattern as {@link BookingMailListener}). */
@Component
public class AccountMailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountMailListener.class);

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final AccountMails mails;
    private final MailService mail;

    public AccountMailListener(AppUserRepository users, TenantRepository tenants, AccountMails mails, MailService mail) {
        this.users = users;
        this.tenants = tenants;
        this.mails = mails;
        this.mail = mail;
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(VerificationRequested e) {
        send(e.userId(), "verify", (u, t) -> mails.verify(u, t, e.rawToken()));
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(PasswordResetRequested e) {
        send(e.userId(), e.invite() ? "invite" : "reset",
                (u, t) -> e.invite() ? mails.invite(u, t, e.rawToken()) : mails.reset(u, t, e.rawToken()));
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(AlreadyRegistered e) {
        send(e.userId(), "already-registered", mails::alreadyRegistered);
    }

    private void send(UUID userId, String kind, BiFunction<AppUser, Tenant, MailContent> build) {
        try {
            AppUser u = users.findById(userId).orElseThrow();
            Tenant t = tenants.findById(u.getTenantId()).orElseThrow();
            mail.send(build.apply(u, t));
            log.info("Sent {} mail to account {}", kind, userId);
        } catch (Exception ex) {
            log.error("Could not send {} mail to account {}: {}", kind, userId, ex.getMessage(), ex);
        }
    }
}
