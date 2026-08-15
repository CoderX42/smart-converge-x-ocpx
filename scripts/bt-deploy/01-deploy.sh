#!/usr/bin/env bash
# SCX 宝塔标准打包发布入口：可选同步 -> Boot/Engine/XXL 打包 -> 前端构建 -> 备份 -> 发布。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/01-deploy.sh [选项] [all|backend|engine|frontend|xxl]

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
    all|backend|engine|frontend|xxl) target="$1"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知参数：$1" ;;
  esac
done
[[ "$target" != xxl || "$include_xxl" == true ]] || bt_die 'xxl 目标不能与 --skip-xxl 同时使用。'

if [[ "$do_pull" == true ]]; then
  SCX_HOME="$SCX_HOME" SCX_BRANCH="$SCX_BRANCH" bash "${SCRIPT_DIR}/00-sync_repos.sh"
fi
bt_assert_repository
if [[ "$target" == all || "$target" == backend || "$target" == engine || "$target" == xxl ]]; then
  bt_require_command mvn
  bt_detect_java
  bt_require_command jar
fi
if [[ "$target" == all || "$target" == frontend ]]; then bt_prepare_node; fi

release_id="$(bt_release_id)"
mkdir -p "${SCX_BUILD_DIR}" "${SCX_BACKUP_DIR}"
stage_dir="$(mktemp -d "${SCX_BUILD_DIR}/.stage.${release_id}.XXXXXX")"
cleanup_stage() { rm -rf "${stage_dir}"; }
trap cleanup_stage EXIT
mkdir -p "${stage_dir}/backend" "${stage_dir}/frontend"

# Partial backend releases preserve active artifacts that are not being rebuilt
# (for example, `engine` leaves scx-boot.jar and xxl-job-admin.jar in place).
if [[ "$target" == all || "$target" == backend || "$target" == engine || "$target" == xxl ]]; then
  if [[ -d "${SCX_BUILD_DIR}/backend" ]]; then
    cp -a "${SCX_BUILD_DIR}/backend/." "${stage_dir}/backend/"
  fi
fi

build_boot() {
  bt_log '构建 scx-boot（跳过测试；日常发布前请先在 CI 或本机完成测试）'
  # A clean lifecycle is required because Maven does not remove resources that
  # were deleted from src/main/resources during an incremental package. Without
  # it, retired MyBatis XML files can remain in target/classes and enter the JAR.
  (cd "${SCX_HOME}/scx-backend" && mvn -pl scx-boot -am -DskipTests clean package)
  local jar_file="${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar"
  local ocpx_admin_jar="${SCX_HOME}/scx-backend/scx-ocpx-admin/target/scx-ocpx-admin.jar"
  [[ -f "$jar_file" ]] || bt_die "未找到后端产物：${jar_file}"
  [[ -f "$ocpx_admin_jar" ]] || bt_die "未找到 OCPX 管理模块产物：${ocpx_admin_jar}"
  jar tf "$jar_file" | grep -qx 'BOOT-INF/classes/application-prod.yml' || bt_die '后端 JAR 缺少 application-prod.yml，拒绝发布。'
  if jar tf "$ocpx_admin_jar" | grep -Eq '(^|/)(OcpxHourlyReportMapper\.xml|ReportRow\.class)$'; then
    bt_die 'OCPX 管理模块仍包含已退役报表类或 Mapper XML，拒绝发布。'
  fi
  cp -p "$jar_file" "${stage_dir}/backend/scx-boot.jar"
  (cd "${stage_dir}/backend" && sha256sum scx-boot.jar > scx-boot.jar.sha256)
}

build_engine() {
  bt_log '构建 scx-ocpx-engine 数据面可执行 JAR（跳过测试；发布前须先通过本机门禁）'
  (cd "${SCX_HOME}/scx-backend" && mvn -pl scx-ocpx-engine -am -DskipTests clean package)
  local jar_file="${SCX_HOME}/scx-backend/scx-ocpx-engine/target/scx-ocpx-engine-exec.jar"
  [[ -f "$jar_file" ]] || bt_die "未找到数据面可执行产物：${jar_file}"
  jar tf "$jar_file" | grep -qx 'BOOT-INF/classes/application-prod.yml' \
    || bt_die 'scx-ocpx-engine 可执行 JAR 缺少 application-prod.yml，拒绝发布。'
  jar tf "$jar_file" | grep -qx 'BOOT-INF/classes/com/yzytech/scx/ocpx/ScxOcpxEngineApplication.class' \
    || bt_die 'scx-ocpx-engine 可执行 JAR 缺少启动类，拒绝发布。'
  cp -p "$jar_file" "${stage_dir}/backend/scx-ocpx-engine.jar"
  (cd "${stage_dir}/backend" && sha256sum scx-ocpx-engine.jar > scx-ocpx-engine.jar.sha256)
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

validate_frontend_bundle() {
  local root="$1" ref path
  [[ -f "${root}/index.html" ]] || bt_die "前端站点缺少 index.html：${root}"

  # Vite emits root-relative asset URLs for this app, so every local src/href
  # must exist below the site root before anything is published.
  while IFS= read -r ref; do
    path="${ref%%\?*}"
    [[ "$path" == /* ]] || continue
    [[ -f "${root}${path}" ]] || bt_die "前端资源缺失：${path}（站点根：${root}）"
  done < <(grep -oE '(src|href)="[^"]+"' "${root}/index.html" | sed -E 's/^[^=]+="//; s/"$//')
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
  validate_frontend_bundle "$source"
  mkdir -p "${SCX_WEB_ROOT}"
  bt_log "同步前端到宝塔站点根：${SCX_WEB_ROOT}"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete --backup --backup-dir="${SCX_BACKUP_DIR}/${release_id}/site-before-publish" --exclude '.user.ini' --exclude '.well-known' "${source}/" "${SCX_WEB_ROOT}/"
  else
    # Minimal Baota images may not include rsync. Keep protected site files and
    # copy the validated bundle so frontend-only publishing still works.
    bt_warn '未找到 rsync，使用 cp 兼容发布；旧的未引用静态文件不会自动清理。'
    cp -a "${source}/." "${SCX_WEB_ROOT}/"
  fi
  validate_frontend_bundle "${SCX_WEB_ROOT}"
  bt_ok "前端已发布到 ${SCX_WEB_ROOT}"
}

case "$target" in
  all) build_boot; build_engine; [[ "$include_xxl" == true ]] && build_xxl; build_frontend; activate_component backend; activate_component frontend; [[ "$do_publish" == true ]] && publish_frontend ;;
  backend) build_boot; build_engine; [[ "$include_xxl" == true ]] && build_xxl; activate_component backend ;;
  engine) build_engine; activate_component backend ;;
  xxl) build_xxl; activate_component backend ;;
  frontend) build_frontend; activate_component frontend; [[ "$do_publish" == true ]] && publish_frontend ;;
esac
git -C "${SCX_HOME}" rev-parse HEAD > "${SCX_BACKUP_DIR}/${release_id}/source-commit.txt"
bt_prune_backups
bt_ok "打包部署流程完成：备份位于 ${SCX_BACKUP_DIR}/${release_id}"
if [[ "$target" == all || "$target" == backend || "$target" == engine || "$target" == xxl ]]; then
  [[ "${SCX_SUPPRESS_RESTART_HINT:-0}" == '1' ]] || bt_print_baota_restart_hint "$target"
fi
