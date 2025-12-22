package com.jairo.utils.map_generator.map_modifier;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.jairo.utils.map_generator.Cells.*;

public final class DoorConstructor {
    private DoorConstructor() {
    }

    private static final int DOOR_OPEN_FROM_NORTH = 4;
    private static final int DOOR_OPEN_FROM_SOUTH = 5;
    private static final int DOOR_OPEN_FROM_WEST = 6;
    private static final int DOOR_OPEN_FROM_EAST = 7;

    private static final int MAX_BYPASS_DIST = 16;

    public static String addOneWayLockedDoors(String baseMap, int width, int height) {
        // Valores “buenos por defecto” (ajustados)
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

        List<int[]> candidates = collectDoorCandidates(g, width, height);
        if (candidates.isEmpty()) {
            return baseMap;
        }

        int target = Math.max(1, (int) Math.round(countWalkable(g, width, height) * density));
        int placed = 0;

        List<int[]> placedDoors = new ArrayList<>();
        int attempts = Math.min(candidates.size() * 3, 8000);

        // ✅ FIX: reachable desde el “main” debe arrancar en WALKABLE
        // (PATH/EXIT_CONNECTOR), no en puerta
        boolean[][] reachableFromMain = computeReachableFromMain(g);

        for (int i = 0; i < attempts && placed < target && !candidates.isEmpty(); i++) {
            int idx = rnd.nextInt(candidates.size());
            int[] c = candidates.get(idx);
            int x = c[0];
            int y = c[1];

            if (tooClose(x, y, placedDoors, minSpacing)) {
                candidates.remove(idx);
                continue;
            }

            int openSide = pickOpenSideReachable(g, x, y, rnd, reachableFromMain);
            if (openSide == -1) {
                candidates.remove(idx);
                continue;
            }

            g[y][x] = openSide;
            placedDoors.add(new int[] { x, y });
            placed++;
            candidates.remove(idx);

            // recalcular alcanzabilidad con puertas ya puestas
            reachableFromMain = computeReachableFromMain(g);
        }

        return toMapDataString(g, width, height);
    }

    private static int[][] parse(String map, int width, int height) {
        int[][] g = new int[height][width];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char ch = map.charAt(i++);
                int v = ch - '0';
                g[y][x] = v;
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
                if (isWalkable(g[y][x]))
                    c++;
            }
        }
        return c;
    }

    private static List<int[]> collectDoorCandidates(int[][] g, int width, int height) {
        List<int[]> out = new ArrayList<>();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int v = g[y][x];
                if (v != WALL || nearExit(g, x, y))
                    continue;

                boolean n = isWalkable(g[y - 1][x]);
                boolean s = isWalkable(g[y + 1][x]);
                boolean w = isWalkable(g[y][x - 1]);
                boolean e = isWalkable(g[y][x + 1]);

                boolean doorVertical = n && s && !w && !e;
                boolean doorHorizontal = w && e && !n && !s;

                if (doorVertical || doorHorizontal) {
                    out.add(new int[] { x, y });
                }
            }
        }
        return out;
    }

    private static boolean nearExit(int[][] g, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int v = g[y + dy][x + dx];
                if (v == EXIT || v == EXIT_CONNECTOR)
                    return true;
            }
        }
        return false;
    }

    private static boolean tooClose(int x, int y, List<int[]> placedDoors, int minSpacing) {
        int ms2 = minSpacing * minSpacing;
        for (int[] p : placedDoors) {
            int dx = p[0] - x;
            int dy = p[1] - y;
            if (dx * dx + dy * dy <= ms2)
                return true;
        }
        return false;
    }

    private static boolean[][] computeReachableFromMain(int[][] g) {
        int h = g.length;
        int w = g[0].length;

        boolean[][] reach = new boolean[h][w];

        int[] start = findMainStartWalkable(g); // ✅ aquí está la clave
        if (start.length == 0)
            return reach;

        int sx = start[0];
        int sy = start[1];

        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        reach[sy][sx] = true;

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1];

            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (reach[ny][nx])
                    continue;

                // Reachability “real” respetando puertas one-way
                if (!canMove(g, x, y, nx, ny))
                    continue;

                reach[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return reach;
    }

    private static int[] findMainStartWalkable(int[][] g) {
        int h = g.length, w = g[0].length;

        // (1) Preferir EXIT_CONNECTOR (si existe), porque es el “main” natural
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (g[y][x] == EXIT_CONNECTOR) {
                    return new int[] { x, y };
                }
            }
        }

        // (2) Si no hay EXIT_CONNECTOR, elegir la componente walkable más grande
        boolean[][] vis = new boolean[h][w];
        int bestSize = 0;
        int bestX = -1, bestY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (vis[y][x])
                    continue;
                if (!isWalkable(g[y][x]))
                    continue;

                int size = floodWalkableUndirected(g, x, y, vis);
                if (size > bestSize) {
                    bestSize = size;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        if (bestSize == 0)
            return new int[0];
        return new int[] { bestX, bestY };
    }

    /**
     * Flood-fill SIN puertas (solo WALKABLE) y SIN one-way (adyacencia normal),
     * para medir tamaño de componente principal.
     */
    private static int floodWalkableUndirected(int[][] g, int sx, int sy, boolean[][] vis) {
        int h = g.length, w = g[0].length;

        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        vis[sy][sx] = true;

        int count = 0;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1];
            count++;

            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (vis[ny][nx])
                    continue;
                if (!isWalkable(g[ny][nx]))
                    continue;

                vis[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return count;
    }

    private static int pickOpenSideReachable(int[][] g, int x, int y, SecureRandom rnd, boolean[][] reachableFromMain) {
        // Pared en (x,y) con pasillos a ambos lados en UNA sola orientación.
        // OJO: aquí usamos isPassable para tolerar puertas ya colocadas cerca.
        boolean n = isPassable(g[y - 1][x]);
        boolean s = isPassable(g[y + 1][x]);
        boolean w = isPassable(g[y][x - 1]);
        boolean e = isPassable(g[y][x + 1]);

        boolean ud = n && s && !w && !e; // corredor vertical
        boolean lr = w && e && !n && !s; // corredor horizontal

        if (ud == lr)
            return -1;

        // Ajuste fino: umbral local
        final int minLocal = 6;

        int chosen = -1;

        if (ud) {
            boolean canFromNorth = reachableFromMain[y - 1][x];
            boolean canFromSouth = reachableFromMain[y + 1][x];

            if (!canFromNorth && !canFromSouth)
                return -1;

            int northScore = canFromNorth ? reachableScore(g, x, y - 1) : 0;
            int southScore = canFromSouth ? reachableScore(g, x, y + 1) : 0;

            if (northScore < minLocal && southScore < minLocal)
                return -1;

            if (canFromNorth && !canFromSouth) {
                chosen = DOOR_OPEN_FROM_NORTH;
            } else if (canFromSouth && !canFromNorth) {
                chosen = DOOR_OPEN_FROM_SOUTH;
            } else if (northScore == southScore) {
                chosen = rnd.nextBoolean() ? DOOR_OPEN_FROM_NORTH : DOOR_OPEN_FROM_SOUTH;
            } else {
                chosen = (northScore > southScore) ? DOOR_OPEN_FROM_NORTH : DOOR_OPEN_FROM_SOUTH;
            }

        } else { // lr
            boolean canFromWest = reachableFromMain[y][x - 1];
            boolean canFromEast = reachableFromMain[y][x + 1];

            if (!canFromWest && !canFromEast)
                return -1;

            int westScore = canFromWest ? reachableScore(g, x - 1, y) : 0;
            int eastScore = canFromEast ? reachableScore(g, x + 1, y) : 0;

            if (westScore < minLocal && eastScore < minLocal)
                return -1;

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

        // Filtro final: evita puertas que no aportan
        if (chosen != -1 && !isSensibleOneWayDoor(g, x, y, chosen)) {
            return -1;
        }

        return chosen;
    }

    private static int reachableScore(int[][] g, int sx, int sy) {
        int w = g[0].length;
        int h = g.length;

        if (!inBounds(sx, sy, w, h))
            return 0;

        // ✅ FIX: el score debe aceptar puertas también
        if (!isPassable(g[sy][sx]))
            return 0;

        int score = 0;
        int max = 64;

        boolean[][] vis = new boolean[h][w];
        int[] qx = new int[max * max];
        int[] qy = new int[max * max];
        int qi = 0;
        int qj = 0;

        qx[qj] = sx;
        qy[qj] = sy;
        qj++;
        vis[sy][sx] = true;

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (qi < qj && score < max) {
            int x = qx[qi];
            int y = qy[qi];
            qi++;
            score++;

            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (vis[ny][nx])
                    continue;
                if (!canMove(g, x, y, nx, ny))
                    continue;

                vis[ny][nx] = true;
                if (qj < qx.length) {
                    qx[qj] = nx;
                    qy[qj] = ny;
                    qj++;
                }
            }
        }

        return score;
    }

    /**
     * ¿Se puede mover desde (x,y) a (nx,ny) con la semántica de puertas one-way?
     */
    private static boolean canMove(int[][] g, int x, int y, int nx, int ny) {
        int from = g[y][x];
        int to = g[ny][nx];

        if (!isPassable(to))
            return false;

        int dx = nx - x;
        int dy = ny - y;

        // Si SALGO desde una puerta, solo dejo avanzar hacia el lado "cerrado"
        if (isDoor(from)) {
            return switch (from) {
                case DOOR_OPEN_FROM_NORTH -> (dx == 0 && dy == 1);
                case DOOR_OPEN_FROM_SOUTH -> (dx == 0 && dy == -1);
                case DOOR_OPEN_FROM_WEST -> (dx == 1 && dy == 0);
                case DOOR_OPEN_FROM_EAST -> (dx == -1 && dy == 0);
                default -> false;
            };
        }

        // Si ENTRO en una puerta, solo puedo entrar por su lado abierto
        if (isDoor(to)) {
            return switch (to) {
                case DOOR_OPEN_FROM_NORTH -> (dx == 0 && dy == 1);
                case DOOR_OPEN_FROM_SOUTH -> (dx == 0 && dy == -1);
                case DOOR_OPEN_FROM_WEST -> (dx == 1 && dy == 0);
                case DOOR_OPEN_FROM_EAST -> (dx == -1 && dy == 0);
                default -> false;
            };
        }

        return true;
    }

    private static boolean isSensibleOneWayDoor(int[][] g, int x, int y, int doorValue) {
        // Valores ajustados (los tuyos, coherentes con el filtro “anti-pasillo largo”)
        final int MIN_DEST_REGION = 40;
        final int BRANCH_SEARCH_LIMIT = 20;
        final int MIN_BRANCH_DEGREE = 3;

        int old = g[y][x];
        g[y][x] = doorValue;

        int[] sides = doorSides(x, y, doorValue);
        int ax = sides[0], ay = sides[1], bx = sides[2], by = sides[3];

        int bypass = bypassDistanceWithoutDoorCell(g, ax, ay, bx, by, x, y, MAX_BYPASS_DIST);
        if (bypass != Integer.MAX_VALUE && bypass <= MAX_BYPASS_DIST) {
            g[y][x] = old;
            return false;
        }

        // Tile destino (al que llegas tras cruzar la puerta)
        int tx = x, ty = y;
        switch (doorValue) {
            case DOOR_OPEN_FROM_NORTH -> ty = y + 1;
            case DOOR_OPEN_FROM_SOUTH -> ty = y - 1;
            case DOOR_OPEN_FROM_WEST -> tx = x + 1;
            case DOOR_OPEN_FROM_EAST -> tx = x - 1;
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

        // (A) Región alcanzable real al otro lado (con reglas de puertas)
        int region = directedFloodCount(g, tx, ty, MIN_DEST_REGION);
        if (region < MIN_DEST_REGION) {
            g[y][x] = old;
            return false;
        }

        // (B) Debe existir una bifurcación relativamente cerca
        if (!hasBranchingNearby(g, tx, ty, BRANCH_SEARCH_LIMIT, MIN_BRANCH_DEGREE)) {
            g[y][x] = old;
            return false;
        }

        g[y][x] = old;
        return true;
    }

    private static boolean hasBranchingNearby(int[][] g, int sx, int sy, int limit, int minDegree) {
        int w = g[0].length, h = g.length;
        boolean[][] vis = new boolean[h][w];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        vis[sy][sx] = true;

        int seen = 0;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty() && seen < limit) {
            int[] p = q.poll();
            int x = p[0], y = p[1];
            seen++;

            int degree = 0;
            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (canMove(g, x, y, nx, ny))
                    degree++;
            }
            if (degree >= minDegree)
                return true;

            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (vis[ny][nx])
                    continue;
                if (!canMove(g, x, y, nx, ny))
                    continue;

                vis[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static int countOutgoingMoves(int[][] g, int x, int y) {
        int w = g[0].length, h = g.length;
        int out = 0;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : dirs) {
            int nx = x + d[0], ny = y + d[1];
            if (!inBounds(nx, ny, w, h))
                continue;
            if (canMove(g, x, y, nx, ny))
                out++;
        }
        return out;
    }

    private static int directedFloodCount(int[][] g, int sx, int sy, int limit) {
        int w = g[0].length, h = g.length;
        boolean[][] vis = new boolean[h][w];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { sx, sy });
        vis[sy][sx] = true;

        int count = 0;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty() && count < limit) {
            int[] p = q.poll();
            int x = p[0], y = p[1];
            count++;

            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (vis[ny][nx])
                    continue;
                if (!canMove(g, x, y, nx, ny))
                    continue;

                vis[ny][nx] = true;
                q.add(new int[] { nx, ny });
            }
        }
        return count;
    }

    private static int bypassDistanceWithoutDoorCell(int[][] g,
            int ax, int ay, int bx, int by,
            int doorX, int doorY,
            int maxDist) {

        int h = g.length, w = g[0].length;
        if (!inBounds(ax, ay, w, h) || !inBounds(bx, by, w, h))
            return Integer.MAX_VALUE;
        if (!isPassable(g[ay][ax]) || !isPassable(g[by][bx]))
            return Integer.MAX_VALUE;

        boolean[][] vis = new boolean[h][w];
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { ax, ay, 0 });
        vis[ay][ax] = true;

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], d = p[2];
            if (d > maxDist)
                continue;
            if (x == bx && y == by)
                return d;

            for (int[] dir : dirs) {
                int nx = x + dir[0], ny = y + dir[1];
                if (!inBounds(nx, ny, w, h))
                    continue;
                if (vis[ny][nx])
                    continue;

                // 🚫 no permitimos pasar por la celda donde iría la puerta
                if (nx == doorX && ny == doorY)
                    continue;

                // “Rodeo” ignora semántica one-way: si es pasable en el mapa, cuenta.
                if (!isPassable(g[ny][nx]))
                    continue;

                vis[ny][nx] = true;
                q.add(new int[] { nx, ny, d + 1 });
            }
        }

        return Integer.MAX_VALUE;
    }

    private static int[] doorSides(int x, int y, int doorValue) {
        // devuelve {ax, ay, bx, by} = los dos tiles a cada lado del hueco
        return switch (doorValue) {
            case DOOR_OPEN_FROM_NORTH, DOOR_OPEN_FROM_SOUTH -> new int[] { x, y - 1, x, y + 1 };
            case DOOR_OPEN_FROM_WEST, DOOR_OPEN_FROM_EAST -> new int[] { x - 1, y, x + 1, y };
            default -> new int[] { 0, 0, 0, 0 };
        };
    }

}
