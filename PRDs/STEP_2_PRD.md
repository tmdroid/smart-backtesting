# STEP 2 PRD — Interactive Candle Chart UI (JavaFX + Kotlin)

## Purpose

Build a **desktop interactive candlestick chart application** using **JavaFX (Kotlin)** that allows visual inspection of historical futures data.

This UI is a **visual/debugging companion** to Step 1.  
It must reuse the Step‑1 candle aggregation code as-is and must not duplicate aggregation logic.

---

## Scope & Non‑Goals

### In Scope
- Desktop JavaFX application
- Load 1‑minute OHLCV CSV files
- Aggregate candles using **Step 1 CandleAggregator**
- Render interactive candlestick charts
- Switch aggregation timeframes dynamically
- Zoom / pan / scroll the chart
- Snapshot chart to PNG

### Explicit Non‑Goals
- No strategy logic
- No order simulation
- No indicators beyond raw OHLC candles (MA/EMA/etc. are Step 3+)
- No real‑time streaming
- No web / browser UI
- No TradingView embedding

---

## Dependencies

- Kotlin JVM
- JavaFX (OpenJFX)
- Step 1 aggregation module (imported as library/module)
- Standard JavaFX APIs only (no Swing UI)

---

## Core User Stories

### US‑1 Load Candle File
As a user, I want to:
- Click “Open File”
- Select a CSV file containing 1‑minute candles
- See the chart rendered automatically

### US‑2 Select Timeframe
As a user, I want to:
- Select a timeframe (1m, 5m, 15m, 1h, custom)
- See the chart re‑render using aggregated candles
- Have aggregation performed via Step 1 code

### US‑3 Inspect Chart
As a user, I want to:
- Zoom in/out
- Pan left/right
- Scroll through history
- Visually inspect price action

### US‑4 Export Snapshot
As a user, I want to:
- Export the current chart view to a PNG file

---

## Functional Requirements

### FR‑1 Application Structure
- JavaFX `Application` entry point
- Single main window
- Responsive layout (resizes cleanly)

---

### FR‑2 File Loading
- File picker dialog (`FileChooser`)
- Accept CSV files only
- On load:
  - Use `CsvCandleSource` from Step 1
  - Assume source timeframe = 1m
  - Do not pre‑aggregate until timeframe selected

Error handling:
- Parsing errors displayed in modal alert
- No crash on malformed file

---

### FR‑3 Timeframe Selection
- Timeframe selector UI element:
  - Dropdown (ComboBox)
  - Presets: 1m, 5m, 15m, 1h
  - Text field for custom timeframe (e.g., 7m, 90m)
- On change:
  - Re‑aggregate candles using Step 1 aggregator
  - Replace chart data (not incremental update)

Validation:
- Invalid timeframe strings show user error
- Non‑divisible + DROP policy must surface meaningful error

---

### FR‑4 Aggregation Integration
- Must call Step 1 APIs:
  - `CsvCandleSource`
  - `Timeframe`
  - `CandleAggregator` or `aggregate(...)`
- No duplicate aggregation logic in Step 2
- Aggregation runs off the JavaFX UI thread

---

### FR‑5 Chart Rendering
- Use JavaFX scene graph
- Candlestick chart implementation:
  - Each candle drawn as body + wick
  - Color:
    - Green if close ≥ open
    - Red if close < open
- X‑axis:
  - Time (Instant → epoch millis)
- Y‑axis:
  - Price (linear scale)
- Volume:
  - Optional overlay or lower pane (nice‑to‑have)

---

### FR‑6 Interactivity
- Zoom:
  - Mouse wheel zooms horizontally (time axis)
- Pan:
  - Click + drag pans chart left/right
- Scroll:
  - Scroll bar or drag‑based navigation
- Performance:
  - Must handle ≥100k 1m candles aggregated to ≥10k candles smoothly

---

### FR‑7 Snapshot Export
- Button “Export PNG”
- Snapshot current chart view:
  - `Scene.snapshot(...)`
- Save using `FileChooser`
- PNG output only

---

## UI Layout (Conceptual)

```
+---------------------------------------------------+
| File | Timeframe | Export PNG                     |
+---------------------------------------------------+
|                                                   |
|            Candlestick Chart Area                 |
|                                                   |
+---------------------------------------------------+
| (optional) volume bars / status bar               |
+---------------------------------------------------+
```

---

## Architecture & Separation

### Packages
- ui/
  - MainApp
  - MainView
  - ChartView
  - ControlsPane
- chart/
  - CandleStickChart (custom JavaFX Node)
  - CandleNode (body + wick)
- integration/
  - AggregationService (wraps Step 1 calls, background threads)
- util/
  - TimeAxisUtils
  - ZoomPanHandler

---

## Threading Model

- JavaFX Application Thread:
  - UI updates only
- Background thread (Executor / Coroutine):
  - CSV loading
  - Aggregation
- UI updated via `Platform.runLater`

---

## Error Handling & UX

- All errors shown as JavaFX `Alert`
- Recoverable errors do not close app
- Loading indicator shown during aggregation

---

## Performance Requirements

- Initial load under 2 seconds for typical MNQ CSV
- Timeframe switching under 1 second for moderate datasets
- No UI freezes during aggregation

---

## Testing Strategy (Step 2)

- Unit tests:
  - Timeframe parsing (reuse Step 1 tests)
  - AggregationService logic (mock UI)
- Manual testing:
  - Visual correctness
  - Zoom/pan behavior
  - Snapshot export

(No automated UI testing required in Step 2.)

---

## Acceptance Criteria

Step 2 is complete when:
- User can load a 1m CSV file
- User can switch timeframes and see chart update
- Chart is interactive (zoom/pan)
- Snapshot export produces valid PNG
- Step 1 code is reused without modification
- Application is stable and responsive

---

## Future Extensions (Out of Scope)

- Indicators (MA, VWAP, RSI)
- Trade markers / annotations
- Strategy overlays
- Multi‑pane charts
- Session highlighting (RTH/ETH)
- Real‑time streaming

