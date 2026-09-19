# metrics-to-file — Project Plan

## Background

In order to investigate memory usage on a Linux machine running
a number of Java and C++ applications, metrics per Java app is needed.
The goal is a reusable open source library on GitHub.

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

1. Set up GitHub repo (boon17labs/metrics-to-file) with Maven
   multi-module structure — done
2. Start with metrics-to-file-core — done
3. Implement FileMetricsLogger — done
4. Implement InMemoryMetricsLogger and NoOpMetricsLogger — done
5. Add tests — done
6. Build metrics-to-file-prometheus — file mode done; server mode and
   opt-in binders open
7. Build metrics-to-file-spring — not started
8. Build metrics-to-file-autoinstrument — not started
9. Documentation and README — core and prometheus documented (README,
   ARCHITECTURE.md, module README); the other modules as they are built
