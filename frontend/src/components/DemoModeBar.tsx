import { getTranslations } from "next-intl/server";

/**
 * Site-wide "DEMO" strip, rendered by the layout when the shop runs demo payments
 * (bookingRules.demoPayments, i.e. PAYMENT_PROVIDER=demo — also on a public demo
 * deployment with PAYMENT_DEMO_ALLOW_IN_PROD=true). Fixed at the top and part of the
 * server HTML, so it is visible from the first paint (above the preloader) and never
 * shifts the layout; html[data-demo] reserves its height (--demo-bar, globals.css) for
 * the nav, the scroll progress bar and the page.
 */
export async function DemoModeBar({ locale }: { locale: string }) {
  const t = await getTranslations({ locale, namespace: "pay" });
  return (
    <div
      role="note"
      className="demo-bar fixed inset-x-0 top-0 z-[105] flex items-center justify-center gap-2 overflow-hidden whitespace-nowrap bg-[repeating-linear-gradient(-45deg,#c9a45c_0_14px,#b08a3e_14px_28px)] px-3 text-[11px] font-bold uppercase tracking-[0.14em] text-[#0a0a0a] sm:text-xs"
    >
      <span aria-hidden="true">⚠</span>
      <span className="truncate">{t("demoSiteBar")}</span>
    </div>
  );
}
