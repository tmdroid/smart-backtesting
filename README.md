# smart-backtester

Deterministic candle aggregation, interactive JavaFX charting, and a pluggable backtest engine with date-range
selection. The system is built in steps and reuses the Step 1 aggregator across UI and backtests.

## Features (by step)

Step 1: Universal Candle Aggregator
- Streaming CSV ingest with strict parsing and schema validation.
- Custom Timeframe type (1m/5m/1h/1d/7m/90m, etc).
- Aggregation policies (gap, ordering, duplicates, flush, validation).
- Deterministic bucket alignment and completeness handling.
- Large dataset support without materializing input.

Step 2: JavaFX Candle Chart UI
- Open CSV files and aggregate using Step 1.
- Timeframe presets + custom timeframe input.
- Zoom, pan, scroll, and crosshair with axis labels.
- Day selection with previous/next buttons.
- Export chart-only PNG snapshots.
- Background loading with a UI blocking overlay.

Step 3: Strategy Engine (pluggable)
- Strategy interface + deterministic StrategyRunner.
- Range Breakout strategy with strict close-only breakouts.
- Wick-based SL/TP/BE evaluation with worst-case intrabar order.
- Trade results and performance aggregation.

Step 4: Backtest Date Range Engine
- Period selection (custom range, month, year, month-range).
- Streaming date filtering by session timezone (America/New_York).
- Per-range and overall performance summaries.
- Strategy factories ensure clean state per range.

## Requirements

- JDK 21+
- Gradle wrapper (`./gradlew`)

## Build and test

- `./gradlew run` launches the JavaFX chart UI.
- `./gradlew test` runs all unit tests.
- `./gradlew clean` removes build outputs.

## CLI usage

Aggregate CSV to a target timeframe:
```
./gradlew run --args="--input /path/to/file.csv --tf 5m --out /path/to/out.csv --schema mnq --timestamp-format epochnanos"
```

Backtest with Range Breakout strategy:
```
./gradlew run --args="--backtest --input /path/to/file.csv --tf 5m --month 2025-11 --session-start 09:30 --session-end 10:30 --sl 10 --tp 20 --be 5 --schema mnq --timestamp-format epochnanos"
```

Backtest period flags:
- `--start-date YYYY-MM-DD --end-date YYYY-MM-DD`
- `--month YYYY-MM`
- `--year YYYY`
- `--month-range YYYY-MM:YYYY-MM`

## UI usage

1) Run `./gradlew run`.
2) Open a CSV file (auto-selects `data/MNQ/mnq-history/mnq1_continuous.ohlcv-1m.csv` if present).
3) Choose a timeframe and optional custom strategy parameters:
   - Session start/end (NY time)
   - SL/TP/BE points
4) Use zoom/pan/scroll to inspect candles.
5) Export a chart-only PNG.

The overlay shows:
- Orange range box spanning the session window through breakout + 5 candles.
- Red/green risk-reward box extending until SL/TP/BE resolution (worst-case order).

## CSV format and schema

Default schema (header required):
- `timestamp,open,high,low,close,volume`

MNQ schema preset (`--schema mnq`):
- Timestamp column: `ts_event`
- Optional columns: `symbol`, `source_symbol`

Timestamp formats:
- `iso` (ISO-8601 UTC with zone/offset)
- `epochmillis`
- `epochnanos`

All session logic and UI time axis use America/New_York.

## Caching

Per-day binary caches and CSV byte-range indices are stored under:
- `data/.../.candle-cache`

Safe to delete when you want a full rebuild.

## Project layout

- `app/` main application (UI, aggregation, strategy, backtest)
- `buildSrc/` shared Gradle convention plugins

## Notes

This repo uses the Gradle Wrapper, a version catalog (`gradle/libs.versions.toml`), and configuration cache
(`gradle.properties`) to keep builds reproducible and fast.
