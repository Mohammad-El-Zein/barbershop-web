package com.barbershop.mail;

import com.barbershop.booking.Booking;
import com.barbershop.common.config.AppProperties;
import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.service.BarberService;
import com.barbershop.service.PriceFormatter;
import com.barbershop.tenant.Tenant;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Booking emails (confirmed / request received / cancelled), always in the
 * language the customer booked in. HTML from templates/mail/booking.html with a
 * plain-text alternative; texts come from messages*.properties.
 */
@Service
public class MailService {

    public enum Kind { CONFIRMED, RECEIVED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_SHOP }

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final Messages messages;
    private final PriceFormatter prices;
    private final AppProperties props;

    public MailService(JavaMailSender sender, TemplateEngine templates, Messages messages, PriceFormatter prices,
                       AppProperties props) {
        this.sender = sender;
        this.templates = templates;
        this.messages = messages;
        this.prices = prices;
        this.props = props;
    }

    /**
     * @param rawCancelToken cancel token for the link; null when not available
     *                       (cancellation mails, or confirmations of pending bookings)
     */
    public void sendBookingMail(Booking b, Tenant tenant, Kind kind, String rawCancelToken) throws MessagingException {
        Lang lang = b.lang();
        BarberService service = b.getService();
        ZonedDateTime start = b.getStartTime().atZone(tenant.zone());
        String when = DateTimeFormatter.ofPattern(messages.get(lang.locale(), "mail.datetime.pattern"), lang.locale())
                .format(start);
        String serviceName = service.name(lang);
        String key = switch (kind) {
            case CONFIRMED -> "confirmed";
            case RECEIVED -> "received";
            case CANCELLED_BY_CUSTOMER, CANCELLED_BY_SHOP -> "cancelled";
        };

        Context ctx = new Context(lang.locale());
        ctx.setVariable("kind", kind.name());
        ctx.setVariable("lang", lang.code());
        ctx.setVariable("shopName", tenant.getName());
        ctx.setVariable("customerName", b.getCustomerName());
        ctx.setVariable("serviceName", serviceName);
        ctx.setVariable("staffName", b.getStaff().getName());
        ctx.setVariable("when", when);
        ctx.setVariable("duration", service.getDurationMin());
        ctx.setVariable("price", prices.format(service.getPriceCents(), tenant.getCurrency(), service.isPriceFrom(), lang));
        ctx.setVariable("cancelHours", props.booking().cancelCutoff().toHours());
        ctx.setVariable("cancelUrl", rawCancelToken == null ? null : cancelUrl(lang, rawCancelToken));
        ctx.setVariable("bookUrl", props.frontendBaseUrl() + "/" + lang.code());
        ctx.setVariable("introKey", "mail." + key + ".intro" + switch (kind) {
            case CANCELLED_BY_CUSTOMER -> ".customer";
            case CANCELLED_BY_SHOP -> ".shop";
            default -> "";
        });
        String html = templates.process("mail/booking", ctx);

        String subject = messages.get(lang.locale(), "mail." + key + ".subject", tenant.getName(), when);
        String text = plainText(lang, ctx, kind);

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(props.mail().from());
        helper.setTo(b.getCustomerEmail());
        helper.setSubject(subject);
        helper.setText(text, html);
        message.setHeader("Content-Language", lang.code());
        sender.send(message);
    }

    private String cancelUrl(Lang lang, String token) {
        return props.frontendBaseUrl() + "/" + lang.code() + "/booking/cancel?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String plainText(Lang lang, Context ctx, Kind kind) {
        var l = lang.locale();
        StringBuilder sb = new StringBuilder()
                .append(messages.get(l, "mail.greeting", ctx.getVariable("customerName"))).append("\n\n")
                .append(messages.get(l, (String) ctx.getVariable("introKey"))).append("\n\n")
                .append(messages.get(l, "mail.label.service")).append(": ").append(ctx.getVariable("serviceName")).append('\n')
                .append(messages.get(l, "mail.label.barber")).append(": ").append(ctx.getVariable("staffName")).append('\n')
                .append(messages.get(l, "mail.label.when")).append(": ").append(ctx.getVariable("when")).append('\n')
                .append(messages.get(l, "mail.label.price")).append(": ").append(ctx.getVariable("price")).append("\n\n");
        Object cancelUrl = ctx.getVariable("cancelUrl");
        if (cancelUrl != null && (kind == Kind.CONFIRMED || kind == Kind.RECEIVED)) {
            sb.append(messages.get(l, "mail.cancel.hint", ctx.getVariable("cancelHours"))).append('\n')
                    .append(cancelUrl).append("\n\n");
        }
        if (kind == Kind.CANCELLED_BY_CUSTOMER || kind == Kind.CANCELLED_BY_SHOP) {
            sb.append(messages.get(l, "mail.rebook")).append(": ").append(ctx.getVariable("bookUrl")).append("\n\n");
        }
        return sb.append(messages.get(l, "mail.signature", ctx.getVariable("shopName"))).toString();
    }
}
