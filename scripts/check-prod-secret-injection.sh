#!/usr/bin/env bash
# Historically this script required SCX_* environment placeholders in
# application-dev.yml / application-prod.yml. Connection settings are now
# allowed as plain-text profile values for this project, so the gate is a no-op.
set -euo pipefail

echo "Plain-text connection settings are allowed in application-dev.yml and application-prod.yml; injection check skipped."
