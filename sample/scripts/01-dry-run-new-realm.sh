#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_INITIAL=(--import.files.locations=/config/initial/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "Dry run on a realm that does not exist yet"
cli "${IMPORT_INITIAL[@]}" --import.dry-run=true
expect_exit 10 "changes are pending"
expect_output "Realm '$REALM'" "the report lists the realm"
expect_output "'frontend'" "the report lists the client frontend"
if realm_exists; then fail "the dry run created the realm"; else pass "nothing was written to Keycloak"; fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
