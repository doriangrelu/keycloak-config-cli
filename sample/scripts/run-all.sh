#!/usr/bin/env bash
# Runs every scenario in order, then stops Keycloak (unless --keep).
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for script in "$SCRIPTS"/0*.sh "$SCRIPTS"/1*.sh; do
    # shellcheck disable=SC1090
    source "$script"
    # Nothing can run without Keycloak
    if [ "$(basename "$script")" = "00-up.sh" ] && [ "$FAILED" -gt 0 ]; then
        break
    fi
done

summary
status=$?
if [ "${1:-}" != "--keep" ]; then
    "$SCRIPTS/99-down.sh" >/dev/null 2>&1
fi
exit $status
