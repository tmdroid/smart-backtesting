#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

ARGS="$(cat <<'EOF'
--backtest
--input /Users/mac/IdeaProjects/smart-backtester/data/MNQ/mnq-history/mnq1_continuous.ohlcv-1m.csv
--tf 5m
--month-range 2025-06:2025-12
--session 03:00-03:15
--session 09:30-09:45
--session-risk 40,35,25
--session-risk 40,45,25
--schema mnq
--timestamp-format epochnanos
EOF
)"
ARGS="$(echo "$ARGS" | tr '\n' ' ' | xargs)"

./gradlew -q run --no-configuration-cache --args="$ARGS"
