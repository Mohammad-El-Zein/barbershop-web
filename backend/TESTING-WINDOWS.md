# Testing the backend on Windows (PowerShell, no bash)

Everything below is copy-paste for **Windows PowerShell 5.1** (built in) or **PowerShell 7**.
Run it from the repository root (`C:\...\barbershop-web`). You need **Docker Desktop**.

> Payments are **DEMO ONLY**: no real payment provider, no card fields, no money moves.
> "Simulate payment" goes through the same code path a real provider webhook will use later.

---

## 1. Start the stack

```powershell
# once: create your local config (then edit the passwords/secret in infra\.env)
if (-not (Test-Path infra\.env)) { Copy-Item infra\.env.example infra\.env }

docker compose -f infra/docker-compose.yml up -d --build

# wait until the API is ready
do { Start-Sleep 3; try { $h = Invoke-RestMethod http://localhost:8080/actuator/health/readiness } catch { $h = $null } } until ($h.status -eq 'UP'); "API is UP"
```

| What | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Mailpit (all emails) | http://localhost:8025 |

Restart after code changes: `docker compose -f infra/docker-compose.yml up -d --build backend`
Logs: `docker logs -f barbershop-backend-1`
Start from an empty database (deletes all local data!): `docker compose -f infra/docker-compose.yml down -v`

## 2. Run the automated tests

```powershell
cd backend
.\mvnw.cmd test      # unit tests: state machine, prices/loyalty/deposit, refund rule, deadlines, availability, .ics
.\mvnw.cmd verify    # + integration tests (Testcontainers Postgres + GreenMail) - Docker must be running
cd ..
```

Integration test suites (`backend/src/test/java/com/barbershop/`):

| Suite | Covers |
|---|---|
| `BookingFlowIT` | happy path via the real email links (accept → demo payment → confirmed, all emails, .ics), decline, link replay / purpose binding / expiry, idempotent and concurrent payment success, cancel with and without refund, cancel on the payment page |
| `BookingJobsIT` | expiry of unanswered requests and unpaid acceptances, late payment refunded, auto-complete vs. no-show, 24 h reminder |
| `BookingConcurrencyIT` | 16 parallel requests for one slot (exactly one wins), "any barber" race, DB exclusion constraint for every status, buffer |
| `LoyaltyIT` | 10 completed visits → 11th booking 50 % off → reset; release on cancel; parallel bookings get the reward only once |
| `AccountIT` | register/verify/login, refresh rotation + reuse detection, logout, no user enumeration, lockout, forgot/reset/change password, guest-to-account linking, GDPR export + delete, barber invite and barber API |
| `AdminApiIT` | walk-in/phone bookings, refund override, admin cancel, customer list, settings, CRUD, stats, tenant isolation, OpenAPI |
| `PublicApiIT` | availability per barber + any barber, multiple services, lead time / slot step / horizon, price preview, validation, anti-spam limit |
| `RateLimitIT` | per-IP limits on booking, login, mail-sending endpoints and link actions |
| `PaymentProviderGuardIT` | the app refuses to start with `prod` + demo payments unless `payment.demo.allow-in-prod=true` |

## 3. The main flow in one command

```powershell
powershell -ExecutionPolicy Bypass -File infra\demo-flow.ps1
```

Request (haircut + beard, guest) → barber's email link (GET shows, POST accepts) → "Pay now" email → payment page
→ **Simulate payment** → CONFIRMED + `.ics` → repeat is idempotent → used link is refused (410) → cancel via the
manage link → deposit refunded (simulated). All links are read from Mailpit. Optional: `-Date 2026-11-03`.

Accounts, loyalty, barber login, admin tools and the expiry job:

```powershell
powershell -ExecutionPolicy Bypass -File infra\demo-accounts.ps1
```

> The per-IP rate limits are real (5 logins/min, 5 register/forgot-password per 10 min). If you run the demos
> several times in a row, the scripts print `(rate limit: HTTP 429 ... waiting N s)` and continue. For lots of
> manual testing you can raise them in `infra\.env`, e.g. `APP_RATE_LIMIT_LOGIN=100`, `APP_RATE_LIMIT_AUTH_MAIL=100`.

---

## 4. Step by step (copy-paste blocks)

### 4.0 Session setup (run first, in every new PowerShell window)

```powershell
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. .\infra\demo-helpers.ps1
Initialize-Demo
# helpers: Api METHOD PATH [body] [-Token t] [-Lang en] [-Session s]  -> .Status .Body .Headers  (never throws on 4xx)
#          Wait-Mail <to> <subject part>   Get-LinkToken <mail> "/pay/"   Login-Admin   Get-Mails
$date = (Get-Date).Date.AddDays(3); while ($date.DayOfWeek -ne 'Tuesday') { $date = $date.AddDays(1) }; $date = $date.ToString('yyyy-MM-dd'); $date
$me = "max-" + [guid]::NewGuid().ToString().Substring(0, 6) + "@example.com"; $me
```

### 4.1 Availability (all barbers at once) and price preview

```powershell
$day = (Api GET "/api/public/$TENANT/availability?services=$HAIRCUT,$BEARD&date=$date").Body
$day.barbers | ForEach-Object { "{0}: {1}" -f $_.name, (($_.slots | Select-Object -First 8 | ForEach-Object time) -join ' ') }
"any barber: " + (($day.anyBarber | Select-Object -First 8 | ForEach-Object { "$($_.time)($($_.staffIds.Count))" }) -join ' ')

(Api POST "/api/public/$TENANT/bookings/price-preview" @{ serviceIds = @($HAIRCUT, $BEARD) }).Body.price | ConvertTo-Json
```

### 4.2 Request a booking (guest)

```powershell
$start = (($day.barbers | Where-Object staffId -eq $LUCA).slots | Select-Object -First 1).start
$r = Api POST "/api/public/$TENANT/bookings" @{ serviceIds = @($HAIRCUT, $BEARD); staffId = $LUCA; start = $start;
      customerName = "Max Mustermann"; customerEmail = $me; customerPhone = "+49 151 2345678"; lang = "en";
      consent = $true; note = "Please not too short" }
$r.Status; $id = $r.Body.id; $r.Body | Select-Object id, status, start, end, durationMin
# "any barber": leave out staffId - the server picks the least busy free barber
```

### 4.3 Barber accepts via the email link

```powershell
$mail = Wait-Mail $ADMIN_EMAIL "Neue Terminanfrage: Max Mustermann"   # barber without own login -> admin gets it
$decision = Get-LinkToken $mail "/barber/requests/"
(Api GET "/api/public/barber-decision/$decision").Body | Select-Object status, customerName, durationMin, loyaltyNote, actions   # GET changes nothing
(Api POST "/api/public/barber-decision/$decision/accept").Body.status          # AWAITING_PAYMENT
(Api POST "/api/public/barber-decision/$decision/accept" -Lang en).Body | Select-Object code, detail   # 410 token.used
# decline instead:  Api POST "/api/public/barber-decision/$decision/decline" @{ reason = "Holiday" }
```

### 4.4 Demo payment

```powershell
$payMail = Wait-Mail $me "accepted"
$pay = Get-LinkToken $payMail "/pay/"
(Api GET "/api/public/payments/$pay" -Lang en).Body | Select-Object demoMode, status, @{n='amount';e={$_.amount.formatted}}, payUntil, actions
(Api POST "/api/public/payments/$pay/simulate").Body      # PAID / CONFIRMED / alreadyProcessed=false
(Api POST "/api/public/payments/$pay/simulate").Body      # idempotent: alreadyProcessed=true, no second email
# "Cancel" button instead:  Api POST "/api/public/payments/$pay/cancel"
(Wait-Mail $me "Appointment confirmed").Attachments | Select-Object FileName, ContentType
```

### 4.5 Customer cancels via the manage link (refund rule)

```powershell
$manage = Get-LinkToken (Wait-Mail $me "Appointment confirmed") "/booking/manage/"
(Api GET "/api/public/manage/$manage").Body.actions                 # canCancel, refundIfCancelledNow, refundableUntil
(Api POST "/api/public/manage/$manage/cancel" @{ reason = "sick" }).Body | Select-Object status, @{n='payment';e={$_.payment.status}}
# >= 24 h before: REFUNDED (simulated), less: FORFEITED. Try "less than 24 h": move a confirmed booking closer:
# docker exec barbershop-postgres-1 psql -U barbershop -d barbershop -c "update booking set start_time = now() + interval '5 hours', end_time = now() + interval '6 hours', blocked_until = now() + interval '6 hours' where id = '<booking id>'"
```

### 4.6 Customer account

```powershell
$anna = "anna-" + [guid]::NewGuid().ToString().Substring(0, 6) + "@example.com"; $pw = "anna-long-password"
(Api POST "/api/public/$TENANT/auth/register" @{ email = $anna; password = $pw; name = "Anna Muster"; phone = "+49 160 1234567"; lang = "de"; consent = $true }).Status   # 202
$verify = Get-LinkToken (Wait-Mail $anna "Bitte best") "/account/verify/"
(Api POST "/api/public/auth/verify-email" @{ token = $verify }).Body           # links earlier guest bookings with that email

$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession               # keeps the httpOnly refresh cookie
$login = Api POST "/api/auth/$TENANT/login" @{ email = $anna; password = $pw } -Session $s
$token = $login.Body.accessToken; $login.Body.user
$token = (Api POST "/api/auth/refresh" -Session $s).Body.accessToken          # new access token, cookie rotated

(Api GET "/api/me" -Token $token).Body
(Api GET "/api/me/bookings?scope=upcoming" -Token $token).Body | Select-Object id, status, start
(Api GET "/api/me/loyalty" -Token $token).Body                                # visitsInCurrentCycle, nextDiscountAvailable
# book as logged-in customer (name/email/phone from the account, loyalty applies):
# Api POST "/api/public/$TENANT/bookings" @{ serviceIds = @($HAIRCUT); staffId = $MO; start = "<slot start>" } -Token $token
(Api POST "/api/auth/password" @{ currentPassword = $pw; newPassword = "anna-new-password" } -Token $token -Session $s).Status   # other sessions end
(Api GET "/api/me/export" -Token $token).Body | ConvertTo-Json -Depth 6 | Out-File my-data.json
(Api POST "/api/auth/logout" -Session $s).Status                               # 204
# GDPR delete:  Api POST "/api/me/delete" @{ password = "anna-new-password" } -Token $token
```

Password reset: `Api POST "/api/public/$TENANT/auth/forgot-password" @{ email = $anna }`, then
`Api POST "/api/public/auth/reset-password" @{ token = (Get-LinkToken (Wait-Mail $anna "Passwort") "/account/reset-password/"); newPassword = "..." }`.

### 4.7 Barber login

```powershell
$admin = Login-Admin
$lucaMail = "luca-" + [guid]::NewGuid().ToString().Substring(0, 6) + "@example.com"
(Api POST "/api/admin/staff/$LUCA/account" @{ email = $lucaMail; lang = "de" } -Token $admin).Body   # 409 if Luca already has a login
$invite = Get-LinkToken (Wait-Mail $lucaMail "Barber-Zugang") "/account/reset-password/"
Api POST "/api/public/auth/reset-password" @{ token = $invite; newPassword = "luca-long-password" } | Out-Null
$luca = (Api POST "/api/auth/$TENANT/login" @{ email = $lucaMail; password = "luca-long-password" }).Body.accessToken

(Api GET "/api/barber/requests" -Token $luca).Body | Select-Object id, start, customerName, allowedTransitions
(Api GET "/api/barber/calendar?date=$date&view=week" -Token $luca).Body | Select-Object start, status, customerName
# Api POST "/api/barber/bookings/<id>/accept|decline|complete|no-show|cancel" -Token $luca
```

From now on new requests for Luca go to `$lucaMail` instead of the admin.

### 4.8 Admin

```powershell
$admin = Login-Admin
(Api GET "/api/admin/bookings?from=$date&to=$date" -Token $admin).Body.items | Select-Object start, status, customerName, @{n='payment';e={$_.payment.status}}
(Api POST "/api/admin/bookings" @{ serviceIds = @($BEARD); staffId = $MO; start = "${date}T17:00:00+01:00"; customerName = "Walk-in Willi"; source = "WALK_IN" } -Token $admin).Body | Select-Object status, @{n='due';e={$_.price.amountDueAtShop.formatted}}
(Api GET "/api/admin/customers" -Token $admin).Body.items | Select-Object registered, email, completedVisits, visitsInCurrentCycle, nextDiscountAvailable
(Api GET "/api/admin/settings" -Token $admin).Body                               # deadlines, deposit, refund cutoff, loyalty, slot step ...
# refund override (PAID or FORFEITED deposit):   Api POST "/api/admin/bookings/<id>/refund" @{ reason = "goodwill" } -Token $admin
# cancel and keep the deposit:                   Api POST "/api/admin/bookings/<id>/cancel" @{ refund = $false } -Token $admin
```

(Use the correct offset for the date: `+01:00` in winter, `+02:00` in summer — or take `start` from the availability.)

### 4.9 Scheduled job (expiry, reminders, auto-complete — runs every minute)

```powershell
# let a request expire now: move its barber deadline into the past, then wait up to a minute
docker exec barbershop-postgres-1 psql -U barbershop -d barbershop -c "update booking set barber_deadline_at = now() - interval '1 minute' where id = '$id'"
Start-Sleep 65; (Get-Mails | Where-Object Subject -like '*abgelaufen*' | Select-Object -First 3).Subject
```

### 4.10 Prod-profile guard

```powershell
# refuses to start (exit with "Refusing to start: the `prod` profile is active but payment.provider=demo ...")
docker compose -f infra/docker-compose.yml run --rm -e SPRING_PROFILES_ACTIVE=prod,demo --no-deps backend 2>&1 | Select-String "Refusing to start"
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `running scripts is disabled on this system` | use `powershell -ExecutionPolicy Bypass -File ...`, or once: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| Umlauts look broken | `[Console]::OutputEncoding = [Text.Encoding]::UTF8` (the helpers decode responses as UTF-8) |
| Port 5432 / 8080 already in use | set `POSTGRES_HOST_PORT` / `BACKEND_HOST_PORT` in `infra\.env` |
| `HTTP 429` | per-IP rate limit; the demo helpers wait automatically, or raise `APP_RATE_LIMIT_*` in `infra\.env` |
| Testcontainers can't find Docker | start Docker Desktop; `docker info` must work in the same shell |
| No free slot | pass another date (`-Date 2026-11-03`), or the shop is closed that day (Sunday) |
