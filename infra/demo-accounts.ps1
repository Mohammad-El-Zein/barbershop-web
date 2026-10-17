<#
  Accounts, loyalty, barber login, admin tools and the scheduled job against the running stack.
  Windows PowerShell 5.1 or 7, no bash needed:

    powershell -ExecutionPolicy Bypass -File infra\demo-accounts.ps1

  Covers: register -> verify (email link) -> login (refresh cookie) -> refresh -> my bookings / loyalty,
  guest booking linked on verification, barber invite -> barber login -> pending requests -> accept,
  admin walk-in booking, customer list, expiry job (deadline moved via psql in the postgres container).
#>
param([string]$Date = "")
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot "demo-helpers.ps1")
Initialize-Demo

if (-not $Date) {
    $d = (Get-Date).Date.AddDays(3)
    while ($d.DayOfWeek -ne [DayOfWeek]::Wednesday) { $d = $d.AddDays(1) }
    $Date = $d.ToString("yyyy-MM-dd")
}
$tag = [guid]::NewGuid().ToString().Substring(0, 6)
$email = "anna-$tag@example.com"
$password = "anna-secret-$tag"
$admin = Login-Admin

function Free-Slot([string]$Staff, [string]$From = "09:00") {
    $day = Api GET "/api/public/$TENANT/availability?services=$HAIRCUT&date=$Date"
    $s = ($day.Body.barbers | Where-Object staffId -eq $Staff).slots | Where-Object { $_.time -ge $From } | Select-Object -First 1
    if (-not $s) { throw "no free slot on $Date for $Staff - pass -Date" }
    return $s.start
}

Step "1. Guest booking first (before Anna has an account)"
$guest = Api POST "/api/public/$TENANT/bookings" @{ serviceIds = @($HAIRCUT); staffId = $MO; start = (Free-Slot $MO "10:00");
    customerName = "Anna Gast"; customerEmail = $email; customerPhone = "+49 160 1111111"; lang = "de"; consent = $true }
"HTTP $($guest.Status) guest booking $($guest.Body.id) status=$($guest.Body.status)"

Step "2. Register (same email) - always 202, no user enumeration"
$reg = Api POST "/api/public/$TENANT/auth/register" @{ email = $email; password = $password; name = "Anna Muster";
    phone = "+49 160 1111111"; lang = "de"; consent = $true }
"HTTP $($reg.Status) $($reg.Body.message)"
$again = Api POST "/api/public/$TENANT/auth/register" @{ email = $email; password = $password; name = "x x";
    phone = "+49 160 1111111"; consent = $true }
"same email again -> HTTP $($again.Status) $($again.Body.message)"

Step "3. Login before verification -> 403 (only because the password is right)"
$early = Api POST "/api/auth/$TENANT/login" @{ email = $email; password = $password }
"HTTP $($early.Status) $($early.Body.code)"
$wrong = Api POST "/api/auth/$TENANT/login" @{ email = $email; password = "wrong-password" }
$nobody = Api POST "/api/auth/$TENANT/login" @{ email = "nobody-$tag@example.com"; password = "wrong-password" }
"wrong password -> $($wrong.Status) $($wrong.Body.code) | unknown email -> $($nobody.Status) $($nobody.Body.code)"

Step "4. Verify via the email link (the frontend page POSTs the token) -> guest booking linked"
$vm = Wait-Mail $email "Bitte best"
$verify = Api POST "/api/public/auth/verify-email" @{ token = (Get-LinkToken $vm "/account/verify/") }
Show $verify.Body

Step "5. Login -> access token (body) + refresh token (httpOnly cookie)"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Api POST "/api/auth/$TENANT/login" @{ email = $email; password = $password } -Session $session
$token = $login.Body.accessToken
"HTTP $($login.Status) role=$($login.Body.user.role) expiresAt=$($login.Body.expiresAt)"
"Set-Cookie: " + (($login.Headers["Set-Cookie"] -join "") -replace "bs_refresh=[^;]+", "bs_refresh=<hidden>")

Step "6. Refresh (cookie is sent automatically by the session, and rotated)"
$ref = Api POST "/api/auth/refresh" -Session $session
"HTTP $($ref.Status) new access token: $($ref.Body.accessToken.Substring(0, 20))..."
$token = $ref.Body.accessToken

Step "7. Logged-in booking: name/email/phone from the account; my bookings; loyalty"
$mine = Api POST "/api/public/$TENANT/bookings" @{ serviceIds = @($HAIRCUT, $BEARD); staffId = $LUCA; start = (Free-Slot $LUCA "14:00") } -Token $token
"HTTP $($mine.Status) $($mine.Body.customerName) <$($mine.Body.customerEmail)> status=$($mine.Body.status) total=$($mine.Body.price.total.formatted)"
$list = Api GET "/api/me/bookings?scope=upcoming" -Token $token
$list.Body | ForEach-Object { "  {0}  {1,-16} {2}" -f $_.start, $_.status, (($_.services | ForEach-Object name) -join " + ") }
$loyal = Api GET "/api/me/loyalty" -Token $token
"loyalty: visitsInCurrentCycle=$($loyal.Body.visitsInCurrentCycle)/$($loyal.Body.threshold) nextDiscountAvailable=$($loyal.Body.nextDiscountAvailable)"

Step "8. Barber login: admin invites Mo -> Mo sets a password via the email link"
$moEmail = "mo-$tag@example.com"
# re-runnable: remove the barber login an EARLIER RUN OF THIS DEMO created (only mo-*@example.com)
docker exec barbershop-postgres-1 psql -U barbershop -d barbershop -q -c "delete from app_user where role = 'BARBER' and email like 'mo-%@example.com'" | Out-Null
$inv = Api POST "/api/admin/staff/$MO/account" @{ email = $moEmail; lang = "de" } -Token $admin
if ($inv.Status -eq 409) { "Mo already has a login (earlier run) - skipping the barber part"; $moToken = $null }
else {
    "HTTP $($inv.Status) invite sent to $moEmail"
    $im = Wait-Mail $moEmail "Barber-Zugang"
    $set = Api POST "/api/public/auth/reset-password" @{ token = (Get-LinkToken $im "/account/reset-password/"); newPassword = "mo-secret-$tag" }
    "set password -> HTTP $($set.Status) $($set.Body.message)"
    $moLogin = Api POST "/api/auth/$TENANT/login" @{ email = $moEmail; password = "mo-secret-$tag" }
    "Mo logs in -> HTTP $($moLogin.Status) role=$($moLogin.Body.user.role) staffId=$($moLogin.Body.user.staffId)"
    $moToken = $moLogin.Body.accessToken
    $req = Api GET "/api/barber/requests" -Token $moToken
    "Mo's pending requests:"
    $req.Body | ForEach-Object { "  {0}  {1}  next: {2}" -f $_.start, $_.customerName, ($_.allowedTransitions -join ",") }
    $acc = Api POST "/api/barber/bookings/$($guest.Body.id)/accept" -Token $moToken
    "Mo accepts Anna's request -> HTTP $($acc.Status) status=$($acc.Body.status)"
    $foreign = Api POST "/api/barber/bookings/$($mine.Body.id)/accept" -Token $moToken
    "Mo tries Luca's booking -> HTTP $($foreign.Status) $($foreign.Body.code)"
    $cal = Api GET "/api/barber/calendar?date=$Date&view=week" -Token $moToken
    "Mo's week: $($cal.Body.Count) booking(s)"
}

Step "9. Admin: walk-in booking (confirmed, no deposit) and the customer list"
$walk = Api POST "/api/admin/bookings" @{ serviceIds = @($BEARD); staffId = $LUCA; start = (Free-Slot $LUCA "17:00");
    customerName = "Walk-in Willi"; source = "WALK_IN" } -Token $admin
"HTTP $($walk.Status) status=$($walk.Body.status) deposit=$($walk.Body.price.deposit.formatted) due=$($walk.Body.price.amountDueAtShop.formatted)"
$cust = Api GET "/api/admin/customers?q=$tag" -Token $admin
$cust.Body.items | ForEach-Object { "  registered={0,-5} {1,-28} completedVisits={2} bookings={3}" -f $_.registered, $_.email, $_.completedVisits, $_.totalBookings }

Step "10. Expiry job: move Luca's request deadline into the past, wait for the job (runs every minute)"
docker exec barbershop-postgres-1 psql -U barbershop -d barbershop -c "update booking set barber_deadline_at = now() - interval '1 minute' where id = '$($mine.Body.id)'" | Out-Null
$deadline = (Get-Date).AddSeconds(90)
do { Start-Sleep 5; $st = (Api GET "/api/me/bookings/$($mine.Body.id)" -Token $token).Body.status } until ($st -ne "PENDING_BARBER" -or (Get-Date) -gt $deadline)
"status now: $st"
$em = Wait-Mail $email "abgelaufen" 30; "email: $($em.Subject)"

Step "11. Logout -> refresh no longer works"
$out = Api POST "/api/auth/logout" -Session $session
$after = Api POST "/api/auth/refresh" -Session $session
"logout -> $($out.Status) | refresh afterwards -> $($after.Status) $($after.Body.code)"
