package com.jairo.services;

import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.utils.KeyBind.Action;
import com.jairo.utils.map_generator.Cells;
import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.models.Inventory;
import com.jairo.items.PowerType;
import com.jairo.items.SpecialType;

import static com.jairo.utils.map_generator.Cells.*;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.app.audio.Steps;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.player_skins.SkinManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.services.sub_simulator.IceSlideSystem;
import com.jairo.services.sub_simulator.UseSystem;
import com.jairo.services.sub_simulator.UseSystem.DnResult;
import com.jairo.services.sub_simulator.DoorSystem;

public class Simulator {
    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    private Player player;
    private ItemPlacer placer;
    private final Inventory inventory = new Inventory();

    private Board board;
    private Drawer drawer;
    private static final SoundManager sm = SoundManager.get();

    private boolean continuity = true;
    private Action lastMovement = Action.UP;
    private Action currentAction = Action.UP;
    private ItemType lastPower = null;

    private final CheatWallActivationSystem cheatWallSystem;

    // ✅ Extraído: IceSlideSystem
    private final IceSlideSystem iceSystem;

    // ✅ Extraído: DoorSystem
    private final DoorSystem doorSystem;

    // ✅ Extraído: UseSystem
    private final UseSystem useSystem;

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

        this.iceSystem = new IceSlideSystem(board, player);
        this.doorSystem = new DoorSystem(board);

        // ✅ UseSystem: ahora abre puerta mediante DoorSystem
        this.useSystem = new UseSystem(
                player,
                board,
                inventory,
                (item, dn) -> {
                    lastPower = item;
                    useItem(item, dn);
                },
                action -> doorSystem.tryToOpenDoor(action, player.getX(), player.getY()),
                this::playLockedExit);

        log.info("Simulator created");
    }

    public void loadDrawer(Drawer drawer) {
        this.drawer = drawer;
        this.iceSystem.setDrawer(drawer);
        log.info("Drawer loaded into Simulator: {}", drawer);
    }

    public void simulate(Action action) {
        // Si estamos deslizándonos en hielo, ignorar inputs de movimiento para no
        // “romper” el slide
        if (iceSystem.isSliding() && action.isAMovement) {
            updateCheatedSystem();
            return;
        }

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
            if (moved) {
                int nx = dx + player.getX();
                int ny = dx + player.getY();
                int tile = board.getTile(nx, ny);

                if (tile != ICE) Steps.playRandomStep();
                iceSystem.afterManualMove(lasNow, lastMovement);
            }

            updateCheatedSystem();

            log.debug("Move dx={}, dy={} -> moved={}, pos=({}, {})",
                    dx, dy, moved, player.getX(), player.getY());
            return;
        }

        switch (action) {
            case ZOOM_IN:
                if (drawer != null)
                    drawer.zoomIn();
                log.info("Zoom in");
                break;

            case ZOOM_OUT:
                if (drawer != null)
                    drawer.zoomOut();
                log.info("Zoom out");
                break;

            case NEXT_SKIN:
                SkinManager.get().next();
                break;

            case PREVIOUS_SKIN:
                SkinManager.get().previous();
                break;

            case USE:
                use();
                break;

            case PREVIOUS_ITEM:
                inventory.selectPrevPower();
                break;

            case NEXT_ITEM:
                inventory.selectNextPower();
                break;
            
            case SWITCH_SHOW_FPS:
                drawer.switchFps();
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

        iceSystem.tick(now, this::simulatePlayerMovement, forced -> {
            currentAction = forced;
            lastMovement = forced;
        });
    }

    private void updateCheatedSystem() {
        cheatWallSystem.update(board.getCells(), player.getX(), player.getY(), lasNow);

        iceSystem.tick(lasNow, this::simulatePlayerMovement, forced -> {
            currentAction = forced;
            lastMovement = forced;
        });
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

    // ✅ Wrapper a UseSystem
    private void use() {
        ItemType used = useSystem.use(lastMovement, currentAction, LOCKED_EXIT);
        lastPower = used;
    }

    private void useItem(ItemType item, DnResult dn) {
        switch (item) {
            case PowerType.PICKAXE:
                usePickaxe(item, dn);
                break;

            case PowerType.BLAI_GLASSES:
                tryToActiveBlaiGlasses(dn);
                break;

            case PowerType.KEY:
                tryToOpenExit(dn);
                break;

            default:
                break;
        }
    }

    // =========================
    // PICKAXE (usa DoorSystem)
    // =========================
    private void usePickaxe(ItemType item, DnResult dn) {
        if (item != PowerType.PICKAXE)
            return;

        int dx = dn.dx();
        int dy = dn.dy();

        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx();
        int ny = dn.ny();

        boolean outOfBounds = nx < 1 || ny < 1 ||
                nx >= Board.BOARD_WIDTH - 1 ||
                ny >= Board.BOARD_HEIGHT - 1;

        if (outOfBounds) {
            playDoorHit();
            return;
        }

        int cell = dn.cell();

        if (!Cells.hasCollision(cell))
            return;

        if (doorSystem.isDoorClosed(cell)) {
            boolean opened = false;

            if (doorSystem.isDoorClosedButOpenable(cell)) {
                opened = doorSystem.tryOpenDoorAt(nx, ny, dx, dy, cell);
            }

            if (!opened)
                playDoorHit();
            return;
        }

        if (doorSystem.isDoorOpened(cell)) {
            playDoorHit();
            return;
        }

        if (cell != WALL) {
            if (Cells.playMetalSound(cell)) {
                playDoorHit();
            }
            return;
        }

        boolean isBorderWall = nx <= 0 || ny <= 0 ||
                nx >= Board.BOARD_WIDTH - 1 ||
                ny >= Board.BOARD_HEIGHT - 1;

        if (isBorderWall) {
            playDoorHit();
            return;
        }

        if (wallTouchesDoor(nx, ny)) {
            playDoorHit();
            return;
        }

        if (!inventory.consumeOne(item))
            return;

        board.updateTile(nx, ny, DESTROYED_PATH);
        sm.playSfx(Sound.PICKAXE_WALL.path());
    }

    private boolean wallTouchesDoor(int x, int y) {
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (int[] d : dirs) {
            int ax = x + d[0];
            int ay = y + d[1];

            if (ax < 0 || ay < 0 || ax >= Board.BOARD_WIDTH || ay >= Board.BOARD_HEIGHT)
                continue;

            int neighbor = board.getTile(ax, ay);

            if (doorSystem.isAnyDoor(neighbor) || doorSystem.isDoorOpened(neighbor)
                    || doorSystem.isDoorClosedButOpenable(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private void playDoorHit() {
        sm.playSfxWithTailDelay(Sound.PICKAXE_DOOR.path(), 1.0, true, 200);
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

        if (type.removeRemaining()) {
            placer.removeAllOfType(type);
        }

        runPickup(type);

        String sfx = type.getPickupSoundPath();
        if (sfx != null && !sfx.isBlank()) {
            sm.playSfx(sfx);
        }

        log.info("Picked up {} at ({},{})", type.getId(), x, y);
    }

    private void runPickup(ItemType type) {
        if (type.isAPower()) {
            inventory.addPower(type);
        }

        switch (type) {
            case PowerType.KEY:
                board.openSecretWalls();
                break;

            case SpecialType.CHEATED_BUTTON:
                cheatWallSystem.switchOff();
                break;

            case SpecialType.BOOTS:
                iceSystem.setIceActivated(false);
                break;

            default:
                break;
        }
    }

    public ItemType getLastPower() {
        return lastPower;
    }

    // =========================
    // BLAI GLASSES (usa DoorSystem)
    // =========================
    private static final long BLAI_GLASSES_MAX_POWER = 5_000_000_000L;
    private boolean blaiGlassesActive = false;
    private long blaiGlassesRemainingNs = 0L;

    private void tryToActiveBlaiGlasses(DnResult dn) {
        int dx = dn.dx();
        int dy = dn.dy();

        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx();
        int ny = dn.ny();
        int cell = dn.cell();

        if (doorSystem.isDoorClosed(cell)) {
            boolean opened = false;

            if (doorSystem.isDoorClosedButOpenable(cell)) {
                opened = doorSystem.tryOpenDoorAt(nx, ny, dx, dy, cell);
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
        double dis = Math.hypot(dx, dy);
        return Math.round(dis * 100.0) / 100.0;
    }

    // =========================
    // KEY / EXIT (usa DoorSystem)
    // =========================
    private void tryToOpenExit(DnResult dn) {
        int dx = dn.dx();
        int dy = dn.dy();

        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx();
        int ny = dn.ny();
        int cell = dn.cell();

        if (doorSystem.isDoorClosed(cell) && doorSystem.isDoorClosedButOpenable(cell)) {
            doorSystem.tryOpenDoorAt(nx, ny, dx, dy, cell);
            return;
        }

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
