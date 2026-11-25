#!/bin/sh
# Writes the access rules of the administration from ADMIN_ALLOW_FROM ("all" or a comma-separated list of
# addresses / CIDR ranges), e.g. ADMIN_ALLOW_FROM=203.0.113.7,10.8.0.0/24
set -eu
target=/etc/nginx/snippets/admin-access.conf
: > "$target"
if [ "${ADMIN_ALLOW_FROM:-all}" = "all" ]; then
  echo "allow all;" >> "$target"
else
  echo "$ADMIN_ALLOW_FROM" | tr ',' '\n' | while read -r source; do
    [ -n "$source" ] && echo "allow $source;" >> "$target"
  done
  echo "deny all;" >> "$target"
fi
