package com.jairo.app.gfx;

public enum Sprite {
    WALL("/com/jairo/app/img/game/wall.png"),
    PATH("/com/jairo/app/img/game/path.png"),
    PLAYER("/com/jairo/app/img/game/player.png");

    private final String resourcePath;

    Sprite(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String path() {
        return resourcePath;
    }
}
