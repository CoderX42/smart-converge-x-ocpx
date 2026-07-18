#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION_DIR="${ROOT_DIR}/scx-backend/scx-boot/src/main/resources/db/migration"
COMPOSE_FILES=(
  "${ROOT_DIR}/scx-backend/docker-compose.yml"
  "${ROOT_DIR}/scx-backend/docker-compose-boot.yml"
)

expected="$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' -exec basename {} \; | sort -V)"

if [[ -z "$expected" ]]; then
  echo "No SCX migration files found." >&2
  exit 1
fi

for compose_file in "${COMPOSE_FILES[@]}"; do
  packaged="$(sed -nE 's#.*db/migration/(V[0-9]+__[^:]+\.sql):.*#\1#p' "$compose_file" | sort -V)"
  if [[ "$packaged" != "$expected" ]]; then
    echo "Migration mounts in ${compose_file#"${ROOT_DIR}/"} do not match the migration directory." >&2
    diff -u <(printf '%s\n' "$expected") <(printf '%s\n' "$packaged") || true
    exit 1
  fi
done

echo "Migration packaging check passed: $(printf '%s\n' "$expected" | wc -l | tr -d ' ') scripts are mounted by both Compose files."
