package com.jairo.services;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.placement.Constraint;
import com.jairo.items.placement.RelaxPlan;
import com.jairo.services.item_placer.PathDistanceCalculator;
import com.jairo.services.item_placer.cache.GridIndex;
import com.jairo.services.item_placer.cache.MapCache;
import com.jairo.utils.ItemLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

public class ItemPlacer {
    private static final Random RNG = new Random();
    private boolean logItemPlacer = true;

    // ===== Original public API backing fields (kept for compatibility) =====
    private final List<PlacedItem> placedItems = new ArrayList<>();

    // ===== Extracted objects =====
    private final MapCache map = new MapCache();
    private final GridIndex grid = new GridIndex();
    private final PathDistanceCalculator distanceCalc = new PathDistanceCalculator();

    // ===== Per-type caches =====
    private final Map<ItemType, int[]> baseCandidatesByType = new HashMap<>();
    private final Map<ItemType, Set<Integer>> blacklistCache = new HashMap<>();

    // ===== Bucket grid (for minDistBetween) =====
    private int bucketSize = 6; // fixed; does not change results
    private int bucketsW, bucketsH;
    private ArrayList<PlacedItem>[] buckets;

    // Buffers reutilizables (crecen si hace falta)
    private int[] poolBuffer = new int[256];
    private int[] orderedBuffer = new int[256];

    // ✅ PUNTO 1 (ya aplicado): buffer reutilizable para claves empaquetadas (evita objetos WeightedKey)
    private long[] packedKeysBuffer = new long[256];

    // ✅ PUNTO 2: evitar Arrays.fill(firstEligibleRound, -1) con "epoch stamping"
    private final EligibilityRounds eligibility = new EligibilityRounds();

    private int bX(int x) {
        return x / bucketSize;
    }

    private int bY(int y) {
        return y / bucketSize;
    }

    private int bIdx(int bx, int by) {
        return by * bucketsW + bx;
    }

    @SuppressWarnings("unchecked")
    private void initBuckets() {
        bucketsW = (map.w() + bucketSize - 1) / bucketSize;
        bucketsH = (map.h() + bucketSize - 1) / bucketSize;
        buckets = (ArrayList<PlacedItem>[]) new ArrayList[bucketsW * bucketsH];
    }

    /* ===================== API (UNCHANGED) ===================== */

    public void placeObjects(
            List<List<Integer>> cells,
            int playerX,
            int playerY,
            int exitX,
            int exitY,
            List<ItemType> types) {

        if (logItemPlacer)
            ItemLogger.reset();

        placedItems.clear();
        baseCandidatesByType.clear();
        blacklistCache.clear();

        // Rebuild map caches
        map.rebuild(cells);

        // Init grid for this map
        grid.init(map.size());
        grid.clear();

        // Init buckets
        initBuckets();

        // ✅ PUNTO 2: asegurar arrays de elegibilidad al tamaño del mapa (una vez por ejecución)
        eligibility.ensureSize(map.size());

        // Occupy player position (same semantics as before)
        if (map.inBounds(playerX, playerY)) {
            grid.setOccupied(map.idx(playerX, playerY), true);
        }

        int pathCount = map.pathCount();

        int[][] distFromPlayer = distanceCalc.distFrom(cells, playerX, playerY);
        int[][] distFromExit = distanceCalc.distFromExit(cells, exitX, exitY);

        // Prebuild base candidates per type (invariant filtering)
        for (ItemType type : types) {
            baseCandidatesByType.put(type, buildBaseCandidatesForType(type, distFromPlayer));
        }

        List<ItemType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator
                .comparingInt(ItemType::getMinDistBetweenItems).reversed()
                .thenComparingInt(ItemType::getMinDistFromPlayer).reversed()
                .thenComparingInt(ItemType::getMinDistFromExit).reversed()
                .thenComparingInt(ItemType::getMinDistFromBorder).reversed()
                .thenComparingDouble(ItemType::getDensity).reversed());

        for (ItemType type : sorted) {
            int amount = computeAmountForType(type, pathCount);
            if (amount > 0) {
                placeTypeGuaranteeing(distFromPlayer, distFromExit, type, amount);
            }
        }

        if (logItemPlacer)
            ItemLogger.summary();
    }

    public List<PlacedItem> getPlacedItems() {
        return Collections.unmodifiableList(placedItems);
    }

    public List<PlacedItem> getPlacedItems(int minX, int minY, int maxX, int maxY) {
        if (placedItems.isEmpty())
            return Collections.emptyList();

        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int hiY = Math.max(minY, maxY);

        long width = (long) hiX - (long) loX + 1L;
        long height = (long) hiY - (long) loY + 1L;
        long area = (width <= 0 || height <= 0) ? 0 : width * height;

        // Prefer direct cell scan when the query rectangle is small
        if (area > 0 && area <= placedItems.size() * 2L) {
            List<PlacedItem> res = new ArrayList<>();
            for (int y = loY; y <= hiY; y++) {
                if (y < 0 || y >= map.h())
                    continue;
                for (int x = loX; x <= hiX; x++) {
                    if (x < 0 || x >= map.w())
                        continue;
                    PlacedItem it = grid.get(map.idx(x, y));
                    if (it != null)
                        res.add(it);
                }
            }
            return Collections.unmodifiableList(res);
        }

        int cap = (int) Math.min(area, Integer.MAX_VALUE);
        List<PlacedItem> res = new ArrayList<>(cap);
        for (PlacedItem it : placedItems) {
            int x = it.getX();
            int y = it.getY();
            if (x >= loX && x <= hiX && y >= loY && y <= hiY)
                res.add(it);
        }
        return Collections.unmodifiableList(res);
    }

    public PlacedItem getItemAt(int x, int y) {
        if (!map.inBounds(x, y))
            return null;
        return grid.get(map.idx(x, y));
    }

    // ===== Original public methods preserved (even if not used internally) =====

    public int removeAllOfType(ItemType type) {
        if (type == null || placedItems.isEmpty())
            return 0;

        int removed = 0;
        for (int i = placedItems.size() - 1; i >= 0; i--) {
            PlacedItem it = placedItems.get(i);
            if (it.getType() == type) {
                removeOne(it);
                placedItems.remove(i);
                removed++;
            }
        }
        return removed;
    }

    public int removeAllOfTypeExcept(ItemType type, int keepX, int keepY) {
        if (type == null || placedItems.isEmpty())
            return 0;

        PlacedItem keep = (map.inBounds(keepX, keepY)) ? grid.get(map.idx(keepX, keepY)) : null;

        int removed = 0;
        for (int i = placedItems.size() - 1; i >= 0; i--) {
            PlacedItem it = placedItems.get(i);
            if (it.getType() != type)
                continue;
            if (it == keep)
                continue;

            removeOne(it);
            placedItems.remove(i);
            removed++;
        }
        return removed;
    }

    public int countPlacedItemsOf(ItemType item) {
        int c = 0;
        for (PlacedItem it : placedItems)
            if (it.getType() == item)
                c++;
        return c;
    }

    public List<int[]> getPositionsOf(ItemType type) {
        List<int[]> res = new ArrayList<>();
        for (PlacedItem it : placedItems) {
            if (it.getType() == type)
                res.add(new int[] { it.getX(), it.getY() });
        }
        return res;
    }

    public PlacedItem pickupAt(int x, int y) {
        if (!map.inBounds(x, y))
            return null;

        PlacedItem it = getItemAt(x, y);
        if (it == null)
            return null;

        if (it.getType().getIfRemovePlaced()) {
            // Remove from placedItems without changing semantics
            for (int i = placedItems.size() - 1; i >= 0; i--) {
                if (placedItems.get(i).getX() == x && placedItems.get(i).getY() == y) {
                    placedItems.remove(i);
                    break;
                }
            }
            removeOne(it);
        }

        return it;
    }

    public PlacedItem peekAt(int x, int y) {
        return getItemAt(x, y);
    }

    public boolean anyPlaced(ItemType item) {
        for (PlacedItem it : placedItems)
            if (it.getType() == item)
                return true;
        return false;
    }

    /* ===================== Core placement ===================== */

    private static final class ConstraintsState {
        int minPlayer;
        int minBetween;
        int minExit;
        int minBorder;

        int sincePlayer;
        int sinceBetween;
        int sinceExit;
        int sinceBorder;

        ConstraintsState(int player, int between, int exit, int border) {
            this.minPlayer = player;
            this.minBetween = between;
            this.minExit = exit;
            this.minBorder = border;

            this.sincePlayer = Integer.MAX_VALUE / 4;
            this.sinceBetween = Integer.MAX_VALUE / 4;
            this.sinceExit = Integer.MAX_VALUE / 4;
            this.sinceBorder = Integer.MAX_VALUE / 4;
        }

        void tickRound() {
            if (sincePlayer < Integer.MAX_VALUE / 2)
                sincePlayer++;
            if (sinceBetween < Integer.MAX_VALUE / 2)
                sinceBetween++;
            if (sinceExit < Integer.MAX_VALUE / 2)
                sinceExit++;
            if (sinceBorder < Integer.MAX_VALUE / 2)
                sinceBorder++;
        }

        boolean canRelax(Constraint c, int cooldown) {
            return switch (c) {
                case PLAYER -> sincePlayer >= cooldown;
                case BETWEEN -> sinceBetween >= cooldown;
                case EXIT -> sinceExit >= cooldown;
                case BORDER -> sinceBorder >= cooldown;
            };
        }

        void markRelaxed(Constraint c) {
            switch (c) {
                case PLAYER -> sincePlayer = 0;
                case BETWEEN -> sinceBetween = 0;
                case EXIT -> sinceExit = 0;
                case BORDER -> sinceBorder = 0;
            }
        }
    }

    private void placeTypeGuaranteeing(
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int amount) {

        ConstraintsState s = new ConstraintsState(
                Math.max(0, type.getMinDistFromPlayer()),
                Math.max(0, type.getMinDistBetweenItems()),
                Math.max(0, type.getMinDistFromExit()),
                Math.max(0, type.getMinDistFromBorder()));

        RelaxPlan plan = type.getRelaxPlan();
        IntToDoubleFunction weightFn = (plan == null || plan.weightFunction() == null)
                ? (r -> 1.0)
                : plan.weightFunction();

        // ✅ PUNTO 2: "reset" lógico sin Arrays.fill
        eligibility.nextEpoch();

        IntBag everEligible = new IntBag(256);
        int[] base = baseCandidatesByType.getOrDefault(type, new int[0]);

        int placed = 0;

        // Round 0
        placed += placeWithConstraints(
                distFromPlayer, distFromExit,
                type, amount - placed,
                s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                eligibility, everEligible, 0, weightFn, base);

        if (placed >= amount)
            return;
        if (plan == null)
            return;

        List<Constraint> order = plan.order();
        int idx = 0;
        int stall = 0;
        int maxStall = plan.maxStallRounds();

        for (int round = 0; placed < amount && round < plan.maxRounds(); round++) {
            s.tickRound();

            boolean changed = false;

            if (plan.mode() == RelaxPlan.Mode.ONE_PER_ROUND) {
                for (int tries = 0; tries < order.size(); tries++) {
                    Constraint c = order.get(idx);
                    idx = (idx + 1) % order.size();
                    if (relaxOne(c, plan, s)) {
                        changed = true;
                        break;
                    }
                }
            } else {
                for (Constraint c : order)
                    changed |= relaxOne(c, plan, s);
            }

            if (!changed) {
                stall++;
                if (stall > maxStall)
                    break;
                continue;
            } else {
                stall = 0;
            }

            int roundIndex = round + 1;

            placed += placeWithConstraints(
                    distFromPlayer, distFromExit,
                    type, amount - placed,
                    s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                    eligibility, everEligible, roundIndex, weightFn, base);
        }
    }

    private int placeWithConstraints(
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int need,
            int minPlayer,
            int minExit,
            int minBetween,
            int minBorder,
            EligibilityRounds firstEligibleRound,
            IntBag everEligible,
            int roundIndex,
            IntToDoubleFunction weightFn,
            int[] baseCandidates) {

        if (need <= 0)
            return 0;

        RelaxPlan plan = type.getRelaxPlan();

        // Scan only base candidates (not full path)
        for (int pos : baseCandidates) {

            // ✅ PUNTO 2: si ya fue elegible en esta "época", no lo reevaluamos
            if (firstEligibleRound.get(pos) != -1)
                continue;

            if (grid.isOccupied(pos))
                continue;

            int x = map.xOf(pos);
            int y = map.yOf(pos);

            int dp = distFromPlayer[y][x];
            if (dp == -1 || dp < minPlayer)
                continue;

            if (minExit > 0) {
                int de = distFromExit[y][x];
                if (de == -1 || de < minExit)
                    continue;
            }

            if (minBorder > 0 && map.distToBorderAtPos(pos) < minBorder)
                continue;

            if (!respectsMinDistBetween(x, y, minBetween, type, plan))
                continue;
            if (!respectsGranulation(type, plan, x, y))
                continue;

            firstEligibleRound.set(pos, roundIndex);
            everEligible.add(pos);
        }

        if (everEligible.size == 0)
            return 0;

        // Build pool, ignoring occupied
        poolBuffer = ensureCapacity(poolBuffer, everEligible.size);
        int[] pool = poolBuffer;
        int poolSize = 0;

        for (int i = 0; i < everEligible.size; i++) {
            int pos = everEligible.data[i];
            if (!grid.isOccupied(pos))
                pool[poolSize++] = pos;
        }

        int placedNow = 0;

        if (poolSize > 0) {
            orderedBuffer = ensureCapacity(orderedBuffer, poolSize);
            packedKeysBuffer = ensureCapacity(packedKeysBuffer, poolSize);

            int orderedSize = weightedOrderEfraimidisIntoPacked(
                    pool, poolSize,
                    firstEligibleRound, weightFn, RNG,
                    orderedBuffer, packedKeysBuffer);

            for (int i = 0; i < orderedSize && placedNow < need; i++) {
                int chosenPos = orderedBuffer[i];
                if (grid.isOccupied(chosenPos))
                    continue;

                int x = map.xOf(chosenPos);
                int y = map.yOf(chosenPos);

                if (!respectsMinDistBetween(x, y, minBetween, type, plan))
                    continue;

                place(type, x, y);
                placedNow++;
            }
        }

        return placedNow;
    }

    private void place(ItemType type, int x, int y) {
        PlacedItem it = new PlacedItem(type, x, y);
        placedItems.add(it);

        int pos = map.idx(x, y);
        grid.put(pos, it);

        // buckets
        int bi = bIdx(bX(x), bY(y));
        ArrayList<PlacedItem> list = buckets[bi];
        if (list == null)
            buckets[bi] = list = new ArrayList<>();
        list.add(it);

        if (logItemPlacer)
            ItemLogger.onPlaced(type, x, y);
    }

    /* ===================== Relaxation helpers ===================== */

    private boolean relaxOne(Constraint c, RelaxPlan plan, ConstraintsState s) {
        int step = plan.step(c);
        int floor = plan.floor(c);
        int cooldown = plan.cooldown(c);

        if (!s.canRelax(c, cooldown))
            return false;

        boolean changed = switch (c) {
            case PLAYER -> decPlayer(s, step, floor);
            case BETWEEN -> decBetween(s, step, floor);
            case EXIT -> decExit(s, step, floor);
            case BORDER -> decBorder(s, step, floor);
        };

        if (changed)
            s.markRelaxed(c);
        return changed;
    }

    private boolean decPlayer(ConstraintsState s, int step, int floor) {
        if (s.minPlayer <= floor)
            return false;
        int next = Math.max(floor, s.minPlayer - step);
        if (next == s.minPlayer)
            return false;
        s.minPlayer = next;
        return true;
    }

    private boolean decBetween(ConstraintsState s, int step, int floor) {
        if (s.minBetween <= floor)
            return false;
        int next = Math.max(floor, s.minBetween - step);
        if (next == s.minBetween)
            return false;
        s.minBetween = next;
        return true;
    }

    private boolean decExit(ConstraintsState s, int step, int floor) {
        if (s.minExit <= floor)
            return false;
        int next = Math.max(floor, s.minExit - step);
        if (next == s.minExit)
            return false;
        s.minExit = next;
        return true;
    }

    private boolean decBorder(ConstraintsState s, int step, int floor) {
        if (s.minBorder <= floor)
            return false;
        int next = Math.max(floor, s.minBorder - step);
        if (next == s.minBorder)
            return false;
        s.minBorder = next;
        return true;
    }

    /* ===================== Candidate base ===================== */

    private int[] buildBaseCandidatesForType(ItemType type, int[][] distFromPlayer) {
        Set<Integer> black = getBlacklist(type);
        int[] path = map.pathPositions();

        IntBag bag = new IntBag(Math.max(64, path.length / 4));

        for (int pos : path) {
            if (!black.isEmpty() && black.contains(map.cellValueAtPos(pos)))
                continue;

            int x = map.xOf(pos);
            int y = map.yOf(pos);

            if (distFromPlayer[y][x] == -1)
                continue;

            bag.add(pos);
        }

        return Arrays.copyOf(bag.data, bag.size);
    }

    private Set<Integer> getBlacklist(ItemType type) {
        List<Integer> bl = type.getSpawnBlacklist();
        if (bl == null || bl.isEmpty())
            return Collections.emptySet();
        return blacklistCache.computeIfAbsent(type, t -> new HashSet<>(bl));
    }

    /* ===================== Between + Granulation ===================== */

    private boolean respectsMinDistBetween(int x, int y, int minDistBetween, ItemType type, RelaxPlan plan) {
        if (minDistBetween <= 0)
            return true;

        int cbx = bX(x), cby = bY(y);
        int r = (minDistBetween + bucketSize - 1) / bucketSize;

        for (int oy = -r; oy <= r; oy++) {
            int by = cby + oy;
            if (by < 0 || by >= bucketsH)
                continue;

            for (int ox = -r; ox <= r; ox++) {
                int bx = cbx + ox;
                if (bx < 0 || bx >= bucketsW)
                    continue;

                ArrayList<PlacedItem> list = buckets[bIdx(bx, by)];
                if (list == null)
                    continue;

                for (PlacedItem it : list) {
                    if (plan != null && !plan.distConflicts(type, it.getType()))
                        continue;

                    int d = Math.abs(it.getX() - x) + Math.abs(it.getY() - y);
                    if (d < minDistBetween)
                        return false;
                }
            }
        }
        return true;
    }

    private boolean respectsGranulation(ItemType type, RelaxPlan plan, int x, int y) {
        if (plan == null || plan.scanMode() == null)
            return true;
        if (plan.scanMode() == RelaxPlan.ScanMode.NONE)
            return true;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;

                int nx = x + dx;
                int ny = y + dy;

                if (!map.inBounds(nx, ny))
                    continue;

                PlacedItem neighbor = grid.get(map.idx(nx, ny));
                if (neighbor != null && plan.conflicts(type, neighbor.getType()))
                    return false;
            }
        }
        return true;
    }

    /* ===================== Utils ===================== */

    private int computeAmountForType(ItemType type, int pathCount) {
        double raw = pathCount * Math.max(0.0, type.getDensity());
        int amount = (int) Math.round(raw);
        amount = Math.max(amount, type.getMinCount());
        amount = Math.min(amount, type.getMaxCount());
        return amount;
    }

    /* ===================== Removals backing ===================== */

    private void removeOne(PlacedItem it) {
        int x = it.getX();
        int y = it.getY();
        if (!map.inBounds(x, y))
            return;

        int pos = map.idx(x, y);
        grid.remove(pos);
        removeFromBucket(it);
    }

    private void removeFromBucket(PlacedItem it) {
        int bi = bIdx(bX(it.getX()), bY(it.getY()));
        ArrayList<PlacedItem> list = buckets[bi];
        if (list != null)
            list.remove(it);
    }

    /* ===================== Tiny int bag ===================== */

    private static final class IntBag {
        int[] data;
        int size;

        IntBag(int cap) {
            data = new int[Math.max(16, cap)];
        }

        void add(int v) {
            if (size == data.length)
                data = Arrays.copyOf(data, data.length * 2);
            data[size++] = v;
        }
    }

    /* ===================== PUNTO 2: Epoch stamping helper ===================== */

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
            if (epoch == 0) { // overflow raro
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

    /* ===================== Buffers ===================== */

    private int[] ensureCapacity(int[] buf, int needed) {
        if (buf.length >= needed)
            return buf;
        int n = buf.length;
        while (n < needed)
            n <<= 1;
        return Arrays.copyOf(buf, n);
    }

    private long[] ensureCapacity(long[] buf, int needed) {
        if (buf.length >= needed)
            return buf;
        int n = buf.length;
        while (n < needed)
            n <<= 1;
        return Arrays.copyOf(buf, n);
    }

    /* ===================== PUNTO 1: Weighted order (sin objetos) ===================== */

    private static long sortableDoubleBits(double d) {
        long bits = Double.doubleToRawLongBits(d);
        return bits ^ ((bits >> 63) & 0x7fffffffffffffffL);
    }

    private static int weightedOrderEfraimidisIntoPacked(
            int[] pool,
            int poolSize,
            EligibilityRounds firstEligibleRound,
            IntToDoubleFunction weightFn,
            Random rng,
            int[] out,
            long[] packedBuf) {

        for (int i = 0; i < poolSize; i++) {
            int pos = pool[i];

            int r = firstEligibleRound.get(pos);
            if (r < 0)
                r = 0;

            double w = weightFn.applyAsDouble(r);
            if (w <= 0.0 || !Double.isFinite(w))
                w = 1.0;

            double u = rng.nextDouble();
            if (u <= 0.0)
                u = Double.MIN_VALUE;

            double key = -Math.log(u) / w;

            long keyBits = sortableDoubleBits(key);
            long packed = ((keyBits >>> 32) << 32) | (pos & 0xffffffffL);
            packedBuf[i] = packed;
        }

        Arrays.sort(packedBuf, 0, poolSize);

        for (int i = 0; i < poolSize; i++) {
            out[i] = (int) packedBuf[i];
        }
        return poolSize;
    }
}
