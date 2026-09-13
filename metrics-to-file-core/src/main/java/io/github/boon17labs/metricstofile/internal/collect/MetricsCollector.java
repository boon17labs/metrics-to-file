package io.github.boon17labs.metricstofile.internal.collect;

import java.util.List;
import java.util.Map;

/**
 * Produces one or more metric groups of the same {@link #type()} for
 * {@code MetricsCollectionDaemon} to log on each collection tick
 * (e.g. one group per garbage collector bean for GC metrics).
 */
public interface MetricsCollector {

    String type();

    List<Map<String, Object>> collect();
}
