#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
# Smoke test of the running stack (docker compose up) with the demo shop:
#   availability → book → double booking (409) → view/cancel via token →
#   admin login (wrong + right) → admin booking list → emails in Mailpit.
#
#   ./infra/smoke-test.sh [YYYY-MM-DD]     (default: next Tuesday ≥ 2 days ahead)
#
# Reads ports and admin credentials from infra/.env. Prints every curl command.
# ─────────────────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "$0")"
# .env is compose syntax, not shell syntax → read single keys instead of sourcing it
env_get() { grep -E "^$1=" ./.env | tail -n1 | cut -d= -f2- | tr -d '\r'; }
BACKEND_HOST_PORT=$(env_get BACKEND_HOST_PORT)
MAILPIT_UI_PORT=$(env_get MAILPIT_UI_PORT)
APP_ADMIN_EMAIL=$(env_get APP_ADMIN_EMAIL)
APP_ADMIN_PASSWORD=$(env_get APP_ADMIN_PASSWORD)

API="http://localhost:${BACKEND_HOST_PORT:-8080}"
MAILPIT="http://localhost:${MAILPIT_UI_PORT:-8025}"
TENANT=the-barber-news
HAIRCUT=7b3f1c2e-0000-4000-8000-000000000101
LUCA=7b3f1c2e-0000-4000-8000-000000000201

if [[ $# -ge 1 ]]; then DATE=$1; else
  d=$(date -d "+2 days" +%F)
  while [[ $(date -d "$d" +%u) != 2 ]]; do d=$(date -d "$d +1 day" +%F); done
  DATE=$d
fi

pretty() { if command -v jq >/dev/null; then jq .; elif command -v node >/dev/null; then
  node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{console.log(JSON.stringify(JSON.parse(d),null,2))}catch{console.log(d)}})'
  else cat; fi; }
step() { printf '\n\033[1;33m▶ %s\033[0m\n' "$1"; }
show() { printf '\033[2m$ %s\033[0m\n' "$*"; }
# run curl, print status + pretty body; body also saved to $BODY
req() {
  show "curl $*"
  local out; out=$(curl -s -w '\n%{http_code}' "$@")
  STATUS=${out##*$'\n'}; BODY=${out%$'\n'*}
  echo "HTTP $STATUS"; [[ -n $BODY ]] && echo "$BODY" | pretty || true
}
field() { node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const v=process.argv[1].split(".").reduce((o,k)=>o?.[k],JSON.parse(d));console.log(v??"")})' "$1"; }

step "Services (English via Accept-Language)"
req -H "Accept-Language: en" "$API/api/public/$TENANT/services"

step "Availability: Haircut, any barber, $DATE (first 3 of the slots)"
show "curl \"$API/api/public/$TENANT/availability?service=$HAIRCUT&date=$DATE&lang=de\""
AVAIL=$(curl -s "$API/api/public/$TENANT/availability?service=$HAIRCUT&date=$DATE&lang=de")
echo "$AVAIL" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const j=JSON.parse(d);j.slots=j.slots.slice(0,3).concat([`… ${j.slots.length} slots in total`]);console.log(JSON.stringify(j,null,2))})'

# start of Luca's 10:00 slot, with the Berlin offset exactly as the API returned it
START=$(echo "$AVAIL" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const s=JSON.parse(d).slots.find(s=>s.time==="10:00"&&s.staffIds.includes(process.argv[1]));if(!s){console.error("no 10:00 slot for Luca on that date");process.exit(1)}console.log(s.start)})' "$LUCA")
BOOKING="{\"serviceId\":\"$HAIRCUT\",\"staffId\":\"$LUCA\",\"start\":\"$START\",\"customerName\":\"Max Mustermann\",\"customerPhone\":\"+49 151 2345678\",\"customerEmail\":\"max@example.com\",\"lang\":\"en\"}"

step "Create booking: Luca, $START (customer language: English)"
req -X POST -H "Content-Type: application/json" -H "Accept-Language: de" \
  -d "$BOOKING" "$API/api/public/$TENANT/bookings"
TOKEN=$(echo "$BODY" | field cancelToken)
BOOKING_ID=$(echo "$BODY" | field id)

step "Same slot again → 409 (localized problem detail, German)"
req -X POST -H "Content-Type: application/json" -H "Accept-Language: de" \
  -d "$BOOKING" "$API/api/public/$TENANT/bookings"

step "View booking via cancel token"
req "$API/api/public/bookings/$TOKEN?lang=en"

step "Cancel via token"
req -X DELETE "$API/api/public/bookings/$TOKEN?lang=de"

step "Admin login with a wrong password → 401 (English)"
req -X POST -H "Content-Type: application/json" -H "Accept-Language: en" \
  -d "{\"email\":\"$APP_ADMIN_EMAIL\",\"password\":\"wrong-password\"}" "$API/api/admin/auth/login"

step "Admin login (credentials from infra/.env)"
show "curl -X POST -H 'Content-Type: application/json' -d '{\"email\":\"$APP_ADMIN_EMAIL\",\"password\":\"****\"}' $API/api/admin/auth/login"
LOGIN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$APP_ADMIN_EMAIL\",\"password\":\"$APP_ADMIN_PASSWORD\"}" "$API/api/admin/auth/login")
ADMIN_TOKEN=$(echo "$LOGIN" | field accessToken)
echo "$LOGIN" | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const j=JSON.parse(d);j.accessToken=j.accessToken.slice(0,24)+"…";console.log(JSON.stringify(j,null,2))})'

step "Admin: bookings on $DATE (needs the JWT)"
show "curl -H \"Authorization: Bearer \$ADMIN_TOKEN\" \"$API/api/admin/bookings?from=$DATE&to=$DATE\""
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$API/api/admin/bookings?from=$DATE&to=$DATE" | pretty

step "Admin without token → 401"
req "$API/api/admin/bookings?lang=en"

step "Emails caught by Mailpit ($MAILPIT)"
show "curl $MAILPIT/api/v1/messages"
curl -s "$MAILPIT/api/v1/messages" \
  | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{for(const m of JSON.parse(d).messages.slice(0,4))console.log(`- to ${m.To[0].Address}: ${m.Subject}`)})'
echo
