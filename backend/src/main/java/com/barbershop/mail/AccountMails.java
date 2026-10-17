package com.barbershop.mail;

import com.barbershop.auth.AppUser;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.config.FrontendLinks;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.tenant.Tenant;
import org.springframework.stereotype.Component;

/** Account emails: verify email, reset password, barber invite, "you already have an account". */
@Component
public class AccountMails {

    private final Messages messages;
    private final FrontendLinks links;
    private final AppProperties.Auth config;

    public AccountMails(Messages messages, FrontendLinks links, AppProperties props) {
        this.messages = messages;
        this.links = links;
        this.config = props.auth();
    }

    public MailContent verify(AppUser u, Tenant t, String token) {
        Lang l = u.lang();
        return base(u, t)
                .subject(msg(l, "mail.verify.subject", t.getName()))
                .paragraph(msg(l, "mail.verify.intro", t.getName(), config.emailVerifyTtl().toHours()))
                .button(msg(l, "mail.button.verify"), links.verifyEmail(l, token), true)
                .afterButtons(msg(l, "mail.ignore_if_not_you"));
    }

    public MailContent reset(AppUser u, Tenant t, String token) {
        Lang l = u.lang();
        return base(u, t)
                .subject(msg(l, "mail.reset.subject", t.getName()))
                .paragraph(msg(l, "mail.reset.intro", config.passwordResetTtl().toMinutes()))
                .button(msg(l, "mail.button.reset"), links.resetPassword(l, token), true)
                .afterButtons(msg(l, "mail.ignore_if_not_you"));
    }

    public MailContent invite(AppUser u, Tenant t, String token) {
        Lang l = u.lang();
        return base(u, t)
                .subject(msg(l, "mail.invite.subject", t.getName()))
                .paragraph(msg(l, "mail.invite.intro", t.getName(), config.barberInviteTtl().toHours()))
                .button(msg(l, "mail.button.set_password"), links.resetPassword(l, token), true);
    }

    public MailContent alreadyRegistered(AppUser u, Tenant t) {
        Lang l = u.lang();
        return base(u, t)
                .subject(msg(l, "mail.exists.subject", t.getName()))
                .paragraph(msg(l, "mail.exists.intro"))
                .button(msg(l, "mail.button.login"), links.account(l), true)
                .afterButtons(msg(l, "mail.ignore_if_not_you"));
    }

    private MailContent base(AppUser u, Tenant t) {
        Lang l = u.lang();
        return new MailContent(u.getEmail(), l, t.getName())
                .greeting(msg(l, "mail.greeting", u.getName()))
                .signature(msg(l, "mail.signature", t.getName()))
                .footer(msg(l, "mail.footer"));
    }

    private String msg(Lang l, String code, Object... args) {
        return messages.get(l.locale(), code, args);
    }
}
