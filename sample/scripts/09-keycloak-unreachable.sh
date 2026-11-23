#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_INITIAL=(--import.files.locations=/config/initial/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "Keycloak unreachable"
CLI_OUTPUT="$(compose run --rm -T -e KEYCLOAK_URL=http://keycloak:9999 -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=false cli "${IMPORT_INITIAL[@]}" 2>&1)"
CLI_EXIT=$?
expect_exit 3 "Keycloak unavailable: retry later"

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
