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

    /**
     * Coloca objetos usando la configuración embebida en cada ItemType:
     * - densidad => calcula cantidad según tamaño del mapa (PATH)
     * - distancias => por tipo (jugador / entre items / salida)
     *
     * Asegura colocación:
     * - Si la salida NO está en una celda PATH, calcula distancias desde el PATH
     * más cercano.
     * - Si un tipo no puede colocarse por restricciones, relaja (en este orden):
     * 1) minDistFromPlayer
     * 2) minDistBetweenItems
     * 3) minDistFromExit (último recurso, para garantizar colocación)
     */
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

        // no colocar nada en la posición del jugador
        occupied.add(pack(playerX, playerY));

        // 1) cachear todas las posiciones PATH una sola vez
        buildPathCache(cells);
        int pathCount = pathPositions.size();

        // 2) distancias reales por caminos (BFS)
        int[][] distFromPlayer = bfsFrom(cells, playerX, playerY);
        int[][] distFromExit = bfsFromExit(cells, exitX, exitY);

        // 3) ordenar tipos más restrictivos primero
        List<ItemType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator
                .comparingInt(ItemType::getMinDistBetweenItems).reversed()
                .thenComparingInt(ItemType::getMinDistFromPlayer).reversed()
                .thenComparingInt(ItemType::getMinDistFromExit).reversed()
                .thenComparingDouble(ItemType::getDensity).reversed());

        // 4) colocar por tipo con cantidad auto-calculada
        for (ItemType type : sorted) {
            int amount = computeAmountForType(type, pathCount);
            if (amount <= 0)
                continue;

            placeTypeGuaranteeing(cells, distFromPlayer, distFromExit, type, amount);
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

    public PlacedItem pickupAt(int x, int y) {
        long key = pack(x, y);
        PlacedItem it = itemsByPos.remove(key);
        if (it == null)
            return null;

        placedItems.removeIf(pi -> pi.getX() == x && pi.getY() == y);
        occupied.remove(key);
        return it;
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

    /* ===================== Internos ===================== */

    private void buildPathCache(List<List<Integer>> cells) {
        pathPositions.clear();

        int h = cells.size();
        int w = cells.isEmpty() ? 0 : cells.get(0).size();
        cachedW = w;
        cachedH = h;

        for (int y = 0; y < h; y++) {
            List<Integer> row = cells.get(y);
            for (int x = 0; x < w; x++) {
                int v = row.get(x);
                if (Cells.isPath(v)) {
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

    /**
     * Coloca "amount" items de este type. Garantiza colocación relajando restricciones:
     * - minPlayer baja a 0
     * - minBetween baja a 0
     * - minExit baja a 0 (último recurso)
     */
    private void placeTypeGuaranteeing(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int amount) {

        int minPlayer = Math.max(0, type.getMinDistFromPlayer());
        int minBetween = Math.max(0, type.getMinDistBetweenItems());
        int minExit = Math.max(0, type.getMinDistFromExit());

        int placed = 0;

        placed += placeWithConstraints(cells, distFromPlayer, distFromExit, type, amount - placed,
                minPlayer, minExit, minBetween);

        // 2) Relajar minPlayer
        int relaxedPlayer = minPlayer - 1;
        while (placed < amount && relaxedPlayer >= 0) {
            placed += placeWithConstraints(cells, distFromPlayer, distFromExit, type, amount - placed,
                    relaxedPlayer, minExit, minBetween);
            relaxedPlayer--;
        }

        // 3) Relajar minBetween
        int relaxedBetween = minBetween - 1;
        while (placed < amount && relaxedBetween >= 0) {
            placed += placeWithConstraints(cells, distFromPlayer, distFromExit, type, amount - placed,
                    0, minExit, relaxedBetween);
            relaxedBetween--;
        }

        // 4) Último recurso: relajar minExit también (para garantizar que coloque)
        int relaxedExit = minExit - 1;
        while (placed < amount && relaxedExit >= 0) {
            placed += placeWithConstraints(cells, distFromPlayer, distFromExit, type, amount - placed,
                    0, relaxedExit, 0);
            relaxedExit--;
        }
    }

    /**
     * Intenta colocar "need" items con las restricciones dadas.
     * Devuelve cuántos ha colocado realmente.
     */
    private int placeWithConstraints(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            ItemType type,
            int need,
            int minPlayer,
            int minExit,
            int minBetween) {

        if (need <= 0)
            return 0;

        // Candidatos usando SOLO las celdas PATH cacheadas
        List<Long> candidates = collectCandidatesPacked(
                cells,
                distFromPlayer,
                distFromExit,
                type.getSpawnBlackList(),
                minPlayer,
                minExit,
                minBetween);

        if (candidates.isEmpty())
            return 0;

        Collections.shuffle(candidates, RNG);

        int placedNow = 0;
        for (long key : candidates) {
            if (placedNow >= need)
                break;

            if (occupied.contains(key))
                continue;

            int x = (int) key;
            int y = (int) (key >>> 32);

            // Nota: ya se comprobó en collectCandidatesPacked, pero lo dejamos por seguridad
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

    /**
     * Recoge candidatos iterando SOLO pathPositions (cache).
     * Devuelve posiciones empaquetadas (long) para evitar crear int[]{x,y} por candidato.
     */
    private List<Long> collectCandidatesPacked(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int[][] distFromExit,
            List<Integer> spawnBlackList,
            int minDistPlayer,
            int minDistExit,
            int minDistBetween) {

        // Para lookup rápido (y evitar NPE)
        Set<Integer> black = (spawnBlackList == null || spawnBlackList.isEmpty())
                ? Collections.emptySet()
                : new HashSet<>(spawnBlackList);

        List<Long> res = new ArrayList<>();

        for (long pos : pathPositions) {
            int x = (int) pos;
            int y = (int) (pos >>> 32);

            // blacklist por valor real de celda
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
                return false; // Manhattan
        }
        return true;
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
            int px = p[0], py = p[1];

            for (int[] d : dirs) {
                int nx = px + d[0];
                int ny = py + d[1];

                if (nx < 0 || ny < 0 || nx >= w || ny >= h)
                    continue;
                if (dist[ny][nx] != -1)
                    continue;
                if (!Cells.isPath(cells.get(ny).get(nx)))
                    continue;

                dist[ny][nx] = dist[py][px] + 1;
                q.add(new int[] { nx, ny });
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
        int h = cells.size();
        int w = cells.get(0).size();

        if (exitX >= 0 && exitY >= 0 && exitX < w && exitY < h && Cells.isPath(cells.get(exitY).get(exitX))) {
            return bfsFrom(cells, exitX, exitY);
        }

        int[] start = findNearestPathCell(cells, exitX, exitY);
        if (start == null) {
            int[][] dist = new int[h][w];
            for (int y = 0; y < h; y++)
                Arrays.fill(dist[y], -1);
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

        if (h == 0 || w == 0)
            return null;

        boolean[][] vis = new boolean[h][w];
        ArrayDeque<int[]> q = new ArrayDeque<>();

        int cx = Math.max(0, Math.min(w - 1, sx));
        int cy = Math.max(0, Math.min(h - 1, sy));

        q.add(new int[] { cx, cy });
        vis[cy][cx] = true;

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1];

            if (Cells.isPath(cells.get(y).get(x)))
                return new int[] { x, y };

            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
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
}
