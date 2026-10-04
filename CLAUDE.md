# metrics-to-file

Open source Java library that writes JVM metrics and custom metrics to
file — no external dependencies in the core module.

## Purpose

The simplest possible way to log metrics from a Java app to file, without
requiring Prometheus, Grafana, or other infrastructure. One line of code:
`Metrics.start("app-name")`.

## Project decisions

```
Name:         metrics-to-file
GitHub:       https://github.com/boon17labs/metrics-to-file
Group id:     io.github.boon17labs
Java minimum: 8 (bump to 21 in v2 once target apps upgrade)
License:      Apache 2.0
```

## API stability policy

Public classes and methods in all modules are stable from v1.0. No
breaking changes are introduced without a new major version.
Internal classes (the `internal` package and every sub-package under
it) are not considered public API. This covers this project's own
classes; types from other libraries that appear in a signature (for
example Micrometer's `MeterRegistry`) follow that library's own
compatibility.

## Module structure

```
metrics-to-file-core              → JVM and custom metrics to file, no external dependencies
metrics-to-file-prometheus        → Micrometer + Prometheus format, file and/or server
metrics-to-file-spring            → Spring Boot autoconfiguration
metrics-to-file-autoinstrument    → automatic instrumentation via reflection
```

## Java code standard (metrics-to-file-core)

1. **`final` everywhere** — every method parameter and local variable is
   declared `final`.
2. **No external dependencies** in `metrics-to-file-core` — only
   `java.lang.*`, `java.util.*`, `java.io.*`, `java.lang.management.*`,
   `java.time.*`. File I/O uses `java.io` (not `java.nio.file`).
   `com.sun.management.OperatingSystemMXBean` is allowed for the CPU
   opt-in metric, and as the process opt-in's non-Linux fallback
   (direct cast with an `instanceof` guard, never a blind cast) — it
   ships with every mainstream JDK, but isn't part of the Java SE
   spec, so this is a deliberate, narrow exception, not a general
   license to reach for it elsewhere.
3. **Threading** — daemon threads for file writing and cleanup.
   `close()` shuts down both.
4. **Error handling** — the host app is never affected by metrics
   problems. Warning to stderr, never an exception to the caller.
   Falls back to noop if the file can't be created.
5. **File format** — key-value, one line per metric group:
   ```
   2026-08-27T10:00:00Z app=order-service type=heap used_mb=312 committed_mb=400 max_mb=1024
   ```

## Testing

Unit tests are named `*Test.java` (run by Surefire, `mvn test`).
Integration tests are named `*IT.java` (run by Failsafe, only in the
`integration-tests` CI job, never re-running unit tests).

## Packages

```
io.github.boon17labs.metricstofile            ← public API
io.github.boon17labs.metricstofile.internal.collect  ← MetricsCollector + implementations
io.github.boon17labs.metricstofile.internal.provider ← MetricsLoggerProvider SPI + resolver
io.github.boon17labs.metricstofile.internal.daemon   ← IntervalDaemon + implementations
io.github.boon17labs.metricstofile.internal.file     ← file format/permissions/cleanup
io.github.boon17labs.metricstofile.internal.config   ← MetricsOptions, BuilderProperties
```

`metrics-to-file-prometheus`:

```
io.github.boon17labs.metricstofile.prometheus          ← public API (PrometheusMetrics)
io.github.boon17labs.metricstofile.prometheus.internal ← registry, snapshotter, formatter, writer, daemon
```

None of the `internal.*` sub-packages are public API — grouped by
concept purely for navigability as the module grew past ~24 flat
files. See ARCHITECTURE.md for how they relate.

## Current status

- `MetricsLogger` interface — done
- `NoOpMetricsLogger` — done (default)
- `InMemoryMetricsLogger` — done (inspectable, for tests)
- `FileMetricsLogger` — done (writes one line per metric group; daily
  rotation is implicit in the filename, cleanup is handled by
  `Metrics`' `CleanupDaemon`, files are restricted to owner
  read/write via `internal.file.FilePermissions`, approximating POSIX
  600 through `java.io.File` — not an exact guarantee on every
  platform; `log()` is `synchronized` so concurrent callers — the
  daemon plus any thread calling `Metrics.log()` — never interleave
  writes)
- `Metrics` (facade/entry point) — done: `start(appName)` and
  `builder().appName(...).logDir(...).sampleInterval(Duration)
  .writeInterval(Duration).keepDays(...).withDirectMemory()...start()`
  resolve a `MetricsLogger` via ServiceLoader (`metrics.implementation`),
  then start only the daemon threads that implementation's provider
  declares it needs
  (`internal.provider.MetricsLoggerProvider.requirements()` →
  `internal.provider.DaemonRequirements`) — `NoOpMetricsLogger` needs
  neither, `InMemoryMetricsLogger` needs only collection,
  `FileMetricsLogger` needs both, so an unconfigured app runs no
  background threads at all. `MetricsCollectionDaemon` samples the
  active collectors into an in-memory buffer
  (`internal.buffer.TimestampedSample`) every `sampleInterval`, and
  flushes that buffer — one file open/write/close via
  `FileMetricsLogger.logBatch(...)` — every `writeInterval`, early if
  the buffer fills, on `stop()`, or on demand via `Metrics.snapshot()`.
  `sampleInterval` defaults to the resolved `writeInterval`, so an app
  that never sets it keeps the original one-sample-per-write
  behaviour. `CleanupDaemon` deletes files older than `keepDays` on
  every tick via `internal.file.LogFileCleaner`, then — if `maxSizeMb`
  is set above its default of `0` (disabled) — deletes the oldest
  surviving files one at a time until this app's own matching files
  are back under that total size; a second, independent retention
  control for a machine that might go unattended longer than its disk
  can hold. Any builder field left unset falls back to its matching
  `metrics.*` system property (`metrics.log.dir`, `metrics.sample.interval`
  and `metrics.write.interval` in minutes or with a unit suffix
  (`500ms`/`30s`/`2m`), `metrics.keep.days`, `metrics.max.size.mb`,
  `metrics.opt.direct`/`classloading`/`cpu`/`codecache`/`process`), then to the
  documented default — see `internal.config.BuilderProperties`.
  `Metrics.stop()` flushes any buffered samples, then shuts down
  whichever daemons are running, joining each (bounded, 5s) so no
  write is left in flight before it returns, and a JVM shutdown hook
  calls it automatically so an app that never calls `stop()` explicitly
  still shuts down cleanly. `Metrics.log(type, values)` lets a host app
  log its own custom metric group through the same active logger — a
  no-op before `start()`, and always written immediately, unaffected
  by the collection buffer.
- `metrics-to-file-prometheus` — file mode done: `PrometheusMetrics`
  (`start(appName)` / `builder()...start()`, `registry()`, `snapshot()`,
  `stop()`) keeps a Micrometer `PrometheusMeterRegistry` with the
  default JVM binders (memory, threads, GC), every meter tagged
  `application=<appName>`, and samples it (`# HELP`/`# TYPE` lines
  dropped) into an in-memory line buffer every `sampleInterval`, via
  `PrometheusSnapshotter.sample()` → `PrometheusSnapshotFormatter`.
  That buffer is flushed to `<appName>-<date>.prom` via
  `PrometheusSnapshotter.flush(...)` → `PrometheusFileWriter` every
  `writeInterval` — once at start, then per write interval, early if
  the buffer fills, on `stop()`, or on demand via `snapshot()`. A
  `PrometheusWriteDaemon` (extending core's `IntervalDaemon`) and
  core's `CleanupDaemon` (with the `.prom` suffix, on the write
  interval's cadence) run as daemon threads; `stop()` flushes the
  buffer, joins both, then closes the registry, and a JVM shutdown
  hook calls it. Same `metrics.log.dir`/`metrics.sample.interval`/
  `metrics.write.interval`/`metrics.keep.days`/`metrics.max.size.mb`
  fallbacks as core, including the same `maxSizeMb` size-cap retention
  control on the shared `CleanupDaemon`.
  Deliberately independent of `MetricsLogger` and the provider SPI —
  see ARCHITECTURE.md. Not started: HTTP `/metrics` server mode,
  opt-in binders.
- `metrics-to-file-spring`, `metrics-to-file-autoinstrument` — not
  started.

## Roadmap

The ordered roadmap and the open decisions are in
`metricslibraryplan.md`, under "Next steps". Priority: file-based use
cases first (the files may be the only data source); the `/metrics`
server mode comes last.

## Workflow

See WORKFLOW.md.
