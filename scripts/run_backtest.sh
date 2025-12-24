#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

ARGS="$(cat <<'EOF'
--backtest
--input /Users/mac/IdeaProjects/smart-backtester/data/MNQ/mnq-history/mnq1_continuous.ohlcv-1m.csv
--tf 5m
--month-range 2025-06:2025-11
--session-start 03:00
--session-end 03:15
--sl 40
--tp 35
--be 25
--schema mnq
--timestamp-format epochnanos
EOF
)"
ARGS="$(echo "$ARGS" | tr '\n' ' ' | xargs)"

./gradlew run --no-configuration-cache --args="$ARGS"
