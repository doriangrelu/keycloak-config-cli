#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_INITIAL=(--import.files.locations=/config/initial/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "Import the first version of the realm"
cli "${IMPORT_INITIAL[@]}"
expect_exit 0 "import applied"
clients="$(client_ids)"
expect_in "$clients" frontend "client frontend created"
expect_in "$clients" orphan-app "client orphan-app created"
roles="$(realm_role_names)"
expect_in "$roles" reader "realm role reader created"
expect_in "$roles" writer "realm role writer created"
expect_in "$(usernames)" alice "user alice created"
if kcadm get "realms/$REALM" --fields displayName | grep -qF "Sample realm"; then
    pass "default value of the template variable used"
else
    fail "display name of the realm is not the default value"
fi
if kcadm get "clients/$(client_id_of frontend)" -r "$REALM" --fields redirectUris | grep -qF "$APP_URL/*"; then
    pass "template variable given on the command line rendered"
else
    fail "redirect URI of frontend does not hold $APP_URL"
fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
