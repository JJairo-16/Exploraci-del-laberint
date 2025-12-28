package com.jairo.services;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.placement.Constraint;
import com.jairo.items.placement.RelaxPlan;
import com.jairo.utils.ItemLogger;
import com.jairo.utils.map_generator.Cells;

import java.util.ArrayDeque;
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

    private final List<PlacedItem> placedItems = new ArrayList<>();

    // Fast grid lookup (replaces occupied HashSet + itemsByPos HashMap)
    private boolean[] occ;          // occupied?
    private PlacedItem[] at;        // item at cell
    private int cachedW = -1;
    private int cachedH = -1;

    // Precomputed per-cell data (hot path)
    private int[] cellValue;        // cells[y][x] flattened
    private int[] distToBorder;     // precomputed min distance to border for each pos

    // Path cells as linear positions (y*W + x)
    private final IntBag pathPositions = new IntBag(1024);

    // Base candidates per type (filters that never change during placement)
    private final Map<ItemType, int[]> baseCandidatesByType = new HashMap<>();
    private final Map<ItemType, Set<Integer>> blacklistCache = new HashMap<>();

    // Buckets (for minDistBetween acceleration)
    private int bucketSize = 6; // fixed; does not change results
    private int bucketsW, bucketsH;
    private ArrayList<PlacedItem>[] buckets;

    /* ===================== API ===================== */

    public void placeObjects(
            List<List<Integer>> cells,
            int playerX,
            int playerY,
            int exitX,
            int exitY,
            List<ItemType> types) {

        if (logItemPlacer) ItemLogger.reset();

        placedItems.clear();
        baseCandidatesByType.clear();
        blacklistCache.clear();

        // Build path cache + dimensions
        buildPathCache(cells);

        // Init arrays for this map
        initGrid();
        initBuckets();

        // Precompute flattened cell values + border distances (big win)
        buildCellCaches(cells);

        // Mark player cell as occupied (same behavior as before)
        if (inBounds(playerX, playerY)) {
            occ[idx(playerX, playerY)] = true;
        }

        int pathCount = pathPositions.size;

        int[][] distFromPlayer = bfsFrom(cells, playerX, playerY);
        int[][] distFromExit = bfsFromExit(cells, exitX, exitY);

        // Prebuild baseCandidates for each type once (big win)
        for (ItemType type : types) {
            baseCandidatesByType.put(type, buildBaseCandidatesForType(type, distFromPlayer, distFromExit));
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
                placeTypeGuaranteeing(cells, distFromPlayer, distFromExit, type, amount);
            }
        }

        if (logItemPlacer) ItemLogger.summary();
    }

    public List<PlacedItem> getPlacedItems() {
        return Collections.unmodifiableList(placedItems);
    }

    public List<PlacedItem> getPlacedItems(int minX, int minY, int maxX, int maxY) {
        if (placedItems.isEmpty()) return Collections.emptyList();

        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int hiY = Math.max(minY, maxY);

        long width = (long) hiX - (long) loX + 1L;
        long height = (long) hiY - (long) loY + 1L;
        long area = (width <= 0 || height <= 0) ? 0 : width * height;

        // If query area is small, scan cells directly using at[]
        if (area > 0 && area <= placedItems.size() * 2L) {
            List<PlacedItem> res = new ArrayList<>();
            for (int y = loY; y <= hiY; y++) {
                if (y < 0 || y >= cachedH) continue;
                for (int x = loX; x <= hiX; x++) {
                    if (x < 0 || x >= cachedW) continue;
                    PlacedItem it = at[idx(x, y)];
                    if (it != null) res.add(it);
                }
            }
            return Collections.unmodifiableList(res);
        }

        int cap = (int) Math.min(area, Integer.MAX_VALUE);
        List<PlacedItem> res = new ArrayList<>(cap);
        for (PlacedItem it : placedItems) {
            int x = it.getX();
            int y = it.getY();
            if (x >= loX && x <= hiX && y >= loY && y <= hiY) res.add(it);
        }
        return Collections.unmodifiableList(res);
    }

    public PlacedItem getItemAt(int x, int y) {
        if (!inBounds(x, y)) return null;
        return at[idx(x, y)];
    }

    public PlacedItem peekAt(int x, int y) {
        return getItemAt(x, y);
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
            if (sincePlayer < Integer.MAX_VALUE / 2) sincePlayer++;
            if (sinceBetween < Integer.MAX_VALUE / 2) sinceBetween++;
            if (sinceExit < Integer.MAX_VALUE / 2) sinceExit++;
            if (sinceBorder < Integer.MAX_VALUE / 2) sinceBorder++;
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
            List<List<Integer>> cells,
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

        // firstEligibleRound indexed by pos (0..W*H-1), -1 means never eligible
        int[] firstEligibleRound = new int[cachedW * cachedH];
        Arrays.fill(firstEligibleRound, -1);
        IntBag everEligible = new IntBag(256);

        int placed = 0;

        int[] base = baseCandidatesByType.get(type);
        if (base == null) base = new int[0];

        // Round 0
        placed += placeWithConstraints(
                distFromPlayer, distFromExit,
                type, amount - placed,
                s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                firstEligibleRound, everEligible, 0, weightFn,
                base);

        if (placed >= amount) return;
        if (plan == null) return;

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
                for (Constraint c : order) changed |= relaxOne(c, plan, s);
            }

            if (!changed) {
                stall++;
                if (stall > maxStall) break;
                continue;
            } else {
                stall = 0;
            }

            int roundIndex = round + 1;

            placed += placeWithConstraints(
                    distFromPlayer, distFromExit,
                    type, amount - placed,
                    s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                    firstEligibleRound, everEligible, roundIndex, weightFn,
                    base);
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
            int[] firstEligibleRound,
            IntBag everEligible,
            int roundIndex,
            IntToDoubleFunction weightFn,
            int[] baseCandidates) {

        if (need <= 0) return 0;

        RelaxPlan plan = type.getRelaxPlan();

        // Scan ONLY baseCandidates (not full path) and mark eligibility
        for (int i = 0; i < baseCandidates.length; i++) {
            int pos = baseCandidates[i];
            if (occ[pos]) continue;

            int x = pos % cachedW;
            int y = pos / cachedW;

            int dp = distFromPlayer[y][x];
            if (dp == -1 || dp < minPlayer) continue;

            if (minExit > 0) {
                int de = distFromExit[y][x];
                if (de == -1 || de < minExit) continue;
            }

            if (minBorder > 0) {
                if (distToBorder[pos] < minBorder) continue;
            }

            if (!respectsMinDistBetween(x, y, minBetween, type, plan)) continue;
            if (!respectsGranulation(type, plan, x, y)) continue;

            if (firstEligibleRound[pos] == -1) {
                firstEligibleRound[pos] = roundIndex;
                everEligible.add(pos);
            }
        }

        if (everEligible.size == 0) return 0;

        // Build pool as int[] (swap-remove), ignoring occupied
        int[] pool = new int[everEligible.size];
        int poolSize = 0;
        for (int i = 0; i < everEligible.size; i++) {
            int pos = everEligible.data[i];
            if (!occ[pos]) pool[poolSize++] = pos;
        }

        int placedNow = 0;

        while (placedNow < need && poolSize > 0) {
            int pickIndex = weightedPickIndexByFirstRound(pool, poolSize, firstEligibleRound, weightFn, RNG);
            int chosenPos = pool[pickIndex];

            // swap-remove
            pool[pickIndex] = pool[poolSize - 1];
            poolSize--;

            if (occ[chosenPos]) continue;

            int x = chosenPos % cachedW;
            int y = chosenPos / cachedW;

            if (!respectsMinDistBetween(x, y, minBetween, type, plan)) continue;

            place(type, x, y);
            placedNow++;
        }

        return placedNow;
    }

    private static int weightedPickIndexByFirstRound(
            int[] pool,
            int poolSize,
            int[] firstEligibleRound,
            IntToDoubleFunction weightFn,
            Random rng) {

        double total = 0.0;
        for (int i = 0; i < poolSize; i++) {
            int r = firstEligibleRound[pool[i]];
            if (r < 0) r = 0;
            double w = weightFn.applyAsDouble(r);
            if (w > 0.0 && Double.isFinite(w)) total += w;
        }

        if (!(total > 0.0) || !Double.isFinite(total)) {
            return rng.nextInt(poolSize);
        }

        double t = rng.nextDouble() * total;
        for (int i = 0; i < poolSize; i++) {
            int r = firstEligibleRound[pool[i]];
            if (r < 0) r = 0;
            double w = weightFn.applyAsDouble(r);
            if (w <= 0.0 || !Double.isFinite(w)) continue;
            t -= w;
            if (t <= 0.0) return i;
        }

        return poolSize - 1;
    }

    private void place(ItemType type, int x, int y) {
        PlacedItem it = new PlacedItem(type, x, y);
        placedItems.add(it);

        int p = idx(x, y);
        occ[p] = true;
        at[p] = it;

        // buckets
        int bi = bIdx(bX(x), bY(y));
        ArrayList<PlacedItem> list = buckets[bi];
        if (list == null) buckets[bi] = list = new ArrayList<>();
        list.add(it);

        if (logItemPlacer) ItemLogger.onPlaced(type, x, y);
    }

    /* ===================== Relaxation helpers ===================== */

    private boolean relaxOne(Constraint c, RelaxPlan plan, ConstraintsState s) {
        int step = plan.step(c);
        int floor = plan.floor(c);
        int cooldown = plan.cooldown(c);

        if (!s.canRelax(c, cooldown)) return false;

        boolean changed = switch (c) {
            case PLAYER -> decPlayer(s, step, floor);
            case BETWEEN -> decBetween(s, step, floor);
            case EXIT -> decExit(s, step, floor);
            case BORDER -> decBorder(s, step, floor);
        };

        if (changed) s.markRelaxed(c);
        return changed;
    }

    private boolean decPlayer(ConstraintsState s, int step, int floor) {
        if (s.minPlayer <= floor) return false;
        int next = Math.max(floor, s.minPlayer - step);
        if (next == s.minPlayer) return false;
        s.minPlayer = next;
        return true;
    }

    private boolean decBetween(ConstraintsState s, int step, int floor) {
        if (s.minBetween <= floor) return false;
        int next = Math.max(floor, s.minBetween - step);
        if (next == s.minBetween) return false;
        s.minBetween = next;
        return true;
    }

    private boolean decExit(ConstraintsState s, int step, int floor) {
        if (s.minExit <= floor) return false;
        int next = Math.max(floor, s.minExit - step);
        if (next == s.minExit) return false;
        s.minExit = next;
        return true;
    }

    private boolean decBorder(ConstraintsState s, int step, int floor) {
        if (s.minBorder <= floor) return false;
        int next = Math.max(floor, s.minBorder - step);
        if (next == s.minBorder) return false;
        s.minBorder = next;
        return true;
    }

    /* ===================== Between + Granulation checks ===================== */

    private boolean respectsMinDistBetween(int x, int y, int minDistBetween, ItemType type, RelaxPlan plan) {
        if (minDistBetween <= 0) return true;

        int cbx = bX(x), cby = bY(y);
        int r = (minDistBetween + bucketSize - 1) / bucketSize; // safe radius

        for (int oy = -r; oy <= r; oy++) {
            int by = cby + oy;
            if (by < 0 || by >= bucketsH) continue;

            for (int ox = -r; ox <= r; ox++) {
                int bx = cbx + ox;
                if (bx < 0 || bx >= bucketsW) continue;

                ArrayList<PlacedItem> list = buckets[bIdx(bx, by)];
                if (list == null) continue;

                for (PlacedItem it : list) {
                    if (plan != null && !plan.distConflicts(type, it.getType())) continue;

                    int d = Math.abs(it.getX() - x) + Math.abs(it.getY() - y);
                    if (d < minDistBetween) return false;
                }
            }
        }
        return true;
    }

    private boolean respectsGranulation(ItemType type, RelaxPlan plan, int x, int y) {
        if (plan == null || plan.scanMode() == null) return true;

        RelaxPlan.ScanMode mode = plan.scanMode();
        if (mode == RelaxPlan.ScanMode.NONE) return true;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;

                int nx = x + dx;
                int ny = y + dy;

                if (!inBounds(nx, ny)) continue;

                PlacedItem neighbor = at[idx(nx, ny)];
                if (neighbor == null) continue;

                if (plan.conflicts(type, neighbor.getType())) return false;
            }
        }
        return true;
    }

    /* ===================== Precompute base candidates ===================== */

    private int[] buildBaseCandidatesForType(ItemType type, int[][] distFromPlayer, int[][] distFromExit) {
        Set<Integer> black = getBlacklist(type);
        RelaxPlan plan = type.getRelaxPlan();

        // This base list includes only invariants:
        // - is path (already)
        // - not blacklisted for this type
        // - reachable from player (dp != -1)
        // - if exit distance is required at all, we can still keep de == -1 out (safe)
        //   BUT since minExit can be 0 in some rounds, we only filter out de == -1 if the type can ever require exit.
        //   Easiest safe choice: do NOT filter by exit reachability here (keep it for per-round check).
        // Result correctness is preserved either way.

        IntBag bag = new IntBag(Math.max(64, pathPositions.size / 4));

        for (int i = 0; i < pathPositions.size; i++) {
            int pos = pathPositions.data[i];

            // blacklist (invariant)
            if (!black.isEmpty() && black.contains(cellValue[pos])) continue;

            int x = pos % cachedW;
            int y = pos / cachedW;

            // reachable from player (invariant)
            if (distFromPlayer[y][x] == -1) continue;

            // (Optional) You can also prefilter exit reachability if you KNOW minExit will always be >0.
            // Here we keep it safe and defer to per-round check.

            bag.add(pos);
        }

        return Arrays.copyOf(bag.data, bag.size);
    }

    /* ===================== Utils / Caches ===================== */

    private void buildPathCache(List<List<Integer>> cells) {
        pathPositions.clear();

        cachedH = cells.size();
        cachedW = cells.isEmpty() ? 0 : cells.get(0).size();

        for (int y = 0; y < cachedH; y++) {
            for (int x = 0; x < cachedW; x++) {
                if (Cells.isPath(cells.get(y).get(x))) {
                    pathPositions.add(y * cachedW + x);
                }
            }
        }
    }

    private void buildCellCaches(List<List<Integer>> cells) {
        int n = cachedW * cachedH;
        cellValue = new int[n];
        distToBorder = new int[n];

        for (int y = 0; y < cachedH; y++) {
            List<Integer> row = cells.get(y);
            int top = y;
            int bottom = cachedH - 1 - y;

            for (int x = 0; x < cachedW; x++) {
                int pos = y * cachedW + x;

                cellValue[pos] = row.get(x);

                int left = x;
                int right = cachedW - 1 - x;
                distToBorder[pos] = Math.min(Math.min(left, right), Math.min(top, bottom));
            }
        }
    }

    private void initGrid() {
        int n = cachedW * cachedH;
        occ = new boolean[n];
        at = new PlacedItem[n];
    }

    private void initBuckets() {
        bucketsW = (cachedW + bucketSize - 1) / bucketSize;
        bucketsH = (cachedH + bucketSize - 1) / bucketSize;
        buckets = (ArrayList<PlacedItem>[]) new ArrayList[bucketsW * bucketsH];
    }

    private int computeAmountForType(ItemType type, int pathCount) {
        double raw = pathCount * Math.max(0.0, type.getDensity());
        int amount = (int) Math.round(raw);
        amount = Math.max(amount, type.getMinCount());
        amount = Math.min(amount, type.getMaxCount());
        return amount;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < cachedW && y < cachedH;
    }

    private int idx(int x, int y) {
        return y * cachedW + x;
    }

    private int bX(int x) { return x / bucketSize; }
    private int bY(int y) { return y / bucketSize; }
    private int bIdx(int bx, int by) { return by * bucketsW + bx; }

    private Set<Integer> getBlacklist(ItemType type) {
        List<Integer> bl = type.getSpawnBlacklist();
        if (bl == null || bl.isEmpty()) return Collections.emptySet();
        return blacklistCache.computeIfAbsent(type, t -> new HashSet<>(bl));
    }

    /* ===================== BFS ===================== */

    private static final int[] DX = { 1, -1, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1 };

    private int[][] bfsFrom(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        int[][] dist = new int[h][w];
        for (int y = 0; y < h; y++) Arrays.fill(dist[y], -1);

        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return dist;
        if (!Cells.isPath(cells.get(sy).get(sx))) return dist;

        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[sy][sx] = 0;
        q.add(new int[] { sx, sy });

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            int base = dist[py][px];

            for (int k = 0; k < 4; k++) {
                int nx = px + DX[k];
                int ny = py + DY[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (dist[ny][nx] != -1) continue;
                if (!Cells.isPath(cells.get(ny).get(nx))) continue;
                dist[ny][nx] = base + 1;
                q.add(new int[] { nx, ny });
            }
        }
        return dist;
    }

    private int[][] bfsFromExit(List<List<Integer>> cells, int exitX, int exitY) {
        if (exitX >= 0 && exitY >= 0 &&
                exitY < cells.size() &&
                exitX < cells.get(0).size() &&
                Cells.isPath(cells.get(exitY).get(exitX))) {
            return bfsFrom(cells, exitX, exitY);
        }

        int[] start = findNearestPathCell(cells, exitX, exitY);
        if (start == null) {
            int[][] dist = new int[cells.size()][cells.get(0).size()];
            for (int[] row : dist) Arrays.fill(row, -1);
            return dist;
        }
        return bfsFrom(cells, start[0], start[1]);
    }

    private int[] findNearestPathCell(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        boolean[][] vis = new boolean[h][w];
        ArrayDeque<int[]> q = new ArrayDeque<>();

        int cx = Math.max(0, Math.min(w - 1, sx));
        int cy = Math.max(0, Math.min(h - 1, sy));

        q.add(new int[] { cx, cy });
        vis[cy][cx] = true;

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];

            if (Cells.isPath(cells.get(py).get(px))) return p;

            for (int k = 0; k < 4; k++) {
                int nx = px + DX[k];
                int ny = py + DY[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (vis[ny][nx]) continue;
                vis[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return null;
    }

    /* ===================== Removals / queries ===================== */

    public int removeAllOfType(ItemType type) {
        if (type == null || placedItems.isEmpty()) return 0;

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
        if (type == null || placedItems.isEmpty()) return 0;

        PlacedItem keep = (inBounds(keepX, keepY)) ? at[idx(keepX, keepY)] : null;

        int removed = 0;
        for (int i = placedItems.size() - 1; i >= 0; i--) {
            PlacedItem it = placedItems.get(i);
            if (it.getType() != type) continue;
            if (it == keep) continue;

            removeOne(it);
            placedItems.remove(i);
            removed++;
        }
        return removed;
    }

    public int countPlacedItemsOf(ItemType item) {
        int c = 0;
        for (PlacedItem it : placedItems) if (it.getType() == item) c++;
        return c;
    }

    public List<int[]> getPositionsOf(ItemType type) {
        List<int[]> res = new ArrayList<>();
        for (PlacedItem it : placedItems) {
            if (it.getType() == type) res.add(new int[] { it.getX(), it.getY() });
        }
        return res;
    }

    public PlacedItem pickupAt(int x, int y) {
        if (!inBounds(x, y)) return null;
        PlacedItem it = at[idx(x, y)];
        if (it == null) return null;

        if (it.getType().getIfRemovePlaced()) {
            for (int i = placedItems.size() - 1; i >= 0; i--) {
                if (placedItems.get(i) == it) {
                    placedItems.remove(i);
                    break;
                }
            }
            removeOne(it);
        }

        return it;
    }

    public boolean anyPlaced(ItemType item) {
        for (PlacedItem it : placedItems) if (it.getType() == item) return true;
        return false;
    }

    private void removeOne(PlacedItem it) {
        int p = idx(it.getX(), it.getY());
        occ[p] = false;
        at[p] = null;
        removeFromBucket(it);
    }

    private void removeFromBucket(PlacedItem it) {
        int bi = bIdx(bX(it.getX()), bY(it.getY()));
        ArrayList<PlacedItem> list = buckets[bi];
        if (list != null) list.remove(it);
    }

    /* ===================== Small int bag ===================== */

    private static final class IntBag {
        int[] data;
        int size;

        IntBag(int initialCap) {
            data = new int[Math.max(16, initialCap)];
        }

        void add(int v) {
            if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = v;
        }

        void clear() {
            size = 0;
        }
    }
}
