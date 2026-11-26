#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

scenario "Reorganize a monolithic realm export with the shell, then import it"
WORK="$SAMPLE_DIR/shell/work/reorganized"
mkdir -p "$WORK"
cp "$SAMPLE_DIR/shell/monolithic/realm-export.yaml" "$WORK/"

SAVED_REALM=$REALM
REALM=reorganized

shell_cli --shell.realm=reorganized --shell.script=/shell/reorganize.kcc
expect_exit 0 "realm reorganize --apply"
for file in 00-realm 20-clients 30-roles 40-groups 50-links; do
    if [ -f "$WORK/$file.yaml" ]; then pass "$file.yaml written"; else fail "$file.yaml not written"; fi
done
if [ ! -f "$WORK/realm-export.yaml" ]; then pass "the monolithic file is gone"; else fail "realm-export.yaml is still there"; fi
if grep -q '^clients:' "$WORK/20-clients.yaml" 2>/dev/null && grep -q 'frontend' "$WORK/20-clients.yaml"; then
    pass "the clients are in 20-clients.yaml"
else
    fail "20-clients.yaml does not hold the clients"
fi
if grep -q '^defaultGroups:' "$WORK/50-links.yaml" 2>/dev/null && grep -q '^users:' "$WORK/50-links.yaml"; then
    pass "the users and the default groups are in 50-links.yaml"
else
    fail "50-links.yaml does not hold the users and the default groups"
fi

cli "--import.files.locations=/shell/work/reorganized/*"
expect_exit 0 "import of the reorganized files applied"
clients="$(client_ids)"
expect_in "$clients" frontend "client frontend created"
expect_in "$clients" orphan-app "client orphan-app created"
roles="$(realm_role_names)"
expect_in "$roles" reader "realm role reader created"
expect_in "$roles" writer "realm role writer created"
expect_in "$(usernames)" alice "user alice created"
if kcadm get groups -r "$REALM" -q search=team --fields name | tr -d '\r' | grep -qF '"name" : "team"'; then
    pass "group team created"
else
    fail "group team not found"
fi
if kcadm get "realms/$REALM" --fields defaultGroups | tr -d '\r' | grep -qF '/team'; then
    pass "team is a default group"
else
    fail "team is not a default group"
fi

cli "--import.files.locations=/shell/work/reorganized/*" --import.dry-run=true
expect_exit 0 "dry run after the import"
expect_output "Dry run: no change pending." "the dry run announces no change"
REALM=$SAVED_REALM

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
