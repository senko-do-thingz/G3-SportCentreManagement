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

redact() {
  local value=$1 secret
  for secret in "${DB_PASSWORD:-}" "${JWT_SECRET:-}" "${MSSQL_SA_PASSWORD:-}" weak; do
    if [ -n "$secret" ]; then
      value=${value//"$secret"/[REDACTED]}
    fi
  done
  printf '%s' "$value"
}

redact_file() {
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    redact "$line"
    printf '\n'
  done < "$1"
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
  local container created=0 wait_status=0 logs_status=0 inspect_status=0 env_status=0
  local exit_code state engine_error candidate_matches timeout_class=none policy_signature=missing
  local state_status state_exit oom_killed
  local inspect_log="$policy_log.inspect"
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
  docker logs "$container" > "$policy_log" 2>&1 || logs_status=$?
  # Collect evidence before classifying the exit, including when wait times out.
  state="$(docker inspect --format '{{.State.Status}} {{.State.ExitCode}} {{.State.OOMKilled}}' "$container" 2>"$inspect_log")" || inspect_status=$?
  engine_error="$(docker inspect --format '{{.State.Error}}' "$container" 2>>"$inspect_log")" || inspect_status=$?
  candidate_matches="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" 2>>"$inspect_log" |
    grep -Fxc "MSSQL_SA_PASSWORD=$MSSQL_SA_PASSWORD")" || env_status=$?
  # Require SA setup rejection for complexity/length, not generic password errors.
  if grep -Eqi 'Unable to set system administrator password:.*Password validation failed[.].*password does not meet SQL Server password policy requirements because it is (not complex enough|too short)' "$policy_log"; then
    policy_signature=matched
  fi
  case "$wait_status" in
    0) timeout_class=none ;;
    124) timeout_class=deadline-exceeded ;;
    137) timeout_class=wait-killed ;;
    *) timeout_class=wait-error ;;
  esac
  read -r state_status state_exit oom_killed <<< "$state"
  printf 'Password-policy diagnostic: image=%s wait_status=%s timeout=%s state=%q state_exit=%q oom_killed=%q wait_exit=%q engine_error=%q policy_signature=%s candidate_matches=%s logs_status=%s inspect_status=%s env_status=%s\n' \
    "$policy_image" "$wait_status" "$timeout_class" "$(redact "$state_status")" "$(redact "$state_exit")" \
    "$(redact "$oom_killed")" "$(redact "$exit_code")" \
    "$(redact "$engine_error")" "$policy_signature" "$candidate_matches" "$logs_status" "$inspect_status" "$env_status"
  if [ -s "$inspect_log" ]; then
    redact_file "$inspect_log"
  fi
  if [ "$logs_status" -ne 0 ] || [ "$inspect_status" -ne 0 ] || [ "$env_status" -ne 0 ]; then
    echo 'Error: could not collect trustworthy SQL Server container evidence' >&2
    exit 1
  fi
  if [ "$wait_status" -ne 0 ]; then
    echo "Error: waiting for SQL Server failed (status $wait_status), not a confirmed password rejection" >&2
    exit 1
  fi
  # docker wait's stdout is the container process status, not the Docker CLI status.
  # 255 is a valid application exit; signal-range exits still fail even with policy logs.
  if ! [[ "$exit_code" =~ ^[1-9][0-9]{0,2}$ ]] || [ "$exit_code" -gt 255 ] ||
     { [ "$exit_code" -ge 128 ] && [ "$exit_code" -le 192 ]; }; then
    echo 'Error: SQL Server must exit nonzero without a signal-range process status' >&2
    exit 1
  fi
  if [ "$state" != "exited $exit_code false" ] || [ -n "$engine_error" ]; then
    echo 'Error: SQL Server exited because of a container/engine failure' >&2
    exit 1
  fi
  if [ "$candidate_matches" != 1 ]; then
    echo 'Error: the intended invalid SA password was not supplied exactly once' >&2
    exit 1
  fi
  # A file search avoids echo | grep -q SIGPIPE failures under pipefail on large logs.
  if [ "$policy_signature" != matched ]; then
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
      redact_file "$policy_log"
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
