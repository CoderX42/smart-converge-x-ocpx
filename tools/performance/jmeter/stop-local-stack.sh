#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker-compose -p scx-ocpx-jmeter -f "${SCRIPT_DIR}/docker-compose.yml" down --volumes --remove-orphans
