package com.barbershop.mail;

import com.barbershop.common.i18n.Lang;
import java.util.ArrayList;
import java.util.List;

/**
 * Structure of every email, rendered twice from the same data: HTML
 * (templates/mail/layout.html, dark/gold) and plain text. Built with the
 * fluent methods by {@link BookingMails} / {@link AccountMails}.
 */
public final class MailContent {

    public record Row(String label, String value, boolean strong, boolean struck) {}

    public record Button(String label, String url, boolean primary) {}

    /** kind: "demo" (gold dashed), "info", "warn". */
    public record Notice(String text, String kind) {}

    public record Attachment(String filename, String contentType, byte[] data) {}

    private final String to;
    private final Lang lang;
    private final String shopName;
    private String subject;
    private String preheader;
    private String greeting;
    private final List<String> paragraphs = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    /** Text after the summary table (deadlines, how to cancel …). */
    private final List<String> notes = new ArrayList<>();
    private final List<Notice> notices = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<String> afterButtons = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();
    private String signature;
    private String footer;

    public MailContent(String to, Lang lang, String shopName) {
        this.to = to;
        this.lang = lang;
        this.shopName = shopName;
    }

    public MailContent subject(String s) { this.subject = s; return this; }
    public MailContent preheader(String s) { this.preheader = s; return this; }
    public MailContent greeting(String s) { this.greeting = s; return this; }
    public MailContent paragraph(String s) { if (s != null && !s.isBlank()) paragraphs.add(s); return this; }
    public MailContent row(String label, String value) { return row(label, value, false, false); }
    public MailContent strongRow(String label, String value) { return row(label, value, true, false); }

    public MailContent row(String label, String value, boolean strong, boolean struck) {
        if (value != null && !value.isBlank()) rows.add(new Row(label, value, strong, struck));
        return this;
    }

    public MailContent note(String s) { if (s != null && !s.isBlank()) notes.add(s); return this; }
    public MailContent notice(String text, String kind) { if (text != null) notices.add(new Notice(text, kind)); return this; }
    public MailContent button(String label, String url, boolean primary) { buttons.add(new Button(label, url, primary)); return this; }
    public MailContent afterButtons(String s) { if (s != null && !s.isBlank()) afterButtons.add(s); return this; }
    public MailContent attach(Attachment a) { attachments.add(a); return this; }
    public MailContent signature(String s) { this.signature = s; return this; }
    public MailContent footer(String s) { this.footer = s; return this; }

    public String to() { return to; }
    public Lang lang() { return lang; }
    public String shopName() { return shopName; }
    public String subject() { return subject; }
    public String preheader() { return preheader; }
    public String greeting() { return greeting; }
    public List<String> paragraphs() { return paragraphs; }
    public List<Row> rows() { return rows; }
    public List<String> notes() { return notes; }
    public List<Notice> notices() { return notices; }
    public List<Button> buttons() { return buttons; }
    public List<String> afterButtons() { return afterButtons; }
    public List<Attachment> attachments() { return attachments; }
    public String signature() { return signature; }
    public String footer() { return footer; }

    /** The plain-text alternative: same content, links written out. */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        if (greeting != null) sb.append(greeting).append("\n\n");
        paragraphs.forEach(p -> sb.append(p).append("\n\n"));
        if (!rows.isEmpty()) {
            rows.forEach(r -> sb.append(r.label()).append(": ").append(r.value()).append('\n'));
            sb.append('\n');
        }
        notes.forEach(p -> sb.append(p).append("\n\n"));
        notices.forEach(n -> sb.append("* ").append(n.text()).append("\n\n"));
        buttons.forEach(b -> sb.append(b.label()).append(":\n").append(b.url()).append("\n\n"));
        afterButtons.forEach(p -> sb.append(p).append("\n\n"));
        if (signature != null) sb.append(signature).append("\n\n");
        if (footer != null) sb.append("--\n").append(footer).append('\n');
        return sb.toString();
    }
}
