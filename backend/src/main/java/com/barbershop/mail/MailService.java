package com.barbershop.mail;

import com.barbershop.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Mail transport: renders a {@link MailContent} (HTML + plain text, attachments) once —
 * for the {@link MailOutbox} — and sends rendered mails (the outbox worker).
 */
@Service
public class MailService {

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final AppProperties props;

    public MailService(JavaMailSender sender, TemplateEngine templates, AppProperties props) {
        this.sender = sender;
        this.templates = templates;
        this.props = props;
    }

    /** A fully rendered mail, as stored in the outbox. */
    public record Rendered(String to, String subject, String langCode, String html, String plainText,
                           List<MailContent.Attachment> attachments) {}

    public Rendered render(MailContent m) {
        Context ctx = new Context(m.lang().locale());
        ctx.setVariable("m", m);
        String html = templates.process("mail/layout", ctx);
        return new Rendered(m.to(), m.subject(), m.lang().code(), html, m.plainText(), m.attachments());
    }

    public void send(Rendered m) throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(props.mail().from());
        helper.setTo(m.to());
        helper.setSubject(m.subject());
        helper.setText(m.plainText(), m.html());
        for (MailContent.Attachment a : m.attachments()) {
            helper.addAttachment(a.filename(), new ByteArrayResource(a.data()), a.contentType());
        }
        message.setHeader("Content-Language", m.langCode());
        sender.send(message);
    }
}
