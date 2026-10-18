# Legal texts — TEMPLATES, review required

The Impressum, Datenschutzerklärung and AGB / cancellation terms in this folder are
**templates written by the developer, not legal advice**. Before the website goes
live they **must be reviewed and adapted by a lawyer** (German law: DDG, MStV,
DSGVO/GDPR, TDDDG, BGB, HwO).

## What has to be done before launch

1. Fill in every `[…]` placeholder in `src/config/legal.config.ts` (company data,
   register, VAT ID, chamber, supervisory authority, processors, retention).
   Placeholders are highlighted on the pages, so missing ones are easy to spot.
2. Have a lawyer review all three texts (both languages; the German version is
   the binding one). Open questions are marked inline, e.g. the right of
   withdrawal (§ 312g BGB) and consumer dispute resolution.
3. Keep the privacy policy in sync with what is really configured in production:
   - hosting provider and region, email provider,
   - whether Cloudflare Turnstile is enabled,
   - which statistics / marketing tools are active (GA4, Meta Pixel, Google Ads)
     — remove the sections of tools you don't use,
   - the retention periods of the backend jobs (`APP_RETENTION_*`, see
     docs/DEPLOYMENT.md) must match `legalConfig.retention`.
4. Set `reviewed: true` and update `lastUpdated` in `legal.config.ts`. Only then
   does the "template — not legally reviewed" notice disappear.

## Where the numbers come from

The AGB (`Terms.tsx`) read deposit, refund cutoff (24 h), online cancellation
cutoff (2 h), barber response and payment deadlines, lead time and loyalty rules
from the shop's live booking rules (`GET /api/public/{tenant}`), so the terms
always match what the booking system enforces. If the backend is unreachable,
the defaults from `src/lib/rules.ts` are shown.

## Files

| File | Page | Route |
| --- | --- | --- |
| `Impressum.tsx` | Impressum / legal notice | `/[locale]/impressum` |
| `Privacy.tsx` | Datenschutzerklärung / privacy policy | `/[locale]/datenschutz` |
| `Terms.tsx` | AGB & Stornobedingungen / terms & cancellation | `/[locale]/agb` |
