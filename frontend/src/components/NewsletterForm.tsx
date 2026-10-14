"use client";

import { useState } from "react";

/**
 * Newsletter signup — UI only. NOT connected to any provider yet
 * (hook up Brevo/Mailchimp/backend in a later phase; keep GDPR double opt-in).
 */
export function NewsletterForm() {
  const [done, setDone] = useState(false);

  return (
    <div>
      <form
        className="mt-4 flex flex-col gap-2 sm:flex-row"
        onSubmit={(e) => {
          e.preventDefault();
          setDone(true);
        }}
      >
        <label htmlFor="newsletter-email" className="sr-only">
          Email address
        </label>
        <input
          id="newsletter-email"
          type="email"
          required
          placeholder="your@email.com"
          autoComplete="email"
          className="h-11 w-full min-w-0 rounded-full sm:flex-1 border border-white/15 bg-white/[0.03] px-5 text-sm text-ink placeholder:text-ink/35 focus:border-gold focus:outline-none"
        />
        <button type="submit" className="btn btn-outline-gold h-11">
          Subscribe
        </button>
      </form>
      <p className="mt-2 min-h-4 text-xs text-gold" aria-live="polite">
        {done ? "Thanks! (Demo: the newsletter is not connected yet.)" : ""}
      </p>
    </div>
  );
}
