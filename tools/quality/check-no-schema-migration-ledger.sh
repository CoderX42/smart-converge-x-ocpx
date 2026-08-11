#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FORBIDDEN_IDENTIFIER='scx_schema_migration'
violations=0

strip_sql_comments() {
  awk '
    BEGIN { in_block = 0 }
    {
      line = $0
      output = ""
      while (length(line) > 0) {
        if (in_block) {
          block_end = index(line, "*/")
          if (block_end == 0) {
            line = ""
            break
          }
          line = substr(line, block_end + 2)
          in_block = 0
          continue
        }

        block_start = index(line, "/*")
        line_comment = index(line, "--")
        if (line_comment > 0 && (block_start == 0 || line_comment < block_start)) {
          output = output substr(line, 1, line_comment - 1)
          line = ""
          break
        }
        if (block_start > 0) {
          output = output substr(line, 1, block_start - 1)
          line = substr(line, block_start + 2)
          in_block = 1
          continue
        }

        output = output line
        line = ""
      }
      print output
    }
  '
}

check_sql_file() {
  local file="$1"
  local executable_sql
  executable_sql="$(strip_sql_comments < "$file")"
  if grep -Fqi "$FORBIDDEN_IDENTIFIER" <<< "$executable_sql"; then
    echo "Retired schema ledger reference found in executable SQL: ${file#"${ROOT_DIR}/"}" >&2
    violations=$((violations + 1))
  fi
}

while IFS= read -r -d '' file; do
  check_sql_file "$file"
done < <(
  find "$ROOT_DIR" \
    \( -type d \( -name .git -o -name build -o -name node_modules -o -name target \) -prune \) \
    -o \( -type f -name '*.sql' -print0 \)
)

if [[ "$violations" -ne 0 ]]; then
  echo "Schema ledger check failed with ${violations} violation(s)." >&2
  exit 1
fi

echo "Schema ledger check passed: executable SQL does not use the retired ledger."
