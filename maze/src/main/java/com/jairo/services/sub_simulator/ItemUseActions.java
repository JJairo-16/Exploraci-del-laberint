package com.jairo.services.sub_simulator;

import com.jairo.app.audio.Sound;
import com.jairo.app.audio.SoundManager;
import com.jairo.items.ItemType;
import com.jairo.items.PowerType;
import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.models.Inventory;
import com.jairo.services.ItemPlacer;
import com.jairo.services.sub_simulator.UseSystem.DnResult;
import com.jairo.services.sub_simulator.coin_system.CoinsPowerState;
import com.jairo.utils.map_generator.Cells;

import static com.jairo.utils.map_generator.Cells.*;

import java.util.Random;

public class ItemUseActions {
    private final Random random = new Random(); 

    private final Player player;
    private final Board board;
    private final Inventory inventory;
    private final ItemPlacer placer;
    private final DoorSystem doorSystem;
    private final SoundManager sm;

    private static final String SOUND_GROUP_NAME = "brokenKeyFail";

    public ItemUseActions(
            Player player,
            Board board,
            Inventory inventory,
            ItemPlacer placer,
            DoorSystem doorSystem,
            SoundManager soundManager) {
        this.player = player;
        this.board = board;
        this.inventory = inventory;
        this.placer = placer;
        this.doorSystem = doorSystem;
        this.sm = soundManager;

        sm.defineGroup(SOUND_GROUP_NAME, 
            Sound.JIJI_CA.path(),
            Sound.JIJI_ES.path(),
            Sound.JIJI_EN.path(),
            Sound.BROKEN_KEY_FAIL.path()
        );
    }

    // =========================
    // ENTRYPOINT
    // =========================
    public void onUse(ItemType item, DnResult dn) {
        if (item == null || dn == null)
            return;

        switch (item) {
            case PowerType.PICKAXE -> usePickaxe(dn);
            case PowerType.BLAI_GLASSES -> tryToActiveBlaiGlasses(dn);
            case PowerType.KEY -> tryToOpenExit(dn);
            case PowerType.BROKEN_KEY -> tryToUseBrokenKey(dn);
            default -> {
                /* no-op */ }
        }
    }

    // =========================
    // PICKAXE (usa DoorSystem)
    // =========================
    private void usePickaxe(DnResult dn) {
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

        if (!Cells.hasCollision(cell)) {
            if (doorSystem.isDoorOpened(cell)) {
                playDoorHit();
            }

            return;
        }

        if (doorSystem.isAnyDoor(cell)) {
            boolean opened = false;

            if (doorSystem.isDoorClosedButOpenable(cell)) {
                opened = doorSystem.tryOpenDoorAt(nx, ny, dx, dy, cell);
            }

            if (!opened) {
                playDoorHit();
            }
        }

        if (!Cells.isBreakable(cell)) {
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

        if (!inventory.consumeOne(PowerType.PICKAXE))
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

            if (doorSystem.isAnyDoor(neighbor)
                    || doorSystem.isDoorOpened(neighbor)
                    || doorSystem.isDoorClosedButOpenable(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private void playDoorHit() {
        sm.playSfxWithTailDelay(Sound.PICKAXE_DOOR.path(), 1.0, true, 200);
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

        if (cell == EXIT)
            return;

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
        int playerX = player.getX();
        int playerY = player.getY();

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

        if (cell != LOCKED_EXIT)
            return;
        if (CoinsPowerState.getLevel() < 4) {
            playLockedExit();
            return;
        }

        inventory.consumeOne(PowerType.KEY);
        board.updateTile(nx, ny, EXIT);
        sm.playSfx(Sound.OPEN_LOCK.path());
    }

    public void playLockedExit() {
        sm.playSfxWithTailDelay(Sound.EXIT_LOCK.path(), 1.5, false, 50);
    }

    // =========================
    // BROKEN KEY
    // =========================

    private boolean randomWithProbably(int probably) {
        return random.nextInt(100) < probably;
    }

    private static final int BROKEN_KEY_EXIT_RATIO = 35;
    private static final int JIJI_RATIO = 35;
    private static final long DELAY_MS = 20L;

    private void tryToUseBrokenKey(DnResult dn) {
        if (sm.isGroupPlaying(SOUND_GROUP_NAME)) return;

        int dx = dn.dx();
        int dy = dn.dy();
        if (dx == 0 && dy == 0)
            return;

        int nx = dn.nx();
        int ny = dn.ny();
        int cell = dn.cell();

        if (!doorSystem.isDoorClosed(cell))
            return;

        boolean opened = false;

        if (doorSystem.isDoorClosedButOpenable(cell)) {
            opened = doorSystem.tryOpenDoorAt(nx, ny, dx, dy, cell);
        }

        if (opened)
            return;

        inventory.consumeOne(PowerType.BROKEN_KEY);

        if (randomWithProbably(BROKEN_KEY_EXIT_RATIO)) {
            doorSystem.tryOpenDoorAt(nx, ny, dx, dy, cell, true);
            return;
        }

        String path;
        long delay;
        if (randomWithProbably(JIJI_RATIO)) {
            path = doorSystem.getJiJiPath();
            delay = DoorSystem.DELAY_MS;
        } else {
            path = Sound.BROKEN_KEY_FAIL.path();
            delay = DELAY_MS;
        }

        sm.playSfxWithTailDelay(path, 1.0, false, delay);
    }
}
