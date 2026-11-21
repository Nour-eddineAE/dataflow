#!/bin/sh
set -eu
auth="admin:$GRAFANA_ADMIN_PASSWORD"
for attempt in $(seq 1 60); do
  curl -fsS -u "$auth" "$GRAFANA_URL/api/health" >/dev/null 2>&1 && break
  sleep 2
done
id=$(curl -fsS -u "$auth" "$GRAFANA_URL/api/serviceaccounts/search?query=workspace" | sed -n 's/.*"serviceAccounts":\[{"id":\([0-9]*\).*/\1/p')
if [ -z "$id" ]; then
  id=$(curl -fsS -u "$auth" -H 'Content-Type: application/json' -X POST "$GRAFANA_URL/api/serviceaccounts" \
    -d '{"name":"workspace","role":"Viewer","isDisabled":false}' | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
fi
for token in $(curl -fsS -u "$auth" "$GRAFANA_URL/api/serviceaccounts/$id/tokens" | grep -o '"id":[0-9]*' | cut -d: -f2); do
  curl -fsS -u "$auth" -X DELETE "$GRAFANA_URL/api/serviceaccounts/$id/tokens/$token" >/dev/null
done
key=$(curl -fsS -u "$auth" -H 'Content-Type: application/json' -X POST "$GRAFANA_URL/api/serviceaccounts/$id/tokens" \
  -d "{\"name\":\"workspace-$(date +%s)\"}" | sed -n 's/.*"key":"\([^"]*\)".*/\1/p')
umask 077
printf '%s' "$key" > /tokens/grafana.token
chown 65532:65532 /tokens/grafana.token
echo "Grafana token for the Workspace written (service account $id, Viewer)"
