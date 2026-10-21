#!/usr/bin/env bash
# Smoke test do mechanical-hub-stock contra uma instancia ja no ar.
#
# Uso: ci/smoke-test.sh <base-url>     (ex.: http://localhost:8080)
#
# Confere que a aplicacao subiu com banco e migrations (readiness), que o saldo
# das seeds e servido, que POST /stock/entry exige Idempotency-Key e que as
# metricas do outbox estao expostas.
set -euo pipefail

BASE_URL="${1:?informe a URL base, ex.: http://localhost:8080}"
ADMIN_ID="7a2e9b41-3c5d-4f60-8e1a-000000000001"

fail() {
  echo "FALHOU: $*" >&2
  exit 1
}

echo "-> readiness"
readiness="$(curl -fsS --max-time 10 "$BASE_URL/actuator/health/readiness")" || fail "readiness nao respondeu"
grep -q '"status":"UP"' <<< "$readiness" || fail "readiness diferente de UP: $readiness"

echo "-> GET /stock"
stock="$(curl -fsS --max-time 10 \
  -H "x-user-id: $ADMIN_ID" -H "x-user-role: ADMIN" -H "x-user-name: Smoke Test" \
  "$BASE_URL/stock")" || fail "GET /stock nao retornou 2xx"
grep -q '"available":' <<< "$stock" || fail "saldo sem o campo available: $stock"
grep -q 'eeff1111-1111-1111-1111-000000000001' <<< "$stock" || fail "saldo sem o material das seeds (Vela de Ignicao): $stock"

echo "-> POST /stock/entry sem Idempotency-Key"
code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -X POST \
  -H "x-user-id: $ADMIN_ID" -H "x-user-role: ADMIN" -H "x-user-name: Smoke Test" \
  -H 'Content-Type: application/json' \
  -d '{"materialId":"00000000-0000-0000-0000-000000000000","quantity":1}' \
  "$BASE_URL/stock/entry")"
[ "$code" = "400" ] || fail "entrada sem Idempotency-Key deveria ser 400, veio $code"

echo "-> /actuator/prometheus"
metrics="$(curl -fsS --max-time 10 "$BASE_URL/actuator/prometheus")" || fail "prometheus nao respondeu"
grep -q '^outbox_oldest_pending_age_seconds' <<< "$metrics" || fail "metrica outbox_oldest_pending_age_seconds ausente"

echo "Smoke test OK em $BASE_URL"
