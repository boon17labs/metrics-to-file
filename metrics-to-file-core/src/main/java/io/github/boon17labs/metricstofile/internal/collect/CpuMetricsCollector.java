package io.github.boon17labs.metricstofile.internal.collect;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads process and system CPU load via
 * {@link com.sun.management.OperatingSystemMXBean} when available
 * (see CLAUDE.md for why this HotSpot-specific extension is allowed
 * here), falling back to {@code -1.0} otherwise.
 */
public final class CpuMetricsCollector implements MetricsCollector {

    @Override
    public String type() {
        return "cpu";
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<Map<String, Object>> collect() {
        final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        final Map<String, Object> values = new LinkedHashMap<>();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            final com.sun.management.OperatingSystemMXBean sunBean =
                    (com.sun.management.OperatingSystemMXBean) osBean;
            values.put("process_load", sanitize(sunBean.getProcessCpuLoad()));
            values.put("system_load", sanitize(sunBean.getSystemCpuLoad()));
        } else {
            values.put("process_load", -1.0);
            values.put("system_load", -1.0);
        }
        return Collections.singletonList(values);
    }

    // The bean's javadoc documents -1.0 as the "unavailable" sentinel, but on
    // some platforms it returns NaN instead before the first sample window
    // completes — normalize that here so -1.0 is the only "unavailable" value
    // ever written to the metrics file.
    private static double sanitize(final double load) {
        return Double.isNaN(load) ? -1.0 : load;
    }
}
