package com.jairo.services;

import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;

import java.util.*;

public class ItemPlacer {

    public static final int PATH = 0;
    private static final Random RNG = new Random();

    private final List<PlacedItem> placedItems = new ArrayList<>();
    private final Set<Long> occupied = new HashSet<>();
    private final Map<Long, PlacedItem> itemsByPos = new HashMap<>();

    /**
     * Coloca objetos usando la configuración embebida en cada ItemType:
     * - densidad => calcula cantidad según tamaño del mapa (PATH)
     * - distancias => por tipo
     */
    public void placeObjects(
            List<List<Integer>> cells,
            int playerX,
            int playerY,
            List<ItemType> types
    ) {
        placedItems.clear();
        occupied.clear();
        itemsByPos.clear();

        occupied.add(pack(playerX, playerY));

        // 1) calcular tamaño útil del mapa (PATH)
        int pathCount = countCellsOfType(cells, PATH);

        // 2) BFS del jugador una vez (distancias)
        int[][] distFromPlayer = bfsFromPlayer(cells, playerX, playerY);

        // 3) ordenar tipos más restrictivos primero
        List<ItemType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator
                .comparingInt(ItemType::getMinDistBetweenItems).reversed()
                .thenComparingInt(ItemType::getMinDistFromPlayer).reversed()
                .thenComparingDouble(ItemType::getDensity).reversed()
        );

        // 4) colocar por tipo con cantidad auto-calculada
        for (ItemType type : sorted) {
            int amount = computeAmountForType(type, pathCount);
            if (amount <= 0) continue;

            placeType(cells, distFromPlayer, type, amount);
        }
    }

    public List<PlacedItem> getPlacedItems() {
        return Collections.unmodifiableList(placedItems);
    }

    public PlacedItem getItemAt(int x, int y) {
        return itemsByPos.get(pack(x, y));
    }

    public PlacedItem pickupAt(int x, int y) {
        long key = pack(x, y);
        PlacedItem it = itemsByPos.remove(key);
        if (it == null) return null;

        placedItems.removeIf(pi -> pi.getX() == x && pi.getY() == y);
        occupied.remove(key);
        return it;
    }

    /* ===================== Internos ===================== */

    private int computeAmountForType(ItemType type, int pathCount) {
        // cantidad por densidad
        double raw = pathCount * Math.max(0.0, type.getDensity());

        // redondeo: puedes cambiar a floor/ceil según estilo
        int amount = (int) Math.round(raw);

        // aplicar límites
        amount = Math.max(amount, type.getMinCount());
        amount = Math.min(amount, type.getMaxCount());
        return amount;
    }

    private void placeType(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            ItemType type,
            int amount
    ) {
        int minPlayer = type.getMinDistFromPlayer();
        int minBetween = type.getMinDistBetweenItems();

        // intento principal
        List<int[]> candidates = collectCandidates(cells, distFromPlayer, minPlayer, minBetween);
        Collections.shuffle(candidates, RNG);

        int placed = 0;
        for (int[] p : candidates) {
            if (placed >= amount) break;
            place(type, p[0], p[1]);
            placed++;
        }

        // fallback: relajar minPlayer
        int relaxedPlayer = minPlayer - 1;
        while (placed < amount && relaxedPlayer >= 0) {
            List<int[]> more = collectCandidates(cells, distFromPlayer, relaxedPlayer, minBetween);
            Collections.shuffle(more, RNG);

            for (int[] p : more) {
                if (placed >= amount) break;
                if (occupied.contains(pack(p[0], p[1]))) continue;
                if (!respectsMinDistBetween(p[0], p[1], minBetween)) continue;

                place(type, p[0], p[1]);
                placed++;
            }
            relaxedPlayer--;
        }

        // fallback: relajar también minBetween
        int relaxedBetween = minBetween - 1;
        while (placed < amount && relaxedBetween >= 0) {
            List<int[]> more = collectCandidates(cells, distFromPlayer, 0, relaxedBetween);
            Collections.shuffle(more, RNG);

            for (int[] p : more) {
                if (placed >= amount) break;
                if (occupied.contains(pack(p[0], p[1]))) continue;
                if (!respectsMinDistBetween(p[0], p[1], relaxedBetween)) continue;

                place(type, p[0], p[1]);
                placed++;
            }
            relaxedBetween--;
        }
    }

    private void place(ItemType type, int x, int y) {
        PlacedItem it = new PlacedItem(type, x, y);
        placedItems.add(it);
        occupied.add(pack(x, y));
        itemsByPos.put(pack(x, y), it);
    }

    private List<int[]> collectCandidates(
            List<List<Integer>> cells,
            int[][] distFromPlayer,
            int minDistPlayer,
            int minDistBetween
    ) {
        int h = cells.size();
        int w = cells.get(0).size();

        List<int[]> res = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (cells.get(y).get(x) != PATH) continue;
                if (occupied.contains(pack(x, y))) continue;

                int d = distFromPlayer[y][x];
                if (d == -1) continue;
                if (d < minDistPlayer) continue;

                if (!respectsMinDistBetween(x, y, minDistBetween)) continue;

                res.add(new int[]{x, y});
            }
        }
        return res;
    }

    private boolean respectsMinDistBetween(int x, int y, int minDistBetween) {
        if (minDistBetween <= 0) return true;

        for (PlacedItem it : placedItems) {
            int dx = Math.abs(it.getX() - x);
            int dy = Math.abs(it.getY() - y);
            if (dx + dy < minDistBetween) return false;
        }
        return true;
    }

    private int countCellsOfType(List<List<Integer>> cells, int cellType) {
        int count = 0;
        for (List<Integer> row : cells) {
            for (int v : row) {
                if (v == cellType) count++;
            }
        }
        return count;
    }

    private int[][] bfsFromPlayer(List<List<Integer>> cells, int px, int py) {
        int h = cells.size();
        int w = cells.get(0).size();

        int[][] dist = new int[h][w];
        for (int y = 0; y < h; y++) Arrays.fill(dist[y], -1);

        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[py][px] = 0;
        q.add(new int[]{px, py});

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];

                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (dist[ny][nx] != -1) continue;
                if (cells.get(ny).get(nx) != PATH) continue;

                dist[ny][nx] = dist[p[1]][p[0]] + 1;
                q.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    private static long pack(int x, int y) {
        return (((long) y) << 32) ^ (x & 0xffffffffL);
    }
}
