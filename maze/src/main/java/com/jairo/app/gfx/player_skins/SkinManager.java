package com.jairo.app.gfx.player_skins;

import com.jairo.app.gfx.Sprite;
import com.jairo.app.gfx.ImageStore;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SkinManager {
    private static final SkinManager INSTANCE = new SkinManager();

    private Skin current = Skin.DEFAULT;

    // Tuning por skin (si no existe, usa defaults)
    private final EnumMap<Skin, HeldItemTuning> tuningBySkin = new EnumMap<>(Skin.class);

    private SkinManager() {
        Map<String, HeldItemTuning> tunings = HeldItemTuningLoader.loadFromResources("skins/HeldItemTuningConfig.json");

        for (Skin s : Skin.values()) {
            String id = s.id;
            HeldItemTuning tuning = tunings.getOrDefault(id, HeldItemTuning.defaults());
            tuningBySkin.put(s, tuning);
        }
    }

    public static SkinManager get() {
        return INSTANCE;
    }

    public Skin current() {
        return current;
    }

    public void set(Skin skin) {
        Objects.requireNonNull(skin);
        if (skin == current)
            return;

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

    // -------------------------------
    // Tuning público
    // -------------------------------

    /** Devuelve el tuning del skin actual. */
    public HeldItemTuning heldItemTuning() {
        return tuningBySkin.getOrDefault(current, HeldItemTuning.defaults());
    }

    /** Setea el tuning del skin actual. */
    public void setHeldItemTuning(HeldItemTuning tuning) {
        Objects.requireNonNull(tuning);
        tuningBySkin.put(current, tuning);
    }

    /** Reset tuning del skin actual. */
    public void resetHeldItemTuning() {
        tuningBySkin.put(current, HeldItemTuning.defaults());
    }

    /** Ajustes finos (paso delta) para el skin actual. */
    public void tweakHeldItemBaseScale(double delta) {
        HeldItemTuning t = heldItemTuning();
        setHeldItemTuning(t.withBaseScale(clamp(t.baseScale() + delta, 0.30, 2.00)));
    }

    public void tweakHeldItemNoCursorScaleMul(double delta) {
        HeldItemTuning t = heldItemTuning();
        setHeldItemTuning(t.withNoCursorScaleMul(clamp(t.noCursorScaleMul() + delta, 0.50, 3.00)));
    }

    public void tweakHeldItemCursorOffset(double deltaX, double deltaY) {
        HeldItemTuning t = heldItemTuning();
        setHeldItemTuning(t.withCursorOffset(
                clamp(t.cursorOffsetMulX() + deltaX, -5, 5),
                clamp(t.cursorOffsetMulY() + deltaY, -5, 5)));
    }

    public void tweakHeldItemNoCursorOffset(double deltaX, double deltaY) {
        HeldItemTuning t = heldItemTuning();
        setHeldItemTuning(t.withNoCursorOffset(
                clamp(t.noCursorOffsetMulX() + deltaX, -5, 5),
                clamp(t.noCursorOffsetMulY() + deltaY, -5, 5)));
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    static {

    }
}
