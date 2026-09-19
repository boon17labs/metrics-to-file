# metrics-to-file-prometheus

JVM metrics in Prometheus text format, appended to a daily file — from
one line of code, with no Prometheus server, no HTTP endpoint and no
other infrastructure. Built on [Micrometer](https://micrometer.io).

Part of [metrics-to-file](../README.md). Not yet published to Maven
Central; until then, build it from source with `mvn install`.

```xml
<dependency>
    <groupId>io.github.boon17labs</groupId>
    <artifactId>metrics-to-file-prometheus</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This pulls in `metrics-to-file-core`, `micrometer-core` and
`micrometer-registry-prometheus`. Requires Java 8+.

## Usage

```java
// Minimal — one line. Writes to ./metrics every 60 minutes by default.
final PrometheusMetrics metrics = PrometheusMetrics.start("order-service");

// With configuration
PrometheusMetrics.builder()
    .appName("order-service")
    .logDir("/var/log/metrics")          // default: ./metrics
    .interval(Duration.ofMinutes(15))    // default: 60 min
    .keepDays(14)                        // default: 7
    .start();

// Stop — symmetric with start
metrics.stop();
```

`start` returns a handle. Its `registry()` is the Micrometer
`MeterRegistry` holding the metrics, so you can register your own
meters on it and have them written to the same file:

```java
Gauge.builder("queue.size", queue::size).register(metrics.registry());
```

Anything that takes a `MeterRegistry` works the same way, for example
Micrometer's `ExecutorServiceMetrics.monitor(metrics.registry(), pool,
"worker-pool")`.

## What it writes

One file per day, `<logDir>/<appName>-<yyyy-MM-dd>.prom`. A snapshot of
the whole registry is appended once when you call `start`, and then
once per interval. Each sample is one line:

```
jvm_memory_used_bytes{application="order-service",area="heap",id="G1 Old Gen"} 1573480.0 1789807921221
jvm_threads_live_threads{application="order-service"} 8.0 1789807921221
jvm_gc_pause_seconds_count{action="end of major GC",application="order-service",cause="System.gc()",gc="G1 Old Generation"} 1 1789807921667
```

- **Timestamp.** The last field is the time of the snapshot in epoch
  milliseconds — the optional timestamp of the Prometheus exposition
  format. Every sample of one snapshot carries the same one.
- **No `# HELP` or `# TYPE` lines.** They are left out so that
  snapshots can be appended one after another into a single history
  file. The names follow Micrometer's, so they are the ones you would
  see on a `/metrics` endpoint.
- **`application` label.** Every sample carries `application=<appName>`,
  so files or series from several apps can be told apart.
- **A history, not a scrape target.** The file holds many snapshots of
  the same series, which is what you want for looking back at memory
  use over time. It is not a file to hand to a tool that expects only
  the latest value of each metric, such as node_exporter's textfile
  collector.
- **Rotation, permissions, cleanup.** A new day is a new file. Files
  are restricted to owner read/write. Files older than `keepDays` are
  deleted automatically; only `.prom` files of this app name are
  touched, so `.log` files written by `metrics-to-file-core` in the
  same directory are left alone.

## Default metrics

The registry comes with Micrometer's JVM binders for memory, threads
and garbage collection:

| Area    | Metrics |
|---------|---------|
| Memory  | `jvm_memory_used_bytes`, `jvm_memory_committed_bytes`, `jvm_memory_max_bytes` per memory pool (`area` is `heap` or `nonheap`, `id` is the pool name); `jvm_buffer_count_buffers`, `jvm_buffer_memory_used_bytes`, `jvm_buffer_total_capacity_bytes` for direct and mapped buffers |
| Threads | `jvm_threads_live_threads`, `jvm_threads_peak_threads`, `jvm_threads_daemon_threads`, `jvm_threads_started_threads_total`, `jvm_threads_states_threads` per `state` |
| GC      | `jvm_gc_pause_seconds_count`, `_sum` and `_max` per collector and cause; `jvm_gc_memory_allocated_bytes_total`, `jvm_gc_memory_promoted_bytes_total`, `jvm_gc_live_data_size_bytes`, `jvm_gc_max_data_size_bytes` |

The memory pool names depend on the JVM and its garbage collector
(`G1 Old Gen`, `PS Old Gen`, `Metaspace`, ...). The `jvm_gc_pause_*`
samples appear only after the first collection has happened.

## Configuration

An explicit builder value wins; otherwise the matching system property
is used; otherwise the default. `appName` is required.

| Builder            | System property                   | Default    |
|--------------------|-----------------------------------|------------|
| `logDir(String)`   | `metrics.log.dir`                 | `./metrics` |
| `interval(Duration)` | `metrics.interval` (whole minutes) | 60 minutes |
| `keepDays(int)`    | `metrics.keep.days`               | 7          |

These are the same properties `metrics-to-file-core` uses, so an
application that runs both is tuned in one place — for example
`-Dmetrics.interval=15` on the command line changes both, with no code
change. An invalid property value is warned about on stderr and the
default wins.

## Lifecycle and threading

`start` creates the registry and two daemon threads: one taking the
snapshots — the first immediately, then one per interval — and one
deleting old files, on the same interval. Neither keeps the JVM alive.

`stop()` stops both threads, waiting (at most 5 seconds each) until
they have actually finished, so no write is left in flight when it
returns, and then closes the registry. It is safe to call more than
once. A JVM shutdown hook calls it automatically, so an application that
never calls `stop()` explicitly still shuts down cleanly; an explicit
`stop()` removes the hook again.

## Error handling

A metrics problem never affects the host application. Failures — an
unusable log directory, a failing write, a JVM binder that cannot bind
— are warned about on stderr and never thrown, and `start` still
returns a working handle. The one exception is a missing `appName`,
which is a programming error and throws `IllegalStateException`.

## Things to know

- **One instance per app name.** Each `start` creates an independent
  instance with its own registry and threads. Two instances with the
  same app name would append to the same daily file.
- **Micrometer 1.13.** The module is built against Micrometer 1.13 and
  uses its Prometheus registry. If your application manages its own
  Micrometer version below 1.13 (for example through an older Spring
  Boot version's dependency management), align it.
- **Different metric set from core.** Micrometer's JVM binders do not
  report everything `metrics-to-file-core` does — for example no
  deadlocked-thread count and no process RSS. Use core for those; the
  two can run side by side.

## Not yet available

Planned, but not part of this module yet: an HTTP `/metrics` endpoint
for scraping, opt-in binders (CPU, class loading), and the Spring Boot
integration (`metrics-to-file-spring`).

For how the pieces fit together, see
[ARCHITECTURE.md](../ARCHITECTURE.md#the-prometheus-module).
