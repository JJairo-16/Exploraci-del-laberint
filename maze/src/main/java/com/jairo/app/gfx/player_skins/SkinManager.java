package com.jairo.app.gfx.player_skins;

import com.jairo.app.gfx.ImageStore;
import com.jairo.app.gfx.Sprite;
import com.jairo.items.PowerType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SkinManager {
    private static final SkinManager INSTANCE = new SkinManager();

    private Skin current = Skin.DEFAULT;

    // tuningBySkin: Skin -> (PowerType -> HeldItemTuning)
    private final EnumMap<Skin, EnumMap<PowerType, HeldItemTuning>> tuningBySkin = new EnumMap<>(Skin.class);

    // fallback "default" del JSON, por PowerType (si existe)
    private final EnumMap<PowerType, HeldItemTuning> defaultByPower = new EnumMap<>(PowerType.class);

    private SkinManager() {
        Map<String, EnumMap<PowerType, HeldItemTuning>> loaded =
                HeldItemTuningLoader.loadAllFromResources("config/HeldItemTuningConfig.json");

        // Guardamos defaultByPower (puede estar vacío)
        EnumMap<PowerType, HeldItemTuning> def = loaded.get("default");
        if (def != null) defaultByPower.putAll(def);

        // Preparamos mapa por cada skin
        for (Skin s : Skin.values()) {
            EnumMap<PowerType, HeldItemTuning> perPower = new EnumMap<>(PowerType.class);

            EnumMap<PowerType, HeldItemTuning> fromJson = loaded.get(s.id);
            if (fromJson != null) perPower.putAll(fromJson);

            tuningBySkin.put(s, perPower);
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
        if (skin == current) return;

        current = skin;
        Sprite.PLAYER.reload(skin.playerPath());
        ImageStore.getInstance().reloadPlayer();
    }

    public void next() {
        Skin[] skins = Skin.values();
        int max = skins.length - 1;
        int i = current.ordinal();
        set(skins[(i + 1) % max]);
    }

    public void previous() {
        Skin[] skins = Skin.values();
        int max = skins.length - 1;
        int i = current.ordinal();
        set(skins[(i - 1 + max) % max]);
    }

    // -------------------------------
    // Tuning público (por PowerType)
    // -------------------------------

    /** Devuelve el tuning del skin actual para un PowerType, con fallback a "default" y luego defaults(). */
    public HeldItemTuning heldItemTuning(PowerType power) {
        Objects.requireNonNull(power);

        EnumMap<PowerType, HeldItemTuning> perSkin = tuningBySkin.get(current);
        if (perSkin != null) {
            HeldItemTuning t = perSkin.get(power);
            if (t != null) return t;
        }

        HeldItemTuning def = defaultByPower.get(power);
        return (def != null) ? def : HeldItemTuning.defaults();
    }

    /** Setea el tuning del skin actual para ese PowerType. */
    public void setHeldItemTuning(PowerType power, HeldItemTuning tuning) {
        Objects.requireNonNull(power);
        Objects.requireNonNull(tuning);

        tuningBySkin.computeIfAbsent(current, k -> new EnumMap<>(PowerType.class))
                .put(power, tuning);
    }

    /** Reset del tuning del skin actual para ese PowerType (elimina override del skin). */
    public void resetHeldItemTuning(PowerType power) {
        Objects.requireNonNull(power);

        EnumMap<PowerType, HeldItemTuning> perSkin = tuningBySkin.get(current);
        if (perSkin != null) perSkin.remove(power);
    }

    // -------------------------------
    // Tweaks (por PowerType)
    // -------------------------------

    public void tweakHeldItemBaseScale(PowerType power, double delta) {
        HeldItemTuning t = heldItemTuning(power);
        setHeldItemTuning(power, t.withBaseScale(Math.clamp(t.baseScale() + delta, 0.30, 2.00)));
    }

    public void tweakHeldItemNoCursorScaleMul(PowerType power, double delta) {
        HeldItemTuning t = heldItemTuning(power);
        setHeldItemTuning(power, t.withNoCursorScaleMul(Math.clamp(t.noCursorScaleMul() + delta, 0.50, 3.00)));
    }

    public void tweakHeldItemCursorOffset(PowerType power, double deltaX, double deltaY) {
        HeldItemTuning t = heldItemTuning(power);
        setHeldItemTuning(power, t.withCursorOffset(
                Math.clamp(t.cursorOffsetMulX() + deltaX, -5, 5),
                Math.clamp(t.cursorOffsetMulY() + deltaY, -5, 5)
        ));
    }

    public void tweakHeldItemNoCursorOffset(PowerType power, double deltaX, double deltaY) {
        HeldItemTuning t = heldItemTuning(power);
        setHeldItemTuning(power, t.withNoCursorOffset(
                Math.clamp(t.noCursorOffsetMulX() + deltaX, -5, 5),
                Math.clamp(t.noCursorOffsetMulY() + deltaY, -5, 5)
        ));
    }

    // --- NUEVO: ROTACIÓN ---
    public void tweakHeldItemRotation(PowerType power, double deltaDeg) {
        HeldItemTuning t = heldItemTuning(power);
        setHeldItemTuning(power, t.withRotation(Math.clamp(t.rotationDeg() + deltaDeg, -180, 180)));
    }

    public void tweakHeldItemNoCursorRotation(PowerType power, double deltaDeg) {
        HeldItemTuning t = heldItemTuning(power);
        setHeldItemTuning(power, t.withNoCursorRotation(Math.clamp(t.noCursorRotationDeg() + deltaDeg, -180, 180)));
    }
}
