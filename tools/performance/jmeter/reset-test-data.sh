#!/usr/bin/env bash
set -euo pipefail

MYSQL_PWD=local-jmeter-root-only mysql --host=127.0.0.1 --port=13316 --user=root smart_converge_x <<'SQL'
DELETE FROM scx_ocpx_delivery_attempt;
DELETE FROM scx_ocpx_delivery_job;
DELETE FROM scx_ocpx_deduction_decision;
DELETE FROM scx_ocpx_billing_entry;
DELETE FROM scx_ocpx_protocol_snapshot;
DELETE FROM scx_ocpx_callback_ref;
DELETE FROM scx_ocpx_click_dedup_decision;
DELETE FROM scx_ocpx_click_dedup_state;
DELETE FROM scx_ocpx_ingress_dlq;
DELETE FROM scx_ocpx_ingress_audit;
DELETE FROM scx_ocpx_event;
DELETE FROM scx_adx_hourly_report;
SQL
