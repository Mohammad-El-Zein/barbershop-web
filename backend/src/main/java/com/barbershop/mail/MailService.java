package com.barbershop.mail;

import com.barbershop.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Renders a {@link MailContent} (HTML + plain text, attachments) and sends it. */
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

    public void send(MailContent m) throws MessagingException {
        Context ctx = new Context(m.lang().locale());
        ctx.setVariable("m", m);
        String html = templates.process("mail/layout", ctx);

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(props.mail().from());
        helper.setTo(m.to());
        helper.setSubject(m.subject());
        helper.setText(m.plainText(), html);
        for (MailContent.Attachment a : m.attachments()) {
            helper.addAttachment(a.filename(), new ByteArrayResource(a.data()), a.contentType());
        }
        message.setHeader("Content-Language", m.lang().code());
        sender.send(message);
    }
}
