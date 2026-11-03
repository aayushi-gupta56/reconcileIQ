#!/usr/bin/env bash
# Creates idempotent PayPal sandbox discrepancy scenarios.
# Requires PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET.
# Orders v2 capture needs buyer approval: the app prints an approval URL and waits for Enter.

set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root/backend"

if [[ -z "${PAYPAL_CLIENT_ID:-}" || -z "${PAYPAL_CLIENT_SECRET:-}" ]]; then
  echo "Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET before running this script." >&2
  exit 1
fi

mvn spring-boot:run -Dspring-boot.run.profiles=sandbox-scenarios
