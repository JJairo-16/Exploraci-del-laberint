package com.jairo.app.gfx;

public enum SpritesRoot {
    GEN("/com/jairo/app/img/"),
    GAME("/com/jairo/app/img/game/"),
    PL_SKINS("/com/jairo/app/img/game/playerSkins/"),
    ITEMS("/com/jairo/app/img/game/items/");

    private final String baseRoot;
    
    private SpritesRoot(String baseRoot) {
        this.baseRoot = baseRoot;
    }

    public String get(String path) {
        return baseRoot + path;
    }
}
