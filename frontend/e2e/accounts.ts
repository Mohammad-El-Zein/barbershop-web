import { expect, type Page } from "@playwright/test";
import { api, linkIn, nameFor, sql, TENANT, uniqueEmail, waitForMail } from "./support";
import { T, type Texts } from "./texts";

export const TENANT_ID = "7b3f1c2e-0000-4000-8000-000000000001";
export const PASSWORD = "e2e-long-password";

/** A verified customer account (API register + the link from Mailpit), optionally with loyalty visits. */
export async function seededCustomer(lang: "de" | "en", visitsInCycle = 0, prefix = "customer") {
  const email = uniqueEmail(`${prefix}-${lang}`);
  const reg = await api(
    "POST",
    `/api/public/${TENANT}/auth/register`,
    { email, password: PASSWORD, name: nameFor(email), phone: "+49 151 2345678", lang, consent: true },
    { lang },
  );
  if (reg.status >= 300) throw new Error(`register failed: ${reg.status}`);
  const mail = await waitForMail(email, T[lang].verifyMail);
  const token = linkIn(mail, "/account/verify/").split("/").pop()!;
  const verified = await api("POST", "/api/public/auth/verify-email", { token });
  if (verified.status !== 200) throw new Error(`verify failed: ${verified.status}`);
  if (visitsInCycle > 0) {
    sql(
      `insert into loyalty_account (user_id, tenant_id, visits_in_cycle, total_visits)
       select id, '${TENANT_ID}', ${visitsInCycle}, ${visitsInCycle} from app_user where lower(email) = lower('${email}')
       on conflict (user_id) do update set visits_in_cycle = excluded.visits_in_cycle, total_visits = excluded.total_visits`,
    );
  }
  return email;
}

/** Log in through the account login page (lands on "my bookings"). */
export async function uiLogin(page: Page, t: Texts, lang: string, email: string, password = PASSWORD) {
  await page.goto(`/${lang}/account/login`);
  await page.getByLabel(t.email, { exact: true }).fill(email);
  await page.getByLabel(t.password, { exact: true }).fill(password);
  await page.getByRole("main").getByRole("button", { name: t.login, exact: true }).click();
  await expect(page.getByRole("heading", { name: t.hello })).toBeVisible();
}
