package com.jairo.services;

import com.jairo.models.Board;
import com.jairo.models.Player;
import com.jairo.utils.KeyBind.Action;
import com.jairo.items.ItemType;
import com.jairo.items.PlacedItem;
import com.jairo.models.Inventory;
import com.jairo.items.PowerType;
import com.jairo.items.SpecialType;

import static com.jairo.utils.map_generator.Cells.*;

import com.jairo.app.audio.SoundManager;
import com.jairo.app.audio.Steps;
import com.jairo.app.gfx.Drawer;
import com.jairo.app.gfx.player_skins.SkinManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jairo.services.sub_simulator.IceSlideSystem;
import com.jairo.services.sub_simulator.UseSystem;
import com.jairo.services.sub_simulator.DoorSystem;
import com.jairo.services.sub_simulator.ItemUseActions;

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

    // ✅ Extraído: ItemUseActions (lógica de pico/gafas/llave)
    private final ItemUseActions itemUseActions;

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

        this.itemUseActions = new ItemUseActions(
                player,
                board,
                inventory,
                placer,
                doorSystem,
                sm
        );

        // ✅ UseSystem: abre puerta mediante DoorSystem
        this.useSystem = new UseSystem(
                player,
                board,
                inventory,
                (item, dn) -> {
                    lastPower = item;
                    itemUseActions.onUse(item, dn);
                },
                action -> doorSystem.tryToOpenDoor(action, player.getX(), player.getY()),
                itemUseActions::playLockedExit);

        log.info("Simulator created");
    }

    public void loadDrawer(Drawer drawer) {
        this.drawer = drawer;
        this.iceSystem.setDrawer(drawer);
        log.info("Drawer loaded into Simulator: {}", drawer);
    }

    public void simulate(Action action) {
        // Si estamos deslizándonos en hielo, ignorar inputs de movimiento para no “romper” el slide
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
                int ny = dy + player.getY(); // ✅ fix: era dx + player.getY()
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
                if (drawer != null) drawer.zoomIn();
                log.info("Zoom in");
                break;

            case ZOOM_OUT:
                if (drawer != null) drawer.zoomOut();
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
                inventory.selectPrevPowerWithJump();
                break;

            case NEXT_ITEM:
                inventory.selectNextPowerWithJump();
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

    public record Position(int x, int y) {}

    public Position getPlayerPosition() {
        return new Position(player.getX(), player.getY());
    }

    // ✅ Wrapper a UseSystem
    private void use() {
        ItemType used = useSystem.use(lastMovement, currentAction, LOCKED_EXIT);
        lastPower = used;
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
        if (picked == null) return;

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
    // DELEGADOS DE BLAI GLASSES
    // =========================
    public boolean isBlaiGlassesPowerActive() {
        return itemUseActions.isBlaiGlassesPowerActive();
    }

    public void offBlaiGlasses() {
        itemUseActions.offBlaiGlasses();
    }

    public long getRemainingBlaiGlassesPower() {
        return itemUseActions.getRemainingBlaiGlassesPower();
    }

    public void updateRemainingBlaiGlassesPower(long update) {
        itemUseActions.updateRemainingBlaiGlassesPower(update);
    }

    public double getBlaiNumber() {
        return itemUseActions.getBlaiNumber();
    }
}
