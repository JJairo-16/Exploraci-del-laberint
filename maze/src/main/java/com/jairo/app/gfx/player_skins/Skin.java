package com.jairo.app.gfx.player_skins;

public enum Skin {
    DEFAULT(getRoot("default.png"), true),
    BLAI(getRoot("blai.png"), true),
    CRISTIAN(getRoot("cristian.png"), true),
    RUBEN(getRoot("ruben.png"), true),
    JESSICA(getRoot("jessica.jpg"), true),
    LAIA_C(getRoot("laia-c.png"), true),
    LAIA_V(getRoot("laia-v.png"), true),
    JORDI(getRoot("jordi.png"), true),
    ISABEL(getRoot("isabel.png"), true),
    MARTI(getRoot("marti.png"), true),
    DEV(getRoot("dev.png"));

    private static final String BASE_ROOT = "/com/jairo/app/img/game/playerSkins/";

    private static String getRoot(String path) {
        return BASE_ROOT + path;
    }

    private final String playerPath;
    private final boolean needArrow;
    public final String id;

    Skin(String playerPath, boolean needArrow) {
        this.playerPath = playerPath;
        this.needArrow = needArrow;
        this.id = extractId(playerPath);
    }

    Skin(String playerPath) {
        this.playerPath = playerPath;
        this.needArrow = false;
        this.id = extractId(playerPath);
    }

    public String playerPath() {
        return playerPath;
    }

    public boolean needArrow() {
        return needArrow;
    }

    private static String extractId(String playerPath) {
        String fileName = playerPath.substring(playerPath.lastIndexOf('/') + 1);
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

}