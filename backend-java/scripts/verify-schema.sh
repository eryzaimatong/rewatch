#!/usr/bin/env bash
# Proves the JPA entities and strict `validate` agree, against a real Postgres.
#
#   1. boots the app with ddl-auto=update on the (empty) database -> builds the schema
#   2. boots it again with ddl-auto=validate                       -> must start cleanly
#
# Production runs `validate`, so a mapping that `update` tolerated but `validate`
# rejects would take the site down at boot. This catches that in CI instead.
#
# Needs: a reachable Postgres and the DB_* env vars (defaults: localhost:5432,
# database "rewatch_schema_check", user "postgres").
set -euo pipefail
cd "$(dirname "$0")/.."

export DB_HOST="${DB_HOST:-127.0.0.1}" DB_PORT="${DB_PORT:-5432}" DB_NAME="${DB_NAME:-rewatch_schema_check}"
export DB_USERNAME="${DB_USERNAME:-postgres}" DB_PASSWORD="${DB_PASSWORD:-}" SERVER_PORT="${SERVER_PORT:-8099}"
LOG_DIR="$(mktemp -d)"

./mvnw -q -B -DskipTests package
JAR="$(ls target/*.jar | grep -v original | head -n1)"

boot() { # $1 = ddl mode
  local mode="$1" log="$LOG_DIR/boot-$1.log"
  SPRING_JPA_HIBERNATE_DDL_AUTO="$mode" java -jar "$JAR" >"$log" 2>&1 &
  local pid=$!
  for _ in $(seq 1 60); do
    sleep 2
    if grep -q "Started .*Application" "$log"; then kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; echo "ok: boot with ddl-auto=$mode"; return 0; fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
  done
  kill "$pid" 2>/dev/null || true
  echo "FAILED: boot with ddl-auto=$mode did not start. Relevant log lines:" >&2
  grep -m5 -i "SchemaManagementException\|Schema-validation\|missing\|wrong column type" "$log" >&2 || tail -n 20 "$log" >&2
  return 1
}

boot update
boot validate
echo "Schema check passed: entities and strict validation agree."
