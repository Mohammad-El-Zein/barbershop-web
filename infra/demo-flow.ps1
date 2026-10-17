<#
  Main booking flow against the running stack (docker compose up), Windows PowerShell 5.1 or 7:

    request (guest, haircut + beard) -> barber accepts via email link -> DEMO payment via email link
    -> CONFIRMED (+ .ics) -> idempotent repeat -> link replay refused -> cancel via manage link (refund)

  Email links are taken from Mailpit, exactly as a user would click them. No bash needed.

    powershell -ExecutionPolicy Bypass -File infra\demo-flow.ps1
    powershell -ExecutionPolicy Bypass -File infra\demo-flow.ps1 -Date 2026-10-27
#>
param(
    [string]$Date = "",
    [string]$Api = "",
    [string]$Mailpit = ""
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. (Join-Path $PSScriptRoot "demo-helpers.ps1")
Initialize-Demo -Api $Api -Mailpit $Mailpit

if (-not $Date) {
    # next Tuesday at least 2 days ahead (lead time 2 h, barber deadline 12 h)
    $d = (Get-Date).Date.AddDays(2)
    while ($d.DayOfWeek -ne [DayOfWeek]::Tuesday) { $d = $d.AddDays(1) }
    $Date = $d.ToString("yyyy-MM-dd")
}
$customer = "max-" + [guid]::NewGuid().ToString().Substring(0, 8) + "@example.com"
$runStart = (Get-Date).ToUniversalTime().AddSeconds(-2)

Step "1. Shop info and booking rules"
$shop = Api GET "/api/public/$TENANT" -Lang en
Show $shop.Body.bookingRules

Step "2. Availability $Date, haircut + beard (75 min), one column per barber"
$day = Api GET "/api/public/$TENANT/availability?services=$HAIRCUT,$BEARD&date=$Date"
"durationMin = $($day.Body.durationMin), slot step = $($day.Body.slotStepMin) min"
foreach ($c in $day.Body.barbers) {
    $free = @($c.slots | Where-Object available)
    "  {0,-5} {1,2} free / {2,2} total: {3} ..." -f $c.name, $free.Count, $c.slots.Count, (($free | Select-Object -First 6 | ForEach-Object time) -join ", ")
}
"  any barber: $($day.Body.anyBarber.Count) start times"
$slot = ($day.Body.barbers | Where-Object staffId -eq $LUCA).slots | Where-Object { $_.available -and $_.time -ge "10:00" } | Select-Object -First 1
if (-not $slot) { throw "Luca has no free slot on $Date - pass another date with -Date" }

Step "3. Price preview (guest)"
$preview = Api POST "/api/public/$TENANT/bookings/price-preview" @{ serviceIds = @($HAIRCUT, $BEARD) } -Lang en
"total {0} | loyalty discount {1} | deposit {2} | due at shop {3}" -f $preview.Body.price.total.formatted,
    $preview.Body.price.loyaltyDiscount.formatted, $preview.Body.price.deposit.formatted, $preview.Body.price.amountDueAtShop.formatted

Step "4. Booking request: Luca, $($slot.start), customer $customer (emails in English)"
$req = @{ serviceIds = @($HAIRCUT, $BEARD); staffId = $LUCA; start = $slot.start; customerName = "Max Mustermann";
          customerEmail = $customer; customerPhone = "+49 151 2345678"; lang = "en"; consent = $true;
          note = "Please not too short on the sides" }
$created = Api POST "/api/public/$TENANT/bookings" $req
$bookingId = $created.Body.id
"HTTP $($created.Status)  id=$bookingId  status=$($created.Body.status)  $($created.Body.start) - $($created.Body.end)"

Step "5. Emails: customer 'request received', barber 'new request' with Accept / Decline"
$m = Wait-Mail $customer "Your appointment request"; "to customer: $($m.Subject)"
$b = Wait-Mail $ADMIN_EMAIL "Neue Terminanfrage: Max Mustermann"; "to barber : $($b.Subject)   (Luca has no own login yet -> admin)"
$decision = Get-LinkToken $b "/barber/requests/"
"decision link token: $($decision.Substring(0, 10))..."

Step "6. Opening the barber link (GET, twice - like an email scanner) changes nothing"
1..2 | ForEach-Object { $p = Api GET "/api/public/barber-decision/$decision"; "GET -> $($p.Status) status=$($p.Body.status) actions=$($p.Body.actions -join ',') loyalty='$($p.Body.loyaltyNote)'" }

Step "7. Barber clicks 'Accept booking' (POST)"
$acc = Api POST "/api/public/barber-decision/$decision/accept"
"HTTP $($acc.Status) status=$($acc.Body.status)"

Step "8. Customer email 'accepted - pay the deposit' with 'Pay now' link"
$pm = Wait-Mail $customer "accepted"; "to customer: $($pm.Subject)"
$pay = Get-LinkToken $pm "/pay/"

Step "9. Payment page data (GET, changes nothing)"
$page = Api GET "/api/public/payments/$pay" -Lang en
"demoMode=$($page.Body.demoMode) status=$($page.Body.status) amount=$($page.Body.amount.formatted) payUntil=$($page.Body.payUntil) actions=$($page.Body.actions -join ',')"

Step "10. 'Simulate payment' (DEMO) -> same code path a real provider webhook would use"
$paid = Api POST "/api/public/payments/$pay/simulate"
Show $paid.Body

Step "11. Repeat (double click / webhook retry) -> idempotent"
Show (Api POST "/api/public/payments/$pay/simulate").Body

Step "12. Replaying the used barber link -> 410"
$replay = Api POST "/api/public/barber-decision/$decision/accept" -Lang en
"HTTP $($replay.Status) code=$($replay.Body.code) detail='$($replay.Body.detail)'"

Step "13. Confirmation emails"
$cm = Wait-Mail $customer "Appointment confirmed"
"to customer: $($cm.Subject)   attachments: $(($cm.Attachments | ForEach-Object FileName) -join ', ')"
$bp = Wait-Mail $ADMIN_EMAIL "Anzahlung erhalten: Max Mustermann"; "to barber : $($bp.Subject)"
($bp.Text -split "`r?`n" | Where-Object { $_ -match "Anzahlung:|Zeit:|Im Laden" }) | ForEach-Object { "             $($_.Trim())" }

Step "14. Admin view of the booking"
$admin = Login-Admin
$av = Api GET "/api/admin/bookings/$bookingId" -Token $admin -Lang en
"status=$($av.Body.status) payment=$($av.Body.payment.status) due at shop=$($av.Body.price.amountDueAtShop.formatted) allowed next=$($av.Body.allowedTransitions -join ',')"

Step "15. Customer cancels via the manage link (> 24 h ahead -> deposit refunded, simulated)"
$manage = Get-LinkToken $cm "/booking/manage/"
$mp = Api GET "/api/public/manage/$manage" -Lang en
"manage page: status=$($mp.Body.status) canCancel=$($mp.Body.actions.canCancel) refundIfCancelledNow=$($mp.Body.actions.refundIfCancelledNow)"
$cx = Api POST "/api/public/manage/$manage/cancel" @{ reason = "Demo run" }
"HTTP $($cx.Status) status=$($cx.Body.status) payment=$($cx.Body.payment.status)"
$cc = Wait-Mail $customer "has been cancelled"; "to customer: $($cc.Subject)"
$bc = Wait-Mail $ADMIN_EMAIL "Storniert: Max Mustermann"; "to barber : $($bc.Subject)"

Step "Done. All emails of this run (Mailpit: $MAILPIT)"
Get-Mails | Where-Object { ([datetime]$_.Created).ToUniversalTime() -ge $runStart -and
                           ($_.To -eq $customer -or $_.Subject -match "Max Mustermann") } |
    Sort-Object Created | ForEach-Object { "  {0,-28} {1}" -f $_.To, $_.Subject }
