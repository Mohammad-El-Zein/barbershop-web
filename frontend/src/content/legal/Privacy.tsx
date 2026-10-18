import { legalConfig } from "@/config/legal.config";
import { Section, V } from "@/components/legal/Legal";

/**
 * Datenschutzerklärung / privacy policy. TEMPLATE — see README.md.
 * Describes what this system really does (booking, accounts, emails, consent,
 * optional analytics/ads, bot protection, retention). Sections for optional tools
 * (statistics / marketing) must be kept in sync with what is configured in prod.
 */
const o = legalConfig.operator;
const p = legalConfig.processors;
const r = legalConfig.retention;

export function PrivacyDe() {
  return (
    <>
      <Section id="verantwortlicher" title="1. Verantwortlicher">
        <p>
          <V>{o.name}</V>, <V>{o.street}</V>, <V>{o.postalCode}</V> <V>{o.city}</V>
          <br />
          E-Mail: <V>{o.email}</V> · Telefon: <V>{o.phone}</V>
        </p>
        {legalConfig.dataProtectionOfficer ? (
          <p>Datenschutzbeauftragte/r: {legalConfig.dataProtectionOfficer}</p>
        ) : (
          <p className="text-xs text-muted">Ein Datenschutzbeauftragter ist nicht benannt. <V>[Prüfen, ob eine Benennungspflicht besteht]</V></p>
        )}
      </Section>

      <Section id="ueberblick" title="2. Überblick">
        <p>
          Wir verarbeiten personenbezogene Daten nur, soweit es für den Betrieb dieser Website, die Online-Terminbuchung
          und dein Kundenkonto erforderlich ist, oder wenn du eingewilligt hast. Rechtsgrundlagen sind insbesondere
          Art. 6 Abs. 1 lit. b DSGVO (Vertrag / vorvertragliche Maßnahmen), lit. c (rechtliche Pflichten), lit. f
          (berechtigte Interessen) und lit. a (Einwilligung) sowie § 25 TDDDG für den Zugriff auf dein Endgerät.
        </p>
      </Section>

      <Section id="hosting" title="3. Hosting und Server-Logfiles">
        <p>
          Die Website und das Buchungssystem werden betrieben bei: <V>{p.hosting}</V>. Mit dem Anbieter besteht ein Vertrag
          zur Auftragsverarbeitung (Art. 28 DSGVO).
        </p>
        <p>
          Beim Aufruf der Website verarbeitet der Server technisch notwendige Daten (IP-Adresse, Zeitpunkt, aufgerufene
          Seite, Browser-Kennung). Das dient der Auslieferung, Stabilität und Sicherheit der Website (z. B. Begrenzung
          zu vieler Anfragen von derselben IP-Adresse) – Art. 6 Abs. 1 lit. f DSGVO. Unsere Anwendungs-Logs enthalten
          keine Namen, E-Mail-Adressen oder Telefonnummern. Logdaten werden nach {r.serverLogsDays} Tagen gelöscht.
        </p>
      </Section>

      <Section id="buchung" title="4. Online-Terminbuchung">
        <p>
          Für eine Buchung verarbeiten wir die gewählten Leistungen, Barber und Uhrzeit, deinen Namen, deine E-Mail-Adresse,
          Telefonnummer, eine optionale Notiz an den Barber, die Sprache sowie den Status der Buchung und der Anzahlung.
          Zweck ist die Durchführung des Termins (Art. 6 Abs. 1 lit. b DSGVO). Dein Barber sieht die Angaben, die er für
          den Termin braucht.
        </p>
        <p>
          Stornierte, abgelehnte und abgelaufene Anfragen werden nach {r.cancelledBookingsMonths} Monaten anonymisiert
          (Name, E-Mail, Telefon und Notiz werden entfernt). Abgeschlossene Termine bewahren wir auf: <V>{r.completedBookingsYears}</V>.
        </p>
      </Section>

      <Section id="konto" title="5. Kundenkonto und Treueprogramm">
        <p>
          Wenn du ein Kundenkonto anlegst, speichern wir Name, E-Mail-Adresse, Telefonnummer, Sprache, ein verschlüsseltes
          (gehashtes) Passwort, deine Buchungen und deinen Treuefortschritt (Art. 6 Abs. 1 lit. b DSGVO). Zur Bestätigung
          deiner E-Mail-Adresse senden wir einen Link. Nicht bestätigte Konten werden nach {r.unverifiedAccountsDays} Tagen
          gelöscht.
        </p>
        <p>
          Für die Anmeldung setzen wir ein technisch notwendiges, verschlüsseltes Sitzungs-Cookie (httpOnly) und speichern
          einen Hinweis im lokalen Speicher deines Browsers, dass eine Sitzung besteht (§ 25 Abs. 2 Nr. 2 TDDDG). In deinem
          Profil kannst du deine Daten jederzeit als Datei exportieren und dein Konto löschen; zukünftige Termine werden
          dabei nach den Stornobedingungen storniert.
        </p>
      </Section>

      <Section id="emails" title="6. E-Mails">
        <p>
          Wir senden dir E-Mails, die für Buchung und Konto nötig sind (Anfrage erhalten, Annahme mit Zahlungslink,
          Bestätigung mit Kalenderdatei, Erinnerung, Stornierung, Konto-Bestätigung, Passwort zurücksetzen) – Art. 6 Abs. 1
          lit. b DSGVO. Versand über: <V>{p.email}</V> (Auftragsverarbeitung). Ausgehende E-Mails werden bis zum
          erfolgreichen Versand zwischengespeichert und danach nur noch als Versandprotokoll ohne Inhalt aufbewahrt.
        </p>
      </Section>

      <Section id="zahlung" title="7. Anzahlung">
        <p>
          <V>{p.payments}</V>. Im Demo-Modus werden keine Zahlungsdaten abgefragt oder verarbeitet; gespeichert werden nur
          Betrag und Status der (simulierten) Zahlung.
        </p>
      </Section>

      <Section id="botschutz" title="8. Schutz vor Missbrauch (Bot-Schutz)">
        <p>
          Zum Schutz der Formulare für Buchung, Registrierung und Passwort-Zurücksetzen vor automatisierten Anfragen
          verwenden wir – sofern aktiviert – Cloudflare Turnstile ({p.captcha}). Dabei werden technische Daten deines
          Browsers und deine IP-Adresse an Cloudflare übermittelt, um Menschen von Bots zu unterscheiden; es werden keine
          Werbe-Cookies gesetzt. Rechtsgrundlage ist unser berechtigtes Interesse an einem sicheren Betrieb (Art. 6 Abs. 1
          lit. f DSGVO, § 25 Abs. 2 Nr. 2 TDDDG). Cloudflare ist unter dem EU-US Data Privacy Framework zertifiziert.{" "}
          <V>[Rechtlich prüfen]</V>
        </p>
      </Section>

      <Section id="cookies" title="9. Cookies und Einwilligung">
        <p>
          Ohne deine Einwilligung verwenden wir nur technisch notwendige Speicherungen: Sprachwahl (Cookie NEXT_LOCALE, ein
          Jahr), Farbschema (lokaler Speicher), Anmeldung (siehe Ziffer 5) und deine Cookie-Auswahl selbst. Für
          <strong> Statistik</strong> und <strong>Marketing</strong> fragen wir beim ersten Besuch nach deiner Einwilligung
          (Art. 6 Abs. 1 lit. a DSGVO, § 25 Abs. 1 TDDDG). Vorher werden keine Analyse- oder Werbeskripte geladen.
        </p>
        <p>
          Deine Auswahl speichern wir mit Zeitpunkt und Version der Einstellungen in deinem Browser für 12 Monate. Du kannst
          sie jederzeit über „Cookie-Einstellungen“ im Fußbereich der Website ändern oder widerrufen; der Widerruf gilt ab
          diesem Zeitpunkt.
        </p>
      </Section>

      <Section id="statistik" title="10. Statistik (nur mit Einwilligung)">
        <p>
          Mit deiner Einwilligung in „Statistik“ messen wir, wie die Website genutzt wird (z. B. aufgerufene Seiten,
          Schritte der Buchung), um sie zu verbessern. Eingesetzter Dienst: <V>[Statistik-Dienst, z. B. Google Analytics 4
          (Google Ireland Ltd.) mit IP-Kürzung – oder „keiner“]</V>. Dabei werden keine Namen, E-Mail-Adressen oder
          Telefonnummern übermittelt.
        </p>
      </Section>

      <Section id="marketing" title="11. Marketing und Kampagnenmessung (nur mit Einwilligung)">
        <p>
          Mit deiner Einwilligung in „Marketing“ erkennen wir, über welche Werbeanzeige oder Kampagne du zu uns gekommen
          bist: Kampagnen-Parameter in der Adresse (utm_source, utm_medium, utm_campaign, utm_term, utm_content) sowie
          Klick-Kennungen von Google (gclid) oder Meta (fbclid) werden beim ersten Besuch für 30 Tage in deinem Browser
          gespeichert und mit einer Buchung an uns übermittelt. So sehen wir, welche Anzeige zu welchen Buchungen geführt
          hat. Optional eingesetzte Dienste: <V>[z. B. Google Ads Conversion-Tracking, Meta-Pixel (Meta Platforms Ireland
          Ltd.) – oder „keine“]</V>. Ohne Einwilligung wird nichts davon gespeichert oder übermittelt.
        </p>
      </Section>

      <Section id="empfaenger" title="12. Empfänger und Drittländer">
        <p>
          Empfänger sind die genannten Auftragsverarbeiter (Hosting, E-Mail-Versand) und – nur bei Einwilligung bzw.
          Aktivierung – die genannten Statistik-, Marketing- und Bot-Schutz-Dienste. Erfolgt dabei eine Übermittlung in die
          USA, stützen wir sie auf das EU-US Data Privacy Framework bzw. Standardvertragsklauseln.
        </p>
      </Section>

      <Section id="rechte" title="13. Deine Rechte">
        <p>
          Du hast das Recht auf Auskunft (Art. 15 DSGVO), Berichtigung (Art. 16), Löschung (Art. 17), Einschränkung (Art. 18),
          Datenübertragbarkeit (Art. 20) und Widerspruch gegen Verarbeitungen auf Grundlage berechtigter Interessen
          (Art. 21). Eine Einwilligung kannst du jederzeit mit Wirkung für die Zukunft widerrufen (Art. 7 Abs. 3). Du hast
          außerdem das Recht, dich bei einer Datenschutz-Aufsichtsbehörde zu beschweren, z. B. bei:{" "}
          <V>{legalConfig.supervisoryAuthority}</V>.
        </p>
      </Section>

      <Section id="pflicht" title="14. Pflicht zur Bereitstellung">
        <p>
          Name, E-Mail-Adresse und Telefonnummer brauchen wir, um einen Termin zu vereinbaren. Ohne diese Angaben ist keine
          Online-Buchung möglich; du kannst uns aber jederzeit anrufen.
        </p>
      </Section>
    </>
  );
}

export function PrivacyEn() {
  return (
    <>
      <p className="text-xs text-muted">
        This English version is for convenience; in case of doubt the German version applies.
      </p>
      <Section id="controller" title="1. Controller">
        <p>
          <V>{o.name}</V>, <V>{o.street}</V>, <V>{o.postalCode}</V> <V>{o.city}</V>
          <br />
          Email: <V>{o.email}</V> · Phone: <V>{o.phone}</V>
        </p>
        {legalConfig.dataProtectionOfficer ? (
          <p>Data protection officer: {legalConfig.dataProtectionOfficer}</p>
        ) : (
          <p className="text-xs text-muted">No data protection officer has been appointed. <V>[Check whether one is required]</V></p>
        )}
      </Section>

      <Section id="overview" title="2. Overview">
        <p>
          We only process personal data as far as it is needed to run this website, the online booking and your customer
          account, or if you have given your consent. Legal bases are in particular Art. 6 (1) (b) GDPR (contract), (c)
          (legal obligations), (f) (legitimate interests) and (a) (consent), and § 25 TDDDG for access to your device.
        </p>
      </Section>

      <Section id="hosting" title="3. Hosting and server logs">
        <p>
          The website and booking system are hosted by: <V>{p.hosting}</V>, under a data processing agreement (Art. 28 GDPR).
        </p>
        <p>
          When you visit the website, the server processes technically necessary data (IP address, time, page requested,
          browser identifier) to deliver the site and keep it stable and secure (e.g. limiting too many requests from one
          IP address) – Art. 6 (1) (f) GDPR. Our application logs contain no names, email addresses or phone numbers. Log
          data is deleted after {r.serverLogsDays} days.
        </p>
      </Section>

      <Section id="booking" title="4. Online booking">
        <p>
          For a booking we process the chosen services, barber and time, your name, email address, phone number, an
          optional note for the barber, your language and the status of the booking and deposit, in order to provide the
          appointment (Art. 6 (1) (b) GDPR). Your barber sees what is needed for the appointment.
        </p>
        <p>
          Cancelled, declined and expired requests are anonymised after {r.cancelledBookingsMonths} months (name, email,
          phone and note are removed). Completed appointments are kept: <V>{r.completedBookingsYears}</V>.
        </p>
      </Section>

      <Section id="account" title="5. Customer account and loyalty programme">
        <p>
          If you create an account, we store your name, email address, phone number, language, a hashed password, your
          bookings and your loyalty progress (Art. 6 (1) (b) GDPR). We send a link to confirm your email address.
          Unconfirmed accounts are deleted after {r.unverifiedAccountsDays} days.
        </p>
        <p>
          For signing in we set a technically necessary, encrypted session cookie (httpOnly) and keep a note in your
          browser&apos;s local storage that a session exists (§ 25 (2) no. 2 TDDDG). In your profile you can export your data
          as a file and delete your account at any time; future appointments are then cancelled under the cancellation
          terms.
        </p>
      </Section>

      <Section id="emails" title="6. Emails">
        <p>
          We send the emails needed for bookings and your account (request received, acceptance with payment link,
          confirmation with calendar file, reminder, cancellation, account confirmation, password reset) – Art. 6 (1) (b)
          GDPR. Sent via: <V>{p.email}</V> (processor). Outgoing emails are stored until they have been sent and afterwards
          only kept as a delivery log without content.
        </p>
      </Section>

      <Section id="deposit" title="7. Deposit">
        <p>
          <V>{p.payments}</V>. In demo mode no payment data is requested or processed; only the amount and status of the
          (simulated) payment are stored.
        </p>
      </Section>

      <Section id="bot-protection" title="8. Abuse protection (bot check)">
        <p>
          To protect the booking, registration and password-reset forms against automated requests we use – if enabled –
          Cloudflare Turnstile ({p.captcha}). Technical browser data and your IP address are sent to Cloudflare to tell
          humans from bots; no advertising cookies are set. Legal basis: our legitimate interest in secure operation
          (Art. 6 (1) (f) GDPR, § 25 (2) no. 2 TDDDG). Cloudflare is certified under the EU-US Data Privacy Framework.{" "}
          <V>[Legal review]</V>
        </p>
      </Section>

      <Section id="cookies" title="9. Cookies and consent">
        <p>
          Without your consent we only use technically necessary storage: language (cookie NEXT_LOCALE, one year), colour
          scheme (local storage), sign-in (see section 5) and your cookie choice itself. For <strong>statistics</strong> and{" "}
          <strong>marketing</strong> we ask for your consent on your first visit (Art. 6 (1) (a) GDPR, § 25 (1) TDDDG).
          No analytics or advertising scripts load before that.
        </p>
        <p>
          We store your choice with the time and the version of the settings in your browser for 12 months. You can change
          or withdraw it at any time via “Cookie settings” in the website footer; withdrawal applies from that moment.
        </p>
      </Section>

      <Section id="statistics" title="10. Statistics (only with consent)">
        <p>
          With your consent to “Statistics” we measure how the website is used (e.g. pages viewed, booking steps) to
          improve it. Service used: <V>[Statistics service, e.g. Google Analytics 4 (Google Ireland Ltd.) with IP
          truncation – or “none”]</V>. No names, email addresses or phone numbers are sent.
        </p>
      </Section>

      <Section id="marketing" title="11. Marketing and campaign measurement (only with consent)">
        <p>
          With your consent to “Marketing” we can tell which ad or campaign brought you to us: campaign parameters in the
          address (utm_source, utm_medium, utm_campaign, utm_term, utm_content) and click IDs from Google (gclid) or Meta
          (fbclid) are stored in your browser for 30 days on your first visit and sent to us with a booking. This shows us
          which ad led to which bookings. Optional services: <V>[e.g. Google Ads conversion tracking, Meta Pixel (Meta
          Platforms Ireland Ltd.) – or “none”]</V>. Without consent none of this is stored or sent.
        </p>
      </Section>

      <Section id="recipients" title="12. Recipients and third countries">
        <p>
          Recipients are the processors named above (hosting, email) and – only with consent or when enabled – the
          statistics, marketing and bot-protection services named. Where data is transferred to the USA, this is based on
          the EU-US Data Privacy Framework or standard contractual clauses.
        </p>
      </Section>

      <Section id="rights" title="13. Your rights">
        <p>
          You have the right of access (Art. 15 GDPR), rectification (Art. 16), erasure (Art. 17), restriction (Art. 18),
          data portability (Art. 20) and to object to processing based on legitimate interests (Art. 21). You can withdraw
          consent at any time with effect for the future (Art. 7 (3)). You also have the right to lodge a complaint with a
          data protection supervisory authority, e.g.: <V>{legalConfig.supervisoryAuthority}</V>.
        </p>
      </Section>

      <Section id="required" title="14. Obligation to provide data">
        <p>
          We need your name, email address and phone number to arrange an appointment. Without them online booking is not
          possible, but you can always call us.
        </p>
      </Section>
    </>
  );
}
