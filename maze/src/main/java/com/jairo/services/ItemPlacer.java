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

import com.jairo.services.item_placer.BucketsIndex;
import com.jairo.services.item_placer.PlacementSelector;

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

    // ===== Buckets extracted =====
    private static final int BUCKET_SIZE = 6; // fixed; do not change mid-process
    private final BucketsIndex buckets = new BucketsIndex(BUCKET_SIZE);

    // ===== Core selection extracted =====
    private final PlacementSelector selector = new PlacementSelector(map, grid, buckets, RNG);

    /* ===================== API (UNCHANGED) ===================== */

    public int placeObjects(
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
        buckets.init(map.w(), map.h());

        // Selector map-dependent init
        selector.onNewMap();

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

        return placedItems.size();
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
            if (it.getType() != type || it == keep)
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

    /**
     * ✅ NEW:
     * If enabled by RelaxPlan, we can "hard-precheck" player distance by filtering candidates to
     * distFromPlayer >= minPlayer BEFORE calling selector.
     *
     * If hard precheck yields 0 candidates:
     * - if forcePlaceIfPrecheckFails == true  => return original base (so selector can still place something)
     * - if forcePlaceIfPrecheckFails == false => return empty (hard block)
     */
    private int[] precheckByPlayerDistance(
            int[] base,
            int[][] distFromPlayer,
            int minPlayer,
            boolean forcePlaceIfPrecheckFails,
            PlacementSelector.IntBag scratch) {

        if (base == null || base.length == 0)
            return new int[0];

        if (minPlayer <= 0)
            return base;

        scratch.size = 0;

        for (int pos : base) {
            int x = map.xOf(pos);
            int y = map.yOf(pos);
            int d = distFromPlayer[y][x];
            if (d >= minPlayer) scratch.add(pos);
        }

        if (scratch.size == 0) {
            return forcePlaceIfPrecheckFails ? base : new int[0];
        }

        return Arrays.copyOf(scratch.data, scratch.size);
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

        // extracted: reset eligibility for this type
        selector.beginType();

        PlacementSelector.IntBag everEligible = new PlacementSelector.IntBag(256);

        int[] base = baseCandidatesByType.getOrDefault(type, new int[0]);

        // ✅ NEW: flags read from RelaxPlan (you will implement these getters)
        boolean doPrecheck = (plan != null) && plan.precheckPlayerDistance();
        boolean forcePlaceIfPrecheckFails = (plan != null) && plan.forcePlaceIfPrecheckFails();

        // scratch for precheck filtering (only allocated when needed)
        PlacementSelector.IntBag precheckScratch = doPrecheck
                ? new PlacementSelector.IntBag(Math.max(256, base.length / 8))
                : null;

        int placed = 0;

        // Round 0
        int[] baseForRound0 = doPrecheck
                ? precheckByPlayerDistance(base, distFromPlayer, s.minPlayer, forcePlaceIfPrecheckFails, precheckScratch)
                : base;

        placed += selector.placeWithConstraints(
                distFromPlayer, distFromExit,
                type, amount - placed,
                s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                everEligible, 0, weightFn, baseForRound0,
                logItemPlacer, placedItems);

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

            int[] baseForThisRound = doPrecheck
                    ? precheckByPlayerDistance(base, distFromPlayer, s.minPlayer, forcePlaceIfPrecheckFails, precheckScratch)
                    : base;

            placed += selector.placeWithConstraints(
                    distFromPlayer, distFromExit,
                    type, amount - placed,
                    s.minPlayer, s.minExit, s.minBetween, s.minBorder,
                    everEligible, roundIndex, weightFn, baseForThisRound,
                    logItemPlacer, placedItems);
        }
    }

    /* ===================== Relaxation helpers (unchanged) ===================== */

    private boolean relaxOne(Constraint c, RelaxPlan plan, ConstraintsState s) {
        int step = plan.step(c);
        int floor = plan.floor(c);
        int cooldown = plan.cooldown(c);

        if (floor < 0)
            floor = 0;

        if (step < 1)
            step = 1;

        if (cooldown < 0)
            cooldown = 0;

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

    /* ===================== Candidate base (unchanged) ===================== */

    private int[] buildBaseCandidatesForType(ItemType type, int[][] distFromPlayer) {
        Set<Integer> black = getBlacklist(type);
        int[] path = map.pathPositions();

        PlacementSelector.IntBag bag = new PlacementSelector.IntBag(Math.max(64, path.length / 4));

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

    /* ===================== Utils ===================== */

    private int computeAmountForType(ItemType type, int pathCount) {
        double raw = pathCount * Math.max(0.0, type.getDensity());
        int amount = (int) Math.round(raw);
        amount = Math.max(amount, type.getMinCount());
        amount = Math.min(amount, type.getMaxCount());
        return amount;
    }

    /* ===================== Removals backing (adapted) ===================== */

    private void removeOne(PlacedItem it) {
        int x = it.getX();
        int y = it.getY();
        if (!map.inBounds(x, y))
            return;

        int pos = map.idx(x, y);
        grid.remove(pos);
        buckets.remove(it);
    }
}
