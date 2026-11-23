#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"


scenario "An invalid protection rule fails the import before anything is changed"
cli --import.files.locations=/config/invalid-protection/*
expect_exit 2 "invalid configuration"
expect_output "ext-(" "the message names the regular expression"
if kcadm get "realms/$REALM" --fields displayName | grep -qF "Must not be applied"; then
    fail "the realm was changed"
else
    pass "the realm was not changed"
fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
