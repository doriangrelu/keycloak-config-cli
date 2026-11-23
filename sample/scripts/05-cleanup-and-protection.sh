#!/usr/bin/env bash
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IMPORT_CLEANUP=(--import.files.locations=/config/cleanup/* --import.mustache.enabled=true "--import.mustache.variables.appUrl=$APP_URL")

scenario "Clean phase: what the files no longer declare is deleted, except users, Keycloak's and protected resources"
for client in legacy-app ext-tool hand-made; do
    kcadm create clients -r "$REALM" -s "clientId=$client" >/dev/null 2>&1 && pass "client $client created by hand"
done
kcadm create roles -r "$REALM" -s name=legacy-app >/dev/null 2>&1 && pass "realm role legacy-app created by hand"

cli "${IMPORT_CLEANUP[@]}" --import.dry-run=true
expect_exit 10 "dry run plans the deletions"
expect_output "'orphan-app'" "the report lists orphan-app"

cli "${IMPORT_CLEANUP[@]}"
expect_exit 0 "import applied"
clients="$(client_ids)"
expect_in "$clients" frontend "declared client frontend kept"
expect_not_in "$clients" orphan-app "client orphan-app, no longer declared, deleted"
expect_not_in "$clients" hand-made "client hand-made, never declared, deleted"
expect_in "$clients" legacy-app "client legacy-app kept: EQUALS rule restricted to clients"
expect_in "$clients" ext-tool "client ext-tool kept: PATTERN rule ext-.*"
expect_in "$clients" account "built-in client account kept"
expect_in "$clients" admin-cli "built-in client admin-cli kept"
roles="$(realm_role_names)"
expect_in "$roles" reader "declared role reader kept"
expect_not_in "$roles" writer "role writer, no longer declared, deleted"
expect_not_in "$roles" legacy-app "role legacy-app deleted: the legacy-app rule only protects clients"
expect_in "$roles" offline_access "built-in role offline_access kept"
expect_in "$roles" "default-roles-$REALM" "default role of the realm kept"
expect_in "$(usernames)" alice "user alice kept: users are never deleted"

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
