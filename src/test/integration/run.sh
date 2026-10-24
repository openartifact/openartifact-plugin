#!/usr/bin/env bash
# SPDX-FileCopyrightText: The OpenArtifact Authors
#
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Runs the plugin's integration test (OaIntegrationTest) against a real
# OpenArtifact server and the real oa CLI:
#
#   1. starts Postgres and the server (a container image, or a local binary),
#   2. as the bootstrap admin creates repositories, a promotion pipeline, a
#      non-admin CI user with a deploy token,
#   3. runs `mvn -Pintegration verify` with that token, like a Jenkins job would.
#
# Environment (all optional):
#   OA_VERSION        release to test: server image tag and oa archive (default 0.10.0)
#   OA_IT_SERVER_BIN  run this server binary instead of the container image
#   OA_IT_OA_BIN      use this oa binary instead of downloading the release
#   OA_IT_PORT        server port (default 18491)
#   OA_IT_PG_PORT     Postgres port (default 55491)
#   OA_IT_COMMAND     run this shell command instead of mvn, with the OA_IT_* variables exported
#                     (to poke at the provisioned stack, for example `mvn hpi:run`)
#   MAVEN_ARGS_EXTRA  extra arguments for mvn
#
# Needs docker (or podman as docker), curl, jq and a JDK 17+.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
version="${OA_VERSION:-0.10.0}"
port="${OA_IT_PORT:-18491}"
pg_port="${OA_IT_PG_PORT:-55491}"
url="http://127.0.0.1:$port"
admin_pass_new='it-admin-password-123'
suffix="$$"
pg_name="oa-plugin-it-pg-$suffix"
srv_name="oa-plugin-it-server-$suffix"
work="$(mktemp -d "${TMPDIR:-/tmp}/oa-plugin-it.XXXXXX")"
srv_pid=""

for tool in docker curl jq; do
	command -v "$tool" >/dev/null || { echo "run.sh: $tool is required" >&2; exit 1; }
done

cleanup() {
	[ -n "$srv_pid" ] && kill "$srv_pid" 2>/dev/null || true
	docker rm -f "$srv_name" "$pg_name" >/dev/null 2>&1 || true
	rm -rf "$work"
}
trap cleanup EXIT

say() { printf '\n== %s\n' "$*"; }

# --- oa CLI ------------------------------------------------------------------
if [ -n "${OA_IT_OA_BIN:-}" ]; then
	oa_bin="$OA_IT_OA_BIN"
else
	say "downloading oa $version and verifying SHA256SUMS"
	base="https://github.com/openartifact/openartifact/releases/download/v$version"
	archive="oa_${version}_linux_amd64.tar.gz"
	curl -fsSL -o "$work/$archive" "$base/$archive"
	curl -fsSL -o "$work/SHA256SUMS" "$base/SHA256SUMS"
	(cd "$work" && grep " $archive\$" SHA256SUMS | sha256sum -c -)
	tar -xzf "$work/$archive" -C "$work" oa
	oa_bin="$work/oa"
fi

# --- Postgres + server -------------------------------------------------------
say "starting Postgres on :$pg_port"
docker run -d --name "$pg_name" -p "127.0.0.1:$pg_port:5432" \
	-e POSTGRES_PASSWORD=it -e POSTGRES_DB=artifact_repo \
	docker.io/library/postgres:18-alpine >/dev/null
for _ in $(seq 1 60); do
	docker exec "$pg_name" pg_isready -U postgres >/dev/null 2>&1 && break
	sleep 1
done

server_env=(
	SERVER_HOST=127.0.0.1 SERVER_PORT="$port" BASE_URL="$url"
	DB_DRIVER=postgres DB_HOST=127.0.0.1 DB_PORT="$pg_port" DB_USER=postgres DB_PASSWORD=it
	DB_NAME=artifact_repo DB_SSLMODE=disable
	STORAGE_BACKEND=filesystem LOG_LEVEL=warn
)
server_log="$work/server.log"
if [ -n "${OA_IT_SERVER_BIN:-}" ]; then
	say "starting server binary $OA_IT_SERVER_BIN"
	mkdir -p "$work/data"
	env "${server_env[@]}" STORAGE_PATH="$work/data" OPENARTIFACT_BACKUP_DIR="$work/backups" \
		"$OA_IT_SERVER_BIN" >"$server_log" 2>&1 &
	srv_pid=$!
else
	say "starting server image ghcr.io/openartifact/openartifact:$version"
	docker_env=()
	for kv in "${server_env[@]}"; do docker_env+=(-e "$kv"); done
	docker run -d --name "$srv_name" --network host "${docker_env[@]}" \
		-e STORAGE_PATH=/tmp/artifacts -e OPENARTIFACT_BACKUP_DIR=/tmp/backups \
		"ghcr.io/openartifact/openartifact:$version" >/dev/null
fi
logs() { if [ -n "$srv_pid" ]; then cat "$server_log"; else docker logs "$srv_name" 2>&1; fi; }

for _ in $(seq 1 90); do
	[ "$(curl -s -o /dev/null -w '%{http_code}' "$url/health" || true)" = 200 ] && break
	sleep 1
done
[ "$(curl -s -o /dev/null -w '%{http_code}' "$url/health" || true)" = 200 ] || { logs >&2; echo "server did not become healthy" >&2; exit 1; }

# --- admin bootstrap ---------------------------------------------------------
say "bootstrapping admin, repositories, pipeline and CI deploy token"
admin_pass="$(logs | awk '/admin password:/{print $3}' | tail -1)"
[ -n "$admin_pass" ] || { logs >&2; echo "no bootstrap admin password in the server output" >&2; exit 1; }

jar="$work/jar"
csrf() { awk '$6=="oa_csrf"{print $7}' "$jar"; }
session() { # METHOD PATH [JSON]
	local method="$1" path="$2" body="${3:-}"
	if [ -n "$body" ]; then
		curl -fsS -X "$method" "$url$path" -b "$jar" -c "$jar" -H "X-CSRF-Token: $(csrf)" -H 'Content-Type: application/json' -d "$body"
	else
		curl -fsS -X "$method" "$url$path" -b "$jar" -c "$jar" -H "X-CSRF-Token: $(csrf)"
	fi
}
jq -n --arg p "$admin_pass" '{username:"admin",password:$p}' |
	curl -fsS -o /dev/null -c "$jar" -H 'Content-Type: application/json' --data-binary @- "$url/api/v1/auth/login"
session POST /api/v1/auth/change-password \
	"$(jq -n --arg c "$admin_pass" --arg n "$admin_pass_new" '{current_password:$c,new_password:$n}')" >/dev/null
jq -n --arg p "$admin_pass_new" '{username:"admin",password:$p}' |
	curl -fsS -o /dev/null -c "$jar" -H 'Content-Type: application/json' --data-binary @- "$url/api/v1/auth/login"
admin_token="$(session POST /api/v1/auth/tokens '{"name":"it-admin","kind":"access"}' | jq -er .token)"

adm() { OA_SERVER="$url" OA_TOKEN="$admin_token" "$oa_bin" "$@"; }
adm repo create ci-dev --format raw >/dev/null
adm repo create ci-prod --format raw >/dev/null
adm_curl() { curl -fsS -H "Authorization: Bearer $admin_token" -H 'Content-Type: application/json' "$@"; }
dev_id="$(adm_curl "$url/api/v1/repository/ci-dev" | jq -er .id)"
prod_id="$(adm_curl "$url/api/v1/repository/ci-prod" | jq -er .id)"
adm_curl -X POST "$url/api/v1/pipelines" -d "$(jq -n --arg d "$dev_id" --arg p "$prod_id" \
	'{name:"release",stages:[{name:"dev",repo_id:$d},{name:"prod",repo_id:$p}]}')" >/dev/null
user_id="$(adm_curl -X POST "$url/api/v1/users" \
	-d '{"username":"ci-bot","email":"ci-bot@example.com","password":"ci-bot-password-123"}' | jq -er '.id // .user.id')"
pt_id="$(adm_curl -X POST "$url/api/v1/permission-targets" \
	-d '{"name":"ci-rw","repository_filter":"^ci-(dev|prod)$","path_pattern":".*","actions":["read","write"]}' | jq -er .id)"
adm_curl -X POST "$url/api/v1/permission-targets/$pt_id/assignments" \
	-d "{\"principal_type\":\"user\",\"principal_id\":\"$user_id\"}" -o /dev/null
ci_token="$(session POST "/api/v1/users/$user_id/tokens" '{"name":"jenkins","kind":"deploy"}' | jq -er .token)"

# --- the test ----------------------------------------------------------------
export OA_IT_SERVER="$url" OA_IT_TOKEN="$ci_token" OA_IT_OA_BIN="$oa_bin" \
	OA_IT_REPO_DEV=ci-dev OA_IT_REPO_PROD=ci-prod OA_IT_PIPELINE=release
if [ -n "${OA_IT_COMMAND:-}" ]; then
	say "running: $OA_IT_COMMAND"
	cd "$root"
	bash -c "$OA_IT_COMMAND"
	exit $?
fi
say "mvn -Pintegration verify against $url with $("$oa_bin" --format json version | jq -r .version)"
cd "$root"
mvn --batch-mode --no-transfer-progress -Pintegration verify ${MAVEN_ARGS_EXTRA:-}
