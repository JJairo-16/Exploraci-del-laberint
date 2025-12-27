package com.jairo.services;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.items.placement.Constraint;
import com.jairo.items.placement.RelaxPlan;
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

    private final List<PlacedItem> placedItems = new ArrayList<>();
    private final Set<Long> occupied = new HashSet<>();
    private final Map<Long, PlacedItem> itemsByPos = new HashMap<>();

    private final List<Long> pathPositions = new ArrayList<>();
    private int cachedW = -1;
    private int cachedH = -1;

    /* ===================== API ===================== */

    public void placeObjects(
            List<List<Integer>> cells,
            int playerX,
            int playerY,
            int exitX,
            int exitY,
            List<ItemType> types) {

        placedItems.clear();
        occupied.clear();
        itemsByPos.clear();

        occupied.add(pack(playerX, playerY));

        buildPathCache(cells);
        int pathCount = pathPositions.size();

        int[][] distFromPlayer = bfsFrom(cells, playerX, playerY);
        int[][] distFromExit = bfsFromExit(cells, exitX, exitY);

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
    }

    public List<PlacedItem> getPlacedItems() {
        return Collections.unmodifiableList(placedItems);
    }

    public List<PlacedItem> getPlacedItems(int minX, int minY, int maxX, int maxY) {
        if (placedItems.isEmpty()) {
            return Collections.emptyList();
        }

        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int hiY = Math.max(minY, maxY);

        long width = (long) hiX - (long) loX + 1L;
        long height = (long) hiY - (long) loY + 1L;
        long area = (width <= 0 || height <= 0) ? 0 : width * height;

        if (area > 0 && area <= placedItems.size() * 2L) {
            List<PlacedItem> res = new ArrayList<>();
            for (int y = loY; y <= hiY; y++) {
                for (int x = loX; x <= hiX; x++) {
                    PlacedItem it = itemsByPos.get(pack(x, y));
                    if (it != null)
                        res.add(it);
                }
            }
            return Collections.unmodifiableList(res);
        }

        List<PlacedItem> res = new ArrayList<>();
        for (PlacedItem it : placedItems) {
            int x = it.getX();
            int y = it.getY();
            if (x >= loX && x <= hiX && y >= loY && y <= hiY) {
                res.add(it);
            }
        }
        return Collections.unmodifiableList(res);
    }

    public PlacedItem getItemAt(int x, int y) {
        return itemsByPos.get(pack(x, y));
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

        // Ponderación por “primera ronda elegible”
        Map<Long, Integer> firstEligibleRound = new HashMap<>();
        RelaxPlan plan = type.getRelaxPlan();
        IntToDoubleFunction weightFn = plan.weightFunction();

        int placed = 0;

        // Ronda 0: restricciones completas
        int roundIndex = 0;
        placed += placeWithConstraints(
                cells, distFromPlayer, distFromExit,
                type, amount - placed,
                s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                firstEligibleRound, roundIndex, weightFn);

        if (placed >= amount)
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
                for (Constraint c : order) {
                    changed |= relaxOne(c, plan, s);
                }
            }

            if (!changed) {
                stall++;
                if (stall > maxStall)
                    break;
                continue;
            } else {
                stall = 0;
            }

            roundIndex = round + 1;

            placed += placeWithConstraints(
                    cells, distFromPlayer, distFromExit,
                    type, amount - placed,
                    s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                    firstEligibleRound, roundIndex, weightFn);
        }
    }

    /**
     * Mantiene el nombre original.
     * Ahora hace selección ponderada por la primera ronda en la que el candidato
     * fue elegible.
     */
    private int placeWithConstraints(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int need,
            int minPlayer,
            int minExit,
            int minBetween,
            int minBorder,
            Map<Long, Integer> firstEligibleRound,
            int roundIndex,
            IntToDoubleFunction weightFn) {

        if (need <= 0)
            return 0;

        RelaxPlan plan = type.getRelaxPlan();
        List<Long> candidatesNow = collectCandidatesPacked(
                cells, distFromPlayer, distFromExit,
                type, plan,
                type.getSpawnBlacklist(),
                minPlayer, minExit, minBetween, minBorder);

        if (candidatesNow.isEmpty() && firstEligibleRound.isEmpty())
            return 0;

        for (Long p : candidatesNow) {
            firstEligibleRound.putIfAbsent(p, roundIndex);
        }

        // Pool = todos los que alguna vez fueron elegibles, ignorando ya ocupados
        List<Long> pool = new ArrayList<>();
        for (Long p : firstEligibleRound.keySet()) {
            if (!occupied.contains(p))
                pool.add(p);
        }

        int placedNow = 0;

        while (placedNow < need && !pool.isEmpty()) {
            long chosen = weightedPickByFirstRound(pool, firstEligibleRound, weightFn, RNG);

            pool.remove(Long.valueOf(chosen));

            if (occupied.contains(chosen))
                continue;

            int x = (int) chosen;
            int y = (int) (chosen >>> 32);

            if (!respectsMinDistBetween(x, y, minBetween))
                continue;

            if (!respectsMinDistBetween(x, y, minBetween))
                continue;

            place(type, x, y);
            placedNow++;
        }

        return placedNow;
    }

    private static long weightedPickByFirstRound(
            List<Long> pool,
            Map<Long, Integer> firstEligibleRound,
            IntToDoubleFunction weightFn,
            Random rng) {

        double total = 0.0;

        for (Long p : pool) {
            int r = firstEligibleRound.getOrDefault(p, 0);
            double w = weightFn.applyAsDouble(r);
            if (w > 0 && Double.isFinite(w))
                total += w;
        }

        if (total < 0.0) {
            return pool.get(rng.nextInt(pool.size()));
        }

        double t = rng.nextDouble() * total;

        for (Long p : pool) {
            int r = firstEligibleRound.getOrDefault(p, 0);
            double w = weightFn.applyAsDouble(r);
            if (total <= 0.0 || !Double.isFinite(w))
                continue;

            t -= w;
            if (t <= 0)
                return p;
        }

        return pool.get(pool.size() - 1);
    }

    private void place(ItemType type, int x, int y) {
        PlacedItem it = new PlacedItem(type, x, y);
        placedItems.add(it);
        long key = pack(x, y);
        occupied.add(key);
        itemsByPos.put(key, it);
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

    /* ===================== Candidate collection ===================== */

    private List<Long> collectCandidatesPacked(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            RelaxPlan plan,
            List<Integer> spawnBlackList,
            int minDistPlayer,
            int minDistExit,
            int minDistBetween,
            int minDistBorder) {

        Set<Integer> black = (spawnBlackList == null || spawnBlackList.isEmpty())
                ? Collections.emptySet()
                : new HashSet<>(spawnBlackList);

        List<Long> res = new ArrayList<>();

        for (Long p : pathPositions) {
            long pos = p;
            int x = (int) pos;
            int y = (int) (pos >>> 32);

            int cellValue = cells.get(y).get(x);
            if (black.contains(cellValue))
                continue;
            if (occupied.contains(pos))
                continue;

            int dp = distFromPlayer[y][x];
            if (dp == -1 || dp < minDistPlayer)
                continue;

            if (minDistExit > 0) {
                int de = distFromExit[y][x];
                if (de == -1 || de < minDistExit)
                    continue;
            }

            if (minDistBorder > 0) {
                int distToBorder = Math.min(
                        Math.min(x, y),
                        Math.min(cachedW - 1 - x, cachedH - 1 - y));
                if (distToBorder < minDistBorder)
                    continue;
            }

            if (!respectsMinDistBetween(x, y, minDistBetween))
                continue;

            if (!respectsGranulation(type, plan, x, y))
                continue;

            res.add(pos);
        }

        return res;
    }

    private boolean respectsMinDistBetween(int x, int y, int minDistBetween) {
        if (minDistBetween <= 0)
            return true;

        for (PlacedItem it : placedItems) {
            int dx = Math.abs(it.getX() - x);
            int dy = Math.abs(it.getY() - y);
            if (dx + dy < minDistBetween)
                return false;
        }
        return true;
    }

    /* ===================== Utils ===================== */

    private void buildPathCache(List<List<Integer>> cells) {
        pathPositions.clear();

        int h = cells.size();
        int w = cells.isEmpty() ? 0 : cells.get(0).size();
        cachedW = w;
        cachedH = h;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (Cells.isPath(cells.get(y).get(x))) {
                    pathPositions.add(pack(x, y));
                }
            }
        }
    }

    private int computeAmountForType(ItemType type, int pathCount) {
        double raw = pathCount * Math.max(0.0, type.getDensity());
        int amount = (int) Math.round(raw);
        amount = Math.max(amount, type.getMinCount());
        amount = Math.min(amount, type.getMaxCount());
        return amount;
    }

    private int[][] bfsFrom(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        int[][] dist = new int[h][w];
        for (int y = 0; y < h; y++)
            Arrays.fill(dist[y], -1);

        if (sx < 0 || sy < 0 || sx >= w || sy >= h)
            return dist;
        if (!Cells.isPath(cells.get(sy).get(sx)))
            return dist;

        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[sy][sx] = 0;
        q.add(new int[] { sx, sy });

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h)
                    continue;
                if (dist[ny][nx] != -1)
                    continue;
                if (!Cells.isPath(cells.get(ny).get(nx)))
                    continue;
                dist[ny][nx] = dist[p[1]][p[0]] + 1;
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
            for (int[] row : dist)
                Arrays.fill(row, -1);
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

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (Cells.isPath(cells.get(p[1]).get(p[0])))
                return p;

            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h)
                    continue;
                if (vis[ny][nx])
                    continue;
                vis[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return null;
    }

    private static long pack(int x, int y) {
        return (((long) y) << 32) ^ (x & 0xffffffffL);
    }

    public int removeAllOfType(ItemType type) {
        if (type == null || placedItems.isEmpty())
            return 0;

        int removed = 0;

        for (int i = placedItems.size() - 1; i >= 0; i--) {
            PlacedItem it = placedItems.get(i);
            if (it.getType() == type) {
                long key = pack(it.getX(), it.getY());
                placedItems.remove(i);
                itemsByPos.remove(key);
                occupied.remove(key);
                removed++;
            }
        }

        return removed;
    }

    public int removeAllOfTypeExcept(ItemType type, int keepX, int keepY) {
        if (type == null || placedItems.isEmpty())
            return 0;

        long keepKey = pack(keepX, keepY);
        int removed = 0;

        for (int i = placedItems.size() - 1; i >= 0; i--) {
            PlacedItem it = placedItems.get(i);
            if (it.getType() != type)
                continue;

            long key = pack(it.getX(), it.getY());
            if (key == keepKey)
                continue;

            placedItems.remove(i);
            itemsByPos.remove(key);
            occupied.remove(key);
            removed++;
        }

        return removed;
    }

    public int countPlacedItemsOf(ItemType item) {
        return (int) placedItems.stream()
                .filter(i -> i.getType() == item)
                .count();
    }

    public List<int[]> getPositionsOf(ItemType type) {
        List<int[]> res = new ArrayList<>();
        for (PlacedItem it : placedItems) {
            if (it.getType() == type) {
                res.add(new int[] { it.getX(), it.getY() });
            }
        }
        return res;
    }

    public PlacedItem pickupAt(int x, int y) {
        long key = pack(x, y);
        PlacedItem it = getItemAt(x, y);

        if (it == null)
            return null;

        if (it.getType().getIfRemovePlaced()) {
            itemsByPos.remove(key);
            placedItems.removeIf(pi -> pi.getX() == x && pi.getY() == y);
            occupied.remove(key);
        }

        return it;
    }

    public PlacedItem peekAt(int x, int y) {
        return itemsByPos.get(pack(x, y));
    }

    private boolean respectsGranulation(ItemType type, RelaxPlan plan, int x, int y) {
        if (plan == null || plan.scanMode() == null)
            return true;

        RelaxPlan.ScanMode mode = plan.scanMode();
        if (mode == RelaxPlan.ScanMode.NONE)
            return true;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;

                int nx = x + dx;
                int ny = y + dy;

                // si quieres ignorar fuera de mapa, compruébalo:
                if (nx < 0 || ny < 0 || nx >= cachedW || ny >= cachedH)
                    continue;

                PlacedItem neighbor = itemsByPos.get(pack(nx, ny));
                if (neighbor == null)
                    continue;

                ItemType neighborType = neighbor.getType();
                if (plan.conflicts(type, neighborType))
                    return false;
            }
        }
        return true;
    }

    public boolean anyPlaced(ItemType item) {
        return placedItems.stream()
                .anyMatch(it -> it.getType() == item);
    }

}
