package com.jairo.services.sub_simulator;

import com.jairo.items.ItemType;
import com.jairo.models.Board;
import com.jairo.models.Inventory;
import com.jairo.models.Player;
import com.jairo.utils.KeyBind.Action;

/**
 * Extrae la lógica de USE + "qué hay delante" (DN: dx/dy/nx/ny/cell).
 * No implementa las acciones en sí: delega en callbacks (pickaxe, blai, key,
 * doors, locked-exit sound).
 */
public class UseSystem {

    public record DnResult(int dx, int dy, int nx, int ny, int cell) {
    }

    @FunctionalInterface
    public interface ItemUser {
        void use(ItemType item, DnResult dn);
    }

    @FunctionalInterface
    public interface DoorOpener {
        boolean tryOpenDoor(Action currentAction);
    }

    @FunctionalInterface
    public interface LockedExitHandler {
        void onLockedExit();
    }

    private final Player player;
    private final Board board;
    private final Inventory inventory;

    private final ItemUser itemUser;
    private final DoorOpener doorOpener;
    private final LockedExitHandler lockedExitHandler;

    public UseSystem(
            Player player,
            Board board,
            Inventory inventory,
            ItemUser itemUser,
            DoorOpener doorOpener,
            LockedExitHandler lockedExitHandler) {
        this.player = player;
        this.board = board;
        this.inventory = inventory;
        this.itemUser = itemUser;
        this.doorOpener = doorOpener;
        this.lockedExitHandler = lockedExitHandler;
    }

    /**
     * Ejecuta la acción USE.
     * - Si hay power seleccionado y existe en inventario -> delega a itemUser
     * - Si no, mira el tile delante. Si es LOCKED_EXIT -> lockedExitHandler
     * - Si no -> doorOpener (intenta abrir puerta con currentAction)
     *
     * @return el item usado o null si se ha hecho USE "sin item"
     */
    public ItemType use(Action lastMovement, Action currentAction, int lockedExit) {
        boolean opened = doorOpener.tryOpenDoor(currentAction);
        if (opened) return null;

        // 1) Intentar usar item seleccionado
        ItemType selected = inventory.getSelectedPower();
        if (selected != null && inventory.has(selected)) {
            DnResult dn = getDN(lastMovement);
            itemUser.use(selected, dn);
            return selected;
        }

        // 2) Sin item: interactuar con lo de delante
        DnResult dn = getDN(lastMovement);
        if (dn.cell == lockedExit) {
            lockedExitHandler.onLockedExit();
            return null;
        }

        return null;
    }

    public DnResult getDN(Action lastMovement) {
        int dx = 0;
        int dy = 0;

        switch (lastMovement) {
            case UP -> dy = -1;
            case DOWN -> dy = 1;
            case LEFT -> dx = -1;
            case RIGHT -> dx = 1;
            default -> {
            }
        }

        int nx = player.getX() + dx;
        int ny = player.getY() + dy;

        int cell = board.getTile(nx, ny);

        return new DnResult(dx, dy, nx, ny, cell);
    }
}
