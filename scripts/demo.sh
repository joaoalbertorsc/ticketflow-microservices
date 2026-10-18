#!/usr/bin/env bash
# End-to-end demo of TicketFlow: the booking saga across every service, its compensations, and the
# circuit breaker opening and closing. Needs the stack running (`docker compose up -d --build`)
# plus bash, curl, python and docker on the PATH.
#
#   ./scripts/demo.sh
#
# Steps:
#   1. a confirmed booking                    5. payment chaos → circuit OPEN → calls fail fast
#   2. sold out           (409, nothing held) 6. chaos off → HALF_OPEN → CLOSED
#   3. card declined      (409, seats back)   7. cancel the first booking (refund + release)
#   4. notification-service down (201, notification PENDING, sent later by the recovery job)
set -euo pipefail
export PYTHONIOENCODING=utf-8
cd "$(dirname "$0")/.."

AUTH=http://localhost:8081
PAYMENT=http://localhost:8084
INVENTORY=http://localhost:8083
BOOKING=http://localhost:8086
JSON='Content-Type: application/json'

title() { printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }
field() { python -c "import sys,json; d=json.load(sys.stdin); print(eval('d' + sys.argv[1]))" "$1"; }
login() { curl -sf -H "$JSON" -d "{\"email\":\"$1\",\"password\":\"$2\"}" "$AUTH/api/auth/login" | field "['accessToken']"; }

# book <sectorId> <quantity> <cardToken> -> prints HTTP status and a one-line summary; sets $LAST_ID
book() {
  local out code
  out=$(curl -s -w '\n%{http_code}' -H "$JSON" -H "Authorization: Bearer $TOKEN" \
    -d "{\"eventId\":\"$EVENT\",\"sectorId\":\"$1\",\"quantity\":$2,\"cardToken\":\"$3\"}" "$BOOKING/api/bookings")
  code=$(tail -1 <<<"$out"); out=$(sed '$d' <<<"$out")
  LAST_ID=$(field "['id']" <<<"$out")
  echo "HTTP $code  $(python -c "
import sys,json; b=json.load(sys.stdin)
print(b['status'], b.get('failureCode') or '', '| notification', b['notificationStatus'])
print('   steps:', ' > '.join(s['step']+('' if s['outcome']=='SUCCEEDED' else '('+s['outcome']+')') for s in b['steps']))" <<<"$out")"
}
circuit() { curl -s "$BOOKING/actuator/circuitbreakers" | python -c "import sys,json; print('   paymentService circuit:', json.load(sys.stdin)['circuitBreakers']['paymentService']['state'])"; }
seats() { curl -s "$INVENTORY/api/inventory/events/$EVENT/sectors/$1" | field "['availableSeats']"; }

title "Setup: sample data and a demo user"
EVENT=$(./scripts/seed.sh | grep -o 'play=[^ ]*' | cut -d= -f2)
EMAIL="demo-$(date +%s)@mail.com"
curl -sf -o /dev/null -H "$JSON" -d "{\"name\":\"Demo User\",\"email\":\"$EMAIL\",\"password\":\"Str0ngPass\"}" "$AUTH/api/auth/register"
TOKEN=$(login "$EMAIL" Str0ngPass)
ADMIN=$(login "${ADMIN_EMAIL:-admin@ticketflow.local}" "${ADMIN_PASSWORD:-Admin12345}")
echo "Event $EVENT (Hamlet): balcao has $(seats balcao) seats, plateia $(seats plateia)"

title "1. Book 2 seats in balcao — the saga goes through every service"
book balcao 2 tok_visa; FIRST=$LAST_ID
echo "   balcao seats left: $(seats balcao)"

title "2. Book 3 more in balcao — only 2 left: sold out"
book balcao 3 tok_visa
echo "   balcao seats left: $(seats balcao) (nothing held)"

title "3. Book plateia with a card that gets declined — seats are given back"
book plateia 2 tok_declined
echo "   plateia seats left: $(seats plateia)"

title "4. Stop notification-service, then book — still CONFIRMED, notification PENDING"
docker compose stop notification-service >/dev/null 2>&1
book plateia 1 tok_visa; PENDING=$LAST_ID
docker compose start notification-service >/dev/null 2>&1
until [ "$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q notification-service)")" = healthy ]; do sleep 2; done
echo "   notification-service is back; the recovery job (every 30 s) will send the e-mail"

title "5. Payment chaos 100% — failures pile up until the circuit OPENS"
curl -sf -o /dev/null -X PUT -H "$JSON" -H "Authorization: Bearer $ADMIN" -d '{"failureRate":1.0,"latencyMs":0}' "$PAYMENT/api/payments/admin/chaos"
for i in 1 2 3 4 5; do book plateia 1 tok_visa | head -1; done
circuit
echo "   next booking: the payment call is not even attempted (fails fast, seats released):"
book plateia 1 tok_visa

title "6. Chaos off — after 10 s the circuit goes HALF_OPEN, trial calls succeed, it CLOSES"
curl -sf -o /dev/null -X DELETE -H "Authorization: Bearer $ADMIN" "$PAYMENT/api/payments/admin/chaos"
sleep 11; circuit
for i in 1 2 3; do book plateia 1 tok_visa | head -1; done
circuit

title "7. Cancel the first booking — refund + release, CANCELLED"
curl -s -X POST -H "Authorization: Bearer $TOKEN" "$BOOKING/api/bookings/$FIRST/cancel" | field "['status']"
echo "   balcao seats left: $(seats balcao)"

title "And the pending notification from step 4?"
for i in $(seq 1 12); do
  s=$(curl -s -H "Authorization: Bearer $TOKEN" "$BOOKING/api/bookings/$PENDING" | field "['notificationStatus']")
  [ "$s" = SENT ] && break; sleep 5
done
echo "   notification of booking $PENDING: $s"
