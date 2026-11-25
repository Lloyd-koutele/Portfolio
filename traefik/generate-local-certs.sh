#!/bin/sh
# Creates the local HTTPS certificate for the two domains (and localhost) in traefik/certs/.
# Uses mkcert, whose local authority your browsers trust after a one-time `mkcert -install`.
# Without mkcert, falls back to a self-signed certificate (the browser will show a warning).
set -eu
cd "$(dirname "$0")"
mkdir -p certs
PUBLIC_HOST="${PUBLIC_HOST:-portfolio-public}"
ADMIN_HOST="${ADMIN_HOST:-portfolio-admin}"
if command -v mkcert > /dev/null 2>&1; then
  mkcert -cert-file certs/local.pem -key-file certs/local-key.pem \
    "$PUBLIC_HOST" "$ADMIN_HOST" localhost 127.0.0.1 ::1
else
  echo "mkcert not found: creating a self-signed certificate (browsers will warn)." >&2
  openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj "/CN=$PUBLIC_HOST" \
    -keyout certs/local-key.pem -out certs/local.pem \
    -addext "subjectAltName=DNS:$PUBLIC_HOST,DNS:$ADMIN_HOST,DNS:localhost,IP:127.0.0.1"
fi
chmod 600 certs/local-key.pem
echo "Certificate written to traefik/certs/ for $PUBLIC_HOST, $ADMIN_HOST and localhost."
