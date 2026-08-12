#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Historical migrations, archived evidence, reverse-engineering artifacts and
# repository backups intentionally remain outside this active-product gate.
ACTIVE_PATHS=(
  "${ROOT_DIR}/AGENTS.md"
  "${ROOT_DIR}/scx-backend/README.md"
  "${ROOT_DIR}/scx-backend/pom.xml"
  "${ROOT_DIR}/scx-backend/scx-boot/pom.xml"
  "${ROOT_DIR}/scx-backend/scx-boot/src/main/resources/application.yml"
  "${ROOT_DIR}/scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql"
  "${ROOT_DIR}/scx-backend/scx-ocpx-admin"
  "${ROOT_DIR}/scx-backend/scx-ocpx-engine"
  "${ROOT_DIR}/scx-backend/db/smart-converge-x/README.md"
  "${ROOT_DIR}/scx-backend/db/smart-converge-x/schema-required-tables.sh"
  "${ROOT_DIR}/scx-backend/db/smart-converge-x/verify-database.sh"
  "${ROOT_DIR}/scx-backend/db/smart-converge-x/retirement"
  "${ROOT_DIR}/scx-fronted/apps/web-ele/src"
  "${ROOT_DIR}/scx-fronted/apps/web-ele/e2e"
  "${ROOT_DIR}/tools/performance"
  "${ROOT_DIR}/docs/广告平台设计/OCPX管理面实现说明.md"
  "${ROOT_DIR}/docs/广告平台设计/OCPX双向转发引擎实现说明.md"
  "${ROOT_DIR}/docs/广告平台设计/OCPX点击计费与充值账务说明.md"
  "${ROOT_DIR}/docs/广告平台设计/README.md"
  "${ROOT_DIR}/docs/广告平台设计/SCX-OCPX平台使用与功能验收手册.md"
  "${ROOT_DIR}/docs/广告平台设计/SCX系统已实现功能验收文档-2026-07-28.md"
  "${ROOT_DIR}/docs/广告平台设计/adr"
  "${ROOT_DIR}/docs/广告平台设计/contracts"
  "${ROOT_DIR}/docs/广告平台设计/ocpx-aerim-模块实现规格"
  "${ROOT_DIR}/docs/广告平台设计/sgyz-ocpx基础能力兼容基线.md"
)

if matches="$(rg --hidden --line-number --ignore-case 'adx' "${ACTIVE_PATHS[@]}" || true)"; [[ -n "${matches}" ]]; then
  echo "Active OCPX product files still contain the retired ADX name:" >&2
  echo "${matches}" >&2
  exit 1
fi

if names="$(find "${ACTIVE_PATHS[@]}" -iname '*adx*' -print 2>/dev/null || true)"; [[ -n "${names}" ]]; then
  echo "Active OCPX product paths still contain the retired ADX name:" >&2
  echo "${names}" >&2
  exit 1
fi

echo "Active OCPX naming validated"
