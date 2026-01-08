package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;
import java.util.Arrays;

import static com.jairo.utils.map_generator.Cells.*;

public final class DoorConstructor {
    private DoorConstructor() {}

    private static final int DOOR_OPEN_FROM_NORTH = 4;
    private static final int DOOR_OPEN_FROM_SOUTH = 5;
    private static final int DOOR_OPEN_FROM_WEST  = 6;
    private static final int DOOR_OPEN_FROM_EAST  = 7;

    private static final int MAX_BYPASS_DIST = 16;

    // -------------------- ✅ Packed position helpers (no int[]{x,y}) --------------------

    private static int pack(int x, int y, int w) { return y * w + x; }
    private static int unpackX(int p, int w) { return p % w; }
    private static int unpackY(int p, int w) { return p / w; }

    /**
     * Minimal int list with O(1) removeSwapLast and no boxing / no int[] allocations.
     */
    private static final class IntList {
        private int[] a;
        private int size;

        IntList(int initialCapacity) {
            this.a = new int[Math.max(4, initialCapacity)];
            this.size = 0;
        }

        int size() { return size; }
        boolean isEmpty() { return size == 0; }

        int get(int idx) { return a[idx]; }

        void add(int v) {
            if (size == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = v;
        }

        /**
         * Removes element at idx by swapping last into idx.
         */
        void removeSwapLast(int idx) {
            int last = size - 1;
            if (idx != last) a[idx] = a[last];
            size = last;
        }
    }

    // -------------------- ✅ Reusable BFS buffers (no per-BFS allocs) --------------------

    /**
     * Reusable BFS buffers.
     * - qPos is reused across BFS runs
     * - visited[] uses "stamps" (visited[pos] == stamp means visited), so clearing is O(1)
     * - qDist is optional (only when a BFS needs distances)
     */
    private static final class BfsScratch {
        final int w, h, n;
        final int[] qPos;
        final int[] qDist;    // null if not needed
        final int[] visited;  // stamp array
        int stamp = 1;

        BfsScratch(int w, int h, boolean withDist) {
            this.w = w;
            this.h = h;
            this.n = w * h;
            this.qPos = new int[n];
            this.qDist = withDist ? new int[n] : null;
            this.visited = new int[n];
        }

        int nextStamp() {
            int s = ++stamp;
            if (s == 0) { // overflow (extremely unlikely)
                Arrays.fill(visited, 0);
                stamp = 1;
                return 1;
            }
            return s;
        }
    }

    public static String addOneWayLockedDoors(String baseMap, int width, int height) {
        return addOneWayLockedDoors(baseMap, width, height, 0.015, 4, new SecureRandom());
    }

    public static String addOneWayLockedDoors(
            String baseMap,
            int width,
            int height,
            double density,
            int minSpacing,
            SecureRandom rnd) {

        int[][] g = parse(baseMap, width, height);

        // ✅ Scratch buffers created once per run (NO allocs inside BFS)
        BfsScratch bfsNoDist = new BfsScratch(width, height, false);
        BfsScratch bfsDist   = new BfsScratch(width, height, true);

        // ✅ Candidates and placed doors are packed ints (pos = y*w + x)
        IntList candidates = collectDoorCandidatesPacked(g, width, height);
        if (candidates.isEmpty()) return baseMap;

        int target = Math.max(1, (int) Math.round(countWalkable(g, width, height) * density));
        int placed = 0;

        IntList placedDoors = new IntList(Math.min(target, 256));

        int attempts = Math.min(candidates.size() * 3, 8000);

        // ✅ Compute once, then update incrementally
        boolean[][] reachableFromMain = computeReachableFromMain(g, bfsNoDist);

        for (int i = 0; i < attempts && placed < target && !candidates.isEmpty(); i++) {
            int idx = rnd.nextInt(candidates.size());
            int p = candidates.get(idx);

            int x = unpackX(p, width);
            int y = unpackY(p, width);

            if (tooClosePacked(p, placedDoors, minSpacing, width)) {
                candidates.removeSwapLast(idx);
                continue;
            }

            int openSide = pickOpenSideReachable(g, x, y, rnd, reachableFromMain, bfsNoDist, bfsDist);
            if (openSide == -1) {
                candidates.removeSwapLast(idx);
                continue;
            }

            // Place door
            g[y][x] = openSide;

            // ✅ Incremental reachable update (no full recompute, no allocs)
            updateReachableAfterDoorPlaced(g, reachableFromMain, x, y, openSide, bfsNoDist);

            placedDoors.add(p);
            placed++;
            candidates.removeSwapLast(idx);
        }

        return toMapDataString(g, width, height);
    }

    // -------------------- Core incremental reachability --------------------

    /**
     * Incrementally updates reachableFromMain after placing a new door at (x,y).
     * Correct here because doors are placed on WALL -> reachability can only increase.
     */
    private static void updateReachableAfterDoorPlaced(
            int[][] g, boolean[][] reach, int x, int y, int doorValue, BfsScratch bfsNoDist) {

        int h = g.length, w = g[0].length;

        // Entry cell (must already be reachable) and destination cell (becomes newly reachable).
        int ex = x, ey = y, tx = x, ty = y;

        switch (doorValue) {
            case DOOR_OPEN_FROM_NORTH -> { ex = x; ey = y - 1; tx = x; ty = y + 1; }
            case DOOR_OPEN_FROM_SOUTH -> { ex = x; ey = y + 1; tx = x; ty = y - 1; }
            case DOOR_OPEN_FROM_WEST  -> { ex = x - 1; ey = y; tx = x + 1; ty = y; }
            case DOOR_OPEN_FROM_EAST  -> { ex = x + 1; ey = y; tx = x - 1; ty = y; }
            default -> { return; }
        }

        if (!inBounds(ex, ey, w, h) || !inBounds(tx, ty, w, h)) return;

        // If entry isn't reachable, this door doesn't add new reachability from main.
        if (!reach[ey][ex]) return;

        if (!canMove(g, ex, ey, x, y)) return;
        if (!canMove(g, x, y, tx, ty)) return;

        // Mark the door cell reachable (optional but consistent).
        reach[y][x] = true;

        // If destination already reachable, nothing new to propagate.
        if (reach[ty][tx]) return;

        // ✅ Reuse queue (no allocation)
        propagateReachableFrom(g, reach, tx, ty, bfsNoDist);
    }

    /**
     * BFS that only visits cells not yet marked reachable in 'reach'.
     * ✅ Uses reusable queue from bfsNoDist (no allocation).
     */
    private static void propagateReachableFrom(int[][] g, boolean[][] reach, int sx, int sy, BfsScratch bfsNoDist) {
        int h = g.length, w = g[0].length;
        if (!inBounds(sx, sy, w, h)) return;
        if (reach[sy][sx]) return;
        if (!isPassable(g[sy][sx])) return;

        int[] q = bfsNoDist.qPos;
        int qi = 0, qj = 0;

        int start = sy * w + sx;
        q[qj++] = start;
        reach[sy][sx] = true;

        while (qi < qj) {
            int pos = q[qi++];
            int x = pos % w;
            int y = pos / w;

            // Right
            if (x + 1 < w) {
                int nx = x + 1, ny = y;
                if (!reach[ny][nx] && canMove(g, x, y, nx, ny)) {
                    reach[ny][nx] = true;
                    q[qj++] = ny * w + nx;
                }
            }
            // Left
            if (x - 1 >= 0) {
                int nx = x - 1, ny = y;
                if (!reach[ny][nx] && canMove(g, x, y, nx, ny)) {
                    reach[ny][nx] = true;
                    q[qj++] = ny * w + nx;
                }
            }
            // Down
            if (y + 1 < h) {
                int nx = x, ny = y + 1;
                if (!reach[ny][nx] && canMove(g, x, y, nx, ny)) {
                    reach[ny][nx] = true;
                    q[qj++] = ny * w + nx;
                }
            }
            // Up
            if (y - 1 >= 0) {
                int nx = x, ny = y - 1;
                if (!reach[ny][nx] && canMove(g, x, y, nx, ny)) {
                    reach[ny][nx] = true;
                    q[qj++] = ny * w + nx;
                }
            }
        }
    }

    // -------------------- Parsing / writing --------------------

    private static int[][] parse(String map, int width, int height) {
        int[][] g = new int[height][width];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char ch = map.charAt(i++);
                g[y][x] = ch - '0';
            }
        }
        return g;
    }

    private static String toMapDataString(int[][] g, int width, int height) {
        StringBuilder sb = new StringBuilder(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append((char) ('0' + g[y][x]));
            }
        }
        return sb.toString();
    }

    // -------------------- Cell predicates --------------------

    private static boolean isWalkable(int v) {
        return v == PATH || v == EXIT_CONNECTOR;
    }

    private static boolean isDoor(int v) {
        return v == DOOR_OPEN_FROM_NORTH
                || v == DOOR_OPEN_FROM_SOUTH
                || v == DOOR_OPEN_FROM_WEST
                || v == DOOR_OPEN_FROM_EAST;
    }

    private static boolean isPassable(int v) {
        return v == PATH || v == EXIT_CONNECTOR || isDoor(v);
    }

    private static boolean inBounds(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static int countWalkable(int[][] g, int width, int height) {
        int c = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isWalkable(g[y][x])) c++;
            }
        }
        return c;
    }

    // -------------------- Candidate collection (✅ packed) --------------------

    private static IntList collectDoorCandidatesPacked(int[][] g, int width, int height) {
        // Rough upper bound, avoids resizes for common cases (optional)
        IntList out = new IntList(Math.max(16, (width * height) / 16));

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int v = g[y][x];
                if (v != WALL || nearExit(g, x, y)) continue;

                boolean n = isWalkable(g[y - 1][x]);
                boolean s = isWalkable(g[y + 1][x]);
                boolean w = isWalkable(g[y][x - 1]);
                boolean e = isWalkable(g[y][x + 1]);

                boolean doorVertical = n && s && !w && !e;
                boolean doorHorizontal = w && e && !n && !s;

                if (doorVertical || doorHorizontal) out.add(pack(x, y, width));
            }
        }
        return out;
    }

    private static boolean nearExit(int[][] g, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int v = g[y + dy][x + dx];
                if (v == EXIT || v == EXIT_CONNECTOR) return true;
            }
        }
        return false;
    }

    // ✅ packed version of tooClose (same math, no int[] allocations)
    private static boolean tooClosePacked(int packedPos, IntList placedDoors, int minSpacing, int w) {
        int x = unpackX(packedPos, w);
        int y = unpackY(packedPos, w);

        int ms2 = minSpacing * minSpacing;
        for (int i = 0; i < placedDoors.size(); i++) {
            int p = placedDoors.get(i);
            int px = unpackX(p, w);
            int py = unpackY(p, w);

            int dx = px - x;
            int dy = py - y;
            if (dx * dx + dy * dy <= ms2) return true;
        }
        return false;
    }

    // -------------------- Reachability (full computation used only once) --------------------

    private static boolean[][] computeReachableFromMain(int[][] g, BfsScratch bfsNoDist) {
        int h = g.length;
        int w = g[0].length;

        boolean[][] reach = new boolean[h][w];

        int[] start = findMainStartWalkable(g, bfsNoDist);
        if (start.length == 0) return reach;

        propagateReachableFrom(g, reach, start[0], start[1], bfsNoDist);
        return reach;
    }

    private static int[] findMainStartWalkable(int[][] g, BfsScratch bfsNoDist) {
        int h = g.length, w = g[0].length;

        // Prefer EXIT_CONNECTOR
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g[y][x] == EXIT_CONNECTOR) return new int[] { x, y };
            }
        }

        // Otherwise pick largest walkable component (undirected, no doors)
        boolean[][] vis = new boolean[h][w]; // computed once (acceptable)
        int bestSize = 0;
        int bestX = -1, bestY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (vis[y][x]) continue;
                if (!isWalkable(g[y][x])) continue;

                int size = floodWalkableUndirected(g, x, y, vis, bfsNoDist);
                if (size > bestSize) {
                    bestSize = size;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        if (bestSize == 0) return new int[0];
        return new int[] { bestX, bestY };
    }

    private static int floodWalkableUndirected(int[][] g, int sx, int sy, boolean[][] vis, BfsScratch bfsNoDist) {
        int h = g.length, w = g[0].length;

        int[] q = bfsNoDist.qPos;
        int qi = 0, qj = 0;

        q[qj++] = sy * w + sx;
        vis[sy][sx] = true;

        int count = 0;

        while (qi < qj) {
            int pos = q[qi++];
            int x = pos % w;
            int y = pos / w;
            count++;

            if (x + 1 < w && !vis[y][x + 1] && isWalkable(g[y][x + 1])) {
                vis[y][x + 1] = true;
                q[qj++] = y * w + (x + 1);
            }
            if (x - 1 >= 0 && !vis[y][x - 1] && isWalkable(g[y][x - 1])) {
                vis[y][x - 1] = true;
                q[qj++] = y * w + (x - 1);
            }
            if (y + 1 < h && !vis[y + 1][x] && isWalkable(g[y + 1][x])) {
                vis[y + 1][x] = true;
                q[qj++] = (y + 1) * w + x;
            }
            if (y - 1 >= 0 && !vis[y - 1][x] && isWalkable(g[y - 1][x])) {
                vis[y - 1][x] = true;
                q[qj++] = (y - 1) * w + x;
            }
        }
        return count;
    }

    // -------------------- Door choice logic --------------------

    private static int pickOpenSideReachable(
            int[][] g, int x, int y,
            SecureRandom rnd,
            boolean[][] reachableFromMain,
            BfsScratch bfsNoDist,
            BfsScratch bfsDist) {

        boolean n = isPassable(g[y - 1][x]);
        boolean s = isPassable(g[y + 1][x]);
        boolean w = isPassable(g[y][x - 1]);
        boolean e = isPassable(g[y][x + 1]);

        boolean ud = n && s && !w && !e;
        boolean lr = w && e && !n && !s;

        if (ud == lr) return -1;

        final int minLocal = 6;
        int chosen = -1;

        if (ud) {
            boolean canFromNorth = reachableFromMain[y - 1][x];
            boolean canFromSouth = reachableFromMain[y + 1][x];

            if (!canFromNorth && !canFromSouth) return -1;

            int northScore = canFromNorth ? reachableScore(g, x, y - 1, bfsNoDist) : 0;
            int southScore = canFromSouth ? reachableScore(g, x, y + 1, bfsNoDist) : 0;

            if (northScore < minLocal && southScore < minLocal) return -1;

            if (canFromNorth && !canFromSouth) {
                chosen = DOOR_OPEN_FROM_NORTH;
            } else if (canFromSouth && !canFromNorth) {
                chosen = DOOR_OPEN_FROM_SOUTH;
            } else if (northScore == southScore) {
                chosen = rnd.nextBoolean() ? DOOR_OPEN_FROM_NORTH : DOOR_OPEN_FROM_SOUTH;
            } else {
                chosen = (northScore > southScore) ? DOOR_OPEN_FROM_NORTH : DOOR_OPEN_FROM_SOUTH;
            }
        } else {
            boolean canFromWest = reachableFromMain[y][x - 1];
            boolean canFromEast = reachableFromMain[y][x + 1];

            if (!canFromWest && !canFromEast) return -1;

            int westScore = canFromWest ? reachableScore(g, x - 1, y, bfsNoDist) : 0;
            int eastScore = canFromEast ? reachableScore(g, x + 1, y, bfsNoDist) : 0;

            if (westScore < minLocal && eastScore < minLocal) return -1;

            if (canFromWest && !canFromEast) {
                chosen = DOOR_OPEN_FROM_WEST;
            } else if (canFromEast && !canFromWest) {
                chosen = DOOR_OPEN_FROM_EAST;
            } else if (westScore == eastScore) {
                chosen = rnd.nextBoolean() ? DOOR_OPEN_FROM_WEST : DOOR_OPEN_FROM_EAST;
            } else {
                chosen = (westScore > eastScore) ? DOOR_OPEN_FROM_WEST : DOOR_OPEN_FROM_EAST;
            }
        }

        if (chosen != -1 && !isSensibleOneWayDoor(g, x, y, chosen, bfsNoDist, bfsDist)) return -1;
        return chosen;
    }

    private static int reachableScore(int[][] g, int sx, int sy, BfsScratch bfsNoDist) {
        int w = g[0].length;
        int h = g.length;

        if (!inBounds(sx, sy, w, h)) return 0;
        if (!isPassable(g[sy][sx])) return 0;

        int score = 0;
        int max = 64;

        int[] q = bfsNoDist.qPos;
        int[] visited = bfsNoDist.visited;
        int stamp = bfsNoDist.nextStamp();

        int qi = 0, qj = 0;

        int start = sy * w + sx;
        q[qj++] = start;
        visited[start] = stamp;

        while (qi < qj && score < max) {
            int pos = q[qi++];
            int x = pos % w;
            int y = pos / w;
            score++;

            // Right
            if (x + 1 < w) {
                int npos = y * w + (x + 1);
                if (visited[npos] != stamp && canMove(g, x, y, x + 1, y)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Left
            if (x - 1 >= 0) {
                int npos = y * w + (x - 1);
                if (visited[npos] != stamp && canMove(g, x, y, x - 1, y)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Down
            if (y + 1 < h) {
                int npos = (y + 1) * w + x;
                if (visited[npos] != stamp && canMove(g, x, y, x, y + 1)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Up
            if (y - 1 >= 0) {
                int npos = (y - 1) * w + x;
                if (visited[npos] != stamp && canMove(g, x, y, x, y - 1)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
        }

        return score;
    }

    // -------------------- Movement semantics --------------------

    private static boolean canMove(int[][] g, int x, int y, int nx, int ny) {
        int from = g[y][x];
        int to = g[ny][nx];

        if (!isPassable(to)) return false;

        int dx = nx - x;
        int dy = ny - y;

        // Leaving from a door: only towards its "closed side"
        if (isDoor(from)) {
            return switch (from) {
                case DOOR_OPEN_FROM_NORTH -> (dx == 0 && dy == 1);
                case DOOR_OPEN_FROM_SOUTH -> (dx == 0 && dy == -1);
                case DOOR_OPEN_FROM_WEST  -> (dx == 1 && dy == 0);
                case DOOR_OPEN_FROM_EAST  -> (dx == -1 && dy == 0);
                default -> false;
            };
        }

        // Entering a door: only from its open side
        if (isDoor(to)) {
            return switch (to) {
                case DOOR_OPEN_FROM_NORTH -> (dx == 0 && dy == 1);
                case DOOR_OPEN_FROM_SOUTH -> (dx == 0 && dy == -1);
                case DOOR_OPEN_FROM_WEST  -> (dx == 1 && dy == 0);
                case DOOR_OPEN_FROM_EAST  -> (dx == -1 && dy == 0);
                default -> false;
            };
        }

        return true;
    }

    // -------------------- Door quality filters (same rules, ✅ reuse BFS scratch) --------------------

    private static boolean isSensibleOneWayDoor(
            int[][] g, int x, int y, int doorValue,
            BfsScratch bfsNoDist,
            BfsScratch bfsDist) {

        final int MIN_DEST_REGION = 40;
        final int BRANCH_SEARCH_LIMIT = 20;
        final int MIN_BRANCH_DEGREE = 3;

        int old = g[y][x];
        g[y][x] = doorValue;

        int[] sides = doorSides(x, y, doorValue);
        int ax = sides[0], ay = sides[1], bx = sides[2], by = sides[3];

        int bypass = bypassDistanceWithoutDoorCell(g, ax, ay, bx, by, x, y, MAX_BYPASS_DIST, bfsDist);
        if (bypass != Integer.MAX_VALUE && bypass <= MAX_BYPASS_DIST) {
            g[y][x] = old;
            return false;
        }

        int tx = x, ty = y;
        switch (doorValue) {
            case DOOR_OPEN_FROM_NORTH -> ty = y + 1;
            case DOOR_OPEN_FROM_SOUTH -> ty = y - 1;
            case DOOR_OPEN_FROM_WEST  -> tx = x + 1;
            case DOOR_OPEN_FROM_EAST  -> tx = x - 1;
            default -> {
                g[y][x] = old;
                return false;
            }
        }

        int w = g[0].length, h = g.length;
        if (!inBounds(tx, ty, w, h) || !isPassable(g[ty][tx])) {
            g[y][x] = old;
            return false;
        }

        int region = directedFloodCount(g, tx, ty, MIN_DEST_REGION, bfsNoDist);
        if (region < MIN_DEST_REGION) {
            g[y][x] = old;
            return false;
        }

        if (!hasBranchingNearby(g, tx, ty, BRANCH_SEARCH_LIMIT, MIN_BRANCH_DEGREE, bfsNoDist)) {
            g[y][x] = old;
            return false;
        }

        g[y][x] = old;
        return true;
    }

    private static boolean hasBranchingNearby(int[][] g, int sx, int sy, int limit, int minDegree, BfsScratch bfsNoDist) {
        int w = g[0].length, h = g.length;

        int[] q = bfsNoDist.qPos;
        int[] visited = bfsNoDist.visited;
        int stamp = bfsNoDist.nextStamp();

        int qi = 0, qj = 0;

        int start = sy * w + sx;
        q[qj++] = start;
        visited[start] = stamp;

        int seen = 0;

        while (qi < qj && seen < limit) {
            int pos = q[qi++];
            int x = pos % w;
            int y = pos / w;
            seen++;

            int degree = 0;
            if (x + 1 < w && canMove(g, x, y, x + 1, y)) degree++;
            if (x - 1 >= 0 && canMove(g, x, y, x - 1, y)) degree++;
            if (y + 1 < h && canMove(g, x, y, x, y + 1)) degree++;
            if (y - 1 >= 0 && canMove(g, x, y, x, y - 1)) degree++;

            if (degree >= minDegree) return true;

            // Right
            if (x + 1 < w) {
                int npos = y * w + (x + 1);
                if (visited[npos] != stamp && canMove(g, x, y, x + 1, y)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Left
            if (x - 1 >= 0) {
                int npos = y * w + (x - 1);
                if (visited[npos] != stamp && canMove(g, x, y, x - 1, y)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Down
            if (y + 1 < h) {
                int npos = (y + 1) * w + x;
                if (visited[npos] != stamp && canMove(g, x, y, x, y + 1)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Up
            if (y - 1 >= 0) {
                int npos = (y - 1) * w + x;
                if (visited[npos] != stamp && canMove(g, x, y, x, y - 1)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
        }
        return false;
    }

    private static int directedFloodCount(int[][] g, int sx, int sy, int limit, BfsScratch bfsNoDist) {
        int w = g[0].length, h = g.length;

        int[] q = bfsNoDist.qPos;
        int[] visited = bfsNoDist.visited;
        int stamp = bfsNoDist.nextStamp();

        int qi = 0, qj = 0;

        int start = sy * w + sx;
        q[qj++] = start;
        visited[start] = stamp;

        int count = 0;

        while (qi < qj && count < limit) {
            int pos = q[qi++];
            int x = pos % w;
            int y = pos / w;
            count++;

            // Right
            if (x + 1 < w) {
                int npos = y * w + (x + 1);
                if (visited[npos] != stamp && canMove(g, x, y, x + 1, y)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Left
            if (x - 1 >= 0) {
                int npos = y * w + (x - 1);
                if (visited[npos] != stamp && canMove(g, x, y, x - 1, y)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Down
            if (y + 1 < h) {
                int npos = (y + 1) * w + x;
                if (visited[npos] != stamp && canMove(g, x, y, x, y + 1)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
            // Up
            if (y - 1 >= 0) {
                int npos = (y - 1) * w + x;
                if (visited[npos] != stamp && canMove(g, x, y, x, y - 1)) {
                    visited[npos] = stamp;
                    q[qj++] = npos;
                }
            }
        }
        return count;
    }

    private static int bypassDistanceWithoutDoorCell(
            int[][] g,
            int ax, int ay, int bx, int by,
            int doorX, int doorY,
            int maxDist,
            BfsScratch bfsDist) {

        int h = g.length, w = g[0].length;
        if (!inBounds(ax, ay, w, h) || !inBounds(bx, by, w, h)) return Integer.MAX_VALUE;
        if (!isPassable(g[ay][ax]) || !isPassable(g[by][bx])) return Integer.MAX_VALUE;

        int[] qPos = bfsDist.qPos;
        int[] qDist = bfsDist.qDist;
        int[] visited = bfsDist.visited;
        int stamp = bfsDist.nextStamp();

        int qi = 0, qj = 0;

        int start = ay * w + ax;
        qPos[qj] = start;
        qDist[qj] = 0;
        qj++;
        visited[start] = stamp;

        while (qi < qj) {
            int pos = qPos[qi];
            int d = qDist[qi];
            qi++;

            if (d > maxDist) continue;

            int x = pos % w;
            int y = pos / w;

            if (x == bx && y == by) return d;

            // 4-neighbors (ignores one-way semantics, only passable)
            // Right
            if (x + 1 < w) {
                int nx = x + 1, ny = y;
                int npos = ny * w + nx;
                if (visited[npos] != stamp && !(nx == doorX && ny == doorY) && isPassable(g[ny][nx])) {
                    visited[npos] = stamp;
                    qPos[qj] = npos;
                    qDist[qj] = d + 1;
                    qj++;
                }
            }
            // Left
            if (x - 1 >= 0) {
                int nx = x - 1, ny = y;
                int npos = ny * w + nx;
                if (visited[npos] != stamp && !(nx == doorX && ny == doorY) && isPassable(g[ny][nx])) {
                    visited[npos] = stamp;
                    qPos[qj] = npos;
                    qDist[qj] = d + 1;
                    qj++;
                }
            }
            // Down
            if (y + 1 < h) {
                int nx = x, ny = y + 1;
                int npos = ny * w + nx;
                if (visited[npos] != stamp && !(nx == doorX && ny == doorY) && isPassable(g[ny][nx])) {
                    visited[npos] = stamp;
                    qPos[qj] = npos;
                    qDist[qj] = d + 1;
                    qj++;
                }
            }
            // Up
            if (y - 1 >= 0) {
                int nx = x, ny = y - 1;
                int npos = ny * w + nx;
                if (visited[npos] != stamp && !(nx == doorX && ny == doorY) && isPassable(g[ny][nx])) {
                    visited[npos] = stamp;
                    qPos[qj] = npos;
                    qDist[qj] = d + 1;
                    qj++;
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    private static int[] doorSides(int x, int y, int doorValue) {
        return switch (doorValue) {
            case DOOR_OPEN_FROM_NORTH, DOOR_OPEN_FROM_SOUTH -> new int[] { x, y - 1, x, y + 1 };
            case DOOR_OPEN_FROM_WEST,  DOOR_OPEN_FROM_EAST  -> new int[] { x - 1, y, x + 1, y };
            default -> new int[] { 0, 0, 0, 0 };
        };
    }
}
