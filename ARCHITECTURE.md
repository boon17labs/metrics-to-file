# Architecture

Conceptual overview of `metrics-to-file-core`: how metrics are collected,
stored, and cleaned up, and how the threading model works — followed,
at the end, by how `metrics-to-file-prometheus` fits on top of it. Not
a line-by-line code walkthrough — see the source and its Javadoc for
that. Update this file when the architecture itself changes, not on
every small addition.

## Big picture

```
Metrics.start("app")            (or Metrics.builder()...start())
        │
        ▼
MetricsLoggerResolver.resolve() ──▶ picks a MetricsLogger + its
        │                           DaemonRequirements via
        │                           ServiceLoader (see below)
        ▼
   MetricsLogger                 (NoOp / InMemory / File)
        ▲                ▲
        │                │
MetricsCollectionDaemon   CleanupDaemon
(only if requirements     (only if requirements
 .collection())            .cleanup())
```

`Metrics` is a thin facade: it resolves a logger, starts whichever
daemon threads that logger's provider declared it needs, and
remembers them so `Metrics.stop()` (or the JVM shutdown hook) can
shut them down again.

The internal package is grouped by concept, not left flat:
`internal.collect` (metric collectors), `internal.provider`
(logger SPI + resolution), `internal.daemon` (the two daemon
threads), `internal.file` (file format/permissions/cleanup),
`internal.config` (options/property resolution). None of it is
public API regardless of sub-package.

## Storage: the `MetricsLogger` abstraction

`MetricsLogger` (public API) is the single contract every storage
backend implements:

```java
void log(String type, Map<String, Object> values);
void close();
```

Three implementations ship in `metrics-to-file-core`:

- `NoOpMetricsLogger` — discards everything. The default, so the
  library does nothing until explicitly configured.
- `InMemoryMetricsLogger` — keeps every logged group in memory,
  inspectable via `entries()`. Used by the project's own integration
  tests; a consuming app could use it the same way in its own tests.
- `FileMetricsLogger` — writes to a daily file (see "File storage
  format" below).

### How the active implementation is selected

`Metrics` never hardcodes which `MetricsLogger` to use. Instead:

1. `MetricsLoggerResolver.resolve(appName, logDir)` reads the
   `metrics.implementation` system property (default `noop`).
2. If it's `noop`, done — no lookup needed; returns a `NoOpMetricsLogger`
   bundled with `DaemonRequirements(false, false)`.
3. Otherwise, it iterates `MetricsLoggerProvider` implementations
   discovered via `ServiceLoader` (registered in
   `META-INF/services/...internal.provider.MetricsLoggerProvider`),
   and picks
   the one whose `implementationKey()` matches (`file`, `inmemory`).
4. That provider's `create(appName, logDir)` builds the real logger,
   and its `requirements()` says which daemons it needs — bundled
   together as a `ResolvedLogger`.
5. Any failure at any step (unknown key, `ServiceConfigurationError`,
   etc.) falls back to `NoOpMetricsLogger` (needing no daemons) —
   never throws.

**Why the indirection instead of `ServiceLoader.load(MetricsLogger
.class)` directly?** `ServiceLoader` requires a public no-arg
constructor to instantiate a provider. `FileMetricsLogger` needs
`appName` and `logDir`, which aren't known until `Metrics.start()` is
called — so `ServiceLoader` instead discovers tiny `MetricsLoggerProvider`
factories (each *does* have a no-arg constructor) that build the real
logger with the right arguments on demand. This also means another
module can add its own logger — *and declare its own daemon needs* —
without `metrics-to-file-core` ever depending on it or needing to
special-case it: it just ships its own provider + `META-INF/services`
entry. (An earlier version of this
had `Metrics` itself decide which daemons to start via `instanceof`
checks against concrete logger types — that would have meant
modifying core every time a new module needed different background
behavior, quietly defeating the whole point of the provider SPI.)

`metrics-to-file-prometheus` deliberately does not use this route; see
[The Prometheus module](#the-prometheus-module) for why.

## Collecting a metric: built-in vs. custom

### Built-in (JVM) metrics

Every built-in metric is one small class implementing the internal
`MetricsCollector` interface:

```java
String type();                        // e.g. "heap", "gc"
List<Map<String, Object>> collect();  // one map per metric group
```

`collect()` returns a *list* rather than a single map because most
metrics produce exactly one group per tick (heap, threads, ...), but
GC produces one group per garbage collector bean — the list
accommodates both without a separate abstraction.

`MetricsCollectionDaemon` owns the full list of active collectors —
the four defaults (`HeapMetricsCollector`, `ThreadMetricsCollector`,
`MetaspaceMetricsCollector`, `GcMetricsCollector`) plus whichever
opt-in ones (`DirectMemoryMetricsCollector`, `ClassLoadingMetricsCollector`,
`CpuMetricsCollector`, `CodeCacheMetricsCollector`, `ProcessMetricsCollector`)
`MetricsOptions` says to include, based on the `withX()` flags on
`Metrics.builder()`. `ThreadMetricsCollector` (a default, always on) also
reports `stack_mb`, an approximation (live thread count × HotSpot's common
default 512KB stack size) rather than a measured value, since the JVM
exposes no public API for actual per-thread stack memory.
On every tick it calls `collector.collect()` for each and logs every
returned group under that collector's `type()`.

To add a new built-in metric: implement `MetricsCollector`, then add
it to `MetricsCollectionDaemon`'s collector list (unconditionally for
a default metric, or behind a new `MetricsOptions` flag for an
opt-in one).

### Custom metrics

`Metrics.log(type, values)` logs a custom metric group through
whichever `MetricsLogger` `Metrics.start()` (or `Builder.start()`)
already activated — same file, same lifecycle, same `keepDays`
cleanup as everything else:

```java
Metrics.start("order-service");
// anywhere in the app:
Metrics.log("cache", Map.of("hits", 42, "misses", 3));
```

It's a direct passthrough to the active logger, so it's a no-op
before `start()` is called (the active logger defaults to
`NoOpMetricsLogger`) — consistent with the rest of the library never
throwing to the caller.

`Metrics.log()` writes immediately on every call, exactly like the
built-in collectors — no internal buffering. That means **the
frequency of your calls is the frequency of file writes.** For
anything called often (e.g. once per request), aggregate a count/
total/max yourself and call `Metrics.log()` periodically instead of
per-event — the same shape the built-in GC metric already uses
(`count` + `time_ms`, accumulated, not one line per collection).

Because arbitrary application threads can now call `Metrics.log()`
concurrently with each other and with the internal collection daemon,
`FileMetricsLogger.log()` is `synchronized` — without it, concurrent
writes reliably corrupted the file (verified: a test hammering it
from 8 threads × 50 calls lost roughly half of all 400 expected
lines before the fix, every single run).

Nothing stops an application from bypassing `Metrics` entirely and
constructing its own `MetricsLogger` instance directly, but that
creates a second, independent logger with its own lifecycle (nothing
closes it, no shared `keepDays` cleanup) — `Metrics.log()` is the
better default choice.

## File storage format

`FileMetricsLogger` writes one line per metric group to
`<logDir>/<appName>-<yyyy-MM-dd>.log`:

```
2026-08-27T10:00:00Z app=order-service type=heap used_mb=312 committed_mb=400 max_mb=1024
```

- Timestamp: `Instant.now()` truncated to seconds (`MetricLineFormatter`).
- The date in the filename *is* the rotation mechanism — a new day
  means a new file, with no separate rotation logic needed.
- Every `log()` call: ensures `logDir` exists, creates the file if
  missing, restricts it to owner read/write (`FilePermissions`,
  approximating POSIX 600 via `java.io.File` — not an exact guarantee
  on every platform), formats the line, and appends it.
- Any I/O failure is caught, warned to stderr, and swallowed — the
  host application is never affected by a metrics-write failure.

Old files are deleted by `LogFileCleaner`, driven by `CleanupDaemon`:
it lists `logDir`, parses each `<appName>-<date>.log` name back into
a date, and deletes any file older than `keepDays`. Files that don't
match that exact naming pattern (wrong app name, unexpected format)
are left alone.

## Threading model

Both daemons share one base class, `IntervalDaemon`:

```
while (running) {
    tick();          // subclass-specific work
    sleep(interval);
}
```

`shutdown()` sets `running = false` *and* interrupts the thread, so a
daemon sleeping through a long interval still stops promptly rather
than waiting out the full sleep. Both daemons are marked as JVM
daemon threads, so they never keep the JVM alive on their own.

- `MetricsCollectionDaemon.tick()` — samples the active collector list
  into an in-memory buffer every sample interval, flushing that buffer
  (writing every buffered sample to the logger) whenever the write
  interval has elapsed since the last flush, or the buffer is full. A
  single-interval constructor overload treats sample and write as the
  same value, giving today's one-sample-per-write behaviour as the
  default when `sampleInterval` is never set.
- `CleanupDaemon.tick()` — runs `LogFileCleaner.clean(...)` once.

Neither daemon is started unless the resolved logger's
`DaemonRequirements` says it's needed (see above) — a `NoOpMetricsLogger`
runs no background threads at all, `InMemoryMetricsLogger` only runs
collection, only `FileMetricsLogger` runs both. When both do run,
cleanup runs on the *write* interval (`Metrics.builder().writeInterval(...)`)
— there's no separate cleanup frequency, since none is documented and
reusing the write interval keeps the configuration surface smaller.

Each buffered reading is an `internal.buffer.TimestampedSample` (the
type, values, and the `Instant` it was actually sampled at — not the
later moment it happens to be flushed). On flush, if the active logger
is a `FileMetricsLogger`, the whole batch goes through its
`logBatch(...)`, opening the file once and writing every line in one
pass; any other logger (`InMemoryMetricsLogger`) just gets `log(...)`
called once per sample, in order, since there's no real I/O cost to
batch there. The buffer is capped at a fixed number of samples
(flushing early if reached) so a write interval set far longer than
the sample interval cannot grow memory without bound — an internal
constant, not yet configurable. `Metrics.log(type, values)` (custom
metrics) is unaffected by any of this: it always calls `log(...)`
directly, immediately, with no buffering.

`Metrics.stop()` shuts down whichever daemons are running — flushing
any buffered samples first — and closes the active logger. Critically,
it also **joins** each running daemon (bounded to 5s)
before returning — `shutdown()` alone only requests termination, it
doesn't wait for it. Without the join, `stop()` could return while a
daemon was still mid-`tick()`, actively writing a file. This was a
real, reproducible bug, not a theoretical one: tests using a real
`@TempDir` with a short interval intermittently failed because
JUnit's directory cleanup raced against a daemon still writing into
it after the test's own `stop()` call had already "returned."
Joining closed that window — `stop()` now guarantees no write is left
in flight by the time it returns.

A JVM shutdown hook (registered once, in a `static` initializer —
see `Metrics.shutdownHook`) calls `stop()` automatically, so an
application that never calls it explicitly still shuts down cleanly.
The hook is registered once and never removed; `stop()` is
idempotent, so the hook firing after an already-explicit `stop()` is
harmless.

## Default behavior

Until `Metrics.start()`/`Metrics.builder()...start()` is called,
nothing happens — no threads, no files. Once started, with no further
configuration:

| Setting                  | Default                             |
|--------------------------|--------------------------------------|
| `metrics.implementation` | `noop` (discards until set to `file`/`inmemory`) |
| log directory            | `./metrics`                         |
| sample interval          | same as write interval              |
| write interval           | 60 minutes                          |
| retention (`keepDays`)   | 7 days                              |
| max total size (`maxSizeMb`) | 0 (disabled)                    |
| opt-in metrics           | all off (direct memory, classloading, CPU, code cache, process memory) |

So calling `Metrics.start("app")` with `metrics.implementation` unset
starts no background threads at all — `NoOpMetricsLogger`'s
`DaemonRequirements` are `(false, false)`, so there's nothing for a
collection or cleanup daemon to do. This is deliberate and total: the
library doesn't just avoid writing to disk by default, it avoids
running at all, unless a host app explicitly opts in via that system
property.

### Configuring an app that only calls `Metrics.start(appName)`

`Metrics.start("app-name")` takes no configuration parameters beyond
the app name, so `Metrics.builder()` isn't the only way to configure
it — every other `Builder` field falls back to a system property if
never set explicitly, resolved by `internal.config.BuilderProperties`
(explicit builder value → property → documented default, in that
order):

| Field                | System property           | Format                |
|----------------------|----------------------------|-----------------------|
| `logDir`             | `metrics.log.dir`          | a path                |
| `sampleInterval`     | `metrics.sample.interval`  | whole minutes (e.g. `15`), or with a unit suffix: `500ms`, `30s`, `2m`; defaults to the resolved `writeInterval` |
| `writeInterval`      | `metrics.write.interval`   | same format; defaults to 60 minutes |
| `keepDays`           | `metrics.keep.days`        | an integer            |
| `maxSizeMb`          | `metrics.max.size.mb`      | an integer, in MB; `0` disables the check (default) |
| `withDirectMemory()` | `metrics.opt.direct`       | `true`/`false`        |
| `withClassLoading()` | `metrics.opt.classloading` | `true`/`false`        |
| `withCpu()`          | `metrics.opt.cpu`          | `true`/`false`        |
| `withCodeCache()`    | `metrics.opt.codecache`    | `true`/`false`        |
| `withProcessMemory()`| `metrics.opt.process`      | `true`/`false`        |

This is what lets an ops team tune a deployed app — sample/write
interval, retention, opt-in metrics — via a JVM flag, with no code
change and no redeploy, even when the app itself only ever calls the
one-line `Metrics.start("app-name")`. An invalid property value (e.g.
`metrics.write.interval=abc`), an interval that resolves to zero or
negative (explicit or via the property), or a negative `maxSizeMb`, is
warned to stderr and the default wins — never throws.

`maxSizeMb` is a second, independent retention control alongside
`keepDays`: age-based deletion runs first, then — only if `maxSizeMb`
is set above its default of `0` — the oldest surviving files are
deleted one at a time until the app's own matching files (`.log` for
core, `.prom` for the prometheus module) are back under that total
size. It's an opt-in safety net for a machine that might go
unattended for longer than its disk can hold; the recommended setup is
a generous `keepDays` (well above the longest period the deployment
might go without being accessed) with `maxSizeMb` as the actual
backstop. There's no sensible positive default size to fall back to,
so an invalid or negative value disables the check entirely rather
than guessing one.

## The Prometheus module

`metrics-to-file-prometheus` writes the same kind of history as core's
`FileMetricsLogger`, but in Prometheus text format and from a Micrometer
registry instead of from core's collectors. Its usage is in the
[module README](metrics-to-file-prometheus/README.md); this is how it is
put together:

```
PrometheusMetrics.start("app")       (or builder()...start())
        │
        ├─▶ JvmMetricsRegistry        Micrometer PrometheusMeterRegistry +
        │        ▲                    JVM binders (memory, threads, GC),
        │        │                    every meter tagged application=<app>
        │        │ scrape()
        ├─▶ PrometheusWriteDaemon ─▶ PrometheusSnapshotter.sample()
        │   (IntervalDaemon,                   │
        │    in-memory line buffer)            ▼
        │                          PrometheusSnapshotFormatter
        │                          (drop # lines, append timestamp)
        │                                       │
        │                                       ▼ (buffered, then on write interval)
        │                          PrometheusSnapshotter.flush()
        │                                       │
        │                                       ▼
        │                          PrometheusFileWriter ─▶ <app>-<date>.prom
        │
        └─▶ CleanupDaemon (from core, suffix ".prom")
```

### Why a separate entry point, not a `MetricsLogger`

Core's abstraction is `log(type, Map)` — one flat metric group at a time,
fed by core's own collection loop. A Micrometer registry does not work
that way: it owns its meters and is read as a whole with `scrape()`. So
this module does not implement a `MetricsLogger` or a
`MetricsLoggerProvider`, and `metrics.implementation` does not apply to
it. `PrometheusMetrics` has its own `start`/`stop`, its own daemon, and
the two facades can run side by side in one application: their files
differ by suffix (`.log` and `.prom`), and each cleanup only touches its
own.

### What it reuses from core

All of it is `internal` to core — not public API, but safe to use across
modules because they always release together at the same version:

- `IntervalDaemon` — the tick-then-sleep loop with prompt shutdown.
  Extended by `PrometheusWriteDaemon`; it was made public and its
  `tick()` protected for exactly this.
- `CleanupDaemon` / `LogFileCleaner` — deleting files older than
  `keepDays`. They take a file suffix (default `.log`), so the same code
  cleans `.prom` files.
- `BuilderProperties` — the builder value → `metrics.*` property →
  default resolution, so the properties are shared with core.
- `FilePermissions` — owner-only read/write on new files.

### The snapshot pipeline

`PrometheusSnapshotter` splits sampling from writing, mirroring core's
sample/write split:

- `sample()` reads the clock, calls `scrape()` on the registry, and
  turns the scrape into sample lines via `PrometheusSnapshotFormatter`
  — dropping `# HELP` / `# TYPE` / blank lines, so snapshots can be
  appended into one history file without repeating metadata, and
  appending the snapshot time in epoch milliseconds at the very end of
  each line, so label values with spaces or braces never need parsing.
  Any failure is warned about on stderr, never thrown; `sample()`
  returns an empty list rather than let the exception propagate.
- `flush(lines)` hands a (possibly multi-snapshot) batch of lines to
  `PrometheusFileWriter`, which appends them to the daily file: created
  with owner-only permissions, written as UTF-8 (what the Prometheus
  format specifies) in a single write per call, and `synchronized` so
  batches from different threads never interleave.
- `snapshot()` is the original, simpler `flush(sample())` — sample and
  write in one call — kept for anyone calling `PrometheusSnapshotter`
  directly outside the buffering daemon.

`PrometheusWriteDaemon` calls `sample()` on its sample interval,
appending the lines to an in-memory buffer (capped at a fixed number
of lines, flushed early if reached), and `flush(...)` whenever the
write interval has elapsed since the last flush. A single-interval
constructor overload treats sample and write as the same value, giving
today's one-scrape-per-write behaviour as the default.

### Public API

Only `PrometheusMetrics` and its `Builder`. `registry()` returns the
general `io.micrometer.core.instrument.MeterRegistry` rather than the
Prometheus-specific class, because Micrometer 1.13 moved that class to
a different package — returning the stable type keeps the public API
independent of that.

### Threading and shutdown

Two daemon threads, like core: the write daemon, sampling on the
sample interval and flushing on the write interval, and a cleanup
daemon on the write interval's cadence. The first sample is taken
immediately at start, then one per sample interval; `stop()` flushes
whatever is buffered as a final write, so a clean shutdown never loses
buffered samples. `snapshot()` samples and flushes immediately, for
marking a test phase.

`stop()` signals both threads, flushes the write daemon's buffer,
joins both threads (bounded to 5s each) and only then closes the
registry, so a snapshot is never taken from a closed registry and no
write is left in flight when it returns. Unlike core's
single static `Metrics`, each `PrometheusMetrics` is an independent
instance, so each registers its own JVM shutdown hook at start and
removes it again in `stop()`. When the hook itself is what calls `stop()`
the JVM is already shutting down, where `Runtime.removeShutdownHook`
throws `IllegalStateException`; `stop()` catches it. That case is covered
by a test that lets a child JVM exit without stopping.
