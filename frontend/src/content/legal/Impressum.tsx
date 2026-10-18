import { legalConfig } from "@/config/legal.config";
import { Section, V } from "@/components/legal/Legal";

/**
 * Impressum / legal notice (§ 5 DDG, § 18 Abs. 2 MStV). TEMPLATE — see README.md.
 * All operator data comes from src/config/legal.config.ts.
 */
const o = legalConfig.operator;

function Address() {
  return (
    <p>
      <V>{o.name}</V>
      <br />
      <V>{o.legalForm}</V>
      <br />
      <V>{o.street}</V>
      <br />
      <V>{o.postalCode}</V> <V>{o.city}</V>
      <br />
      {o.country}
    </p>
  );
}

export function ImpressumDe() {
  return (
    <>
      <Section id="anbieter" title="Angaben gemäß § 5 DDG">
        <Address />
        <p>
          Vertreten durch: <V>{o.representative}</V>
        </p>
      </Section>
      <Section id="kontakt" title="Kontakt">
        <p>
          Telefon: <V>{o.phone}</V>
          <br />
          E-Mail: <V>{o.email}</V>
        </p>
      </Section>
      <Section id="register" title="Registereintrag und Umsatzsteuer">
        <p>
          Registergericht: <V>{o.registerCourt}</V>
          <br />
          Registernummer: <V>{o.registerNumber}</V>
          <br />
          Umsatzsteuer-Identifikationsnummer gemäß § 27a UStG: <V>{o.vatId}</V>
        </p>
        <p className="text-xs text-muted">Nicht zutreffende Angaben (z. B. kein Handelsregistereintrag) bitte entfernen.</p>
      </Section>
      <Section id="beruf" title="Berufsrechtliche Angaben">
        <p>
          Berufsbezeichnung: <V>{o.professionTitle}</V>
          <br />
          Zuständige Kammer: <V>{o.chamber}</V>
          <br />
          Es gelten die Handwerksordnung (HwO) und die Regelungen der zuständigen Handwerkskammer.
        </p>
      </Section>
      <Section id="inhalt" title="Verantwortlich für den Inhalt nach § 18 Abs. 2 MStV">
        <p>
          <V>{o.contentResponsible}</V>
        </p>
      </Section>
      <Section id="streit" title="Verbraucherstreitbeilegung">
        <p>
          Wir sind nicht bereit und nicht verpflichtet, an Streitbeilegungsverfahren vor einer
          Verbraucherschlichtungsstelle teilzunehmen. <V>[Mit Rechtsberatung prüfen und ggf. anpassen]</V>
        </p>
      </Section>
      <Section id="haftung" title="Haftung für Inhalte und Links">
        <p>
          Wir erstellen die Inhalte dieser Website mit Sorgfalt, übernehmen aber keine Gewähr für Richtigkeit,
          Vollständigkeit und Aktualität. Für Inhalte verlinkter externer Websites sind ausschließlich deren Betreiber
          verantwortlich. Wird uns eine Rechtsverletzung bekannt, entfernen wir den betreffenden Inhalt bzw. Link
          umgehend.
        </p>
      </Section>
      <Section id="bilder" title="Bildnachweise">
        <p>
          <V>[Quellen / Lizenzen der verwendeten Fotos und Grafiken]</V>
        </p>
      </Section>
    </>
  );
}

export function ImpressumEn() {
  return (
    <>
      <p className="text-xs text-muted">
        This English version is for convenience; the German Impressum is the legally relevant one.
      </p>
      <Section id="provider" title="Information pursuant to § 5 DDG (German Digital Services Act)">
        <Address />
        <p>
          Represented by: <V>{o.representative}</V>
        </p>
      </Section>
      <Section id="contact" title="Contact">
        <p>
          Phone: <V>{o.phone}</V>
          <br />
          Email: <V>{o.email}</V>
        </p>
      </Section>
      <Section id="register" title="Commercial register and VAT">
        <p>
          Register court: <V>{o.registerCourt}</V>
          <br />
          Register number: <V>{o.registerNumber}</V>
          <br />
          VAT identification number (§ 27a UStG): <V>{o.vatId}</V>
        </p>
      </Section>
      <Section id="profession" title="Professional information">
        <p>
          Professional title: <V>{o.professionTitle}</V>
          <br />
          Competent chamber: <V>{o.chamber}</V>
          <br />
          The German Crafts Code (Handwerksordnung) and the rules of the competent chamber apply.
        </p>
      </Section>
      <Section id="content" title="Responsible for content (§ 18 (2) MStV)">
        <p>
          <V>{o.contentResponsible}</V>
        </p>
      </Section>
      <Section id="disputes" title="Consumer dispute resolution">
        <p>
          We are neither willing nor obliged to take part in dispute resolution proceedings before a consumer
          arbitration board. <V>[Check with legal advice and adapt if needed]</V>
        </p>
      </Section>
      <Section id="liability" title="Liability for content and links">
        <p>
          We create the content of this website with care but cannot guarantee that it is correct, complete and up to
          date. The operators of linked external websites are solely responsible for their content. If we become aware
          of an infringement, we remove the content or link concerned immediately.
        </p>
      </Section>
      <Section id="images" title="Image credits">
        <p>
          <V>[Sources / licences of the photos and graphics used]</V>
        </p>
      </Section>
    </>
  );
}
