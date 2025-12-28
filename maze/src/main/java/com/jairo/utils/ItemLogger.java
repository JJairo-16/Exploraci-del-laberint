package com.jairo.utils;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.app.time.FxTimeSource;
import com.jairo.items.ItemType;

public final class ItemLogger {

    // IMPORTANTE: el nombre del logger debe coincidir con el de logback.xml
    private static final Logger log = LoggerFactory.getLogger(ItemLogger.class);

    private static final LongAdder totalPlaced = new LongAdder();
    private static final ConcurrentHashMap<String, LongAdder> placedByType = new ConcurrentHashMap<>();

    private static boolean logOnAnyPlace = false;

    private static final FxTimeSource time = new FxTimeSource();

    // FxTimeSource.now() devuelve NANOS (AnimationTimer + fallback
    // System.nanoTime())
    private static volatile Long startNs = null;

    private ItemLogger() {
    }

    /** Llamar al inicio de cada generación/colocación */
    public static void reset() {
        totalPlaced.reset();
        placedByType.clear();
        startNs = time.now(); // nanosegundos
        log.info("Item placement logging reset startNs={}", startNs);
    }

    /** Llamar cada vez que se coloque un objeto */
    public static void onPlaced(ItemType type, int x, int y) {
        String id = typeId(type);

        totalPlaced.increment();
        placedByType.computeIfAbsent(id, k -> new LongAdder()).increment();

        if (logOnAnyPlace)
            log.info("PLACED type={} x={} y={} total={} totalOfType={}",
                    id, x, y, totalPlaced.sum(), placedByType.get(id).sum());
    }

    /** Llamar al final si quieres un resumen */
    public static void summary() {
        long total = totalPlaced.sum();
        long nowNs = time.now(); // nanosegundos

        Long s = startNs;
        if (s == null) {
            log.warn("SUMMARY totalPlaced={} elapsedMs=UNKNOWN (reset not called)", total);
            return;
        }

        long elapsedNs = Math.max(0L, nowNs - s);
        long elapsedMs = elapsedNs / 1_000_000L;

        if (placedByType.isEmpty()) {
            log.info("SUMMARY totalPlaced={} elapsedMs={}", total, elapsedMs);
            return;
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("SUMMARY totalPlaced=").append(total)
                .append(" elapsedMs=").append(elapsedMs)
                .append(" byType=[");

        placedByType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue().sum()).append(", "));

        if (sb.length() >= 2)
            sb.setLength(sb.length() - 2); // quita ", "
        sb.append("]");

        log.info(sb.toString());
    }

    public static long total() {
        return totalPlaced.sum();
    }

    public static long totalOf(ItemType type) {
        LongAdder adder = placedByType.get(typeId(type));
        return adder == null ? 0L : adder.sum();
    }

    private static String typeId(ItemType type) {
        if (type == null)
            return "null";
        try {
            String id = type.getId();
            return (id == null || id.isBlank()) ? type.toString() : id;
        } catch (Exception e) {
            return type.toString();
        }
    }
}
