/**
 * LEGAL DATA for Impressum, Datenschutzerklärung and AGB.
 *
 * Every value in [square brackets] is a PLACEHOLDER: it is highlighted on the
 * pages until replaced. The texts in src/content/legal are TEMPLATES and must be
 * reviewed by a lawyer before going live (see src/content/legal/README.md).
 * Set `reviewed: true` only after that review — until then every legal page
 * shows a "template, not legally reviewed" notice.
 */
export const legalConfig = {
  /** true = texts reviewed and all placeholders filled → the template notice disappears. */
  reviewed: false,
  /** "Stand" / last updated date of the legal texts (YYYY-MM-DD). */
  lastUpdated: "2026-10-18",

  /** Operator of the shop and the website (Impressum, controller in the privacy policy). */
  operator: {
    name: "[Firmenname bzw. Vor- und Nachname des Inhabers]",
    legalForm: "[Rechtsform, z. B. Einzelunternehmen / GmbH]",
    representative: "[Vertretungsberechtigte Person, z. B. Geschäftsführer/in]",
    street: "[Straße und Hausnummer]",
    postalCode: "[PLZ]",
    city: "[Ort]",
    country: "Deutschland",
    phone: "[Telefonnummer]",
    email: "[E-Mail-Adresse]",
    /** Commercial register: leave empty strings if not registered. */
    registerCourt: "[Registergericht, z. B. Amtsgericht Berlin-Charlottenburg]",
    registerNumber: "[Registernummer, z. B. HRB 000000]",
    vatId: "[USt-IdNr. nach § 27a UStG, z. B. DE000000000]",
    /** Chamber / profession (hairdressers: Handwerkskammer, Meisterpflicht). */
    chamber: "[Zuständige Handwerkskammer]",
    professionTitle: "[Berufsbezeichnung, z. B. Friseurmeister/in, verliehen in Deutschland]",
    /** Person responsible for editorial content (§ 18 Abs. 2 MStV). */
    contentResponsible: "[Name, Anschrift wie oben]",
  },

  /** Data protection officer — only if one is appointed; otherwise leave empty. */
  dataProtectionOfficer: "",

  /** Competent data protection supervisory authority (depends on the federal state). */
  supervisoryAuthority: "[Zuständige Datenschutz-Aufsichtsbehörde, z. B. Berliner Beauftragte für Datenschutz und Informationsfreiheit]",

  /** Processors named in the privacy policy (replace with the real providers + their seats). */
  processors: {
    hosting: "[Hosting-Anbieter, z. B. Microsoft Ireland Operations Ltd. (Azure, Region Westeuropa) oder VPS-Anbieter]",
    email: "[E-Mail-Versanddienst, z. B. Azure Communication Services / SMTP-Anbieter]",
    captcha: "Cloudflare, Inc. (Turnstile), 101 Townsend St., San Francisco, CA 94107, USA",
    payments: "[Zahlungsdienstleister – derzeit keiner: Anzahlungen laufen im Demo-Modus]",
  },

  /** How long data is kept (must match the backend's retention settings, see docs/DEPLOYMENT.md). */
  retention: {
    serverLogsDays: 14,
    cancelledBookingsMonths: 6,
    unverifiedAccountsDays: 30,
    completedBookingsYears: "[Aufbewahrung abgeschlossener Termine, z. B. 3 Jahre – mit Steuerberatung abstimmen]",
  },
};

/** True for a "[…]" placeholder value. */
export function isPlaceholder(value: string) {
  return /^\[.*\]$/.test(value.trim());
}
