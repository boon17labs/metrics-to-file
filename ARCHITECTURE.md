# Architecture

Conceptual overview of `metrics-to-file-core`: how metrics are collected,
stored, and cleaned up, and how the threading model works. Not a
line-by-line code walkthrough — see the source and its Javadoc for
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
logger with the right arguments on demand. This also means a future
module (e.g. `metrics-to-file-prometheus`) can add its own logger —
*and declare its own daemon needs* — without `metrics-to-file-core` ever
depending on it or needing to special-case it: it just ships its own
provider + `META-INF/services` entry. (An earlier version of this
had `Metrics` itself decide which daemons to start via `instanceof`
checks against concrete logger types — that would have meant
modifying core every time a new module needed different background
behavior, quietly defeating the whole point of the provider SPI.)

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

- `MetricsCollectionDaemon.tick()` — loops over the active collector
  list and logs every group each one returns.
- `CleanupDaemon.tick()` — runs `LogFileCleaner.clean(...)` once.

Neither daemon is started unless the resolved logger's
`DaemonRequirements` says it's needed (see above) — a `NoOpMetricsLogger`
runs no background threads at all, `InMemoryMetricsLogger` only runs
collection, only `FileMetricsLogger` runs both. When both do run,
they run on the *same* interval (`Metrics.builder().interval(...)`)
— there's no separate cleanup frequency, since none is documented and
reusing one interval keeps the configuration surface smaller.

`Metrics.stop()` shuts down whichever daemons are running and closes
the active logger. Critically, it also **joins** each running daemon
(bounded to 5s)
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
| collection interval      | 60 minutes                          |
| retention (`keepDays`)   | 7 days                              |
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
| `interval`           | `metrics.interval`         | whole minutes, e.g. `15` |
| `keepDays`           | `metrics.keep.days`        | an integer            |
| `withDirectMemory()` | `metrics.opt.direct`       | `true`/`false`        |
| `withClassLoading()` | `metrics.opt.classloading` | `true`/`false`        |
| `withCpu()`          | `metrics.opt.cpu`          | `true`/`false`        |
| `withCodeCache()`    | `metrics.opt.codecache`    | `true`/`false`        |
| `withProcessMemory()`| `metrics.opt.process`      | `true`/`false`        |

This is what lets an ops team tune a deployed app — interval,
retention, opt-in metrics — via a JVM flag, with no code change and
no redeploy, even when the app itself only ever calls the one-line
`Metrics.start("app-name")`. An invalid property value (e.g.
`metrics.interval=abc`) is warned to stderr and the default wins —
never throws.
