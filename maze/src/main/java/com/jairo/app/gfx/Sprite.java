package com.jairo.app.gfx;

public enum Sprite {
    WALL(getRoot("game/wall.png")),
    PATH(getRoot("game/path.png")),

    EXIT(getRoot("game/exit.png")),
    EXIT_CONNECTOR(getRoot("game/exit.png")),
    
    ARROW(getRoot("arrow.png")),
    PLAYER(getRoot("game/playerSkins/default.png"));

    private static final String BASE_ROOT = "/com/jairo/app/img/";
    private static String getRoot(String path) {
        return BASE_ROOT + path;
    }
    
    private String resourcePath;

    Sprite(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String path() {
        return resourcePath;
    }

    public void reload(String newPath) {
        this.resourcePath = newPath;
    }
}
