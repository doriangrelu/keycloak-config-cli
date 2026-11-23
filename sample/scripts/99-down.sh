#!/usr/bin/env bash
# Stops Keycloak and removes its data.
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

compose down -v --remove-orphans
