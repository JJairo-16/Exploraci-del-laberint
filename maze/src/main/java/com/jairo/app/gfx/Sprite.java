package com.jairo.app.gfx;

import static com.jairo.app.gfx.SpritesRoot.*;

public enum Sprite {
    NONE(GAME.get("none.png")),

    WALL(GAME.get("wall.png")),
    PATH(GAME.get("path.png")),
    DESTROYED_PATH(GAME.get("destroyed-path.png")),

    EXIT(GAME.get("exit.png")),
    EXIT_CONNECTOR(GAME.get("exit.png")),

    DOOR_OPEN_FROM_NORTH(DOORS.get("door_open_from_north.png")),
    DOOR_OPEN_FROM_SOUTH(DOORS.get("door_open_from_south.png")),
    DOOR_OPEN_FROM_WEST(DOORS.get("door_open_from_west.png")),
    DOOR_OPEN_FROM_EAST(DOORS.get("door_open_from_east.png")),

    DOOR_OPENED_FROM_NORTH(DOORS.get("door_opened_from_north.png")),
    DOOR_OPENED_FROM_SOUTH(DOORS.get("door_opened_from_south.png")),
    DOOR_OPENED_FROM_WEST(DOORS.get("door_opened_from_west.png")),
    DOOR_OPENED_FROM_EAST(DOORS.get("door_opened_from_east.png")),
    
    ARROW(GEN.get("arrow.png")),
    PLAYER(PL_SKINS.get("default.png")),
    
    COIN(ITEMS.get("coin.png")),
    PICKAXE(ITEMS.get("pickaxe.png"));

    static {
        DOOR_OPEN_FROM_NORTH.updateFillTile();
        DOOR_OPEN_FROM_SOUTH.updateFillTile();
        DOOR_OPEN_FROM_WEST.updateFillTile();
        DOOR_OPEN_FROM_EAST.updateFillTile();

        DOOR_OPENED_FROM_NORTH.updateFillTile();
        DOOR_OPENED_FROM_SOUTH.updateFillTile();
        DOOR_OPENED_FROM_WEST.updateFillTile();
        DOOR_OPENED_FROM_EAST.updateFillTile();
    }
    
    private String resourcePath;
    public final double rotation;
    private boolean fullTile = true;

    Sprite(String resourcePath, double rotaion) {
        this.resourcePath = resourcePath;
        this.rotation = rotaion;
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
    }

    public boolean getIfIsFullTile() {
        return fullTile;
    }
}
