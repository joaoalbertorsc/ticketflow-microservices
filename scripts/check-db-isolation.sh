#!/usr/bin/env bash
# Proves database-per-service isolation on the running databases: every service's credentials
# open its own database, and are refused by every other service's database.
# Needs the databases running (`docker compose up -d postgres mongo`).
#
#   ./scripts/check-db-isolation.sh
set -uo pipefail
cd "$(dirname "$0")/.."

PG_SERVICES=("auth:auth_user:auth_pass:auth_db"
             "inventory:inventory_user:inventory_pass:inventory_db"
             "payment:payment_user:payment_pass:payment_db"
             "booking:booking_user:booking_pass:booking_db")
MONGO_SERVICES=("catalog:catalog_user:catalog_pass:catalog_db"
                "notification:notification_user:notification_pass:notification_db")

failures=0
expect() { # description, expected (allowed|refused), actual (allowed|refused)
  if [ "$2" = "$3" ]; then printf '  ok    %-60s %s\n' "$1" "$3"
  else printf '  FAIL  %-60s %s (expected %s)\n' "$1" "$3" "$2"; failures=$((failures + 1)); fi
}

pg_connect() { # user password database -> allowed|refused
  if docker compose exec -T -e PGPASSWORD="$2" postgres \
      psql -h localhost -U "$1" -d "$3" -tAc "SELECT 1" >/dev/null 2>&1; then echo allowed; else echo refused; fi
}

mongo_read() { # user password authDb targetDb -> allowed|refused
  if docker compose exec -T mongo mongosh --quiet \
      "mongodb://$1:$2@localhost:27017/$4?authSource=$3" \
      --eval "db.getCollectionNames()" >/dev/null 2>&1; then echo allowed; else echo refused; fi
}

echo "PostgreSQL (one server, one database + one user per service)"
for own in "${PG_SERVICES[@]}"; do
  IFS=: read -r service user pass db <<<"$own"
  for target in "${PG_SERVICES[@]}"; do
    IFS=: read -r other _ _ other_db <<<"$target"
    [ "$db" = "$other_db" ] && want=allowed || want=refused
    expect "$service-service credentials -> $other_db" "$want" "$(pg_connect "$user" "$pass" "$other_db")"
  done
done

echo "MongoDB (one server, one database + one user per service)"
for own in "${MONGO_SERVICES[@]}"; do
  IFS=: read -r service user pass db <<<"$own"
  for target in "${MONGO_SERVICES[@]}"; do
    IFS=: read -r other _ _ other_db <<<"$target"
    [ "$db" = "$other_db" ] && want=allowed || want=refused
    expect "$service-service credentials -> $other_db" "$want" "$(mongo_read "$user" "$pass" "$db" "$other_db")"
  done
done

echo "Tables are owned by their service's user (created by that service's own Flyway migrations)"
for own in "${PG_SERVICES[@]}"; do
  IFS=: read -r service user _ db <<<"$own"
  owners=$(docker compose exec -T postgres psql -U postgres -d "$db" -tAc \
    "SELECT string_agg(DISTINCT tableowner, ',') FROM pg_tables WHERE schemaname = 'public'" 2>/dev/null | tr -d '[:space:]')
  [ "$owners" = "$user" ] && got=allowed || got=refused
  expect "$db tables owned only by $user (found: ${owners:-none})" allowed "$got"
done

echo
if [ "$failures" -eq 0 ]; then echo "Isolation holds: every service can only reach its own database."
else echo "$failures check(s) failed."; exit 1; fi
