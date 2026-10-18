package com.barbershop.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;

/**
 * First-touch marketing attribution of an online booking (UTM parameters, Google /
 * Meta click ids, landing page). Only present when the visitor gave marketing
 * consent on the website. Values are untrusted browser input: trimmed, control
 * characters removed, cut to the column size, and ignored when older than the
 * 30-day attribution window.
 */
@Embeddable
public class BookingAttribution {

    /** Attribution older than this (first touch → booking) is not stored. */
    static final Duration WINDOW = Duration.ofDays(31);

    @Column(name = "attr_source")
    private String source;
    @Column(name = "attr_medium")
    private String medium;
    @Column(name = "attr_campaign")
    private String campaign;
    @Column(name = "attr_term")
    private String term;
    @Column(name = "attr_content")
    private String content;
    @Column(name = "attr_gclid")
    private String gclid;
    @Column(name = "attr_fbclid")
    private String fbclid;
    @Column(name = "attr_landing_path")
    private String landingPath;
    @Column(name = "attr_captured_at")
    private Instant capturedAt;

    protected BookingAttribution() {}

    /** Null when nothing usable is left after cleaning, or the first touch is outside the window. */
    static BookingAttribution of(BookingDtos.AttributionRequest r, Instant now) {
        if (r == null) return null;
        Instant captured = r.capturedAt() == null ? null : r.capturedAt().toInstant();
        if (captured != null && (captured.isBefore(now.minus(WINDOW)) || captured.isAfter(now.plus(Duration.ofDays(1))))) {
            return null;
        }
        BookingAttribution a = new BookingAttribution();
        a.source = clean(r.source(), 100);
        a.medium = clean(r.medium(), 100);
        a.campaign = clean(r.campaign(), 150);
        a.term = clean(r.term(), 150);
        a.content = clean(r.content(), 150);
        a.gclid = clean(r.gclid(), 255);
        a.fbclid = clean(r.fbclid(), 255);
        String path = clean(r.landingPath(), 300);
        a.landingPath = path != null && path.startsWith("/") ? path : null;
        a.capturedAt = captured;
        boolean empty = a.source == null && a.medium == null && a.campaign == null && a.term == null
                && a.content == null && a.gclid == null && a.fbclid == null && a.landingPath == null;
        return empty ? null : a;
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String v = s.replaceAll("\\p{Cntrl}", "").trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    public String getSource() { return source; }
    public String getMedium() { return medium; }
    public String getCampaign() { return campaign; }
    public String getTerm() { return term; }
    public String getContent() { return content; }
    public String getGclid() { return gclid; }
    public String getFbclid() { return fbclid; }
    public String getLandingPath() { return landingPath; }
    public Instant getCapturedAt() { return capturedAt; }
}
