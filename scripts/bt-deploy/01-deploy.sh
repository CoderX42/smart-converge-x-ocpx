#!/usr/bin/env bash
# SCX 宝塔标准打包发布入口：可选同步 -> 后端/XXL 打包 -> 前端构建 -> 备份 -> 前端站点发布。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/01-deploy.sh [选项] [all|backend|frontend|xxl]

选项：
  --no-pull       不同步代码，使用当前工作区构建
  --no-publish    仅生成 build/ 产物，不同步前端到宝塔站点根
  --skip-xxl      all/backend 时跳过 xxl-job-admin
  --branch <name> 同步时使用的分支（默认 main）
EOF
}

do_pull=true; do_publish=true; include_xxl=true; target=all
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-pull) do_pull=false; shift ;;
    --no-publish) do_publish=false; shift ;;
    --skip-xxl) include_xxl=false; shift ;;
    --branch) [[ $# -ge 2 ]] || bt_die '--branch 缺少分支名'; SCX_BRANCH="$2"; shift 2 ;;
    all|backend|frontend|xxl) target="$1"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知参数：$1" ;;
  esac
done
[[ "$target" != xxl || "$include_xxl" == true ]] || bt_die 'xxl 目标不能与 --skip-xxl 同时使用。'

if [[ "$do_pull" == true ]]; then
  SCX_HOME="$SCX_HOME" SCX_BRANCH="$SCX_BRANCH" bash "${SCRIPT_DIR}/00-sync_repos.sh"
fi
bt_assert_repository
bt_require_command mvn
bt_detect_java
bt_require_command jar
if [[ "$target" == all || "$target" == frontend ]]; then bt_prepare_node; fi

release_id="$(bt_release_id)"
mkdir -p "${SCX_BUILD_DIR}" "${SCX_BACKUP_DIR}"
stage_dir="$(mktemp -d "${SCX_BUILD_DIR}/.stage.${release_id}.XXXXXX")"
cleanup_stage() { rm -rf "${stage_dir}"; }
trap cleanup_stage EXIT
mkdir -p "${stage_dir}/backend" "${stage_dir}/frontend"

# Partial backend releases must not remove the artifact that is not being rebuilt
# (for example, `xxl` must leave the existing scx-boot.jar in place).
if [[ "$target" == all || "$target" == backend || "$target" == xxl ]]; then
  if [[ -d "${SCX_BUILD_DIR}/backend" ]]; then
    cp -a "${SCX_BUILD_DIR}/backend/." "${stage_dir}/backend/"
  fi
fi

build_boot() {
  bt_log '构建 scx-boot（跳过测试；日常发布前请先在 CI 或本机完成测试）'
  (cd "${SCX_HOME}/scx-backend" && mvn -pl scx-boot -am -DskipTests package)
  local jar_file="${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar"
  [[ -f "$jar_file" ]] || bt_die "未找到后端产物：${jar_file}"
  jar tf "$jar_file" | grep -qx 'BOOT-INF/classes/application-prod.yml' || bt_die '后端 JAR 缺少 application-prod.yml，拒绝发布。'
  cp -p "$jar_file" "${stage_dir}/backend/scx-boot.jar"
  (cd "${stage_dir}/backend" && sha256sum scx-boot.jar > scx-boot.jar.sha256)
}

build_xxl() {
  bt_log '构建 xxl-job-admin（跳过测试）'
  (cd "${SCX_HOME}/xxl-job" && mvn -pl xxl-job-admin -am -DskipTests package)
  local jar_file="${SCX_HOME}/xxl-job/xxl-job-admin/target/xxl-job-admin-3.4.1.jar"
  if [[ ! -f "$jar_file" ]]; then
    jar_file="$(find "${SCX_HOME}/xxl-job/xxl-job-admin/target" -maxdepth 1 -type f -name 'xxl-job-admin*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | head -n 1 || true)"
  fi
  [[ -n "${jar_file:-}" && -f "$jar_file" ]] || bt_die '未找到 xxl-job-admin JAR。'
  cp -p "$jar_file" "${stage_dir}/backend/xxl-job-admin.jar"
  (cd "${stage_dir}/backend" && sha256sum xxl-job-admin.jar > xxl-job-admin.jar.sha256)
}

build_frontend() {
  # 源码在 monorepo apps/web-ele，部署产物不沿用库名，统一落到 build/frontend/（管理端静态站）
  bt_log '构建管理端前端（pnpm build:ele → apps/web-ele/dist）'
  (cd "${SCX_HOME}/scx-fronted" && pnpm install --frozen-lockfile && pnpm build:ele)
  local dist="${SCX_HOME}/scx-fronted/apps/web-ele/dist"
  [[ -f "${dist}/index.html" ]] || bt_die "前端产物不完整：${dist}/index.html 不存在"
  mkdir -p "${stage_dir}/frontend"
  # 只拷 dist 内容，目录名为 frontend，不再嵌套 web-ele
  cp -a "${dist}/." "${stage_dir}/frontend/"
}

activate_component() {
  local component="$1" source="${stage_dir}/${1}" destination="${SCX_BUILD_DIR}/${1}" backup="${SCX_BACKUP_DIR}/${release_id}/${1}"
  [[ -d "$source" ]] || return 0
  mkdir -p "$(dirname "$backup")"
  [[ ! -e "$destination" ]] || mv "$destination" "$backup"
  mv "$source" "$destination"
  bt_ok "已更新构建产物：${destination}"
}

publish_frontend() {
  local source="${SCX_BUILD_DIR}/frontend"
  [[ -f "${source}/index.html" ]] || bt_die "前端构建产物缺失：${source}/index.html"
  bt_require_command rsync
  mkdir -p "${SCX_WEB_ROOT}"
  bt_log "同步前端到宝塔站点根：${SCX_WEB_ROOT}"
  rsync -a --delete --backup --backup-dir="${SCX_BACKUP_DIR}/${release_id}/site-before-publish" --exclude '.user.ini' --exclude '.well-known' "${source}/" "${SCX_WEB_ROOT}/"
  [[ -f "${SCX_WEB_ROOT}/index.html" ]] || bt_die '前端同步后缺少 index.html。'
  bt_ok "前端已发布到 ${SCX_WEB_ROOT}"
}

case "$target" in
  all) build_boot; [[ "$include_xxl" == true ]] && build_xxl; build_frontend; activate_component backend; activate_component frontend; [[ "$do_publish" == true ]] && publish_frontend ;;
  backend) build_boot; [[ "$include_xxl" == true ]] && build_xxl; activate_component backend ;;
  xxl) build_xxl; activate_component backend ;;
  frontend) build_frontend; activate_component frontend; [[ "$do_publish" == true ]] && publish_frontend ;;
esac
git -C "${SCX_HOME}" rev-parse HEAD > "${SCX_BACKUP_DIR}/${release_id}/source-commit.txt"
bt_prune_backups
bt_ok "打包部署流程完成：备份位于 ${SCX_BACKUP_DIR}/${release_id}"
if [[ "$target" == all || "$target" == backend || "$target" == xxl ]]; then bt_print_baota_restart_hint; fi
