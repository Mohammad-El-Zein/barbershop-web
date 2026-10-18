"use client";

/**
 * Last-resort error page (the root layout itself failed): renders its own document
 * without the app's CSS or messages, so styles are inline and the text is bilingual.
 */
export default function GlobalError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <html lang="de">
      <body style={{ margin: 0, minHeight: "100vh", display: "grid", placeItems: "center", background: "#0a0a0a", color: "#f2efe9", fontFamily: "system-ui, sans-serif", textAlign: "center", padding: 24 }}>
        <title>THE BARBER NEWS</title>
        <main>
          <p style={{ fontSize: 72, margin: 0, color: "#c9a45c", fontWeight: 700 }}>500</p>
          <h1 style={{ fontSize: 28, margin: "12px 0" }}>Etwas ist schiefgelaufen · Something went wrong</h1>
          <p style={{ color: "rgba(242,239,233,0.7)", maxWidth: 420, margin: "0 auto 24px" }}>
            Bitte versuch es gleich noch einmal. · Please try again in a moment.
          </p>
          <button type="button" onClick={() => retry()} style={{ background: "#c9a45c", color: "#120d05", border: 0, borderRadius: 999, padding: "12px 28px", fontWeight: 600, cursor: "pointer" }}>
            Neu laden · Retry
          </button>
        </main>
      </body>
    </html>
  );
}
