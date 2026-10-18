import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { API, api, LUCA, linkIn, MAILPIT, sql, STATE_FILE, STORAGE_FILE, uniqueEmail, waitForMail } from "./support";

/**
 * Once per run: checks the stack is up, cancels bookings left by earlier runs, reads the
 * admin login from infra/.env and gives Luca a fresh barber login (invite → password via
 * the email link), so his request emails go to an address the tests can read.
 */
export default async function globalSetup() {
  for (const [what, url] of [
    ["backend", `${API}/actuator/health/readiness`],
    ["frontend", `${new URL(process.env.E2E_BASE_URL ?? "http://localhost:3000").origin}/de`],
    ["Mailpit", `${MAILPIT}/api/v1/messages?limit=1`],
  ]) {
    const ok = await fetch(url).then((r) => r.ok).catch(() => false);
    if (!ok) throw new Error(`${what} is not reachable at ${url} — see e2e/README.md`);
  }

  const env = readFileSync(resolve(process.cwd(), "../infra/.env"), "utf8");
  const get = (k: string) => env.match(new RegExp(`^${k}=(.*)$`, "m"))?.[1].trim() ?? "";
  const adminEmail = get("APP_ADMIN_EMAIL");
  const adminPassword = get("APP_ADMIN_PASSWORD");
  const admin = await api<{ accessToken: string }>("POST", "/api/auth/the-barber-news/login", {
    email: adminEmail,
    password: adminPassword,
  });
  if (admin.status !== 200) throw new Error(`admin login failed (${admin.status}) — check infra/.env`);

  // retire bookings of earlier e2e runs so they don't fill Luca's calendar and request list
  sql(
    `update booking set status = 'CANCELLED', cancelled_at = now(), cancelled_by = 'SYSTEM'
     where customer_name like 'E2E %' and status in ('PENDING_BARBER', 'AWAITING_PAYMENT', 'CONFIRMED')`,
  );

  // Luca: remove earlier e2e/demo barber logins, create a fresh one (German emails)
  sql(`delete from app_user where role = 'BARBER' and staff_id = '${LUCA}'`);
  const barberEmail = uniqueEmail("luca-e2e");
  const barberPassword = "luca-e2e-password";
  const invite = await api("POST", `/api/admin/staff/${LUCA}/account`, { email: barberEmail, lang: "de" }, {
    token: admin.body.accessToken,
  });
  if (invite.status !== 201) throw new Error(`barber invite failed: ${invite.status}`);
  const mail = await waitForMail(barberEmail, "Barber-Zugang");
  const token = linkIn(mail, "/account/reset-password/").split("/").pop()!;
  const reset = await api("POST", "/api/public/auth/reset-password", { token, newPassword: barberPassword });
  if (reset.status !== 200) throw new Error(`barber password failed: ${reset.status}`);

  writeFileSync(STATE_FILE, JSON.stringify({ adminEmail, adminPassword, barberEmail, barberPassword }, null, 2));

  // browser storage for all specs: an "only necessary" cookie decision, so the consent
  // banner doesn't cover the page (consent.spec.ts starts without it)
  const origin = new URL(process.env.E2E_BASE_URL ?? "http://localhost:3000").origin;
  const consent = { v: 1, at: new Date().toISOString(), statistics: false, marketing: false };
  writeFileSync(STORAGE_FILE, JSON.stringify({ cookies: [], origins: [{ origin, localStorage: [{ name: "consent", value: JSON.stringify(consent) }] }] }));
}
