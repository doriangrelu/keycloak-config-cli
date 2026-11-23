#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_INITIAL=(--import.files.locations=/config/initial/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "An option removed in 7.0.0 is refused at startup"
cli "${IMPORT_INITIAL[@]}" --import.parallel=true
expect_exit 2 "import.parallel refused"
cli "${IMPORT_INITIAL[@]}" --import.managed.client=no-delete
expect_exit 2 "import.managed.client refused"

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
