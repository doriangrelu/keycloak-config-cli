#!/usr/bin/env bash
# Starts Keycloak, builds the image of the CLI and starts from a Keycloak without the sample realm.
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if [ ! -f "$SAMPLE_DIR/../target/keycloak-config-cli.jar" ]; then
    echo "Build the JAR first, from the root of the repository: ./mvnw clean package -DskipTests" >&2
    exit 1
fi

scenario "Start Keycloak 26.5.3 and build the CLI image"
if compose up -d --wait keycloak; then
    pass "Keycloak is ready on http://localhost:18080 (admin / admin)"
else
    fail "Keycloak did not start"
    return 1 2>/dev/null || exit 1
fi
if compose build -q cli; then pass "image of the CLI built"; else fail "the image of the CLI could not be built"; fi
if kcadm_login; then pass "kcadm logged in"; else fail "kcadm could not log in"; fi
if realm_exists; then
    kcadm delete "realms/$REALM" && pass "realm '$REALM' of a previous run deleted"
fi
if kcadm get realms/shelled --fields realm >/dev/null 2>&1; then
    kcadm delete realms/shelled && pass "realm 'shelled' of a previous run deleted"
fi
if kcadm get realms/reorganized --fields realm >/dev/null 2>&1; then
    kcadm delete realms/reorganized && pass "realm 'reorganized' of a previous run deleted"
fi
# The root of the shell: the realms it created in a previous run
# The CLI container writes there as root: empty the folder from a container, the host user may not be allowed to
mkdir -p "$SAMPLE_DIR/shell/work"
if compose run --rm -T --no-deps --entrypoint sh cli -c 'rm -rf /shell/work/* /shell/work/.[!.]*' >/dev/null 2>&1 &&
    [ -z "$(ls -A "$SAMPLE_DIR/shell/work")" ]; then
    pass "shell/work emptied"
else
    fail "shell/work could not be emptied"
fi

[ "${BASH_SOURCE[0]}" = "$0" ] && summary
