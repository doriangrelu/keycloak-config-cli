#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_INITIAL=(--import.files.locations=/config/initial/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "Import the same files again"
cli "${IMPORT_INITIAL[@]}" --import.dry-run=true
expect_exit 0 "dry run: Keycloak already matches the files"
expect_output "Dry run: no change pending." "the report says so"
cli "${IMPORT_INITIAL[@]}"
expect_exit 0 "second import applied without error"

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
