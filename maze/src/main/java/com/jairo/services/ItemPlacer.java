package com.jairo.services;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
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

public class ItemPlacer {

    public static final int PATH = 0;
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

        // Normalizar por si vienen invertidos
        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int hiY = Math.max(minY, maxY);

        // Evitar overflow al calcular área
        long width = (long) hiX - (long) loX + 1L;
        long height = (long) hiY - (long) loY + 1L;
        long area = (width <= 0 || height <= 0) ? 0 : width * height;

        // Heurística: si el rectángulo es pequeño, es más rápido consultar el mapa por celdas.
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

        // Si el rectángulo es grande, filtrar la lista suele ser mejor.
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

    private void placeTypeGuaranteeing(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int amount) {

        int minPlayer = Math.max(0, type.getMinDistFromPlayer());
        int minBetween = Math.max(0, type.getMinDistBetweenItems());
        int minExit = Math.max(0, type.getMinDistFromExit());
        int minBorder = Math.max(0, type.getMinDistFromBorder());

        int placed = 0;

        placed += placeWithConstraints(
                cells, distFromPlayer, distFromExit,
                type, amount - placed,
                minPlayer, minExit, minBetween, minBorder);

        int phase = 0; // 0=player,1=between,2=exit,3=border

        while (placed < amount &&
                (minPlayer > 0 || minBetween > 0 || minExit > 0 || minBorder > 0)) {

            int tries = 0;
            while (tries < 4) {
                switch (phase) {
                    case 0 -> { if (minPlayer > 0) { minPlayer--; break; } }
                    case 1 -> { if (minBetween > 0) { minBetween--; break; } }
                    case 2 -> { if (minExit > 0) { minExit--; break; } }
                    case 3 -> { if (minBorder > 0) { minBorder--; break; } }
                }
                phase = (phase + 1) % 4;
                tries++;
            }

            phase = (phase + 1) % 4;

            placed += placeWithConstraints(
                    cells, distFromPlayer, distFromExit,
                    type, amount - placed,
                    minPlayer, minExit, minBetween, minBorder);
        }
    }

    private int placeWithConstraints(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int need,
            int minPlayer,
            int minExit,
            int minBetween,
            int minBorder) {

        if (need <= 0)
            return 0;

        List<Long> candidates = collectCandidatesPacked(
                cells, distFromPlayer, distFromExit,
                type.getSpawnBlackList(),
                minPlayer, minExit, minBetween, minBorder);

        if (candidates.isEmpty())
            return 0;

        Collections.shuffle(candidates, RNG);

        int placedNow = 0;
        for (Long k : candidates) {
            if (placedNow >= need)
                break;

            long key = k;
            if (occupied.contains(key))
                continue;

            int x = (int) key;
            int y = (int) (key >>> 32);

            if (!respectsMinDistBetween(x, y, minBetween))
                continue;

            place(type, x, y);
            placedNow++;
        }

        return placedNow;
    }

    private void place(ItemType type, int x, int y) {
        PlacedItem it = new PlacedItem(type, x, y);
        placedItems.add(it);
        long key = pack(x, y);
        occupied.add(key);
        itemsByPos.put(key, it);
    }

    /* ===================== Candidate collection ===================== */

    private List<Long> collectCandidatesPacked(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
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
                        Math.min(cachedW - 1 - x, cachedH - 1 - y)
                );
                if (distToBorder < minDistBorder)
                    continue;
            }

            if (!respectsMinDistBetween(x, y, minDistBetween))
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
        q.add(new int[]{sx, sy});

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

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
                q.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    /**
     * BFS "desde la salida" robusto:
     * - Si (exitX,exitY) es PATH => BFS directo.
     * - Si no lo es => busca el PATH más cercano a la salida y BFS desde ahí.
     */
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

    /**
     * Encuentra el PATH más cercano (por expansión ortogonal) a (sx,sy).
     * Si no hay ninguno, devuelve null.
     */
    private int[] findNearestPathCell(List<List<Integer>> cells, int sx, int sy) {
        int h = cells.size();
        int w = cells.get(0).size();

        boolean[][] vis = new boolean[h][w];
        ArrayDeque<int[]> q = new ArrayDeque<>();

        int cx = Math.max(0, Math.min(w - 1, sx));
        int cy = Math.max(0, Math.min(h - 1, sy));

        q.add(new int[]{cx, cy});
        vis[cy][cx] = true;

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

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
                q.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static long pack(int x, int y) {
        return (((long) y) << 32) ^ (x & 0xffffffffL);
    }

    /**
     * Elimina del mapa (estructuras internas) tod0s los items del tipo indicado.
     *
     * @param type Tipo a eliminar
     * @return cuántos se han eliminado
     */
    public int removeAllOfType(ItemType type) {
        if (type == null || placedItems.isEmpty())
            return 0;

        int removed = 0;

        // Iteramos al revés para poder borrar de placedItems por índice sin líos
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

    /**
     * Elimina del mapa tod0s los items del tipo indicado EXCEPTO el que esté en (keepX, keepY).
     * Útil para "quitar el resto" después de hacer pickup/activar uno.
     *
     * @param type  Tipo a eliminar
     * @param keepX X a conservar
     * @param keepY Y a conservar
     * @return cuántos se han eliminado
     */
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
        PlacedItem it = itemsByPos.remove(key);
        if (it == null)
            return null;

        placedItems.removeIf(pi -> pi.getX() == x && pi.getY() == y);
        occupied.remove(key);
        return it;
    }
}
