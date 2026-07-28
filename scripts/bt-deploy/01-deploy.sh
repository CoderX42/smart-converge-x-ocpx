#!/usr/bin/env bash
# =============================================================================
#  SCX 宝塔一键打包脚本
#  - 同步代码(可选) → scx-boot + xxl-job-admin + 前端 pnpm build:ele → 整理产物
#  - 前端 rsync 到宝塔站点根（SCX_WEB_ROOT，默认 /www/wwwroot/scx）
#
#  使用:
#    bash 01-deploy.sh                 # 同步 + 全量打包 + 发布前端
#    bash 01-deploy.sh --no-pull       # 跳过同步，直接打包
#    bash 01-deploy.sh backend         # scx-boot + xxl-job-admin
#    bash 01-deploy.sh xxl             # 仅 xxl-job-admin
#    bash 01-deploy.sh frontend        # 仅前端
#    bash 01-deploy.sh --skip-xxl      # 跳过 xxl-job
#    bash 01-deploy.sh --no-publish    # 构建但不 rsync 到站点根
#
#  环境变量:
#    SCX_HOME          代码根，默认脚本所在仓库或 /opt/smart-converge-x
#    SCX_WEB_ROOT      宝塔站点根，默认 /www/wwwroot/scx
#    BUILD_DIR         产物目录，默认 ${SCX_HOME}/build
#    NPM_REGISTRY      默认 https://registry.npmmirror.com
#    BOOT_JAR_NAME     默认 scx-boot.jar
#    XXL_JAR_NAME      默认 xxl-job-admin.jar
#    XXL_JOB_HOME      若设置或目录已存在，额外复制到该目录
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_REPO_GUESS="$(cd "${SCRIPT_DIR}/../.." && pwd)"
if [[ -d "${_REPO_GUESS}/scx-backend" ]]; then
  DEFAULT_HOME="${_REPO_GUESS}"
else
  DEFAULT_HOME="/opt/smart-converge-x"
fi

SCX_HOME="${SCX_HOME:-$DEFAULT_HOME}"
BUILD_DIR="${BUILD_DIR:-${SCX_HOME}/build}"
SCX_WEB_ROOT="${SCX_WEB_ROOT:-${SCX_BAOTA_SITE_ROOT:-/www/wwwroot/scx}}"
BOOT_JAR_NAME="${BOOT_JAR_NAME:-scx-boot.jar}"
XXL_JAR_NAME="${XXL_JAR_NAME:-xxl-job-admin.jar}"
XXL_JOB_HOME="${XXL_JOB_HOME:-}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${SCX_HOME}/backup/${TIMESTAMP}"
REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BLUE='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $1"; }
ok()   { echo -e "${GREEN}[✔]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err()  { echo -e "${RED}[✘]${NC} $1"; }

detect_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    echo "$JAVA_HOME"; return 0
  fi
  local p
  for p in \
    /www/server/java/jdk-17* \
    /www/server/java/jdk-21* \
    /usr/lib/jvm/java-17-openjdk* \
    /usr/lib/jvm/java-17* \
    /usr/lib/jvm/java-21* \
    /usr/lib/jvm/default-java
  do
    # shellcheck disable=SC2086
    for cand in $p; do
      if [[ -x "${cand}/bin/java" ]]; then
        echo "$cand"; return 0
      fi
    done
  done
  if command -v java >/dev/null 2>&1; then
    local jhome
    jhome="$(dirname "$(dirname "$(readlink -f "$(command -v java)" 2>/dev/null || command -v java)")")"
    if [[ -x "${jhome}/bin/java" ]]; then echo "$jhome"; return 0; fi
  fi
  return 1
}

setup_java() {
  JAVA_HOME="$(detect_java_home)" || { err "未找到 JDK 17+，请在宝塔安装 Java 或 apt install openjdk-17-jdk"; exit 1; }
  export JAVA_HOME
  export PATH="${JAVA_HOME}/bin:${PATH}"
  export MAVEN_OPTS="${MAVEN_OPTS:--Xmx2g}"
  export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=4096}"
  log "JAVA_HOME = $JAVA_HOME ($(${JAVA_HOME}/bin/java -version 2>&1 | head -1))"
}

ensure_pnpm() {
  if ! command -v pnpm >/dev/null 2>&1; then
    if command -v corepack >/dev/null 2>&1; then
      corepack enable >/dev/null 2>&1 || true
      corepack prepare pnpm@10.12.4 --activate || true
    fi
  fi
  if ! command -v pnpm >/dev/null 2>&1; then
    warn "未检测到 pnpm，尝试 npm 全局安装..."
    command -v npm >/dev/null 2>&1 || { err "缺少 node/npm"; exit 1; }
    npm install -g pnpm@10.12.4 --registry="$REGISTRY" || { err "pnpm 安装失败"; exit 1; }
    hash -r
  fi
  ok "pnpm: $(pnpm -v 2>/dev/null || echo n/a)  node: $(node -v 2>/dev/null || echo n/a)"
}

check_env() {
  command -v git >/dev/null  || { err "缺少 git"; exit 1; }
  command -v mvn >/dev/null  || { err "缺少 mvn"; exit 1; }
  command -v node >/dev/null || { err "缺少 node"; exit 1; }
  command -v java >/dev/null || { err "缺少 java"; exit 1; }
  [[ -d "${SCX_HOME}/scx-backend" ]] || { err "未发现 ${SCX_HOME}/scx-backend，请先执行 00-sync_repos.sh"; exit 1; }
  [[ -d "${SCX_HOME}/scx-fronted" ]] || { err "未发现 ${SCX_HOME}/scx-fronted"; exit 1; }
  ok "环境检查通过  SCX_HOME=${SCX_HOME}"
}

check_xxl_src() {
  [[ -d "${SCX_HOME}/xxl-job/xxl-job-admin" ]] \
    || { err "未发现 ${SCX_HOME}/xxl-job/xxl-job-admin，请确认 monorepo 含 xxl-job 源码"; return 1; }
}

sync_code() {
  log "[1/4] 拉取最新代码"
  bash "${SCRIPT_DIR}/00-sync_repos.sh"
}

backup_old() {
  local target="${1:-all}"
  if [[ -d "${BUILD_DIR}" && -n "$(ls -A "${BUILD_DIR}" 2>/dev/null || true)" ]]; then
    log "[2/4] 备份旧产物到 ${BACKUP_DIR}"
    mkdir -p "${BACKUP_DIR}"
    find "${BUILD_DIR}" -name '.user.ini' -exec chattr -i {} \; 2>/dev/null || true
    find "${BUILD_DIR}" -name '.user.ini' -delete 2>/dev/null || true
    case "$target" in
      all)
        cp -a "${BUILD_DIR}/backend" "${BACKUP_DIR}/" 2>/dev/null || true
        cp -a "${BUILD_DIR}/frontend" "${BACKUP_DIR}/" 2>/dev/null || true
        rm -rf "${BUILD_DIR:?}/backend" "${BUILD_DIR:?}/frontend"
        ;;
      backend)
        cp -a "${BUILD_DIR}/backend" "${BACKUP_DIR}/" 2>/dev/null || true
        rm -rf "${BUILD_DIR}/backend"
        ;;
      frontend)
        cp -a "${BUILD_DIR}/frontend" "${BACKUP_DIR}/" 2>/dev/null || true
        rm -rf "${BUILD_DIR}/frontend"
        ;;
    esac
  else
    log "[2/4] 无旧产物，跳过备份"
  fi
  mkdir -p "${BUILD_DIR}/backend" "${BUILD_DIR}/frontend"
}

build_xxl_job() {
  log "  → xxl-job-admin (3.4.1, skipTests)"
  check_xxl_src || return 1
  (
    cd "${SCX_HOME}/xxl-job"
    mvn -pl xxl-job-admin -am -DskipTests package
  )
  local jar_src="${SCX_HOME}/xxl-job/xxl-job-admin/target/xxl-job-admin-3.4.1.jar"
  if [[ ! -f "$jar_src" ]]; then
    jar_src="$(find "${SCX_HOME}/xxl-job/xxl-job-admin/target" -maxdepth 1 -type f -name 'xxl-job-admin*.jar' \
      ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | head -n 1 || true)"
  fi
  [[ -n "${jar_src:-}" && -f "$jar_src" ]] || { err "未找到 xxl-job-admin JAR"; return 1; }

  mkdir -p "${BUILD_DIR}/backend"
  local xxl_out="${BUILD_DIR}/backend/${XXL_JAR_NAME}"
  local xxl_ver_out="${BUILD_DIR}/backend/xxl-job-admin-3.4.1.jar"
  if [[ "$jar_src" != "$xxl_out" ]]; then
    cp -f "$jar_src" "$xxl_out"
  fi
  if [[ "$jar_src" != "$xxl_ver_out" ]]; then
    cp -f "$jar_src" "$xxl_ver_out"
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${BUILD_DIR}/backend" && sha256sum "${XXL_JAR_NAME}" > "${XXL_JAR_NAME}.sha256")
  fi

  # 可选：同步到运行目录（宝塔 Java 项目 / systemd 常用）
  local install_home="${XXL_JOB_HOME:-/opt/xxl-job-admin}"
  if [[ -d "$install_home" ]] || [[ -n "${XXL_JOB_HOME:-}" ]]; then
    mkdir -p "$install_home"
    local install_jar="${install_home}/xxl-job-admin.jar"
    if [[ "$xxl_out" != "$install_jar" ]]; then
      cp -f "$xxl_out" "$install_jar"
      ok "已同步到 ${install_jar}"
    fi
  fi

  ok "xxl-job ${XXL_JAR_NAME} ($(du -h "${BUILD_DIR}/backend/${XXL_JAR_NAME}" | awk '{print $1}'))"
  ls -lh "${BUILD_DIR}/backend/"xxl-job* 2>/dev/null | sed 's/^/    /' || true
}

build_scx_boot() {
  log "  → scx-boot (skipTests)"
  (
    cd "${SCX_HOME}/scx-backend"
    mvn -pl scx-boot -am -DskipTests package
  )
  local jar_src="${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar"
  if [[ ! -f "$jar_src" ]]; then
    jar_src="$(find "${SCX_HOME}/scx-backend/scx-boot/target" -maxdepth 1 -type f -name '*.jar' \
      ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | head -n 1 || true)"
  fi
  [[ -n "${jar_src:-}" && -f "$jar_src" ]] || { err "未找到 scx-boot JAR"; return 1; }
  mkdir -p "${BUILD_DIR}/backend"
  local boot_out="${BUILD_DIR}/backend/${BOOT_JAR_NAME}"
  # 发布到 build/backend；源与目标相同则跳过（避免 set -e 下 "same file" 中断）
  if [[ "$jar_src" != "$boot_out" ]]; then
    cp -f "$jar_src" "$boot_out"
  fi
  # jar 已在 scx-boot/target/scx-boot.jar 时无需再 self-copy
  ok "后端 ${BOOT_JAR_NAME} ($(du -h "$boot_out" | awk '{print $1}'))  src=${jar_src}"
}

build_backend() {
  local with_xxl="${1:-true}"
  log "[3/4] 后端 Java 打包"
  build_scx_boot || return 1
  if [[ "$with_xxl" == "true" ]]; then
    build_xxl_job || return 1
  else
    warn "已跳过 xxl-job-admin 打包"
  fi
  log "  → 后端产物:"
  ls -lh "${BUILD_DIR}/backend/" | tail -n +2 | sed 's/^/    /'
}

build_frontend() {
  log "[4/4] 前端构建 (web-ele)"
  ensure_pnpm
  (
    cd "${SCX_HOME}/scx-fronted"
    corepack enable >/dev/null 2>&1 || true
    # 优先 frozen；失败则普通 install（首次机）
    if ! pnpm install --frozen-lockfile --registry="$REGISTRY"; then
      warn "frozen-lockfile 失败，改用 pnpm install"
      pnpm install --registry="$REGISTRY"
    fi
    pnpm build:ele
  )
  local dist="${SCX_HOME}/scx-fronted/apps/web-ele/dist"
  [[ -d "$dist" ]] || { err "前端产物不存在: $dist"; return 1; }
  rm -rf "${BUILD_DIR}/frontend/web-ele"
  mkdir -p "${BUILD_DIR}/frontend"
  cp -a "$dist" "${BUILD_DIR}/frontend/web-ele"
  ok "前端 web-ele → ${BUILD_DIR}/frontend/web-ele"
  ls -lh "${BUILD_DIR}/frontend/" | tail -n +2 | sed 's/^/    /'
}

publish_frontend() {
  local dist="${BUILD_DIR}/frontend/web-ele"
  [[ -d "$dist" ]] || { warn "无前端产物，跳过发布"; return 0; }
  log "发布前端到宝塔站点根: ${SCX_WEB_ROOT}"
  mkdir -p "$SCX_WEB_ROOT"
  # 宝塔防篡改
  find "$SCX_WEB_ROOT" -name '.user.ini' -exec chattr -i {} \; 2>/dev/null || true
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete \
      --exclude '.user.ini' \
      --exclude '.well-known' \
      "${dist}/" "${SCX_WEB_ROOT}/"
  else
    # 粗暴覆盖
    find "$SCX_WEB_ROOT" -mindepth 1 -maxdepth 1 ! -name '.user.ini' ! -name '.well-known' -exec rm -rf {} + 2>/dev/null || true
    cp -a "${dist}/." "${SCX_WEB_ROOT}/"
  fi
  ok "前端已发布: ${SCX_WEB_ROOT}"
  [[ -f "${SCX_WEB_ROOT}/index.html" ]] || warn "站点根缺少 index.html，请检查宝塔网站根目录是否指对"
}

main() {
  echo "============================================================"
  echo " SCX 一键打包（宝塔）"
  echo " Time : $(date '+%F %T')"
  echo " Home : ${SCX_HOME}"
  echo " Out  : ${BUILD_DIR}"
  echo " Web  : ${SCX_WEB_ROOT}"
  echo "============================================================"

  setup_java
  check_env

  local do_pull=1
  local do_publish=1
  local do_xxl=1
  local target="all"
  for arg in "$@"; do
    case "$arg" in
      --no-pull) do_pull=0 ;;
      --no-publish) do_publish=0 ;;
      --skip-xxl) do_xxl=0 ;;
      backend) target="backend" ;;
      frontend) target="frontend" ;;
      xxl|xxl-job) target="xxl" ;;
      -h|--help)
        sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
      *) err "未知参数: $arg"; exit 1 ;;
    esac
  done

  [[ $do_pull -eq 1 ]] && sync_code
  local backup_target="$target"
  [[ "$target" == "xxl" ]] && backup_target="backend"
  backup_old "$backup_target"
  case "$target" in
    all)
      if [[ $do_xxl -eq 1 ]]; then build_backend true; else build_backend false; fi
      build_frontend
      ;;
    backend)
      if [[ $do_xxl -eq 1 ]]; then build_backend true; else build_backend false; fi
      ;;
    xxl)
      mkdir -p "${BUILD_DIR}/backend"
      build_xxl_job
      ;;
    frontend) build_frontend ;;
  esac
  if [[ $do_publish -eq 1 && ( "$target" == "all" || "$target" == "frontend" ) ]]; then
    publish_frontend
  fi

  echo "============================================================"
  ok "打包完成"
  echo "产物目录: ${BUILD_DIR}"
  [[ -f "${BUILD_DIR}/backend/${BOOT_JAR_NAME}" ]] && echo "  scx-boot : ${BUILD_DIR}/backend/${BOOT_JAR_NAME}"
  [[ -f "${BUILD_DIR}/backend/${XXL_JAR_NAME}" ]] && echo "  xxl-job  : ${BUILD_DIR}/backend/${XXL_JAR_NAME}"
  [[ -f "${BUILD_DIR}/backend/${XXL_JAR_NAME}.sha256" ]] && echo "  xxl sha  : ${BUILD_DIR}/backend/${XXL_JAR_NAME}.sha256"
  [[ -d "${BUILD_DIR}/frontend/web-ele" ]] && echo "  前端     : ${BUILD_DIR}/frontend/web-ele"
  echo "  站点根   : ${SCX_WEB_ROOT}"
  echo ""
  echo "下一步:"
  echo "  1. 宝塔 → Java 项目管理器 → 重启 scx-boot"
  echo "     JAR: ${BUILD_DIR}/backend/${BOOT_JAR_NAME}"
  echo "  2. 宝塔 → Java 项目管理器 → 重启 xxl-job-admin（若已配置）"
  echo "     JAR: ${BUILD_DIR}/backend/${XXL_JAR_NAME}"
  echo "  3. 前端已发布到 ${SCX_WEB_ROOT}，必要时宝塔重载 Nginx"
  echo "  4. 一键全流程: bash ${SCRIPT_DIR}/03-deploy_all.sh"
  echo "============================================================"
}

main "$@"
