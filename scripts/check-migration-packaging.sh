#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration_dir="${repo_root}/scx-backend/scx-boot/src/main/resources/db/migration"
init_script="${repo_root}/scx-backend/db/smart-converge-x/init-database.sh"

files=()
while IFS= read -r file; do
  files+=("$file")
done < <(find "$migration_dir" -maxdepth 1 -type f -name 'V*.sql' -print | sort -V)
if [[ "${#files[@]}" -eq 0 ]]; then
  echo "Migration packaging check failed: no migration files found." >&2
  exit 1
fi

seen=()
latest=0
for file in "${files[@]}"; do
  name="$(basename "$file")"
  if [[ ! "$name" =~ ^V([1-9][0-9]*)__[a-z0-9_]+\.sql$ ]]; then
    echo "Migration packaging check failed: invalid migration name $name." >&2
    exit 1
  fi
  version="${BASH_REMATCH[1]}"
  if [[ -n "${seen[$version]:-}" ]]; then
    echo "Migration packaging check failed: duplicate version V${version}." >&2
    exit 1
  fi
  seen[$version]="$name"
  (( version > latest )) && latest="$version"
done

for ((version = 1; version <= latest; version++)); do
  if [[ -z "${seen[$version]:-}" ]]; then
    echo "Migration packaging check failed: missing version V${version}." >&2
    exit 1
  fi
done

if ! grep -Fq 'scx-boot/src/main/resources/db/migration' "$init_script"; then
  echo "Migration packaging check failed: init-database.sh does not resolve the canonical migration directory." >&2
  exit 1
fi

echo "Migration packaging check passed: V1-V${latest} are unique and contiguous, and init-database.sh resolves the canonical directory."
