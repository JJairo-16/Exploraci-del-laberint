package com.jairo.services;

import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.utils.map_generator.Cells;
import com.jairo.items.BasicItemType;
import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.models.Inventory;
import com.jairo.items.PowerType;

import static com.jairo.app.gfx.Sprite.CHEATED_BUTTON;
import static com.jairo.utils.KeyBind.Action;
import static com.jairo.utils.map_generator.Cells.*;

import java.util.Random;
import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.audio.Steps;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.player_skins.SkinManager;
import com.jairo.app.i18n.LanguageManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simulator {
    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    private Player player;
    private ItemPlacer placer;
    private final Inventory inventory = new Inventory();

    private Board board;
    private Drawer drawer;
    private static final SoundManager sm = SoundManager.get();

    private static final String LOCKED_DOOR_SOUND = Sound.LOCKED_DOOR.path();
    private static final String TOCTOC_SOUND = Sound.TOCTOC.path();
    private static final String OPEN_DOOR_SOUND = Sound.OPEN_DOOR.path();

    private static final String JIJI_SOUND_CA = Sound.JIJI_CA.path();
    private static final String JIJI_SOUND_ES = Sound.JIJI_ES.path();
    private static final String JIJI_SOUND_EN = Sound.JIJI_EN.path();

    static {
        sm.defineGroup("lockedDoor",
                LOCKED_DOOR_SOUND,
                TOCTOC_SOUND,
                JIJI_SOUND_CA,
                JIJI_SOUND_EN,
                JIJI_SOUND_ES);
    }

    private boolean continuity = true;
    private Action lastMovement = Action.UP;
    private Action currentAction = Action.UP;
    private ItemType lastPower = null;

    private static final long DELAY_MS = 200L;
    private static final long TOC_TOC_DELAY_MS = 100L;

    private static final int TOC_TOC_PROBABLY = 10; // %
    private static final int JI_JI_PROBABLY_RECURSIVE = 20; // %

    private static final Random RANDOM = new Random();
    private final CheatWallActivationSystem cheatWallSystem;

    private boolean randomWithProbably(int probably) {
        return RANDOM.nextInt(100) < probably;
    }

    public Action getCurrentAction() {
        return currentAction;
    }

    public boolean getContinue() {
        return continuity;
    }

    public Board getBoardRef() {
        return board;
    }

    public Simulator(Player player, Board board, ItemPlacer placer) {
        this.placer = placer;
        this.player = player;
        this.board = board;
        this.cheatWallSystem = new CheatWallActivationSystem(board);
        log.info("Simulator created");
    }

    public void loadDrawer(Drawer drawer) {
        this.drawer = drawer;
        log.info("Drawer loaded into Simulator: {}", drawer);
    }

    public void simulate(Action action) {
        if (action.isAMovement) {
            currentAction = action;
            lastMovement = action; // dirección para usar pico
        }

        int dx = switch (action) {
            case LEFT -> -1;
            case RIGHT -> 1;
            default -> 0;
        };

        int dy = switch (action) {
            case UP -> -1;
            case DOWN -> 1;
            default -> 0;
        };

        if (dx != 0 || dy != 0) {
            boolean moved = simulatePlayerMovement(dx, dy);
            if (moved)
                Steps.playRandomStep();

            updateCheatedSystem();

            log.debug("Move dx={}, dy={} -> moved={}, pos=({}, {})",
                    dx, dy, moved, player.getX(), player.getY());
            return;
        }

        switch (action) {
            case Action.ZOOM_IN:
                drawer.zoomIn();
                log.info("Zoom in");
                break;

            case Action.ZOOM_OUT:
                drawer.zoomOut();
                log.info("Zoom out");
                break;

            case Action.NEXT_SKIN:
                SkinManager.get().next();
                break;

            case Action.PREVIOUS_SKIN:
                SkinManager.get().previous();
                break;

            case Action.USE:
                use();
                break;

            case PREVIOUS_ITEM:
                inventory.selectPrevPower();
                break;

            case Action.NEXT_ITEM:
                inventory.selectNextPower();
                break;

            default:
                break;
        }

        updateCheatedSystem();
    }

    private long lasNow = 0L;

    public void updateCheatedSystem(long now) {
        lasNow = now;
        cheatWallSystem.update(board.getCells(), player.getX(), player.getY(), now);
    }

    private void updateCheatedSystem() {
        cheatWallSystem.update(board.getCells(), player.getX(), player.getY(), lasNow);
    }

    public boolean simulatePlayerMovement(int dx, int dy) {
        boolean moved = false;

        try {
            moved = player.move(dx, dy);
        } catch (Exception e) {
            int x = player.getX();
            int y = player.getY();

            boolean sideX = (x == 0 || x == Board.BOARD_WIDTH - 1);
            boolean sideY = (y == 0 || y == Board.BOARD_HEIGHT - 1);

            if (board.getTile(x, y) == EXIT && (sideX || sideY)) {
                log.info("Player wins the game.");
                continuity = false;
            }
        }

        if (moved) {
            tryPickupAtPlayer();
        }

        return moved;
    }

    public record Position(int x, int y) {
    }

    public Position getPlayerPosition() {
        return new Position(player.getX(), player.getY());
    }

    private void use() {
        if (inventory.getSelectedPower() != null) {
            ItemType item = inventory.getSelectedPower();
            if (inventory.has(item)) {
                lastPower = item;
                useItem(item);
                return;
            }
        }

        lastPower = null;

        DnResult dn = getDN();
        int cell = dn.cell;
        if (cell == LOCKED_EXIT) {
            playLockedExit();
            return;
        }

        tryToOpenDoor();
    }

    private void useItem(ItemType item) {
        switch (item) {
            case PowerType.PICKAXE:
                usePickaxe(item);
                break;

            case PowerType.BLAI_GLASSES:
                tryToActiveBlaiGlasses();
                break;

            case PowerType.KEY:
                tryToOpenExit();
                break;

            default:
                break;
        }
    }

    /**
     * Pico:
     * - Si apunta a DOOR_OPEN_FROM_* y puede abrir -> abre (NO gasta pico).
     * - Si apunta a DOOR_OPEN_FROM_* pero no puede -> no hace nada (NO gasta pico).
     * - Si apunta a DOOR_OPENED_FROM_* -> ignora (NO gasta pico).
     * - Rompe cualquier pared (aquí: WALL) y gasta 1 pico SOLO si rompe.
     *
     * Sonidos:
     * - Si intenta romper una puerta o una pared del borde -> sonido de "golpe / no
     * permitido".
     * - Si rompe una pared -> sonido de "romper pared".
     */
    private void usePickaxe(ItemType item) {
        if (item != PowerType.PICKAXE)
            return;

        DnResult dn = getDN();
        int dx = dn.dx;
        int dy = dn.dy;

        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx;
        int ny = dn.ny;

        // Golpe fuera de límites / borde
        boolean outOfBounds = nx < 1 || ny < 1 ||
                nx >= Board.BOARD_WIDTH - 1 ||
                ny >= Board.BOARD_HEIGHT - 1;

        if (outOfBounds) {
            playDoorHit();
            return;
        }

        int cell = dn.cell;

        // Nada sólido → nada que hacer
        if (!Cells.hasCollision(cell))
            return;

        // ===== PUERTAS =====
        if (isDoorClosed(cell)) {
            boolean opened = false;

            if (isDoorClosedButOpenable(cell)) {
                opened = tryOpenDoorAt(nx, ny, dx, dy, cell);
            }

            if (!opened)
                playDoorHit();
            return;
        }

        if (isDoorOpened(cell)) {
            playDoorHit();
            return;
        }

        // ===== PAREDES =====
        if (cell != WALL) {
            if (Cells.playMetalSound(cell)) {
                playDoorHit();
            }
            return;
        }

        // Pared del borde → no se rompe
        boolean isBorderWall = nx <= 0 || ny <= 0 ||
                nx >= Board.BOARD_WIDTH - 1 ||
                ny >= Board.BOARD_HEIGHT - 1;

        if (isBorderWall) {
            playDoorHit();
            return;
        }

        // NUEVO: si la pared toca una puerta (N/S/E/O) -> no romper, no gastar, sonido
        if (wallTouchesDoor(nx, ny)) {
            playDoorHit();
            return;
        }

        // Consumir pico SOLO si rompe
        if (!inventory.consumeOne(item))
            return;

        board.updateTile(nx, ny, DESTROYED_PATH);

        // Sonido de romper pared
        sm.playSfx(Sound.PICKAXE_WALL.path());
    }

    /**
     * Devuelve true si la celda (x,y) tiene una puerta justo al lado (4
     * direcciones).
     */
    private boolean wallTouchesDoor(int x, int y) {
        // 4 vecinos ortogonales
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (int[] d : dirs) {
            int ax = x + d[0];
            int ay = y + d[1];

            // bounds seguros (usa el mismo criterio que tu tablero)
            if (ax < 0 || ay < 0 || ax >= Board.BOARD_WIDTH || ay >= Board.BOARD_HEIGHT)
                continue;

            int neighbor = board.getTile(ax, ay);

            // Cualquier puerta (cerrada/abierta) cuenta
            if (isAnyDoor(neighbor) || isDoorOpened(neighbor) || isDoorClosedButOpenable(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDoorClosed(int cell) {
        return isAnyDoor(cell) && !isDoorOpened(cell);
    }

    private void playDoorHit() {
        sm.playSfxWithTailDelay(Sound.PICKAXE_DOOR.path(), 1.0, true, 200);
    }

    /**
     * Helper opcional: agrupa cualquier tipo de puerta (cerrada/abierta) para
     * detectar intentos de romper puerta.
     * Implementa esto según tus constantes/tipos de celda.
     */
    private boolean isAnyDoor(int cell) {
        return isDoorClosedButOpenable(cell) || isDoorOpened(cell) /* || isDoorClosedButNotOpenable(cell) si existe */;
    }

    private boolean isDoorClosedButOpenable(int cell) {
        return cell == DOOR_OPEN_FROM_NORTH
                || cell == DOOR_OPEN_FROM_SOUTH
                || cell == DOOR_OPEN_FROM_WEST
                || cell == DOOR_OPEN_FROM_EAST;
    }

    private boolean isDoorOpened(int cell) {
        return cell == DOOR_OPENED_FROM_NORTH
                || cell == DOOR_OPENED_FROM_SOUTH
                || cell == DOOR_OPENED_FROM_WEST
                || cell == DOOR_OPENED_FROM_EAST;
    }

    /**
     * Si la puerta se puede abrir desde esta dirección:
     * - la abre (DOOR_OPENED_FROM_*)
     * - reproduce sfx
     * Devuelve true si era puerta (aunque no se pudiera abrir); así el pico nunca
     * rompe puertas.
     */
    private boolean tryOpenDoorAt(int nx, int ny, int dx, int dy, int cell) {
        if (!isDoorClosedButOpenable(cell))
            return false;

        boolean canOpen = switch (cell) {
            case DOOR_OPEN_FROM_NORTH -> dy == 1;
            case DOOR_OPEN_FROM_SOUTH -> dy == -1;
            case DOOR_OPEN_FROM_WEST -> dx == 1;
            case DOOR_OPEN_FROM_EAST -> dx == -1;
            default -> false;
        };

        // Si no puede abrirse desde este lado, no se abre
        if (!canOpen)
            return false;

        int opened = switch (cell) {
            case DOOR_OPEN_FROM_NORTH -> DOOR_OPENED_FROM_NORTH;
            case DOOR_OPEN_FROM_SOUTH -> DOOR_OPENED_FROM_SOUTH;
            case DOOR_OPEN_FROM_WEST -> DOOR_OPENED_FROM_WEST;
            case DOOR_OPEN_FROM_EAST -> DOOR_OPENED_FROM_EAST;
            default -> cell;
        };

        board.updateTile(nx, ny, opened);
        sm.playSfx(OPEN_DOOR_SOUND);
        return true;
    }

    private void tryToOpenDoor() {
        int x = player.getX();
        int y = player.getY();

        int dx = 0;
        int dy = 0;

        switch (currentAction) {
            case Action.UP:
                dy = -1;
                break;
            case Action.DOWN:
                dy = 1;
                break;
            case Action.LEFT:
                dx = -1;
                break;
            case Action.RIGHT:
                dx = 1;
                break;
            default:
                break;
        }

        int nx = x + dx;
        int ny = y + dy;

        // bounds
        if (nx < 0 || ny < 0 || nx >= Board.BOARD_WIDTH || ny >= Board.BOARD_HEIGHT)
            return;

        int cell = board.getTile(nx, ny);

        // Solo puertas cerradas abribles (open_from)
        if (!isDoorClosedButOpenable(cell))
            return;

        boolean canOpen = false;

        switch (cell) {
            case DOOR_OPEN_FROM_NORTH:
                canOpen = dy == 1;
                break;
            case DOOR_OPEN_FROM_SOUTH:
                canOpen = dy == -1;
                break;
            case DOOR_OPEN_FROM_WEST:
                canOpen = dx == 1;
                break;
            case DOOR_OPEN_FROM_EAST:
                canOpen = dx == -1;
                break;
            default:
                break;
        }

        if (canOpen) {
            int opened = switch (cell) {
                case DOOR_OPEN_FROM_NORTH -> DOOR_OPENED_FROM_NORTH;
                case DOOR_OPEN_FROM_SOUTH -> DOOR_OPENED_FROM_SOUTH;
                case DOOR_OPEN_FROM_WEST -> DOOR_OPENED_FROM_WEST;
                case DOOR_OPEN_FROM_EAST -> DOOR_OPENED_FROM_EAST;
                default -> cell;
            };

            board.updateTile(nx, ny, opened);
            sm.playSfx(OPEN_DOOR_SOUND);
            return;
        }

        if (sm.isGroupPlaying("lockedDoor"))
            return;

        String path = getLockedDoorPath();
        long delay = path.equals(TOCTOC_SOUND) ? TOC_TOC_DELAY_MS : DELAY_MS;
        sm.playSfxWithTailDelay(path, 1.0, false, delay);
    }

    private String getLockedDoorPath() {
        if (!randomWithProbably(TOC_TOC_PROBABLY)) {
            return LOCKED_DOOR_SOUND;
        }

        if (!randomWithProbably(JI_JI_PROBABLY_RECURSIVE)) {
            return TOCTOC_SOUND;
        }

        return getJiJiPath();
    }

    private String getJiJiPath() {
        String lang = LanguageManager.getCurrentLanguageCode();
        return switch (lang) {
            case "ca" -> JIJI_SOUND_CA;
            case "es" -> JIJI_SOUND_ES;
            case "en" -> JIJI_SOUND_EN;
            default -> JIJI_SOUND_CA;
        };
    }

    public ItemPlacer getItemPlacer() {
        return placer;
    }

    public Inventory getInventory() {
        return inventory;
    }

    private void tryPickupAtPlayer() {
        int x = player.getX();
        int y = player.getY();

        PlacedItem picked = placer.pickupAt(x, y);
        if (picked == null)
            return;

        ItemType type = picked.getType();
        inventory.add(type);

        if (picked.getType().isAPower()) {
            inventory.addPower(type);
        } else if (type == BasicItemType.CHEATED_BUTTON) {
            cheatWallSystem.switchOff();
            sm.playSfx(CHEATED_BUTTON.path());
        }

        String sfx = type.getPickupSoundPath();
        if (sfx != null && !sfx.isBlank()) {
            sm.playSfx(sfx);
        }

        log.info("Picked up {} at ({},{})", type.getId(), x, y);
    }

    public ItemType getLastPower() {
        return lastPower;
    }

    private static final long BLAI_GLASSES_MAX_POWER = 5_000_000_000L;
    private boolean blaiGlassesActive = false;
    private long blaiGlassesRemainingNs = 0L;

    private void tryToActiveBlaiGlasses() {
        DnResult dn = getDN();
        int dx = dn.dx;
        int dy = dn.dy;

        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx;
        int ny = dn.ny;
        int cell = dn.cell;

        if (isDoorClosed(cell)) {
            boolean opened = false;

            if (isDoorClosedButOpenable(cell)) {
                opened = tryOpenDoorAt(nx, ny, dx, dy, cell);
            }

            if (opened)
                return;
        }

        if (cell == LOCKED_EXIT) {
            playLockedExit();
            return;
        }

        if (cell == EXIT) {
            return;
        }

        if (!blaiGlassesActive && inventory.has(PowerType.BLAI_GLASSES)) {
            // consumir una vez al activar
            inventory.consumeOne(PowerType.BLAI_GLASSES);

            blaiGlassesActive = true;
            blaiGlassesRemainingNs = BLAI_GLASSES_MAX_POWER;

            sm.playSfx(Sound.BLAI_GLASSES_POWER.path());
        }
    }

    public boolean isBlaiGlassesPowerActive() {
        return blaiGlassesActive;
    }

    public void offBlaiGlasses() {
        blaiGlassesActive = false;
    }

    public long getRemainingBlaiGlassesPower() {
        return blaiGlassesRemainingNs;
    }

    public void updateRemainingBlaiGlassesPower(long update) {
        blaiGlassesRemainingNs = update;
    }

    private static final int BLAI_GLASSES_NERF_KEY = 3;
    private static final int BLAI_GLASSES_NERF_EXIT = 20;

    public double getBlaiNumber() {
        Position playerPos = getPlayerPosition();
        int playerX = playerPos.x();
        int playerY = playerPos.y();

        double distance;
        if (!inventory.has(PowerType.KEY)) {
            var keys = placer.getPositionsOf(PowerType.KEY);
            if (keys.isEmpty())
                return -1;

            int[] pos = keys.get(0);

            int keyX = pos[0];
            int keyY = pos[1];
            distance = getDistance(playerX, playerY, keyX, keyY);
            if (distance < BLAI_GLASSES_NERF_KEY)
                distance = -1;
        } else {
            int exitX = board.getExitX();
            int exitY = board.getExitY();
            distance = getDistance(playerX, playerY, exitX, exitY);
            if (distance < BLAI_GLASSES_NERF_EXIT)
                distance = -1;
        }

        return distance;
    }

    private double getDistance(int x1, int y1, int x2, int y2) {
        double dx = (double) x2 - x1;
        double dy = (double) y2 - y1;
        double dis = Math.hypot(dx, dy); // distancia en tiles
        return Math.round(dis * 100.0) / 100.0;
    }

    private record DnResult(int dx, int dy, int nx, int ny, int cell) {
    }

    private DnResult getDN() {
        int dx = 0;
        int dy = 0;

        switch (lastMovement) {
            case Action.UP -> dy = -1;
            case Action.DOWN -> dy = 1;
            case Action.LEFT -> dx = -1;
            case Action.RIGHT -> dx = 1;
            default -> {
            }
        }

        int nx = player.getX() + dx;
        int ny = player.getY() + dy;
        int cell = board.getTile(nx, ny);

        return new DnResult(dx, dy, nx, ny, cell);
    }

    private void tryToOpenExit() {
        DnResult dn = getDN();
        int dx = dn.dx;
        int dy = dn.dy;

        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx;
        int ny = dn.ny;
        int cell = dn.cell;

        if (cell != LOCKED_EXIT)
            return;

        inventory.consumeOne(PowerType.KEY);
        board.updateTile(nx, ny, EXIT);
        sm.playSfx(Sound.OPEN_LOCK.path());
    }

    private void playLockedExit() {
        sm.playSfxWithTailDelay(Sound.EXIT_LOCK.path(), 1.5, false, 50);
    }
}
