#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"


scenario "An undefined template variable fails the import before anything is changed"
cli --import.files.locations=/config/undefined-variable/* --import.mustache.enabled=true
expect_exit 2 "invalid configuration"
expect_output "clientSecret" "the message names the variable"
expect_not_in "$(client_ids)" backend "client backend not created"
if kcadm get "realms/$REALM" --fields displayName | grep -qF "Must not be applied"; then
    fail "the realm was changed"
else
    pass "the realm was not changed"
fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
