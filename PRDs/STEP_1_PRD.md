# STEP 1 PRD — Universal Candle Aggregator (Kotlin)

## Purpose

Build a **correct, streaming, fully tested candle aggregation module** that converts
1-minute OHLCV candles into arbitrary higher-timeframe candles for use in a trading backtesting system.

This module is **foundational**. All later strategy logic, optimizers, and execution simulators
depend on its correctness.

---

## Non-Goals (Step 1 Scope Guardrails)

This step explicitly does **NOT** include:
- Any strategy or backtesting logic
- Any trading rules
- Session / RTH / ETH handling
- Parquet input support
- Real-time ingestion

Step 1 is **offline batch aggregation only**, with CSV input.

---

## Functional Requirements

### FR-1 Candle Model
The candle model represents a **time interval**, not a semantic timeframe.

```kotlin
data class Candle(
    val start: Instant,
    val endExclusive: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
```

- No timeframe field is allowed.
- Timeframe must be derived from `endExclusive - start`.

---

### FR-2 Timeframe Domain Type
A strict `Timeframe` type must be implemented.

Requirements:
- Backed by `java.time.Duration`
- No raw `Duration` exposed in public APIs
- Parse from strings like: `1m`, `5m`, `15m`, `1h`, `2h`, `1d`
- Supported units: minutes (m), hours (h), days (d)
- Must reject invalid or zero values

Helpers:
- `millis`
- `isMultipleOf(other)`

Days are **fixed 24h durations** in Step 1.

---

### FR-3 Input Abstraction
Data must be read via a streaming abstraction.

```kotlin
interface CandleSource {
    fun stream(): Sequence<Candle>
}
```

Step 1 implementation:
- CSV file source
- Line-by-line streaming (no full file load)
- Configurable column schema
- Timestamp format:
  - ISO-8601 UTC (default)
  - epoch millis (configurable)

---

### FR-4 Aggregation Semantics

Aggregation rules:
- Open = first candle in bucket
- High = max high
- Low = min low
- Close = last candle
- Volume = sum (Long)

Buckets:
- Aligned by flooring timestamps to timeframe boundaries
- Default alignment: UTC

---

### FR-5 Aggregation API

#### Stateless
```kotlin
fun aggregate(
    source: Sequence<Candle>,
    targetTimeframe: Timeframe,
    policy: AggregationPolicy = default
): Sequence<Candle>
```

#### Stateful
```kotlin
class CandleAggregator(
    val sourceTimeframe: Timeframe,
    val targetTimeframe: Timeframe,
    val policy: AggregationPolicy = default
) {
    fun onCandle(candle: Candle): Candle?
    fun flush(): Candle?
}
```

- `onCandle` emits a completed candle when a bucket closes
- `flush` finalizes the last bucket based on policy

---

## Policy Requirements

### GapPolicy
- `KEEP_PARTIAL` (default)
- `DROP_BUCKET_IF_INCOMPLETE`

Completeness is defined as:
```
targetTimeframe / sourceTimeframe candles present
```
when divisible.

---

### OrderingPolicy
- `REJECT` (default)
  - Input candles must be strictly increasing by start time

---

### DuplicatePolicy
- `REJECT` (default)
  - Duplicate timestamps are an error

---

### FlushPolicy
- `EMIT_PARTIAL` (default)
- `DROP_PARTIAL`

---

### ValidationPolicy
- `STRICT` (default)
  - high ≥ max(open, close)
  - low ≤ min(open, close)
  - high ≥ low
- Fail fast on violations

---

## Edge Cases (Must Be Correct)

- Missing minutes
- Partial final bucket
- Day rollover at UTC midnight
- Non-standard timeframes (e.g. 7m, 90m)
- Large datasets (≥100k candles)
- Volume overflow safety (Long)

---

## Testing Requirements

- Use JUnit 5
- use Kotest assertions
- use MockK
- Explicit expected-value assertions
- No property-based fuzzing in Step 1

### Mandatory Test Coverage
- 1m → 5m, 15m, 1h, 4h, 1d
- 1m → 7m
- 1m → 3m
- Gaps with each GapPolicy
- Partial final bucket with each FlushPolicy
- Ordering violations
- Duplicate timestamp violations
- Large dataset aggregation sanity test

Fixtures must be readable and deterministic.

---

## CLI (Minimal)

Provide a minimal CLI for validation:

```
--input file.csv
--tf 5m
--out aggregated.csv (optional)
```

CLI exists for smoke testing only.

---

## Non-Functional Requirements

- Deterministic output
- Streaming (O(1) memory per bucket)
- Clear error messages
- Clean, readable Kotlin
- No unnecessary abstractions

---

## Acceptance Criteria

Step 1 is complete when:
- All tests pass
- Aggregation output matches expected candles exactly
- Large datasets do not exhaust memory
- Code can be cleanly extended in Step 2

