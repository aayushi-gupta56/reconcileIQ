# Creates idempotent PayPal sandbox discrepancy scenarios.
# Requires PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET.
# Orders v2 capture needs buyer approval: the app prints an approval URL and waits for Enter.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $root "backend")

if (-not $env:PAYPAL_CLIENT_ID -or -not $env:PAYPAL_CLIENT_SECRET) {
    Write-Error "Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET before running this script."
}

mvn spring-boot:run "-Dspring-boot.run.profiles=sandbox-scenarios"
