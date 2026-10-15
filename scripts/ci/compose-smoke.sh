#!/usr/bin/env bash
# CI-only lifecycle helpers; use only the disposable Actions checkout.
set -euo pipefail
umask 077

if [ "${GITHUB_ACTIONS:-}" != true ]; then
  echo 'Error: this helper is restricted to the disposable GitHub Actions workspace' >&2
  exit 2
fi
compose_file=deploy/compose.yaml
env_file=deploy/.env
policy_image=mcr.microsoft.com/mssql/server:2022-CU27-ubuntu-22.04
policy_log="${RUNNER_TEMP:?RUNNER_TEMP is required}/sportify-password-policy.log"

mask() {
  printf '::add-mask::%s\n' "$1"
}

prepare() {
  local password jwt
  if [ -e "$env_file" ]; then
    echo 'Error: refusing to overwrite an existing deployment environment file' >&2
    return 1
  fi
  password="Ci$(openssl rand -hex 24)!9"
  jwt="$(openssl rand -base64 32)"
  mask "$password"
  mask "$jwt"
  sed -e "s|<REPLACE_WITH_STRONG_SA_PASSWORD>|$password|g" \
      -e "s|<REPLACE_WITH_BASE64_SECRET_MIN_32_BYTES>|$jwt|g" deploy/.env.example > "$env_file"
  printf 'DB_PASSWORD=%s\nJWT_SECRET=%s\n' "$password" "$jwt" >> "${GITHUB_ENV:?GITHUB_ENV is required}"
}

password_policy() (
  local container created=0 wait_status=0 exit_code state
  container="sportify-policy-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}-$$"
  cleanup_policy() {
    local status=$?
    trap - EXIT
    if [ "$created" -eq 1 ] && ! docker rm -fv "$container" >/dev/null 2>&1; then
      echo 'Error: could not remove the password-policy test container' >&2
      status=1
    fi
    exit "$status"
  }
  trap cleanup_policy EXIT
  # Separate image retrieval from the runtime deadline and retain every command's failure.
  docker pull "$policy_image" >/dev/null
  export MSSQL_SA_PASSWORD=weak
  mask "$MSSQL_SA_PASSWORD"
  docker create --name "$container" -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD "$policy_image" >/dev/null
  created=1
  docker start "$container" >/dev/null
  exit_code="$(timeout 180s docker wait "$container")" || wait_status=$?
  docker logs "$container" > "$policy_log" 2>&1
  if [ "$wait_status" -ne 0 ]; then
    echo "Error: waiting for SQL Server failed (status $wait_status), not a confirmed password rejection" >&2
    exit 1
  fi
  if ! [[ "$exit_code" =~ ^[0-9]+$ ]] || [ "$exit_code" -eq 0 ] || [ "$exit_code" -ge 125 ]; then
    echo 'Error: SQL Server must exit with an ordinary nonzero process status' >&2
    exit 1
  fi
  state="$(docker inspect --format '{{.State.Status}} {{.State.ExitCode}} {{.State.OOMKilled}}' "$container")"
  if [ "$state" != "exited $exit_code false" ] || [ -n "$(docker inspect --format '{{.State.Error}}' "$container")" ]; then
    echo 'Error: SQL Server exited because of a container/engine failure' >&2
    exit 1
  fi
  # A file search avoids echo | grep -q SIGPIPE failures under pipefail on large logs.
  if ! grep -Eqi 'password validation failed|password.*(does not meet|policy requirements)' "$policy_log"; then
    echo 'Error: SQL Server exited without an explicit password validation/policy failure' >&2
    exit 1
  fi
  echo 'Verified: SQL Server rejected the invalid password and exited nonzero'
)

with_diagnostic_environment() {
  local mode=$1 temporary_env=0 status=0
  # Fallback values only permit parsing for logs/down; these modes never start services.
  export IMAGE_TAG="${IMAGE_TAG:-ci}"
  export DB_PASSWORD="${DB_PASSWORD:-CiDiagnosticsOnly123!}"
  mask "$DB_PASSWORD"
  if [ ! -f "$env_file" ]; then
    cp deploy/.env.example "$env_file"
    temporary_env=1
  fi
  if [ "$mode" = logs ]; then
    if [ -f "$policy_log" ]; then
      sed 's/weak/[REDACTED]/g' "$policy_log"
    fi
    docker compose -f "$compose_file" --profile with-db logs --no-color || status=$?
  else
    docker compose -f "$compose_file" --profile with-db down -v || status=$?
  fi
  if [ "$temporary_env" -eq 1 ] || [ "$mode" = down ]; then
    rm -f "$env_file"
  fi
  return "$status"
}

case "${1:-}" in
  prepare) prepare ;;
  password-policy) password_policy ;;
  diagnostics) with_diagnostic_environment logs ;;
  cleanup) with_diagnostic_environment down ;;
  *) echo 'Usage: compose-smoke.sh prepare|password-policy|diagnostics|cleanup' >&2; exit 2 ;;
esac
