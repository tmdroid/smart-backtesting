# STEP 4 PRD — Backtest Date Range & Period Selection Engine

## Purpose

Step 4 introduces a **date-range and period selection layer** on top of the Step 3 strategy engine.

The goal is to allow you to:
- Run backtests on **arbitrary date ranges** (e.g. 2023-11-01 → 2023-12-13)
- Run backtests on **predefined periods** (months, years)
- Combine **multiple disjoint ranges** in a single run
- Produce **aggregated performance** across selected ranges

This step remains **engine-only** (no UI required).

---

## Scope & Non-Goals

### In Scope
- Date-range filtering of candle streams
- Period abstractions (date ranges, months, years)
- Backtest orchestration across one or more ranges
- Aggregated performance reporting per range and overall
- Reuse of Step 3 StrategyRunner and strategies

### Explicit Non-Goals
- Strategy logic changes
- Optimization / parameter sweeps
- UI controls or visualization
- Partial-day slicing (handled by strategies)
- Data loading changes (still CSV via Step 1)

---

## Dependencies

- Step 1: CandleSource, Candle
- Step 3: Strategy, StrategyRunner, TradeResult, PerformanceAggregator
- Java/Kotlin time APIs

---

## Core Concepts

### DateRange

Represents an inclusive range of trading dates.

```kotlin
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate
)
```

Rules:
- start <= endInclusive
- Inclusive on both ends
- Uses candle.start converted to backtest timezone

---

### Period

Reusable time selection abstraction.

```kotlin
sealed interface Period {
    fun toDateRanges(): List<DateRange>
}
```

Concrete implementations:
- CustomDateRange(start, end)
- SingleMonth(year, month)
- SingleYear(year)
- MonthRange(startYearMonth, endYearMonth)

---

## BacktestRun

```kotlin
data class BacktestRun(
    val periods: List<Period>,
    val strategies: List<Strategy>,
    val timezone: ZoneId
)
```

Responsibilities:
- Resolve periods → DateRanges
- Deterministic ordering
- Orchestrate execution

---

## BacktestExecutor

```kotlin
class BacktestExecutor(
    val candleSourceFactory: (DateRange) -> Sequence<Candle>
) {
    fun run(backtestRun: BacktestRun): BacktestResult
}
```

Rules:
- Each DateRange executed independently
- Strategies reset per DateRange
- Results aggregated after all ranges

---

## Candle Filtering Semantics

- Candle included if:
  - candle.start converted to timezone → LocalDate
  - LocalDate ∈ [DateRange.start, DateRange.endInclusive]
- Filtering is streaming (no materialization)

---

## Result Model

### BacktestResult

```kotlin
data class BacktestResult(
    val rangeResults: List<RangeBacktestResult>,
    val overallPerformance: PerformanceSummary
)
```

### RangeBacktestResult

```kotlin
data class RangeBacktestResult(
    val dateRange: DateRange,
    val performance: PerformanceSummary
)
```

---

## Execution Semantics

- Resolve periods first
- Sort DateRanges chronologically
- For each DateRange:
  1. Fresh Strategy instances
  2. Filter candle stream
  3. Run StrategyRunner
  4. Aggregate results
- Overall performance = sum of per-range summaries

---

## Edge Cases

- Empty DateRange → zero trades
- Overlapping ranges → allowed (user responsibility)
- DST handled via timezone-aware conversion
- Partial trading days allowed

---

## Testing Requirements

- DateRange validation tests
- Period expansion tests
- Candle filtering correctness
- Strategy reset between ranges
- Aggregation correctness

---

## Performance Requirements

- Streaming filtering
- Multi-year datasets without OOM

---

## Acceptance Criteria

- Arbitrary ranges supported
- Months/years supported
- Deterministic results
- Correct aggregation

---

## Future Extensions

- Rolling windows
- Walk-forward analysis
- Optimization
