package com.barbershop.booking;

import com.barbershop.common.i18n.Lang;
import com.barbershop.common.i18n.Messages;
import com.barbershop.loyalty.LoyaltyService;
import com.barbershop.loyalty.LoyaltyService.LoyaltyView;
import com.barbershop.tenant.BookingPolicy;
import org.springframework.stereotype.Component;

/** The one-line loyalty info for barbers ("regular: 7 of 10 visits", "50 % reward applied", "guest"). */
@Component
public class LoyaltyNotes {

    private final LoyaltyService loyalty;
    private final Messages messages;

    public LoyaltyNotes(LoyaltyService loyalty, Messages messages) {
        this.loyalty = loyalty;
        this.messages = messages;
    }

    public String forBarber(Booking b, BookingPolicy policy, Lang lang) {
        if (b.getUserId() == null) return messages.get(lang.locale(), "loyalty.note.guest");
        if (b.getLoyaltyStatus() == LoyaltyStatus.RESERVED || b.getLoyaltyStatus() == LoyaltyStatus.CONSUMED) {
            return messages.get(lang.locale(), "loyalty.note.reward", policy.getLoyaltyPercent());
        }
        LoyaltyView v = loyalty.view(b.getUserId(), policy);
        return messages.get(lang.locale(), "loyalty.note.visits", v.visitsInCurrentCycle(), v.threshold());
    }
}
