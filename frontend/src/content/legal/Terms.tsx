import type { BookingRules } from "@/lib/api/types";
import { legalConfig } from "@/config/legal.config";
import { Section, V } from "@/components/legal/Legal";
import { money } from "@/lib/format";

/**
 * AGB incl. booking, deposit and cancellation terms. TEMPLATE — see README.md.
 * The numbers (deposit, cutoffs, deadlines, loyalty) come from the shop's live
 * booking rules, so the terms always match what the booking system enforces.
 */
type Props = { rules: Required<BookingRules>; locale: string };

const hours = (min: number) => Math.round((min / 60) * 10) / 10;

export function TermsDe({ rules }: Props) {
  const deposit = money(rules.depositCents, "de");
  const o = legalConfig.operator;
  return (
    <>
      <Section id="geltung" title="1. Geltungsbereich">
        <p>
          Diese Allgemeinen Geschäftsbedingungen gelten für alle Terminbuchungen über diese Website und für die
          Leistungen von <V>{o.name}</V> (im Folgenden „wir“) gegenüber Kundinnen und Kunden (im Folgenden „du“).
        </p>
      </Section>

      <Section id="buchung" title="2. Terminanfrage und Vertragsschluss">
        <p>
          Mit dem Absenden des Buchungsformulars stellst du eine verbindliche Terminanfrage für die gewählten Leistungen,
          den gewählten Barber und die gewählte Uhrzeit. Online buchbar sind Termine frühestens {hours(rules.minLeadMin)} Stunden
          und höchstens {rules.maxDaysAhead} Tage im Voraus.
        </p>
        <p>
          Der Barber nimmt die Anfrage innerhalb von {hours(rules.barberResponseMin)} Stunden an oder lehnt sie ab; du wirst per
          E-Mail informiert. Antwortet der Barber nicht rechtzeitig, verfällt die Anfrage. Der Vertrag kommt mit der
          Annahme zustande{rules.depositCents > 0 ? "; verbindlich reserviert ist der Termin nach Zahlung der Anzahlung (Ziffer 3)." : "."}
        </p>
      </Section>

      {rules.depositCents > 0 && (
        <Section id="anzahlung" title="3. Anzahlung">
          <p>
            Nach der Annahme zahlst du eine Anzahlung von {deposit}. Sie wird vollständig mit dem Preis der Leistungen
            verrechnet; den Rest zahlst du im Laden. Die Zahlung muss innerhalb von {hours(rules.paymentDeadlineMin)} Stunden
            nach der Annahme erfolgen, spätestens aber vor dem Termin; sonst verfällt die Reservierung und der Termin wird
            wieder freigegeben.
          </p>
        </Section>
      )}

      <Section id="storno" title="4. Stornierung durch dich">
        <ul>
          <li>
            <strong>Bis {hours(rules.refundCutoffMin)} Stunden vor dem Termin</strong> kannst du kostenlos stornieren
            {rules.depositCents > 0 ? `; eine gezahlte Anzahlung von ${deposit} wird vollständig erstattet.` : "."}
          </li>
          {rules.depositCents > 0 && (
            <li>
              <strong>Später als {hours(rules.refundCutoffMin)} Stunden vorher</strong> ist die Stornierung weiterhin möglich,
              die Anzahlung wird dann jedoch als Ausgleich für den freigehaltenen Termin einbehalten.
            </li>
          )}
          <li>
            <strong>Online</strong> (über den Link in deiner Bestätigungs-E-Mail oder dein Kundenkonto) kannst du bis{" "}
            {hours(rules.onlineCancelCutoffMin)} Stunden vor dem Termin stornieren. Danach melde dich bitte direkt im Laden
            (Telefon: <V>{o.phone}</V>).
          </li>
          <li>Eine noch nicht angenommene oder noch nicht bezahlte Anfrage kannst du jederzeit kostenlos zurückziehen.</li>
        </ul>
      </Section>

      <Section id="noshow" title="5. Nichterscheinen und Verspätung">
        <p>
          Erscheinst du nicht zum Termin, gilt dies wie eine Stornierung weniger als {hours(rules.refundCutoffMin)} Stunden
          vorher{rules.depositCents > 0 ? " – die Anzahlung wird einbehalten" : ""}. Bei deutlicher Verspätung kann die
          Leistung verkürzt werden oder entfallen. <V>[Toleranz bei Verspätung festlegen, z. B. 15 Minuten]</V>
        </p>
      </Section>

      <Section id="absage" title="6. Absage durch uns">
        <p>
          Müssen wir einen Termin absagen (z. B. wegen Krankheit), informieren wir dich so früh wie möglich und erstatten
          eine gezahlte Anzahlung vollständig.
        </p>
      </Section>

      <Section id="preise" title="7. Preise und Zahlung">
        <p>
          Alle Preise sind Endpreise inkl. gesetzlicher Umsatzsteuer. Auf der Website mit „ab“ gekennzeichnete Preise sind
          Mindestpreise; der Preis deiner Buchung wird dir vor dem Absenden genau angezeigt und gilt verbindlich. Der
          Restbetrag ist im Laden zu zahlen. <V>[Akzeptierte Zahlungsarten im Laden]</V>
        </p>
      </Section>

      {rules.loyaltyPercent > 0 && (
        <Section id="treue" title="8. Treueprogramm">
          <p>
            Mit einem Kundenkonto zählen abgeschlossene Besuche: Nach {rules.loyaltyThreshold} Besuchen erhältst du bei der
            nächsten Buchung {rules.loyaltyPercent} % Rabatt auf die Leistungen. Der Rabatt wird bei der Buchung automatisch
            reserviert und mit dem Abschluss dieses Termins eingelöst; wird die Buchung storniert oder abgelehnt, steht er
            wieder zur Verfügung. Er ist nicht übertragbar und nicht in bar auszahlbar. Wir können das Programm mit Wirkung
            für die Zukunft ändern oder beenden; bereits erreichte Rabatte bleiben erhalten.
          </p>
        </Section>
      )}

      <Section id="konto" title="9. Kundenkonto">
        <p>
          Das Kundenkonto ist freiwillig und kostenlos. Du kannst es jederzeit in deinem Profil löschen. Zukünftige Termine
          werden dabei storniert; für die Anzahlung gilt Ziffer 4.
        </p>
      </Section>

      {rules.demoPayments && (
        <Section id="demo" title="10. Demo-Modus">
          <p>
            <strong>Hinweis:</strong> Diese Installation läuft im Demo-Modus. Es werden keine echten Zahlungen ausgeführt;
            „Zahlung simulieren“ bestätigt den Termin ohne Geldfluss. Im Demo-Modus gebuchte Termine sind Testbuchungen.
          </p>
        </Section>
      )}

      <Section id="widerruf" title="11. Widerrufsrecht">
        <p>
          <V>[Mit Rechtsberatung klären: Ob und wie ein Widerrufsrecht für online gebuchte Dienstleistungstermine besteht
          (§ 312g BGB) und ggf. Widerrufsbelehrung und Muster-Widerrufsformular ergänzen.]</V>
        </p>
      </Section>

      <Section id="schluss" title="12. Haftung und Schlussbestimmungen">
        <p>
          Wir haften unbeschränkt bei Vorsatz und grober Fahrlässigkeit sowie bei Verletzung von Leben, Körper oder
          Gesundheit; im Übrigen nach den gesetzlichen Vorschriften. Es gilt deutsches Recht. Sollten einzelne Bestimmungen
          unwirksam sein, bleibt die Wirksamkeit der übrigen unberührt. <V>[Mit Rechtsberatung prüfen]</V>
        </p>
      </Section>
    </>
  );
}

export function TermsEn({ rules }: Props) {
  const deposit = money(rules.depositCents, "en");
  const o = legalConfig.operator;
  return (
    <>
      <p className="text-xs text-muted">
        This English version is for convenience; in case of doubt the German version applies.
      </p>
      <Section id="scope" title="1. Scope">
        <p>
          These terms apply to all appointments booked through this website and to the services of <V>{o.name}</V>{" "}
          (“we”) for customers (“you”).
        </p>
      </Section>

      <Section id="booking" title="2. Booking request and contract">
        <p>
          By sending the booking form you make a binding request for the chosen services, barber and time. Appointments
          can be booked online at least {hours(rules.minLeadMin)} hours and at most {rules.maxDaysAhead} days in advance.
        </p>
        <p>
          The barber accepts or declines the request within {hours(rules.barberResponseMin)} hours; you are informed by
          email. If the barber does not answer in time, the request expires. The contract is concluded on acceptance
          {rules.depositCents > 0 ? "; the appointment is firmly reserved once the deposit is paid (section 3)." : "."}
        </p>
      </Section>

      {rules.depositCents > 0 && (
        <Section id="deposit" title="3. Deposit">
          <p>
            After acceptance you pay a deposit of {deposit}. It is fully deducted from the price of the services; you pay
            the rest at the shop. Payment is due within {hours(rules.paymentDeadlineMin)} hours of acceptance and at the
            latest before the appointment; otherwise the reservation lapses and the slot is released.
          </p>
        </Section>
      )}

      <Section id="cancellation" title="4. Cancellation by you">
        <ul>
          <li>
            <strong>Up to {hours(rules.refundCutoffMin)} hours before</strong> the appointment you can cancel free of charge
            {rules.depositCents > 0 ? `; a paid deposit of ${deposit} is refunded in full.` : "."}
          </li>
          {rules.depositCents > 0 && (
            <li>
              <strong>Later than {hours(rules.refundCutoffMin)} hours before</strong>, you can still cancel, but the deposit is
              kept as compensation for the reserved slot.
            </li>
          )}
          <li>
            <strong>Online</strong> (via the link in your confirmation email or your account) you can cancel up to{" "}
            {hours(rules.onlineCancelCutoffMin)} hours before the appointment. After that, please contact the shop directly
            (phone: <V>{o.phone}</V>).
          </li>
          <li>A request that has not been accepted or paid yet can be withdrawn free of charge at any time.</li>
        </ul>
      </Section>

      <Section id="no-show" title="5. No-show and lateness">
        <p>
          Not showing up counts as a cancellation less than {hours(rules.refundCutoffMin)} hours before
          {rules.depositCents > 0 ? " – the deposit is kept" : ""}. If you are considerably late, the service may be
          shortened or cancelled. <V>[Define the tolerance, e.g. 15 minutes]</V>
        </p>
      </Section>

      <Section id="our-cancellation" title="6. Cancellation by us">
        <p>
          If we have to cancel an appointment (e.g. due to illness), we let you know as early as possible and refund a
          paid deposit in full.
        </p>
      </Section>

      <Section id="prices" title="7. Prices and payment">
        <p>
          All prices are final prices including VAT. Prices marked “from” on the website are minimum prices; the exact
          price of your booking is shown before you send it and is binding. The remaining amount is paid at the shop.{" "}
          <V>[Accepted payment methods at the shop]</V>
        </p>
      </Section>

      {rules.loyaltyPercent > 0 && (
        <Section id="loyalty" title="8. Loyalty programme">
          <p>
            With a customer account, completed visits count: after {rules.loyaltyThreshold} visits you get{" "}
            {rules.loyaltyPercent}% off the services of your next booking. The discount is reserved automatically when you
            book and redeemed when that appointment is completed; if the booking is cancelled or declined, it becomes
            available again. It is not transferable and cannot be paid out in cash. We may change or end the programme for
            the future; discounts already earned remain valid.
          </p>
        </Section>
      )}

      <Section id="account" title="9. Customer account">
        <p>
          The customer account is optional and free. You can delete it at any time in your profile. Future appointments
          are then cancelled; section 4 applies to the deposit.
        </p>
      </Section>

      {rules.demoPayments && (
        <Section id="demo" title="10. Demo mode">
          <p>
            <strong>Note:</strong> this installation runs in demo mode. No real payments are made; “Simulate payment”
            confirms the appointment without any money being transferred. Appointments booked in demo mode are test
            bookings.
          </p>
        </Section>
      )}

      <Section id="withdrawal" title="11. Right of withdrawal">
        <p>
          <V>[Clarify with legal advice whether and how a right of withdrawal applies to service appointments booked
          online (§ 312g BGB) and add instructions and a model form if needed.]</V>
        </p>
      </Section>

      <Section id="final" title="12. Liability and final provisions">
        <p>
          We are liable without limitation for intent and gross negligence and for injury to life, body or health;
          otherwise in accordance with the statutory provisions. German law applies. Should individual provisions be
          invalid, the validity of the remaining provisions is unaffected. <V>[Check with legal advice]</V>
        </p>
      </Section>
    </>
  );
}
