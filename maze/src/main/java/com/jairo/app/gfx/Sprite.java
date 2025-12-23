package com.jairo.app.gfx;

import static com.jairo.app.gfx.SpritesRoot.*;

public enum Sprite {
    NONE(GAME.get("none.png")),

    WALL(GAME.get("wall.png")),
    PATH(GAME.get("path.png")),
    DESTROYED_PATH(GAME.get("destroyed-path.png")),

    EXIT(GAME.get("exit.png")),
    EXIT_CONNECTOR(GAME.get("exit.png")),
    LOCKED_EXIT(GAME.get("locked-exit.png")),

    DOOR_OPEN_FROM_NORTH(DOORS.get("door_open_from_north.png")),
    DOOR_OPEN_FROM_SOUTH(DOORS.get("door_open_from_south.png")),
    DOOR_OPEN_FROM_WEST(DOORS.get("door_open_from_west.png")),
    DOOR_OPEN_FROM_EAST(DOORS.get("door_open_from_east.png")),

    DOOR_OPENED_FROM_NORTH(DOORS.get("door_opened_from_north.png")),
    DOOR_OPENED_FROM_SOUTH(DOORS.get("door_opened_from_south.png")),
    DOOR_OPENED_FROM_WEST(DOORS.get("door_opened_from_west.png")),
    DOOR_OPENED_FROM_EAST(DOORS.get("door_opened_from_east.png")),

    CHEATED_PATH(GAME.get("cheated-path.png")),
    CHEATED_WALL(Sprite.PATH.path()),
    CHEAT_WALL_ACTIVE(Sprite.WALL.path()),
    CHEATED_WALL_SOLID(Sprite.WALL.path()),
    RUNES(GAME.get("runes.png")),
    
    ARROW(GEN.get("arrow.png")),
    PLAYER(PL_SKINS.get("default.png")),
    
    COIN(ITEMS.get("coin.png")),
    PICKAXE(ITEMS.get("pickaxe.png")),
    BLAI_GLASSES(ITEMS.get("blai-glasses.png")),
    KEY(ITEMS.get("key.png")),
    CHEATED_BUTTON(ITEMS.get("cheated-button.png"));

    static {
        DOOR_OPEN_FROM_NORTH.updateFillTile();
        DOOR_OPEN_FROM_SOUTH.updateFillTile();
        DOOR_OPEN_FROM_WEST.updateFillTile();
        DOOR_OPEN_FROM_EAST.updateFillTile();

        DOOR_OPENED_FROM_NORTH.updateFillTile();
        DOOR_OPENED_FROM_SOUTH.updateFillTile();
        DOOR_OPENED_FROM_WEST.updateFillTile();
        DOOR_OPENED_FROM_EAST.updateFillTile();

        LOCKED_EXIT.updateFillTile(Sprite.EXIT);

        CHEATED_PATH.updateFillTile(Sprite.PATH);
    }
    
    private String resourcePath;
    public final double rotation;
    private boolean fullTile = true;
    private Sprite back;

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
}
