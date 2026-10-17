package com.barbershop.service;

import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Formats prices the way the website shows them:
 * German "ab 35 €" / "35,50 €", English "from €35" / "€35.50".
 * Whole amounts are shown without decimals.
 */
@Component
public class PriceFormatter {

    private final Messages messages;

    public PriceFormatter(Messages messages) {
        this.messages = messages;
    }

    public PriceView view(int amountCents, String currencyCode, boolean from, Lang lang) {
        BigDecimal amount = BigDecimal.valueOf(amountCents).movePointLeft(2);
        return new PriceView(amountCents, amount, currencyCode, from, format(amountCents, currencyCode, from, lang));
    }

    public String format(int amountCents, String currencyCode, boolean from, Lang lang) {
        Locale numberLocale = lang == Lang.EN ? Locale.US : Locale.GERMANY;
        NumberFormat nf = NumberFormat.getNumberInstance(numberLocale);
        boolean whole = amountCents % 100 == 0;
        nf.setMinimumFractionDigits(whole ? 0 : 2);
        nf.setMaximumFractionDigits(whole ? 0 : 2);
        String number = nf.format(BigDecimal.valueOf(amountCents).movePointLeft(2));
        String symbol = Currency.getInstance(currencyCode).getSymbol(Locale.GERMANY);
        String price = lang == Lang.EN ? symbol + number : number + " " + symbol;
        return from ? messages.get(lang.locale(), "price.from") + " " + price : price;
    }

    /** Price as returned by the API: raw cents for calculations, a formatted string for display. */
    public record PriceView(int amountCents, BigDecimal amount, String currency, boolean from, String formatted) {}
}
