package com.jairo.app.gfx;

import static com.jairo.app.gfx.SpritesRoot.*;

import java.util.List;

public enum Sprite {
    // * 
    NONE(GAME.get("none.png")),

    // * Base map
    WALL(GAME.get("wall.png")),
    PATH(GAME.get("path.png")),
    DESTROYED_PATH(GAME.get("destroyed-path.png")),

    // * Exit
    EXIT(GAME.get("exit.png")),
    EXIT_CONNECTOR(GAME.get("exit.png")),
    LOCKED_EXIT(GAME.get("locked-exit.png")),

    // * Doors
    // Closed
    DOOR_OPEN_FROM_NORTH(GAME.get("closed-door.png"), 0),
    DOOR_OPEN_FROM_SOUTH(GAME.get("closed-door.png"), 180),
    DOOR_OPEN_FROM_EAST(GAME.get("closed-door.png"), 90),
    DOOR_OPEN_FROM_WEST(GAME.get("closed-door.png"), -90),

    // Opened
    DOOR_OPENED_FROM_NORTH(GAME.get("opened-door.png"), 180),
    DOOR_OPENED_FROM_SOUTH(GAME.get("opened-door.png"), 0),
    DOOR_OPENED_FROM_EAST(GAME.get("opened-door.png"), -90),
    DOOR_OPENED_FROM_WEST(GAME.get("opened-door.png"), 90),

    // * Cheated
    CHEATED_PATH(GAME.get("cheated-path.png")),
    HIDDEN_CHEATED_PATH(Sprite.PATH.path()),
    CHEATED_WALL(Sprite.PATH.path()),
    CHEAT_WALL_ACTIVE(Sprite.WALL.path()),
    CHEATED_WALL_SOLID(Sprite.WALL.path()),
    SECRET_WALL(Sprite.WALL.path()),
    
    // * Player
    ARROW(GEN.get("arrow.png")),
    PLAYER(PL_SKINS.get("default.png")),
    
    // * Items
    // Basic
    COIN(ITEMS.get("coin.png")),
    MAP(ITEMS.get("map.png")),
    PORTAL_GUN(ITEMS.get("portal-gun.png")),
    
    // Power
    PICKAXE(ITEMS.get("pickaxe.png")),
    BLAI_GLASSES(ITEMS.get("blai-glasses.png")),
    KEY(ITEMS.get("key.png")),
    BROKEN_KEY(ITEMS.get("broken-key.png")),

    // Special
    CHEATED_BUTTON(ITEMS.get("cheated-button.png")),
    BOOTS(ITEMS.get("boots.png")),

    // * Other
    ICE(GAME.get("ice.png"));

    private static final List<Sprite> doors = List.of(
        DOOR_OPEN_FROM_NORTH,
        DOOR_OPEN_FROM_SOUTH,
        DOOR_OPEN_FROM_WEST,
        DOOR_OPEN_FROM_EAST,

        DOOR_OPENED_FROM_NORTH,
        DOOR_OPENED_FROM_SOUTH,
        DOOR_OPENED_FROM_WEST,
        DOOR_OPENED_FROM_EAST
    );

    private static final List<Sprite> mergeable = List.of(
        EXIT,
        EXIT_CONNECTOR,
        CHEATED_WALL,
        CHEATED_WALL_SOLID,
        CHEAT_WALL_ACTIVE,
        SECRET_WALL
    );

    static {
        for (Sprite d : doors) {
            d.updateFillTile();
            d.doItMergeable();
        }

        for (Sprite m : mergeable) {
            m.doItMergeable();
        }

        LOCKED_EXIT.updateFillTile(Sprite.EXIT);
        CHEATED_PATH.updateFillTile();
    }
    
    private String resourcePath;
    private double rotation;
    private boolean fullTile = true;
    private Sprite back;
    private boolean canMerge = false;

    Sprite(String resourcePath, double rotation) {
        this.resourcePath = resourcePath;
        this.rotation = rotation;
    }

    Sprite(String resourcePath) {
        this.resourcePath = resourcePath;
        this.rotation = 0;
    }

    public String path() {
        return resourcePath;
    }

    public void reload(String newPath) {
        this.resourcePath = newPath;
    }

    private void updateFillTile() {
        fullTile = false;
        back = PATH;
    }

    private void updateFillTile(Sprite back) {
        fullTile = false;
        this.back = back;
    }

    public boolean getIfIsFullTile() {
        return fullTile;
    }

    public Sprite getBack() {
        return back;
    }

    private void doItMergeable() {
        this.canMerge = true;
    }

    public boolean isMergeable() {
        return canMerge;
    }

    public void rotate() {
        if (this != PLAYER) return;
        rotation = 180 - rotation;
    }

    public double getRotation() {
        return rotation;
    }
}
