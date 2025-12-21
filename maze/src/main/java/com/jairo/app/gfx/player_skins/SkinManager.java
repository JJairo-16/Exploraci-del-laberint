package com.jairo.app.gfx.player_skins;

import com.jairo.app.gfx.Sprite;
import com.jairo.app.gfx.ImageStore;

import java.util.Objects;

public final class SkinManager {
    private static final SkinManager INSTANCE = new SkinManager();

    private Skin current = Skin.DEFAULT;

    private SkinManager() {
    }

    public static SkinManager get() {
        return INSTANCE;
    }

    public Skin current() {
        return current;
    }

    public void set(Skin skin) {
        Objects.requireNonNull(skin);
        if (skin == current) return;

        current = skin;
        Sprite.PLAYER.reload(skin.playerPath());
        ImageStore.getInstance().reloadPlayer();
    }

    public void next() {
        Skin[] skins = Skin.values();
        int i = current.ordinal();
        set(skins[(i + 1) % skins.length]);
    }

    public void previous() {
        Skin[] skins = Skin.values();
        int i = current.ordinal();
        set(skins[(i - 1 + skins.length) % skins.length]);
    }
}
