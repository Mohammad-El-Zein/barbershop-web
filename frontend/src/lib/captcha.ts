/**
 * Bot check (Cloudflare Turnstile). With NEXT_PUBLIC_TURNSTILE_SITE_KEY set, the booking,
 * registration and forgot-password requests carry the widget token in X-Captcha-Token
 * (verified by the backend, APP_CAPTCHA_PROVIDER=turnstile). Unset: no widget, no header.
 */
export const TURNSTILE_SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? "";
export const CAPTCHA_REQUIRED = !!TURNSTILE_SITE_KEY;

export function captchaHeaders(token: string | null): Record<string, string> {
  return token ? { "X-Captcha-Token": token } : {};
}
