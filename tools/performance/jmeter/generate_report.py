#!/usr/bin/env python3
import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path


def key_values(path):
    values = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def integer(values, key, default=-1):
    try:
        return int(values.get(key, default))
    except (TypeError, ValueError):
        return default


def memory_mib(value):
    match = re.fullmatch(r"\s*([0-9.]+)([KMG]iB)\s*", value)
    if not match:
        return 0.0
    amount = float(match.group(1))
    return amount * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024}[match.group(2)]


def resource_summary(path):
    result = {"engine_rss": 0.0, "engine_cpu": None, "jmeter_rss": None, "jmeter_cpu": None, "docker": {}}
    if not path.exists():
        return result
    with path.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source, delimiter="\t"):
            result["engine_rss"] = max(result["engine_rss"], float(row.get("engine_rss_kb") or 0) / 1024)
            for source_key, target_key, divisor in (
                ("engine_cpu_pct", "engine_cpu", 1),
                ("jmeter_rss_kb", "jmeter_rss", 1024),
                ("jmeter_cpu_pct", "jmeter_cpu", 1),
            ):
                if source_key in row:
                    value = float(row.get(source_key) or 0) / divisor
                    current = result[target_key]
                    result[target_key] = max(current or 0, value)
            for name, cpu, used in re.findall(r"([^=;]+)=([0-9.]+)%/([^/;]+)\s*/[^;]+", row.get("docker_stats", "")):
                current = result["docker"].setdefault(name, {"cpu": 0.0, "memory": 0.0})
                current["cpu"] = max(current["cpu"], float(cpu))
                current["memory"] = max(current["memory"], memory_mib(used))
    return result


def status_text(statuses):
    return "; ".join(f"{code}={count}" for code, count in sorted(statuses.items()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("result_dir")
    args = parser.parse_args()
    root = Path(args.result_dir)
    rows = []
    incomplete = []
    stage_dirs = sorted(root.glob("feedback-qps*"), key=lambda item: int(item.name.split("qps", 1)[1]))
    for stage_dir in stage_dirs:
        summary_path = stage_dir / "summary.json"
        if not summary_path.exists():
            incomplete.append(int(stage_dir.name.split("qps", 1)[1]))
            continue
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        stage = key_values(stage_dir / "stage.txt")
        reconcile = key_values(stage_dir / "reconcile.txt")
        requests = int(summary["requests"])
        accepted = int(summary.get("statuses", {}).get("202", 0))
        facts = integer(reconcile, "feedback_facts")
        dlq = integer(reconcile, "ingress_dlq")
        passed = (
            int(summary.get("failures", 0)) == 0
            and accepted == requests
            and facts >= 0
            and dlq >= 0
            and facts + dlq == accepted
            and integer(reconcile, "redis_pending") == 0
            and integer(reconcile, "redis_lag") == 0
        )
        rows.append({
            "stage": stage,
            "summary": summary,
            "reconcile": reconcile,
            "resources": resource_summary(stage_dir / "resources.tsv"),
            "passed": passed,
            "accepted": accepted,
        })

    passed_rows = [row for row in rows if row["passed"]]
    highest = max((int(row["stage"]["target_qps"]) for row in passed_rows), default=0)
    reached_20k = any(int(row["stage"]["target_qps"]) == 20000 and row["passed"] for row in rows)
    failed_rows = [row for row in rows if not row["passed"]]
    first_failed = min((int(row["stage"]["target_qps"]) for row in failed_rows), default=None)

    lines = [
        "# SCX OCPX 本地 JMeter 压测报告",
        "",
        "## 结论",
        "",
        f"- 本次严格通过的最高本地档位：{highest} QPS。",
        f"- 首个失败档位：{first_failed if first_failed is not None else '无'} QPS；失败档及其后的档位不计入通过结论。",
        f"- 20,000 QPS 60 秒本地突发：{'达到' if reached_20k else '未达到或未执行'}。",
        "- 口径仅适用于本地 Docker 4 CPU / 约 8 GB 隔离环境；不构成生产 20,000 QPS 验收。",
        "- 不丢数结论只覆盖 HTTP 已明确受理的唯一事件，并要求 MySQL 事实 + 接入 DLQ 精确闭合且 Redis pending/lag 最终为 0。",
    ]
    if incomplete:
        lines.append(f"- 未完成档位：{', '.join(map(str, incomplete))} QPS（中止数据，不纳入统计）。")
    lines.extend([
        "",
        "## Feedback 分档",
        "",
        "| 结论 | 目标 QPS | 时长(s) | 请求状态分解 | 实测 QPS | p50 | p95 | p99 | 最大 pending | 最大 lag | 排空(s) | 事实+DLQ / 202 |",
        "|:---:|---:|---:|:---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ])
    for row in rows:
        stage = row["stage"]
        summary = row["summary"]
        reconcile = row["reconcile"]
        facts = integer(reconcile, "feedback_facts", 0)
        dlq = integer(reconcile, "ingress_dlq", 0)
        lines.append(
            f"| {'PASS' if row['passed'] else 'FAIL'} | {stage['target_qps']} | {stage['duration_seconds']} | "
            f"{status_text(summary.get('statuses', {}))} | {summary['measuredQps']} | {summary['p50Ms']} ms | "
            f"{summary['p95Ms']} ms | {summary['p99Ms']} ms | {stage['max_pending']} | {stage['max_lag']} | "
            f"{stage['drain_seconds']} | {facts + dlq} / {row['accepted']} |"
        )

    lines.extend(["", "## 资源峰值", "", "| 目标 QPS | Engine RSS | Engine CPU | JMeter RSS | JMeter CPU | MySQL CPU/内存 | Redis 主 CPU/内存 | Redis 副 CPU/内存 |", "|---:|---:|---:|---:|---:|---:|---:|---:|"])
    for row in rows:
        stage = row["stage"]
        resources = row["resources"]
        docker = resources["docker"]
        mysql = docker.get("scx-ocpx-jmeter-mysql-1", {})
        primary = docker.get("scx-ocpx-jmeter-redis-primary-1", {})
        replica = docker.get("scx-ocpx-jmeter-redis-replica-1", {})
        measured = lambda value, suffix: "未采样" if value is None else f"{value:.1f}{suffix}"
        service = lambda item: "未采样" if not item else f"{item['cpu']:.1f}% / {item['memory']:.1f} MiB"
        lines.append(
            f"| {stage['target_qps']} | {resources['engine_rss']:.1f} MiB | {measured(resources['engine_cpu'], '%')} | "
            f"{measured(resources['jmeter_rss'], ' MiB')} | {measured(resources['jmeter_cpu'], '%')} | "
            f"{service(mysql)} | {service(primary)} | {service(replica)} |"
        )

    functional_path = root / "functional-results.json"
    functional_statuses = Counter()
    if functional_path.exists():
        functional = json.loads(functional_path.read_text(encoding="utf-8"))
        functional_statuses.update(str(item["actual"]) for item in functional.get("results", []))
    callback = key_values(root / "callback-qps100" / "callback-reconcile.txt")
    faults = key_values(root / "fault-results.txt")
    lines.extend([
        "",
        "## 功能、故障与 Callback 对账",
        "",
        f"- 功能请求状态：{status_text(functional_statuses)}；覆盖 Android/iOS 字段矩阵、同 ID 200、载荷冲突 409、设备窗口并发竞争、窗口过期和无设备降级。",
        f"- 故障注入：副本不足={faults.get('redis_replica_unavailable_http', '?')}，主 Redis 中断={faults.get('redis_primary_unavailable_http', '?')}，Pending 接管排空={faults.get('restart_pending_drain_seconds', '?')} 秒，非法消息 DLQ={faults.get('invalid_envelope_dlq', '?')}。",
        f"- Callback：事实={callback.get('callback_facts', '?')}，扣量决策={callback.get('deduction_decisions', '?')}，下游任务={callback.get('downstream_jobs', '?')}，唯一幂等键={callback.get('downstream_unique_keys', '?')}，HTTP 尝试={callback.get('downstream_attempts', '?')}，最终坏状态={callback.get('callback_bad_status', '?')}。",
    ])
    if first_failed is not None:
        failed = next(row for row in rows if int(row["stage"]["target_qps"]) == first_failed)
        summary = failed["summary"]
        facts = integer(failed["reconcile"], "feedback_facts", 0)
        dlq = integer(failed["reconcile"], "ingress_dlq", 0)
        lines.append(
            f"- {first_failed} QPS 失败原因：{status_text(summary.get('statuses', {}))}；明确 202={failed['accepted']}，但事实+DLQ={facts + dlq}，存在 {facts + dlq - failed['accepted']} 条未明确受理后仍落库的写入，且 failures={summary.get('failures', 0)}。"
        )
    lines.extend([
        "",
        "原始证据见 `functional-results.json`、`functional-reconcile.txt`、`fault-results.txt`、`callback-qps100/callback-reconcile.txt`、各档 `summary.json`、`resources.tsv`、`reconcile.txt` 和 JTL。",
        "",
        "## 正式容量验收边界",
        "",
        "正式 20,000 QPS 仍需独立压测机、至少两台数据面实例、分布式 JMeter、持续 30 分钟、实例故障接管及真实第三方 UAT。",
        "",
    ])
    (root / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
