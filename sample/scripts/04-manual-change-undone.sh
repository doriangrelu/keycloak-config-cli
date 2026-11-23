#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_INITIAL=(--import.files.locations=/config/initial/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "A change made by hand in Keycloak is undone by the next import"
id="$(client_id_of frontend)"
kcadm update "clients/$id" -r "$REALM" -s "description=changed by hand" && pass "description of frontend changed by hand"
cli "${IMPORT_INITIAL[@]}" --import.dry-run=true
expect_exit 10 "dry run sees the difference"
expect_output "'frontend'" "the report lists the client frontend"
cli "${IMPORT_INITIAL[@]}"
expect_exit 0 "import applied"
if kcadm get "clients/$id" -r "$REALM" --fields description | grep -qF "The web application"; then
    pass "description of frontend restored from the file"
else
    fail "description of frontend not restored"
fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
