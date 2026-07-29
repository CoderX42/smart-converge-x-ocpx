#!/usr/bin/env bash
# Contract validation is intentionally outside scripts/: that directory is reserved for Baota operations.
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
contracts_dir="${repo_root}/docs/广告平台设计/contracts"
[[ -d "$contracts_dir" ]] || { echo "contracts directory missing: $contracts_dir" >&2; exit 1; }

python3 - "$contracts_dir" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
files = list(root.rglob('*.json'))
if not files:
    raise SystemExit('no JSON contract files found')
for path in files:
    with path.open(encoding='utf-8') as handle:
        json.load(handle)
print(f'Contract check passed: {len(files)} JSON file(s) parsed.')
PY

for file in "${contracts_dir}"/*.{yaml,yml}; do
  [[ -e "$file" ]] || continue
  python3 - "$file" <<'PY'
import sys
try:
    import yaml
except ImportError as exc:
    raise SystemExit(f'PyYAML is required for YAML contracts: {exc}')
with open(sys.argv[1], encoding='utf-8') as handle:
    yaml.safe_load(handle)
PY
done

echo 'Contract check passed.'
