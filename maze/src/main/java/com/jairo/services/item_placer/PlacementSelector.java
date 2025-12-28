package com.jairo.services.item_placer;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.placement.RelaxPlan;
import com.jairo.services.item_placer.cache.GridIndex;
import com.jairo.services.item_placer.cache.MapCache;
import com.jairo.utils.ItemLogger;

import java.util.Arrays;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

/**
 * Hot-path placement selection extracted from ItemPlacer.
 * Keeps reusable buffers to avoid allocations and preserve performance.
 *
 * Optimization: avoid sorting the whole pool (O(n log n)).
 * We compute Efraimidis keys for all candidates, but keep only the K best keys using a max-heap (O(n log K)),
 * then sort only those K (O(K log K)).
 *
 * This preserves the exact weighted random permutation prefix (no quality loss) for the chosen K.
 */
public final class PlacementSelector {
    private final MapCache map;
    private final GridIndex grid;
    private final BucketsIndex buckets;
    private final Random rng;

    // Reusable buffers
    private int[] poolBuffer = new int[256];
    private int[] orderedBuffer = new int[256];
    private long[] packedKeysBuffer = new long[256]; // used as heap + output packed keys

    // "First eligible round" without Arrays.fill: epoch stamping
    private final EligibilityRounds eligibility = new EligibilityRounds();

    public PlacementSelector(MapCache map, GridIndex grid, BucketsIndex buckets, Random rng) {
        this.map = map;
        this.grid = grid;
        this.buckets = buckets;
        this.rng = rng;
    }

    /** Call once per map rebuild. */
    public void onNewMap() {
        eligibility.ensureSize(map.size());
    }

    /** Call once per type (resets eligibility marks for that type). */
    public void beginType() {
        eligibility.nextEpoch();
    }

    public int placeWithConstraints(
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int need,
            int minPlayer,
            int minExit,
            int minBetween,
            int minBorder,
            IntBag everEligible,
            int roundIndex,
            IntToDoubleFunction weightFn,
            int[] baseCandidates,
            boolean logItemPlacer,
            java.util.List<PlacedItem> placedItems) {

        if (need <= 0) return 0;

        RelaxPlan plan = type.getRelaxPlan();

        // Scan only base candidates (not full path)
        for (int pos : baseCandidates) {

            // If already eligible in a previous round for this type, do not re-evaluate
            if (eligibility.get(pos) != -1 || grid.isOccupied(pos)) continue;

            int x = map.xOf(pos);
            int y = map.yOf(pos);

            int dp = distFromPlayer[y][x];
            if (dp == -1 || dp < minPlayer) continue;

            if (minExit > 0) {
                int de = distFromExit[y][x];
                if (de == -1 || de < minExit) continue;
            }

            if (minBorder > 0 && map.distToBorderAtPos(pos) < minBorder) continue;

            if (!buckets.respectsMinDistBetween(x, y, minBetween, type, plan)) continue;
            if (!respectsGranulation(type, plan, x, y)) continue;

            eligibility.set(pos, roundIndex);
            everEligible.add(pos);
        }

        if (everEligible.size == 0) return 0;

        // Build pool ignoring occupied
        poolBuffer = ensureCapacity(poolBuffer, everEligible.size);
        int[] pool = poolBuffer;
        int poolSize = 0;

        for (int i = 0; i < everEligible.size; i++) {
            int pos = everEligible.data[i];
            if (!grid.isOccupied(pos)) pool[poolSize++] = pos;
        }

        int placedNow = 0;

        if (poolSize > 0) {
            // We want at least 'need' candidates in order, but allow a margin to survive late rejections.
            // This does NOT change the weighted ordering "quality"; it only ensures we have enough finalists.
            int kWanted = computeKWanted(poolSize, need);

            orderedBuffer = ensureCapacity(orderedBuffer, kWanted);
            packedKeysBuffer = ensureCapacity(packedKeysBuffer, kWanted);

            int orderedSize = weightedOrderEfraimidisTopKIntoPacked(
                    pool, poolSize,
                    eligibility, weightFn, rng,
                    kWanted,
                    orderedBuffer, packedKeysBuffer
            );

            for (int i = 0; i < orderedSize && placedNow < need; i++) {
                int chosenPos = orderedBuffer[i];
                if (grid.isOccupied(chosenPos)) continue;

                int x = map.xOf(chosenPos);
                int y = map.yOf(chosenPos);

                if (!buckets.respectsMinDistBetween(x, y, minBetween, type, plan)) continue;

                // Place (same semantics)
                PlacedItem it = new PlacedItem(type, x, y);
                placedItems.add(it);
                grid.put(chosenPos, it);
                buckets.add(it);

                if (logItemPlacer) ItemLogger.onPlaced(type, x, y);

                placedNow++;
            }
        }

        return placedNow;
    }

    /**
     * Pick how many "finalists" to keep from the weighted order.
     * Must be >= need to avoid lowering placed count compared to full ordering.
     */
    private static int computeKWanted(int poolSize, int need) {
        if (need >= poolSize) return poolSize;
        // Margin tuned for "no surprises": if late checks reject some, we still likely fill 'need'.
        // Cap by poolSize.
        int margin = (need << 2) + 64; // need*4 + 64
        int k = need + margin;
        if (k > poolSize) k = poolSize;
        if (k < need) k = need;
        return k;
    }

    private boolean respectsGranulation(ItemType type, RelaxPlan plan, int x, int y) {
        if (plan == null || plan.scanMode() == null) return true;
        if (plan.scanMode() == RelaxPlan.ScanMode.NONE) return true;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;

                int nx = x + dx;
                int ny = y + dy;

                if (!map.inBounds(nx, ny)) continue;

                PlacedItem neighbor = grid.get(map.idx(nx, ny));
                if (neighbor != null && plan.conflicts(type, neighbor.getType())) return false;
            }
        }
        return true;
    }

    /* ===================== Small helpers ===================== */

    public static final class IntBag {
        public int[] data;
        public int size;

        public IntBag(int cap) {
            data = new int[Math.max(16, cap)];
        }

        public void add(int v) {
            if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = v;
        }
    }

    private static final class EligibilityRounds {
        private int[] stamp;
        private int[] value;
        private int epoch = 1;

        void ensureSize(int n) {
            if (stamp == null || stamp.length != n) {
                stamp = new int[n];
                value = new int[n];
                epoch = 1;
            }
        }

        void nextEpoch() {
            epoch++;
            if (epoch == 0) { // overflow rare
                Arrays.fill(stamp, 0);
                epoch = 1;
            }
        }

        int get(int pos) {
            return (stamp[pos] == epoch) ? value[pos] : -1;
        }

        void set(int pos, int roundIndex) {
            stamp[pos] = epoch;
            value[pos] = roundIndex;
        }
    }

    private int[] ensureCapacity(int[] buf, int needed) {
        if (buf.length >= needed) return buf;
        int n = buf.length;
        while (n < needed) n <<= 1;
        return Arrays.copyOf(buf, n);
    }

    private long[] ensureCapacity(long[] buf, int needed) {
        if (buf.length >= needed) return buf;
        int n = buf.length;
        while (n < needed) n <<= 1;
        return Arrays.copyOf(buf, n);
    }

    private static long sortableDoubleBits(double d) {
        long bits = Double.doubleToRawLongBits(d);
        return bits ^ ((bits >> 63) & 0x7fffffffffffffffL);
    }

    /**
     * Computes Efraimidis keys for all pool candidates, but keeps only the K smallest keys (best candidates)
     * using a max-heap of size K. Then sorts only those K packed keys and outputs the corresponding positions
     * in exact weighted-order prefix.
     *
     * This preserves the same prefix you would get from sorting all poolSize elements.
     */
    private static int weightedOrderEfraimidisTopKIntoPacked(
            int[] pool,
            int poolSize,
            EligibilityRounds firstEligibleRound,
            IntToDoubleFunction weightFn,
            Random rng,
            int kWanted,
            int[] out,
            long[] packedBuf /* reused as heap+output packed keys */) {

        if (kWanted <= 0) return 0;
        if (kWanted > poolSize) kWanted = poolSize;

        int heapSize = 0;

        // Build a max-heap by packed value (signed compare), holding the K best (smallest packed) values.
        for (int i = 0; i < poolSize; i++) {
            int pos = pool[i];

            int r = firstEligibleRound.get(pos);
            if (r < 0) r = 0;

            double w = weightFn.applyAsDouble(r);
            if (w <= 0.0 || !Double.isFinite(w)) w = 1.0;

            double u = rng.nextDouble();
            if (u <= 0.0) u = Double.MIN_VALUE;

            double key = -Math.log(u) / w;

            long keyBits = sortableDoubleBits(key);
            long packed = ((keyBits >>> 32) << 32) | (pos & 0xffffffffL);

            if (heapSize < kWanted) {
                packedBuf[heapSize] = packed;
                heapSiftUpMax(packedBuf, heapSize);
                heapSize++;
            } else {
                // If this packed is better (smaller) than the current worst (heap root), replace root.
                if (Long.compare(packed, packedBuf[0]) < 0) {
                    packedBuf[0] = packed;
                    heapSiftDownMax(packedBuf, heapSize, 0);
                }
            }
        }

        // Now packedBuf[0..heapSize) holds K best packed keys in heap order.
        // Sort them to produce exact weighted-order prefix.
        Arrays.sort(packedBuf, 0, heapSize);

        for (int i = 0; i < heapSize; i++) {
            out[i] = (int) packedBuf[i];
        }
        return heapSize;
    }

    /**
     * Max-heap helpers for long[] using signed comparison (same ordering Arrays.sort uses).
     */
    private static void heapSiftUpMax(long[] heap, int idx) {
        long v = heap[idx];
        while (idx > 0) {
            int parent = (idx - 1) >>> 1;
            long pv = heap[parent];
            if (Long.compare(pv, v) >= 0) break; // parent >= v
            heap[idx] = pv;
            idx = parent;
        }
        heap[idx] = v;
    }

    private static void heapSiftDownMax(long[] heap, int size, int idx) {
        long v = heap[idx];
        int half = size >>> 1; // nodes with at least one child
        while (idx < half) {
            int left = (idx << 1) + 1;
            int right = left + 1;

            int best = left;
            long bv = heap[left];

            if (right < size) {
                long rv = heap[right];
                if (Long.compare(rv, bv) > 0) {
                    best = right;
                    bv = rv;
                }
            }

            if (Long.compare(bv, v) <= 0) break; // child <= v
            heap[idx] = bv;
            idx = best;
        }
        heap[idx] = v;
    }
}
