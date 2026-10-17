#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
# Smoke test of the running stack (docker compose up) with the demo shop:
#   availability → request → barber accepts via email link → DEMO payment
#   via email link → confirmed → idempotent repeat → link replay (410) →
#   cancel via manage link (refund) → emails in Mailpit.
#
#   ./infra/smoke-test.sh [YYYY-MM-DD]     (default: next Tuesday ≥ 2 days ahead)
#
# Needs curl and node (for JSON). Windows without bash: infra/demo-flow.ps1.
# ─────────────────────────────────────────────────────────────────────────
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash on Windows: don't turn "/pay/" arguments into C:/… paths
cd "$(dirname "$0")"
env_get() { grep -E "^$1=" ./.env | tail -n1 | cut -d= -f2- | tr -d '\r'; }
API="http://localhost:$(env_get BACKEND_HOST_PORT || echo 8080)"
MAILPIT="http://localhost:$(env_get MAILPIT_UI_PORT || echo 8025)"
ADMIN_EMAIL=$(env_get APP_ADMIN_EMAIL)
TENANT=the-barber-news
HAIRCUT=7b3f1c2e-0000-4000-8000-000000000101
BEARD=7b3f1c2e-0000-4000-8000-000000000102
LUCA=7b3f1c2e-0000-4000-8000-000000000201
CUSTOMER="max-$(date +%s)@example.com"

if [[ $# -ge 1 ]]; then DATE=$1; else
  d=$(date -d "+2 days" +%F)
  while [[ $(date -d "$d" +%u) != 2 ]]; do d=$(date -d "$d +1 day" +%F); done
  DATE=$d
fi

step() { printf '\n\033[1;33m▶ %s\033[0m\n' "$1"; }
json() { node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const j=JSON.parse(d);const v=new Function("j","return "+process.argv[1])(j);console.log(typeof v==="object"?JSON.stringify(v,null,2):v)})' "$1"; }
# req METHOD PATH [JSON] → prints "HTTP nnn", body in $BODY
req() {
  local out; out=$(curl -s -w '\n%{http_code}' -X "$1" -H 'Content-Type: application/json' -H 'Accept-Language: en' \
    ${3:+-d "$3"} "$API$2")
  STATUS=${out##*$'\n'}; BODY=${out%$'\n'*}; echo "HTTP $STATUS"
}
# token_from_mail TO SUBJECT_PART PATH_PART → the 43-char token from the newest matching mail
token_from_mail() {
  local id=""
  for _ in $(seq 1 30); do
    id=$(curl -s "$MAILPIT/api/v1/messages?limit=100" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const m=JSON.parse(d).messages.find(m=>m.To.some(t=>t.Address===process.argv[1])&&m.Subject.includes(process.argv[2]));console.log(m?m.ID:"")})' "$1" "$2")
    [[ -n $id ]] && break; sleep 0.5
  done
  [[ -z $id ]] && { echo "no mail to $1 with '$2'" >&2; exit 1; }
  curl -s "$MAILPIT/api/v1/message/$id" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const m=JSON.parse(d).Text.match(new RegExp(process.argv[1].replace(/[/]/g,"\\/")+"([A-Za-z0-9_-]{43})"));console.log(m[1])})' "$3"
}

step "Availability $DATE: haircut + beard, one column per barber"
req GET "/api/public/$TENANT/availability?services=$HAIRCUT,$BEARD&date=$DATE"
echo "$BODY" | json 'j.barbers.map(b=>b.name+": "+b.slots.filter(s=>s.available).slice(0,6).map(s=>s.time).join(" ")+" …").join("\n")'
START=$(echo "$BODY" | json "j.barbers.find(b=>b.staffId==='$LUCA').slots.find(s=>s.available&&s.time>='10:00').start")

step "Request: Luca $START ($CUSTOMER)"
req POST "/api/public/$TENANT/bookings" "{\"serviceIds\":[\"$HAIRCUT\",\"$BEARD\"],\"staffId\":\"$LUCA\",\"start\":\"$START\",\"customerName\":\"Max Mustermann\",\"customerEmail\":\"$CUSTOMER\",\"customerPhone\":\"+49 151 2345678\",\"lang\":\"en\",\"consent\":true}"
echo "$BODY" | json '({id:j.id,status:j.status,total:j.price.total.formatted,deposit:j.price.deposit.formatted,dueAtShop:j.price.amountDueAtShop.formatted})'

step "Barber's email link: GET shows (no change), POST accepts"
DECISION=$(token_from_mail "$ADMIN_EMAIL" "Neue Terminanfrage: Max Mustermann" "/barber/requests/")
req GET "/api/public/barber-decision/$DECISION"; echo "$BODY" | json 'j.status'
req POST "/api/public/barber-decision/$DECISION/accept"; echo "$BODY" | json 'j.status'

step "Payment link from the 'accepted' email → DEMO payment page → simulate"
PAY=$(token_from_mail "$CUSTOMER" "accepted" "/pay/")
req GET "/api/public/payments/$PAY"; echo "$BODY" | json '({demoMode:j.demoMode,status:j.status,amount:j.amount.formatted,actions:j.actions})'
req POST "/api/public/payments/$PAY/simulate"; echo "$BODY" | json 'j'
req POST "/api/public/payments/$PAY/simulate"; echo "$BODY" | json '"alreadyProcessed: "+j.alreadyProcessed'

step "Replay of the used barber link"
req POST "/api/public/barber-decision/$DECISION/accept"; echo "$BODY" | json 'j.code+" – "+j.detail'

step "Cancel via the manage link (≥ 24 h ahead → deposit refunded, simulated)"
MANAGE=$(token_from_mail "$CUSTOMER" "Appointment confirmed" "/booking/manage/")
req POST "/api/public/manage/$MANAGE/cancel" '{"reason":"smoke test"}'
echo "$BODY" | json '({status:j.status,payment:j.payment.status})'

step "Emails of this run (Mailpit $MAILPIT)"
sleep 1
curl -s "$MAILPIT/api/v1/messages?limit=100" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{JSON.parse(d).messages.filter(m=>m.To.some(t=>t.Address===process.argv[1])||m.Subject.includes("Max Mustermann")).slice(0,10).reverse().forEach(m=>console.log("  "+m.To[0].Address.padEnd(30)+m.Subject))})' "$CUSTOMER"
