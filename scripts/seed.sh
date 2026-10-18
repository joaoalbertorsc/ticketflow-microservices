#!/usr/bin/env bash
# Fills the system with sample data through the real APIs: venues and published events in the
# catalog, and the seat count of every sector in the inventory.
# Goes through the api-gateway (8080), like any client. Requires the stack running
# (`docker compose up -d`).
#
#   ./scripts/seed.sh
#
# Override with GATEWAY_URL (or AUTH_URL, CATALOG_URL, INVENTORY_URL), ADMIN_EMAIL, ADMIN_PASSWORD.
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
AUTH_URL="${AUTH_URL:-$GATEWAY_URL}"
CATALOG_URL="${CATALOG_URL:-$GATEWAY_URL}"
INVENTORY_URL="${INVENTORY_URL:-$GATEWAY_URL}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ticketflow.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin12345}"

# Reads one string field from a JSON response (avoids depending on jq).
json_field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

TOKEN=$(curl -sf -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  "$AUTH_URL/api/auth/login" | json_field accessToken)

post() { # path body -> response
  curl -sf -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "$2" "$CATALOG_URL$1"
}

venue() { # name city address -> id
  post /api/venues "{\"name\":\"$1\",\"city\":\"$2\",\"address\":\"$3\"}" | json_field id
}

event() { # json -> id (created and published)
  local id
  id=$(post /api/events "$1" | json_field id)
  post "/api/events/$id/publish" '' > /dev/null
  echo "$id"
}

seats() { # eventId sectorId totalSeats
  curl -sf -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"totalSeats\":$3}" "$INVENTORY_URL/api/inventory/events/$1/sectors/$2" > /dev/null
}

# Dates relative to today, so the events are always in the future.
in_days() { date -u -d "+$1 days" +%Y-%m-%dT20:00:00Z; }

MARACANA=$(venue "Maracana" "Rio de Janeiro" "Av. Pres. Castelo Branco, Portao 3")
ALLIANZ=$(venue "Allianz Parque" "Sao Paulo" "Av. Francisco Matarazzo, 1705")
MUNICIPAL=$(venue "Theatro Municipal" "Sao Paulo" "Praca Ramos de Azevedo, s/n")

CONCERT=$(event "{
  \"name\": \"Summer Rock Festival\", \"startsAt\": \"$(in_days 30)\", \"venueId\": \"$ALLIANZ\",
  \"description\": \"Three bands, one night.\",
  \"sectors\": [
    {\"id\": \"pista\", \"name\": \"Pista\", \"price\": 350.00},
    {\"id\": \"pista-premium\", \"name\": \"Pista Premium\", \"price\": 690.00},
    {\"id\": \"cadeira\", \"name\": \"Cadeira Superior\", \"price\": 250.00}
  ],
  \"details\": {\"type\": \"CONCERT\", \"lineup\": [\"The Riffs\", \"Neon Coast\", \"Paper Kites\"], \"genre\": \"Rock\"}
}")

MATCH=$(event "{
  \"name\": \"Flamengo x Palmeiras\", \"startsAt\": \"$(in_days 14)\", \"venueId\": \"$MARACANA\",
  \"sectors\": [
    {\"id\": \"norte\", \"name\": \"Arquibancada Norte\", \"price\": 80.00},
    {\"id\": \"oeste\", \"name\": \"Setor Oeste\", \"price\": 160.00}
  ],
  \"details\": {\"type\": \"SPORTS\", \"homeTeam\": \"Flamengo\", \"awayTeam\": \"Palmeiras\", \"league\": \"Brasileirao\"}
}")

PLAY=$(event "{
  \"name\": \"Hamlet\", \"startsAt\": \"$(in_days 45)\", \"venueId\": \"$MUNICIPAL\",
  \"sectors\": [
    {\"id\": \"plateia\", \"name\": \"Plateia\", \"price\": 120.00},
    {\"id\": \"balcao\", \"name\": \"Balcao\", \"price\": 70.00}
  ],
  \"details\": {\"type\": \"THEATER\", \"director\": \"Ana Lima\", \"cast\": [\"Pedro Alves\", \"Julia Reis\"], \"durationMinutes\": 150}
}")

seats "$CONCERT" pista 5000
seats "$CONCERT" pista-premium 800
seats "$CONCERT" cadeira 2000
seats "$MATCH" norte 12000
seats "$MATCH" oeste 6000
seats "$PLAY" plateia 300
# Tiny sector on purpose: easy to sell out by hand and see "Not enough seats".
seats "$PLAY" balcao 4

echo "Venues:  $MARACANA $ALLIANZ $MUNICIPAL"
echo "Events:  concert=$CONCERT match=$MATCH play=$PLAY"
echo "Browse:  curl $CATALOG_URL/api/events"
echo "Seats:   curl $INVENTORY_URL/api/inventory/events/$PLAY/sectors"
