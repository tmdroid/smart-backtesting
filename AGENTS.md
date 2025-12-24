# Repository Guidelines

## Project Structure & Module Organization
- `app/` contains all production Kotlin code for aggregation, UI, strategy engine, and backtesting.
- `app/src/main/kotlin/` is the main source tree (packages under `org.example.candles.*`).
- `app/src/test/kotlin/` hosts JUnit 5 tests.
- `app/src/test/resources/fixtures/` contains small CSV fixtures for deterministic tests.
- `data/` holds local datasets (not committed) and `.candle-cache` binary caches.
- `buildSrc/` contains Gradle convention plugins.

## Build, Test, and Development Commands
- `./gradlew run` launches the JavaFX UI.
- `./gradlew run --args="--backtest ..."` runs the CLI backtester.
- `./gradlew test` runs all unit tests.
- `./gradlew clean` removes build artifacts.
- `./scripts/run_backtest.sh` runs a multi-session backtest with preset arguments.

## Coding Style & Naming Conventions
- Kotlin with 4-space indentation; keep lines concise and readable.
- Package names follow `org.example.candles.<area>`.
- Classes use `UpperCamelCase`; functions and properties use `lowerCamelCase`.
- Keep logic streaming-safe: avoid `.toList()`, `.groupBy()`, or multiple iterations over sequences in core paths.

## Testing Guidelines
- Use JUnit 5 (`org.junit.jupiter.*`).
- Test classes follow `*Test` naming in `app/src/test/kotlin/`.
- Prefer small, explicit fixtures and inline candle sequences.
- Streaming guarantees are important: add tests using single-use sequences when touching aggregation/backtest pipelines.

## Commit & Pull Request Guidelines
- Commit messages are short, imperative, and often scoped by step (e.g., `step 4 - ...`, `cleanup warnings`).
- PRs should describe behavior changes, note any new CLI flags, and include example commands when relevant.
- UI changes should include screenshots if they affect layout or overlays.

## Architecture Notes
- Timezone is fixed to `America/New_York` across UI, caching, and backtests.
- Backtests use strategy factories to ensure fresh strategy state per date range.
- CSV parsing is strict: header required and schema-driven.
