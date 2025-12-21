package com.jairo.app.gfx;

public enum Sprite {
    WALL("/com/jairo/app/img/game/wall.png"),
    PATH("/com/jairo/app/img/game/path.png"),

    EXIT("/com/jairo/app/img/game/exit.png"),
    EXIT_CONNECTOR("/com/jairo/app/img/game/exit.png"),
    
    PLAYER("/com/jairo/app/img/game/player.png");
    
    private final String resourcePath;

    Sprite(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String path() {
        return resourcePath;
    }
}
