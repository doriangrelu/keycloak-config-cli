#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Defaults ────────────────────────────────────────────────────────
KC_CONFIG_CLI_VERSION="${KC_CONFIG_CLI_VERSION:-7.0.0}"
JAR_NAME="keycloak-config-cli-${KC_CONFIG_CLI_VERSION}.jar"
JAR_PATH="${SCRIPT_DIR}/bin/${JAR_NAME}"
DOWNLOAD_URL="https://github.com/doriangrelu/keycloak-config-cli/releases/download/v${KC_CONFIG_CLI_VERSION}/${JAR_NAME}"

# ── Helpers ─────────────────────────────────────────────────────────
usage() {
  cat <<EOF
Usage: $0 <command> [options]

Commands:
  download    Download keycloak-config-cli JAR into bin/
  import      Import realm configuration into Keycloak

Environment variables (or .env file):
  KEYCLOAK_URL            Keycloak base URL         (default: http://localhost:8080)
  KEYCLOAK_USER           Admin username             (default: admin)
  KEYCLOAK_PASSWORD       Admin password             (default: admin)
  KC_CONFIG_CLI_VERSION   CLI version to download    (default: 7.0.0)

  REALM_NAME, ENVIRONMENT, FRONTEND_URL, BACKEND_URL,
  SMTP_HOST, SMTP_PORT, SMTP_FROM
    Mustache variables injected into YAML templates.

Any extra arguments are forwarded to the JAR (e.g. --keycloak.ssl-verify=false).
EOF
  exit 1
}

load_env() {
  if [[ -f "${SCRIPT_DIR}/.env" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${SCRIPT_DIR}/.env"
    set +a
  fi
}

# ── Commands ────────────────────────────────────────────────────────
cmd_download() {
  mkdir -p "${SCRIPT_DIR}/bin"
  echo "Downloading keycloak-config-cli v${KC_CONFIG_CLI_VERSION}…"
  if command -v curl &>/dev/null; then
    curl -fSL -o "${JAR_PATH}" "${DOWNLOAD_URL}"
  elif command -v wget &>/dev/null; then
    wget -q -O "${JAR_PATH}" "${DOWNLOAD_URL}"
  else
    echo "Error: neither curl nor wget found." >&2
    exit 1
  fi
  echo "Saved to ${JAR_PATH}"
}

cmd_import() {
  load_env

  if [[ ! -f "${JAR_PATH}" ]]; then
    echo "Error: JAR not found at ${JAR_PATH}" >&2
    echo "Run '$0 download' first." >&2
    exit 1
  fi

  local locations
  locations="$(printf '%s' \
    "${SCRIPT_DIR}/realm-config/*.yaml," \
    "${SCRIPT_DIR}/realm-config/clients/*.yaml," \
    "${SCRIPT_DIR}/realm-config/identity-providers/*.yaml")"

  java -jar "${JAR_PATH}" \
    --keycloak.url="${KEYCLOAK_URL:-http://localhost:8080}" \
    --keycloak.user="${KEYCLOAK_USER:-admin}" \
    --keycloak.password="${KEYCLOAK_PASSWORD:-admin}" \
    --import.files.locations="${locations}" \
    --import.mustache.enabled=true \
    --import.mustache.variables.realm_name="${REALM_NAME:-my-realm}" \
    --import.mustache.variables.environment="${ENVIRONMENT:-dev}" \
    --import.mustache.variables.frontend_url="${FRONTEND_URL:-http://localhost:3000}" \
    --import.mustache.variables.backend_url="${BACKEND_URL:-http://localhost:8081}" \
    --import.mustache.variables.smtp_host="${SMTP_HOST:-localhost}" \
    --import.mustache.variables.smtp_port="${SMTP_PORT:-25}" \
    --import.mustache.variables.smtp_from="${SMTP_FROM:-no-reply@example.com}" \
    "$@"
}

# ── Main ────────────────────────────────────────────────────────────
[[ $# -lt 1 ]] && usage

case "$1" in
  download) shift; cmd_download "$@" ;;
  import)   shift; cmd_import "$@" ;;
  *)        usage ;;
esac
