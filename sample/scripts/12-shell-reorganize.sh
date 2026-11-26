#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

scenario "Reorganize a monolithic realm export with the shell, then import it"
WORK="$SAMPLE_DIR/shell/work/reorganized"
# Start from the monolithic file only, so that the scenario can run again without 00-up.sh
rm -rf "$WORK"
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

# file_has <file> <regex> <what>: a line of the file matches
file_has() {
    if grep -qE -- "$2" "$WORK/$1.yaml" 2>/dev/null; then pass "$3"; else fail "$3: '$2' not in $1.yaml"; fi
}
# key_only_in <key> <file>: the top-level key is in this file and in no other
key_only_in() {
    local files
    files="$(cd "$WORK" && grep -lE "^$1:" ./*.yaml 2>/dev/null | sed 's|^\./||; s|\.yaml$||' | tr '\n' ' ')"
    expect_equal "$files" "$2 " "the key '$1' is in $2 only"
}
file_has 00-realm '^realm: reorganized' "00-realm holds the realm key"
file_has 00-realm '^enabled: true' "00-realm holds enabled"
file_has 00-realm '^displayName: Reorganized realm' "00-realm holds displayName"
file_has 20-clients 'clientId: frontend' "20-clients holds frontend"
file_has 20-clients 'clientId: orphan-app' "20-clients holds orphan-app"
file_has 30-roles '^ +- name: reader' "30-roles holds reader"
file_has 30-roles '^ +- name: writer' "30-roles holds writer"
file_has 40-groups '^ +- name: team' "40-groups holds team"
file_has 50-links 'username: alice' "50-links holds alice"
file_has 50-links '^ +- /team' "50-links holds the default group"
key_only_in clients 20-clients
key_only_in roles 30-roles
key_only_in groups 40-groups
key_only_in users 50-links
key_only_in defaultGroups 50-links
key_only_in displayName 00-realm
key_only_in enabled 00-realm

cli "--import.files.locations=/shell/work/reorganized/*"
expect_exit 0 "import of the reorganized files applied"
clients="$(client_ids)"
expect_in "$clients" frontend "client frontend created"
expect_in "$clients" orphan-app "client orphan-app created"
roles="$(realm_role_names)"
expect_in "$roles" reader "realm role reader created"
expect_in "$roles" writer "realm role writer created"
expect_in "$(usernames)" alice "user alice created"

# first_id: the first "id" of a kcadm answer
first_id() { tr -d '\r' | grep -o '"id" : "[^"]*"' | head -1 | sed 's/.*: "\(.*\)"/\1/'; }

team_id="$(kcadm get groups -r "$REALM" -q search=team --fields id | first_id)"
if [ -n "$team_id" ]; then pass "group team created"; else fail "group team not found"; fi
if [ -n "$team_id" ] && kcadm get "groups/$team_id/role-mappings/realm" -r "$REALM" | tr -d '\r' | grep -qF '"name" : "reader"'; then
    pass "group team gives the role reader"
else
    fail "group team does not give the role reader"
fi
alice_id="$(kcadm get users -r "$REALM" -q username=alice --fields id | first_id)"
if [ -n "$alice_id" ] && kcadm get "users/$alice_id/groups" -r "$REALM" | tr -d '\r' | grep -qF '"path" : "/team"'; then
    pass "alice is a member of team"
else
    fail "alice is not a member of team"
fi
if kcadm get "clients/$(client_id_of frontend)" -r "$REALM" | tr -d '\r' | grep -qF '"https://app.example.org/*"'; then
    pass "frontend keeps its redirect URI"
else
    fail "frontend lost its redirect URI"
fi
if kcadm get "realms/$REALM" --fields displayName | tr -d '\r' | grep -qF '"displayName" : "Reorganized realm"'; then
    pass "the display name of the realm is kept"
else
    fail "the display name of the realm is lost"
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
