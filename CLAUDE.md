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

Public classes and methods in `metrics-to-file-core` are stable from v1.0.
No breaking changes are introduced without a new major version.
Internal classes (the `internal` package and every sub-package under
it) are not considered public API.

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
  `builder().appName(...).logDir(...).interval(Duration)
  .keepDays(...).withDirectMemory()...start()` resolve a
  `MetricsLogger` via ServiceLoader (`metrics.implementation`), then
  start only the daemon threads that implementation's provider
  declares it needs
  (`internal.provider.MetricsLoggerProvider.requirements()` →
  `internal.provider.DaemonRequirements`) — `NoOpMetricsLogger` needs
  neither, `InMemoryMetricsLogger` needs only collection,
  `FileMetricsLogger` needs both, so an unconfigured app runs no
  background threads at all. Any builder field left unset falls back
  to its matching `metrics.*` system property (`metrics.log.dir`,
  `metrics.interval` in minutes, `metrics.keep.days`,
  `metrics.opt.direct`/`classloading`/`cpu`/`codecache`/`process`), then to the
  documented default — see `internal.config.BuilderProperties`.
  `Metrics.stop()` shuts down whichever daemons are running, joining each
  (bounded, 5s) so no write is left in flight before it returns, and
  a JVM shutdown hook calls it automatically so an app that never
  calls `stop()` explicitly still shuts down cleanly.
  `Metrics.log(type, values)` lets a host app log its own custom
  metric group through the same active logger — a no-op before
  `start()`.
- `metrics-to-file-prometheus` — file mode done: `PrometheusMetrics`
  (`start(appName)` / `builder()...start()`, `registry()`, `stop()`)
  keeps a Micrometer `PrometheusMeterRegistry` with the default JVM
  binders (memory, threads, GC), every meter tagged
  `application=<appName>`, and appends a timestamped snapshot of it
  (`# HELP`/`# TYPE` lines dropped) to `<appName>-<date>.prom` — once
  at start, then per interval — via `PrometheusSnapshotter` →
  `PrometheusSnapshotFormatter` → `PrometheusFileWriter`. A
  `PrometheusWriteDaemon` (extending core's `IntervalDaemon`) and
  core's `CleanupDaemon` (with the `.prom` suffix) run as daemon
  threads; `stop()` joins both, then closes the registry, and a JVM
  shutdown hook calls it. Same `metrics.log.dir`/`metrics.interval`/
  `metrics.keep.days` fallbacks as core. Deliberately independent of
  `MetricsLogger` and the provider SPI — see ARCHITECTURE.md. Not
  started: HTTP `/metrics` server mode, opt-in binders.
- `metrics-to-file-spring`, `metrics-to-file-autoinstrument` — not
  started.

## Workflow

See WORKFLOW.md.
