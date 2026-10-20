package com.barbershop.common.retention;

/**
 * What "anonymised" means for a booking — shared by the retention job and the admin erasure
 * (ErasureService), so both remove exactly the same personal data: name → placeholder (the
 * column is NOT NULL); email, phone, note, status reason (free text) and the campaign data
 * removed. Date, barber, services, amounts and status stay (statistics, bookkeeping).
 * Use as {@code update booking set <SET_CLAUSE> where …} with the parameter {@code :placeholder}.
 */
public final class BookingAnonymisation {

    public static final String SET_CLAUSE = """
            customer_name = :placeholder, customer_email = null, customer_phone = null,
            customer_note = null, status_reason = null,
            attr_source = null, attr_medium = null, attr_campaign = null, attr_term = null,
            attr_content = null, attr_gclid = null, attr_fbclid = null, attr_landing_path = null,
            attr_captured_at = null""";

    /** Message key of the placeholder name (German text, like every stored default). */
    public static final String PLACEHOLDER_KEY = "retention.anonymised_customer";

    private BookingAnonymisation() {}
}
