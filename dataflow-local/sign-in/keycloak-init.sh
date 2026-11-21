#!/bin/bash
set -euo pipefail

readonly kcadm=/opt/keycloak/bin/kcadm.sh
readonly realm=dataflow
readonly placeholders=(WORKSPACE_URL TOWER_URL MAILPIT_URL SPARK_HISTORY_URL GRAFANA_URL RUSTFS_URL RUSTFS_CONSOLE_URL)

sign_in() {
  for _ in $(seq 60); do
    "$kcadm" config credentials --server "$KEYCLOAK_URL" --realm master --user admin \
      --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1 && return
    sleep 2
  done
  echo "Cannot sign in to $KEYCLOAK_URL as admin" >&2
  exit 1
}

render() {
  local text
  text=$(<"$1")
  for name in "${placeholders[@]}"; do
    text=${text//\$\{$name\}/${!name}}
  done
  printf '%s' "$text"
}

client_id() {
  "$kcadm" get clients -r "$realm" -q "clientId=$1" --fields id --format csv --noquotes
}

apply_client() {
  local name id
  name=$(basename "$1" .json)
  id=$(client_id "$name")
  if [[ -z $id ]]; then
    render "$1" | "$kcadm" create clients -r "$realm" -f - >/dev/null
    echo "Client $name created"
  else
    render "$1" | "$kcadm" update "clients/$id" -r "$realm" -f -
    echo "Client $name updated"
  fi
}

set_secret() {
  "$kcadm" update "clients/$(client_id "$1")" -r "$realm" -s "secret=$2"
}

grant_object_store_roles() {
  local console pair group role
  console=$(client_id rustfs-console)
  for pair in admins:consoleAdmin; do
    group=${pair%%:*}
    role=${pair##*:}
    "$kcadm" get "clients/$console/roles/$role" -r "$realm" >/dev/null 2>&1 ||
      "$kcadm" create "clients/$console/roles" -r "$realm" -s "name=$role" >/dev/null
    "$kcadm" add-roles -r "$realm" --gname "$group" --cclientid rustfs-console --rolename "$role" >/dev/null
  done
  for pair in engineers:readwrite analysts:readonly; do
    group=${pair%%:*}
    role=${pair##*:}
    if "$kcadm" get "clients/$console/roles/$role" -r "$realm" >/dev/null 2>&1; then
      "$kcadm" remove-roles -r "$realm" --gname "$group" --cclientid rustfs-console --rolename "$role" >/dev/null
    fi
  done
}

configure_realm() {
  "$kcadm" update "realms/$realm" \
    -s loginTheme=dataflow -s rememberMe=true -s resetPasswordAllowed=true -s verifyEmail=true \
    -s "ssoSessionIdleTimeout=$SESSION_IDLE_SECONDS" -s "ssoSessionMaxLifespan=$SESSION_MAX_SECONDS" \
    -s "ssoSessionIdleTimeoutRememberMe=$REMEMBER_ME_IDLE_SECONDS" \
    -s "ssoSessionMaxLifespanRememberMe=$REMEMBER_ME_MAX_SECONDS" \
    -s smtpServer.host=mailpit -s smtpServer.port=1025 -s smtpServer.from=sign-in@dataflow.localhost \
    -s smtpServer.fromDisplayName=DataFlow \
    -s "browserSecurityHeaders.contentSecurityPolicy=frame-src 'self'; frame-ancestors 'self' $WORKSPACE_URL; object-src 'none';" \
    -s "browserSecurityHeaders.xFrameOptions="
}

readonly flow=email-first
readonly forms=email-first-forms

execution() {
  local parent=$1 provider=$2 requirement=$3 id
  id=$("$kcadm" create "authentication/flows/$parent/executions/execution" -r "$realm" -s "provider=$provider" -i)
  "$kcadm" update "authentication/flows/$parent/executions" -r "$realm" -b "{\"id\":\"$id\",\"requirement\":\"$requirement\"}"
}

field_of() {
  local path=$1 match=$2 wanted=$3 key value
  while IFS=, read -r key value; do
    [[ $value == "$match" ]] && { echo "$key"; return; }
  done < <("$kcadm" get "$path" -r "$realm" --fields "$wanted" --format csv --noquotes)
}

configure_email_first() {
  local existing id
  existing=$(field_of authentication/flows "$flow" id,alias)
  if [[ -n $existing && -n $(field_of "authentication/flows/$flow/executions" auth-password-form id,providerId) ]]; then
    "$kcadm" update "realms/$realm" -s "browserFlow=$flow"
    return
  fi
  [[ -n $existing ]] && "$kcadm" delete "authentication/flows/$existing" -r "$realm"
  "$kcadm" create authentication/flows -r "$realm" -s "alias=$flow" -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s "description=Email first, then password or a provider" >/dev/null
  execution "$flow" auth-cookie ALTERNATIVE
  execution "$flow" identity-provider-redirector ALTERNATIVE
  "$kcadm" create "authentication/flows/$flow/executions/flow" -r "$realm" -s "alias=$forms" -s type=basic-flow \
    -s provider=registration-page-form >/dev/null
  id=$(field_of "authentication/flows/$flow/executions" "$forms" id,displayName)
  "$kcadm" update "authentication/flows/$flow/executions" -r "$realm" -b "{\"id\":\"$id\",\"requirement\":\"ALTERNATIVE\"}"
  execution "$forms" auth-username-form REQUIRED
  execution "$forms" auth-password-form REQUIRED
  "$kcadm" update "realms/$realm" -s "browserFlow=$flow"
  echo "Flow $flow ready"
}

configure_google() {
  if [[ -z $GOOGLE_CLIENT_ID || -z $GOOGLE_CLIENT_SECRET ]]; then
    echo "Google sign-in off: set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env"
    return
  fi
  local settings=(-s alias=google -s providerId=google -s displayName=Google -s enabled=true -s trustEmail=true
    -s "config.clientId=$GOOGLE_CLIENT_ID" -s "config.clientSecret=$GOOGLE_CLIENT_SECRET"
    -s "config.defaultScope=openid email profile" -s config.syncMode=IMPORT -s "config.hostedDomain=$GOOGLE_HOSTED_DOMAIN")
  if "$kcadm" get identity-provider/instances/google -r "$realm" >/dev/null 2>&1; then
    "$kcadm" update identity-provider/instances/google -r "$realm" "${settings[@]}"
  else
    "$kcadm" create identity-provider/instances -r "$realm" "${settings[@]}" >/dev/null
  fi
  echo "Google sign-in on${GOOGLE_HOSTED_DOMAIN:+ for $GOOGLE_HOSTED_DOMAIN}"
}

ensure_first_person() {
  local user group
  user=$("$kcadm" get users -r "$realm" -q "username=$PERSON_ID" --fields id --format csv --noquotes)
  if [[ -z $user ]]; then
    "$kcadm" create users -r "$realm" -s "username=$PERSON_ID" -s "email=$PERSON_EMAIL" -s "firstName=$PERSON_NAME" \
      -s enabled=true -s emailVerified=true -s 'requiredActions=["UPDATE_PASSWORD"]'
    "$kcadm" set-password -r "$realm" --username "$PERSON_ID" --new-password "$PERSON_INITIAL_PASSWORD" --temporary
    user=$("$kcadm" get users -r "$realm" -q "username=$PERSON_ID" --fields id --format csv --noquotes)
    echo "Created $PERSON_ID; the first sign-in asks for a new password"
  fi
  "$kcadm" update "users/$user" -r "$realm" -s "email=$PERSON_EMAIL" -s emailVerified=true
  group=$("$kcadm" get groups -r "$realm" -q search=admins --fields id --format csv --noquotes | head -1)
  "$kcadm" update "users/$user/groups/$group" -r "$realm" -s "realm=$realm" -s "userId=$user" -s "groupId=$group" -n
}

sign_in
configure_realm
configure_email_first
configure_google
for file in /clients/*.json; do
  apply_client "$file"
done
set_secret rustfs-console "$RUSTFS_CONSOLE_CLIENT_SECRET"
set_secret oauth2-proxy "$SIGN_IN_PROXY_CLIENT_SECRET"
grant_object_store_roles
ensure_first_person
echo "Sign-in ready"
