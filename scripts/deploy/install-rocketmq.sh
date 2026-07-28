#!/usr/bin/env bash
# Install the pinned, locally supplied RocketMQ binary and local-only systemd units.
set -euo pipefail

ROCKETMQ_VERSION="5.3.1"
ROCKETMQ_SHA256="251d7261fa26d35eaffef6a2fce30880054af7a5883d578dd31574bf908a8b97"
ROCKETMQ_HOME="/opt/rocketmq-${ROCKETMQ_VERSION}"
ROCKETMQ_LINK="/opt/rocketmq"
ROCKETMQ_NAMESRV_CONFIG="/etc/rocketmq/namesrv.conf"
ROCKETMQ_CONFIG="/etc/rocketmq/broker.conf"
ROCKETMQ_TOPIC="scx_tracking_events"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE="${1:-}"
START_SERVICES="${ROCKETMQ_START_SERVICES:-false}"
FORCE_CONFIG="${ROCKETMQ_FORCE_CONFIG:-false}"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

for arg in "$@"; do
  case "$arg" in
    --start) START_SERVICES=true ;;
    --no-start) START_SERVICES=false ;;
    --force-config) FORCE_CONFIG=true ;;
    --help|-h)
      cat <<'EOF'
用法: install-rocketmq.sh /path/to/rocketmq-all-5.3.1-bin-release.zip [--start] [--force-config]

默认只校验并安装固定版本的二进制、配置和 systemd unit，不启动或 enable 服务。
生产恢复窗口必须由授权操作人显式传入 --start；Publisher/Scheduler 仍需单独开启。
--force-config 只在已完成配置差异复核后使用，旧配置会保留为 .bak.<timestamp>。
EOF
      exit 0
      ;;
    --*) die "未知参数: $arg" ;;
    *) : ;;
  esac
done

[[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行"
[[ -n "$ARCHIVE" && -f "$ARCHIVE" ]] || die "用法: $0 /path/to/rocketmq-all-5.3.1-bin-release.zip [--start]"
[[ "${SCX_TRACKING_OUTBOX_TOPIC:-scx_tracking_events}" == "$ROCKETMQ_TOPIC" ]] \
  || die "仅允许创建固定 Topic ${ROCKETMQ_TOPIC}，禁止覆盖 SCX_TRACKING_OUTBOX_TOPIC"
command -v sha256sum >/dev/null 2>&1 || die "缺少 sha256sum"
command -v unzip >/dev/null 2>&1 || die "缺少 unzip"
command -v java >/dev/null 2>&1 || die "缺少 Java"

actual_sha="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
[[ "$actual_sha" == "$ROCKETMQ_SHA256" ]] \
  || die "RocketMQ 制品 SHA-256 不匹配，拒绝安装"

if ! id rocketmq >/dev/null 2>&1; then
  useradd --system --home-dir /var/lib/rocketmq --shell /usr/sbin/nologin rocketmq
fi
install -d -o rocketmq -g rocketmq -m 0750 /var/lib/rocketmq /var/lib/rocketmq/store /var/log/rocketmq
install -d -o root -g rocketmq -m 0750 /etc/rocketmq

if [[ ! -e "$ROCKETMQ_HOME" && ! -L "$ROCKETMQ_HOME" ]]; then
  extract_dir="$(mktemp -d /tmp/scx-rocketmq-install.XXXXXX)"
  cleanup() {
    case "$extract_dir" in
      /tmp/scx-rocketmq-install.*) rm -rf -- "$extract_dir" ;;
    esac
  }
  trap cleanup EXIT
  unzip -q "$ARCHIVE" -d "$extract_dir"
  extracted="$extract_dir/rocketmq-all-${ROCKETMQ_VERSION}-bin-release"
  [[ -x "$extracted/bin/mqnamesrv" ]] || die "RocketMQ 解压结构不符合预期"
  mv "$extracted" "$ROCKETMQ_HOME"
  chown -R root:rocketmq "$ROCKETMQ_HOME"
  find "$ROCKETMQ_HOME" -type d -exec chmod 0750 {} +
  find "$ROCKETMQ_HOME" -type f -exec chmod 0640 {} +
  chmod 0750 "$ROCKETMQ_HOME"/bin/*
fi
[[ -x "$ROCKETMQ_HOME/bin/mqnamesrv" && -x "$ROCKETMQ_HOME/bin/mqbroker" ]] \
  || die "RocketMQ 安装目录不完整: $ROCKETMQ_HOME"
ln -sfn "$ROCKETMQ_HOME" "$ROCKETMQ_LINK"

umask 027
namesrv_tmp="$(mktemp /tmp/scx-rocketmq-namesrv.XXXXXX)"
{
  echo "listenPort=9876"
  echo "bindAddress=127.0.0.1"
} > "$namesrv_tmp"
if [[ -f "$ROCKETMQ_NAMESRV_CONFIG" && "$FORCE_CONFIG" != true ]]; then
  cmp -s "$namesrv_tmp" "$ROCKETMQ_NAMESRV_CONFIG" \
    || die "${ROCKETMQ_NAMESRV_CONFIG} 已存在且内容不同；完成差异复核后传入 --force-config"
else
  if [[ -f "$ROCKETMQ_NAMESRV_CONFIG" ]]; then
    cp -p "$ROCKETMQ_NAMESRV_CONFIG" "${ROCKETMQ_NAMESRV_CONFIG}.bak.$(date +%s)"
  fi
  install -m 0640 -o root -g rocketmq "$namesrv_tmp" "$ROCKETMQ_NAMESRV_CONFIG"
fi
rm -f "$namesrv_tmp"
chown root:rocketmq "$ROCKETMQ_NAMESRV_CONFIG"
chmod 0640 "$ROCKETMQ_NAMESRV_CONFIG"

broker_tmp="$(mktemp /tmp/scx-rocketmq-broker.XXXXXX)"
{
  echo "brokerClusterName=SCXLocalCluster"
  echo "brokerName=scx-broker-a"
  echo "brokerId=0"
  echo "namesrvAddr=127.0.0.1:9876"
  echo "brokerIP1=127.0.0.1"
  echo "bindAddress=127.0.0.1"
  echo "listenPort=10911"
  echo "autoCreateTopicEnable=false"
  echo "autoCreateSubscriptionGroup=false"
  echo "storePathRootDir=/var/lib/rocketmq/store"
  echo "storePathCommitLog=/var/lib/rocketmq/store/commitlog"
  echo "deleteWhen=04"
  echo "fileReservedTime=72"
  echo "brokerRole=ASYNC_MASTER"
  echo "flushDiskType=ASYNC_FLUSH"
} > "$broker_tmp"
if [[ -f "$ROCKETMQ_CONFIG" && "$FORCE_CONFIG" != true ]]; then
  cmp -s "$broker_tmp" "$ROCKETMQ_CONFIG" \
    || die "${ROCKETMQ_CONFIG} 已存在且内容不同；完成差异复核后传入 --force-config"
else
  if [[ -f "$ROCKETMQ_CONFIG" ]]; then
    cp -p "$ROCKETMQ_CONFIG" "${ROCKETMQ_CONFIG}.bak.$(date +%s)"
  fi
  install -m 0640 -o root -g rocketmq "$broker_tmp" "$ROCKETMQ_CONFIG"
fi
rm -f "$broker_tmp"
chown root:rocketmq "$ROCKETMQ_CONFIG"
chmod 0640 "$ROCKETMQ_CONFIG"

install -m 0644 "$SCRIPT_DIR/rocketmq-namesrv.service" /etc/systemd/system/rocketmq-namesrv.service
install -m 0644 "$SCRIPT_DIR/rocketmq-broker.service" /etc/systemd/system/rocketmq-broker.service
systemctl daemon-reload
if [[ "$START_SERVICES" == true ]]; then
  systemctl enable rocketmq-namesrv rocketmq-broker
  systemctl restart rocketmq-namesrv
  for _ in $(seq 1 30); do
    if timeout 1 bash -c '</dev/tcp/127.0.0.1/9876' 2>/dev/null; then break; fi
    sleep 1
  done
  timeout 1 bash -c '</dev/tcp/127.0.0.1/9876' 2>/dev/null \
    || die "RocketMQ NameServer 未监听 127.0.0.1:9876"
  systemctl restart rocketmq-broker
  for _ in $(seq 1 40); do
    if "$ROCKETMQ_LINK/bin/mqadmin" clusterList -n 127.0.0.1:9876 2>/dev/null \
      | grep -q SCXLocalCluster; then break; fi
    sleep 2
  done
  "$ROCKETMQ_LINK/bin/mqadmin" clusterList -n 127.0.0.1:9876 \
    | grep -q SCXLocalCluster || die "RocketMQ Broker 未注册"
  "$ROCKETMQ_LINK/bin/mqadmin" updateTopic \
    -n 127.0.0.1:9876 -c SCXLocalCluster -t "$ROCKETMQ_TOPIC" -r 8 -w 8 >/dev/null
  "$ROCKETMQ_LINK/bin/mqadmin" topicStatus \
    -n 127.0.0.1:9876 -t "$ROCKETMQ_TOPIC" >/dev/null
  log "RocketMQ ${ROCKETMQ_VERSION} 已安装并创建 Topic ${ROCKETMQ_TOPIC}"
else
  log "RocketMQ ${ROCKETMQ_VERSION} 已安装；默认不启动/enable 服务，也不会创建 Topic"
  log "恢复窗口验证命令: $0 $ARCHIVE --start"
fi
log "NameServer/Broker 由 systemd 与 127.0.0.1 bind 限制为仅本机网络访问"
