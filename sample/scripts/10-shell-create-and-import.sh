#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

scenario "Create a realm with the shell, then import it"
shell_cli --shell.script=/shell/create-realm.kcc
expect_exit 0 "the shell script ran"
if [ -f "$SAMPLE_DIR/shell/work/shelled/20-clients.yaml" ]; then
    pass "the files of the realm were written"
else
    fail "shelled/20-clients.yaml was not written"
fi

SAVED_REALM=$REALM
REALM=shelled
cli "--import.files.locations=/shell/work/shelled/*" --import.mustache.enabled=true \
    "--import.mustache.variables.env=dev" "--import.mustache.variables.appUrl=$APP_URL" \
    "--import.mustache.variables.shopSecret=sample-shop-secret"
expect_exit 0 "import of the written files applied"
clients="$(client_ids)"
expect_in "$clients" frontend "client frontend created"
expect_in "$clients" shop "client shop created"
if kcadm get "clients/$(client_id_of frontend)" -r "$REALM" | grep -qF '"pkce.code.challenge.method" : "S256"'; then
    pass "PKCE S256 set on frontend by the rules"
else
    fail "frontend has no PKCE S256"
fi
expect_in "$(realm_role_names)" reader "realm role reader created"
expect_in "$(client_role_names shop)" buyer "role buyer of the client shop created"
first_id() { tr -d '\r' | grep -o '"id" : "[^"]*"' | head -1 | sed 's/.*: "\(.*\)"/\1/'; }
team_id="$(kcadm get groups -r "$REALM" -q search=team --fields id | first_id)"
devs_id="$(kcadm get "groups/$team_id/children" -r "$REALM" --fields id | first_id)"
if [ -n "$devs_id" ] && kcadm get "groups/$devs_id" -r "$REALM" | tr -d '\r' | grep -qF '"path" : "/team/devs"'; then
    pass "group /team/devs created under /team"
else
    fail "group /team/devs not found"
fi
if [ -n "$devs_id" ] && kcadm get "groups/$devs_id" -r "$REALM" | tr -d '\r ' | tr -d '\n' | grep -qF '"clientRoles":{"shop":["buyer"]}'; then
    pass "group /team/devs gives shop/buyer"
else
    fail "group /team/devs does not give shop/buyer"
fi
if kcadm get "clients/$(client_id_of shop)/client-secret" -r "$REALM" | tr -d '' | grep -qF '"value" : "sample-shop-secret"'; then
    pass "secret of shop set from the variable"
else
    fail "secret of shop is not the one of the variable"
fi
REALM=$SAVED_REALM

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
