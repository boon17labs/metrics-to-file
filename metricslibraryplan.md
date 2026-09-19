# metrics-to-file — Project Plan

## Background

In order to investigate memory usage on a Linux machine running
a number of Java and C++ applications, metrics per Java app is needed.
The goal is a reusable open source library on GitHub.

---

## Use cases the design must serve

1. Performance test (about 15 seconds up to several minutes) against an
   app with request-handling problems (worker thread pool limits, slow
   DB queries), then investigate the metrics to find the root cause.
2. Long-running production monitoring of heap, memory and threads, plus
   custom metrics (request counts, request times) — trends and min/max
   over long periods.
3. Deployments that cannot be reached in real time (remote sites, or no
   access to production for a week or more): the metrics files are the
   only data source, and are downloaded and investigated later. Scraping
   `/metrics` is not possible there, so file mode comes first and must
   be complete on its own.

---

## Project decisions

```
Name:         metrics-to-file
GitHub:       github.com/boon17labs/metrics-to-file
Group id:     io.github.boon17labs
Java minimum: 8 (bump to 21 in v2 once target apps upgrade)
License:      Apache 2.0
```

---

## API stability policy

Public classes and methods in all modules are stable from v1.0. No
breaking changes are introduced without a new major version.
Internal classes (the `internal` package and every sub-package under
it) are not considered public API. This covers this project's own
classes; types from other libraries that appear in a signature (for
example Micrometer's `MeterRegistry`) follow that library's own
compatibility.

Consequence for users: upgrading from v1.x to v1.y never
requires code changes — just an updated version number in pom.xml.
Upgrading to v2 (Java 21) is optional and requires only a
version bump — the API stays the same unless explicitly announced as broken.

---

## Module structure

```
metrics-to-file-core              → JVM and custom metrics to file, no external dependencies
metrics-to-file-prometheus        → Micrometer + Prometheus format, file and/or server
metrics-to-file-spring            → Spring Boot autoconfiguration
metrics-to-file-autoinstrument    → automatic instrumentation via reflection/aspects
```

### Dependencies between modules

```
metrics-to-file-core          ← base, no external dependencies
metrics-to-file-prometheus    → pulls in metrics-to-file-core + micrometer-core + micrometer-registry-prometheus
metrics-to-file-spring        → pulls in metrics-to-file-core + spring-boot-actuator
metrics-to-file-autoinstrument → pulls in metrics-to-file-core + micrometer-core
```

---

## metrics-to-file-core

### Purpose
Collect JVM metrics and custom metrics and write them to file. No
external dependencies — only java.lang.management.

### Default metrics (always on)
```
Heap        → used, committed, max
Metaspace   → used, committed
Threads     → live, peak, deadlocked
GC          → count, time per collector
```

### Opt-in metrics
```
Direct memory   → used, count
Code cache      → used
Class loading   → loaded, unloaded
CPU             → process load, system load
```

### API

```java
// Minimal — one line
Metrics.start("order-service");

// With configuration
Metrics.builder()
    .appName("order-service")
    .logDir("/var/log/metrics")   // default: ./metrics
    .interval(Duration.ofMinutes(60)) // default: 60 min
    .keepDays(7)                   // default: 7 days
    .withDirectMemory()            // opt-in
    .withClassLoading()            // opt-in
    .withCpu()                     // opt-in
    .withCodeCache()               // opt-in
    .start();

// Stop — symmetric with start
Metrics.stop();
```

### Implementations

Selected via the system property `metrics.implementation`:

```
file      → FileMetricsLogger, writes to file
inmemory  → InMemoryMetricsLogger, keeps in memory (for tests)
noop      → NoOpMetricsLogger, does nothing (DEFAULT)
```

```bash
# Enable file logging
-Dmetrics.implementation=file

# Default — does nothing
java -jar app.jar
```

### ServiceLoader

The implementation is selected via ServiceLoader:

```
src/main/resources/META-INF/services/io.github.boon17labs.metricstofile.MetricsLogger
→ contains all three implementations
```

### File format

Key-value, one line per metric group:

```
2026-08-27T10:00:00Z app=order-service type=heap used_mb=312 committed_mb=400 max_mb=1024
2026-08-27T10:00:00Z app=order-service type=metaspace used_mb=128 committed_mb=132
2026-08-27T10:00:00Z app=order-service type=threads live=94 peak=120 deadlocked=0
2026-08-27T10:00:00Z app=order-service type=gc name="G1 Young" count=42 time_ms=1823
2026-08-27T10:00:00Z app=order-service type=gc name="G1 Old" count=3 time_ms=612
```

Opt-in:
```
2026-08-27T10:00:00Z app=order-service type=direct used_mb=45 count=1200
2026-08-27T10:00:00Z app=order-service type=classloading loaded=8432 unloaded=12
2026-08-27T10:00:00Z app=order-service type=cpu process_load=0.45 system_load=0.67
```

### File handling

```
New file per day:  order-service-2026-08-27.log
Rotation:          daily, automatic
Cleanup:           files older than keepDays are deleted automatically
Permissions:       set automatically on creation (600)
Errors:            warning to stderr, fallback to noop, the app is never affected
```

### Configuration via properties

```
metrics.implementation=file|inmemory|noop
metrics.log.dir=./metrics
metrics.interval=60
metrics.keep.days=7
metrics.opt.direct=false
metrics.opt.classloading=false
metrics.opt.cpu=false
metrics.opt.codecache=false
```

### Threading

```
One daemon thread for file writing
One daemon thread for cleanup
Metrics.stop() shuts down both
```

### Security

```
File permissions set automatically (600)
The app always starts regardless of metrics errors
Warning to stderr on problems
```

### Testing

```
InMemoryMetricsLogger → unit/integration tests, inspectable
NoOpMetricsLogger     → tests that don't care about metrics
Metrics.stop()        → clean up threads in teardown
```

---

## metrics-to-file-prometheus

### Purpose
Prometheus format via Micrometer. Keeps a Micrometer registry that the
application (and the other modules) can register meters on.

### Status
File mode is done. Server mode and the opt-in binders are not started.

### Entry point
Its own `PrometheusMetrics` — `start("app")` or `builder()...start()` —
independent of `Metrics` in core: it does not use `metrics.implementation`
or the `MetricsLogger` / provider SPI. `registry()` returns the
Micrometer `MeterRegistry`; `stop()` stops it. See ARCHITECTURE.md.

### Modes

```
File mode    → Prometheus format to file (done)
Server mode  → HTTP endpoint /metrics, requires configuration (planned)
Both         → file + server simultaneously (planned)
```

### File mode
Done. Works out of the box with no configuration: one call to `start`
writes a timestamped snapshot to a daily file at start and then once per
interval, with the same `metrics.log.dir`, `metrics.interval` and
`metrics.keep.days` tuning as core.

### Server mode
Planned. Only starts if explicitly configured:

```
metrics.prometheus.port=9090
metrics.prometheus.allowed.ips=192.168.1.100
```

If not configured → no server, the app is unaffected.

### Configuration

File mode reuses core's properties — an explicit builder value wins,
then the property, then the default:

```
metrics.log.dir=./metrics
metrics.interval=60
metrics.keep.days=7
```

The `metrics.prometheus.*` namespace is reserved for the server mode
(planned), where it will also decide whether the file is written too:

```
metrics.prometheus.mode=file|server|both
metrics.prometheus.port=9090
metrics.prometheus.allowed.ips=127.0.0.1
metrics.prometheus.file.enabled=true
```

### Web server with no extra dependency

```java
// com.sun.net.httpserver — built into the JDK
HttpServer server = HttpServer.create(new InetSocketAddress(9090), 0);
```

### File format

One file per day, `<app>-<yyyy-MM-dd>.prom`. Prometheus format with a
timestamp per sample (epoch milliseconds) and an `application` label on
every sample:

```
jvm_memory_used_bytes{application="order-service",area="heap",id="G1 Old Gen"} 1573480.0 1789807921221
jvm_threads_live_threads{application="order-service"} 8.0 1789807921221
```

`# HELP` and `# TYPE` lines are left out, so snapshots can be appended
into one history file. Default metrics: memory, threads and GC; opt-in
binders (CPU, class loading) are planned.

---

## metrics-to-file-spring

### Purpose
Zero-config integration with Spring Boot via autoconfiguration.

### Autoconfiguration

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
→ io.github.boon17labs.metricstofile.spring.MetricsAutoConfiguration
```

### Web server detection

```
Web server present    → Actuator + existing server (Tomcat/Undertow/Jetty)
No web server         → com.sun.net.httpserver
No server at all      → file mode
```

The user can also explicitly choose:
```
metrics.server.type=actuator|lightweight
```

### Modes

```
metrics.mode=file|server|both
```

### Configuration via application.properties

```
metrics.enabled=true
metrics.app.name=order-service
metrics.mode=file
metrics.file.dir=/var/log/metrics
metrics.file.keep-days=7
management.endpoints.web.exposure.include=prometheus
```

### Spring profiles

```
# application-local.properties
metrics.enabled=true
metrics.implementation=file

# application-prod.properties
metrics.enabled=false
```

---

## metrics-to-file-autoinstrument

### Purpose
Automatic instrumentation of known libraries via reflection and Micrometer.

### Activation

```java
Metrics.start("order-service");
AutoInstrument.enable();
```

`AutoInstrument` lives in `metrics-to-file-autoinstrument`, so
`metrics-to-file-core` needs no handle returned from `Metrics.start()`
and its public API stays untouched.

### What gets instrumented automatically

```
HikariCP        → connection pool metrics
ExecutorService → thread pool metrics
RestTemplate    → outgoing HTTP metrics
WebClient       → outgoing HTTP metrics
OkHttpClient    → outgoing HTTP metrics
JDBC            → database calls
```

### How

Via reflection — checks whether the class is present on the classpath:

```java
if (isPresent("com.zaxxer.hikari.HikariDataSource")) {
    // instrument HikariCP automatically
}
```

### Manual instrumentation of thread pools

```java
ExecutorService pool = Executors.newFixedThreadPool(10);
ExecutorServiceMetrics.monitor(registry, pool, "zeromq-pool");
```

---

## Design notes: what a snapshot interval can miss

Metrics live in memory in the registry and are read when a snapshot is
written (or, later, scraped).

- Counters, timers (count/sum) and histograms are cumulative: nothing is
  lost between snapshots, at any interval.
- Gauges (heap used, live threads, custom gauges) store nothing; they are
  evaluated when read, so events between two snapshots are lost.
  Measured: a 2 s burst of 200 threads was invisible at a 10 s interval;
  `jvm_threads_peak_threads` did catch it, because the JVM keeps a peak.
- Timer `max` is kept only in a rolling window (Micrometer default: 2
  minutes), so a coarse interval can miss a slow request.
- Decision: sample fast, write slowly. Two independent settings: a
  sample interval (for example 1–5 s), at which a sampler reads all
  metrics and keeps the raw, timestamped samples in memory, and a write
  interval (for example 1–5 minutes), at which the buffer is written to
  the file in one batch. Losing up to one write interval on a crash is
  accepted. The file holds raw samples, so min/max and trends are
  calculated afterwards at any granularity. The file size depends only
  on the sample interval, not on the write interval; batching only cuts
  the number of writes (good for busy disks and flash storage). The
  buffer is capped (a few MB) and flushed early when full. Min/max
  aggregation may follow later as a compact mode for very long-term
  storage.
- Size: about 5 KB per sample — a 60 s sample interval ≈ 7 MB/day, 30 s
  ≈ 15 MB/day, 10 s ≈ 44 MB/day, 5 s ≈ 88 MB/day, 1 s ≈ 430 MB/day.
- Retention: `keepDays=7` keeps about a week, so anyone unable to reach
  the machine for longer loses the oldest data. Cleanup is age-based
  only; there is no size cap yet, so a fine interval plus a long absence
  could fill the disk.
- The JVM's own GC log (`-Xlog:gc*`, or `-Xloggc` on Java 8) records
  every collection exactly and is a good complement.

---

## General principles

```
The app is never affected by metrics problems
Warning to stderr on error, never an exception to the app
Default is noop — must be explicitly enabled
start() / stop() symmetry
Minimal memory footprint — core has no external dependencies
Fallback to noop if the file can't be created
```

---

## Next steps

Done: repo and Maven structure; core (loggers, `Metrics` facade, default
and opt-in metrics, cleanup, file permissions, custom metrics);
prometheus file mode (`PrometheusMetrics`); docs for both; CI on Java 8
and 17.

Roadmap, in order. File-based use cases come first (see "Use cases"):

1. Sub-minute intervals by system property with units (`1s`, `500ms`,
   `2m`; a plain number stays minutes), and validation: a zero interval
   currently makes the daemon spin and a negative one kills its thread
   (from reading the code) — warn and use the default instead. Shared
   `BuilderProperties` in core, used by both modules.
2. Separate sample interval and write interval, with an in-memory buffer
   (see design notes): a sampler keeps raw timestamped samples, capped
   at a few MB and flushed early when full; the buffer is written every
   write interval, on `stop()` and in the shutdown hook (this is the
   final snapshot), and by a public `PrometheusMetrics.snapshot()` that
   samples and flushes immediately, to mark test phases. Both intervals
   accept the units from step 1. The setting names are public API from
   v1.0, so propose them and get approval before writing tests. Open:
   whether core's `FileMetricsLogger` gets the same write batching. (The
   first sample at start is taken before the app has registered its own
   meters.)
3. Maximum total size of the files (for example `metrics.max.size.mb`,
   oldest deleted first, for `.log` and `.prom`) for unattended machines,
   and documented `keepDays` guidance: set it well above the longest
   period without access.
4. Tooling to investigate the files ("Investigating files from
   deployments"): a dependency-free Java converter shipped in the
   prometheus jar (experimental) that turns `.prom` files into OpenMetrics
   for `promtool tsdb create-blocks-from openmetrics` — UNVERIFIED, so
   spike on a real file first (timestamps become seconds, `# EOF` added)
   — plus docs for VictoriaMetrics import, generic `--label key=value` at
   import (for example to tell sites apart), an optional docker-compose
   plus Grafana dashboard as an example only, and possibly CSV export.
5. `metrics-to-file-autoinstrument` (thread pools, DB pools, HTTP
   clients) to find bottlenecks in performance tests; activation via
   `AutoInstrument.enable()`. Until then a thread pool can be registered
   by hand: `ExecutorServiceMetrics.monitor(registry, pool, name)`.
6. Prometheus server mode (`/metrics`) — lowest priority, for users who
   can scrape. Decisions so far: off unless a port is configured;
   `metrics.prometheus.allowed.ips` defaults to loopback only and `*`
   means anyone (the 403 response names the property); built on the JDK's
   `com.sun.net.httpserver`, and if that is missing (it is not in the
   Java SE specification, for example custom `jlink` images) warn and
   start no server; no built-in authentication (document network
   controls). The property and builder names are public API from v1.0,
   so propose them and get approval before writing tests.
7. `metrics-to-file-spring` — not started.
8. Maven Central release readiness, before v1.0 is tagged: POM
   `developers` and `scm`, sources and javadoc jars, artifact signing, a
   release workflow, verification of the `io.github.boon17labs`
   namespace. Versions are still 1.0.0-SNAPSHOT.

Also open: opt-in binders (CPU, class loading) for the prometheus module,
min/max aggregation as a compact long-term mode, optional extra common
tags in the library, documenting which metrics are safe at coarse
intervals, and opt-in logging of individual GC events.
