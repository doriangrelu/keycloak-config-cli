#!/usr/bin/env bash
# Helpers shared by the scenarios. Source it, do not run it.

SAMPLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REALM=sample
APP_URL=https://app.example.org

# Git Bash on Windows rewrites arguments that look like paths (/config/...): keep them as they are
export MSYS_NO_PATHCONV=1

compose() {
    # Run from the sample directory: no path to convert on Windows
    (cd "$SAMPLE_DIR" && docker compose "$@")
}

# Runs keycloak-config-cli in its container; the import files are mounted in /config.
# Its output is kept in $CLI_OUTPUT and its exit code in $CLI_EXIT.
cli() {
    CLI_OUTPUT="$(compose run --rm -T cli "$@" 2>&1)"
    CLI_EXIT=$?
}

# kcadm.sh of the Keycloak container, logged in as the admin of the sample
kcadm() {
    compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"
}

kcadm_login() {
    kcadm config credentials --server http://localhost:8080 --realm master --user admin --password admin >/dev/null
}

# The id of a client of the sample realm, empty when it does not exist
client_id_of() {
    kcadm get clients -r "$REALM" -q "clientId=$1" --fields id |
        tr -d '\r' | grep -o '"id" : "[^"]*"' | head -1 | sed 's/.*: "\(.*\)"/\1/'
}

client_ids() {
    kcadm get clients -r "$REALM" --fields clientId | tr -d '\r' | grep -o '"clientId" : "[^"]*"' | sed 's/.*: "\(.*\)"/\1/'
}

realm_role_names() {
    kcadm get roles -r "$REALM" --fields name | tr -d '\r' | grep -o '"name" : "[^"]*"' | sed 's/.*: "\(.*\)"/\1/'
}

usernames() {
    kcadm get users -r "$REALM" --fields username | tr -d '\r' | grep -o '"username" : "[^"]*"' | sed 's/.*: "\(.*\)"/\1/'
}

realm_exists() {
    kcadm get "realms/$REALM" --fields realm >/dev/null 2>&1
}

# --- Assertions -------------------------------------------------------------------------------------------------

PASSED=${PASSED:-0}
FAILED=${FAILED:-0}

if [ -t 1 ]; then GREEN=$'\e[32m'; RED=$'\e[31m'; BOLD=$'\e[1m'; RESET=$'\e[0m'; else GREEN=; RED=; BOLD=; RESET=; fi

scenario() {
    echo
    echo "${BOLD}== $*${RESET}"
}

pass() {
    PASSED=$((PASSED + 1))
    echo "  ${GREEN}ok${RESET}    $*"
}

fail() {
    FAILED=$((FAILED + 1))
    echo "  ${RED}FAIL${RESET}  $*"
}

expect_exit() {
    local expected=$1 what=$2
    if [ "$CLI_EXIT" = "$expected" ]; then
        pass "$what (exit code $CLI_EXIT)"
    else
        fail "$what: exit code $CLI_EXIT, expected $expected"
        echo "$CLI_OUTPUT" | tail -20 | sed 's/^/        | /'
    fi
}

expect_output() {
    local text=$1 what=$2
    if grep -qF -- "$text" <<<"$CLI_OUTPUT"; then
        pass "$what"
    else
        fail "$what: '$text' not in the output"
        echo "$CLI_OUTPUT" | tail -20 | sed 's/^/        | /'
    fi
}

# expect_in "<values, one per line>" <value> <what>
expect_in() {
    if grep -qxF -- "$2" <<<"$1"; then pass "$3"; else fail "$3: '$2' not found"; fi
}

expect_not_in() {
    if grep -qxF -- "$2" <<<"$1"; then fail "$3: '$2' still there"; else pass "$3"; fi
}

expect_equal() {
    if [ "$1" = "$2" ]; then pass "$3"; else fail "$3: got '$1', expected '$2'"; fi
}

summary() {
    echo
    if [ "$FAILED" -eq 0 ]; then
        echo "${GREEN}${BOLD}$PASSED check(s) passed${RESET}"
    else
        echo "${RED}${BOLD}$FAILED check(s) failed${RESET}, $PASSED passed"
    fi
    [ "$FAILED" -eq 0 ]
}
