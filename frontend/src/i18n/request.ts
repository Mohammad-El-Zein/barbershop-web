import * as rootParams from "next/root-params";
import { notFound } from "next/navigation";
import { getRequestConfig } from "next-intl/server";
import { hasLocale } from "next-intl";
import { routing } from "./routing";

type Messages = { [key: string]: string | string[] | Messages };

/** Deep merge: the app texts (messages/app.<locale>.json) extend the landing texts (messages/<locale>.json). */
function merge(a: Messages, b: Messages): Messages {
  const out: Messages = { ...a };
  for (const [k, v] of Object.entries(b)) {
    const prev = out[k];
    out[k] =
      prev && typeof prev === "object" && !Array.isArray(prev) && typeof v === "object" && !Array.isArray(v)
        ? merge(prev, v)
        : v;
  }
  return out;
}

/**
 * Per-request i18n config. The locale comes from the [locale] root param
 * (next/root-params, Next 16.3+), which keeps /de and /en statically renderable.
 */
export default getRequestConfig(async ({ locale }) => {
  if (!locale) {
    const param = await rootParams.locale();
    if (!hasLocale(routing.locales, param)) notFound();
    locale = param;
  }
  const landing = (await import(`../../messages/${locale}.json`)).default as Messages;
  const app = (await import(`../../messages/app.${locale}.json`)).default as Messages;
  return { locale, messages: merge(landing, app) };
});
