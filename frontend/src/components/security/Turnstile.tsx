"use client";

import { useEffect, useImperativeHandle, useRef, type Ref } from "react";
import { useLocale } from "next-intl";
import { useTheme } from "next-themes";
import { TURNSTILE_SITE_KEY } from "@/lib/captcha";

/**
 * Cloudflare Turnstile widget (bot check for booking, registration, forgot password).
 * Renders nothing when NEXT_PUBLIC_TURNSTILE_SITE_KEY is not set (local dev, e2e: the
 * backend then runs APP_CAPTCHA_PROVIDER=none). The script is loaded only when a protected
 * form is on screen — it is a security measure, not tracking, so it doesn't wait for the
 * cookie consent (see the privacy policy). Tokens are single-use: call reset() after a
 * submit attempt.
 */
type TurnstileApi = {
  render: (el: HTMLElement, opts: Record<string, unknown>) => string;
  reset: (id: string) => void;
  remove: (id: string) => void;
};
declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

export type TurnstileHandle = { reset: () => void };

let scriptPromise: Promise<TurnstileApi> | null = null;
function loadScript(): Promise<TurnstileApi> {
  scriptPromise ??= new Promise((resolve, reject) => {
    if (window.turnstile) return resolve(window.turnstile);
    const s = document.createElement("script");
    s.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
    s.async = true;
    s.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error("turnstile")));
    s.onerror = () => {
      scriptPromise = null;
      reject(new Error("turnstile"));
    };
    document.head.appendChild(s);
  });
  return scriptPromise;
}

export function Turnstile({ onToken, ref }: { onToken: (token: string | null) => void; ref?: Ref<TurnstileHandle> }) {
  const box = useRef<HTMLDivElement>(null);
  const widget = useRef<string | null>(null);
  const onTokenRef = useRef(onToken);
  const locale = useLocale();
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    onTokenRef.current = onToken;
  }, [onToken]);

  useImperativeHandle(ref, () => ({
    reset() {
      onTokenRef.current(null);
      if (widget.current) window.turnstile?.reset(widget.current);
    },
  }));

  useEffect(() => {
    if (!TURNSTILE_SITE_KEY || !box.current) return;
    let cancelled = false;
    loadScript().then(
      (api) => {
        if (cancelled || !box.current) return;
        widget.current = api.render(box.current, {
          sitekey: TURNSTILE_SITE_KEY,
          language: locale,
          theme: resolvedTheme === "light" ? "light" : "dark",
          callback: (token: string) => onTokenRef.current(token),
          "expired-callback": () => onTokenRef.current(null),
          "error-callback": () => onTokenRef.current(null),
        });
      },
      () => onTokenRef.current(null),
    );
    return () => {
      cancelled = true;
      if (widget.current) window.turnstile?.remove(widget.current);
      widget.current = null;
    };
  }, [locale, resolvedTheme]);

  if (!TURNSTILE_SITE_KEY) return null;
  // fixed height: the widget appears without moving the form (no layout shift)
  return <div ref={box} className="min-h-[65px]" />;
}
