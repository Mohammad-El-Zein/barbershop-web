package com.barbershop.common.config;

import com.barbershop.common.i18n.Lang;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Builds the frontend URLs that appear in emails ({@code app.links.*}). Every
 * link points to a frontend PAGE; the page shows what will happen and only its
 * button sends the POST — opening a link never changes anything.
 */
@Component
public class FrontendLinks {

    private final String base;
    private final AppProperties.Links links;

    public FrontendLinks(AppProperties props) {
        String b = props.frontendBaseUrl();
        this.base = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        this.links = props.links();
    }

    public String book(Lang lang) {
        return build(links.book(), lang, null);
    }

    public String manage(Lang lang, String token) {
        return build(links.manage(), lang, token);
    }

    public String pay(Lang lang, String token) {
        return build(links.pay(), lang, token);
    }

    /** {@code action} = accept | decline: preselects the button on the decision page. */
    public String barberDecision(Lang lang, String token, String action) {
        return build(links.barberDecision(), lang, token) + "?action=" + action;
    }

    public String verifyEmail(Lang lang, String token) {
        return build(links.verifyEmail(), lang, token);
    }

    public String resetPassword(Lang lang, String token) {
        return build(links.resetPassword(), lang, token);
    }

    public String account(Lang lang) {
        return build(links.account(), lang, null);
    }

    /** The staff area (calendar with open requests). */
    public String staff(Lang lang) {
        return build(links.staff(), lang, null);
    }

    private String build(String template, Lang lang, String token) {
        String path = template.replace("{lang}", lang.code());
        if (token != null) path = path.replace("{token}", URLEncoder.encode(token, StandardCharsets.UTF_8));
        return base + path;
    }
}
