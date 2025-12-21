package com.jairo.app.gfx.player_skins;

public enum Skin {
    DEFAULT(getRoot("default.png")),
    BLAI(getRoot("blai.png"), true);

    private static final String BASE_ROOT = "/com/jairo/app/img/game/playerSkins/";
    private static String getRoot(String path) {
        return BASE_ROOT + path;
    }

    private final String playerPath;
    private final boolean needArrow;

    Skin(String playerPath, boolean needArrow) {
        this.playerPath = playerPath;
        this.needArrow = needArrow;
    }

    Skin(String playerPath) {
        this.playerPath = playerPath;
        this.needArrow = false;
    }

    public String playerPath() {
        return playerPath;
    }

    public boolean needArrow() {
        return needArrow;
    }
}