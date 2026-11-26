#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

scenario "The shell refuses a client that a rule forbids"
shell_cli --shell.script=/shell/refused.kcc
expect_exit 2 "the script stopped"
expect_output "client.redirect-uris.no-wildcard" "the refusing rule is named"
if [ -e "$SAMPLE_DIR/shell/work/refused/20-clients.yaml" ] || [ -e "$SAMPLE_DIR/shell/work/refused/20-clients.json" ]; then
    fail "a clients file was written despite the refusal"
else
    pass "no clients file written"
fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
