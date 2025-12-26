package com.jairo.app.gfx;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ImageStore {
    private ImageStore() {}

    public static ImageStore getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final ImageStore INSTANCE = new ImageStore();
    }

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    // Cache principal: 1 entrada por Sprite (rápido)
    private final EnumMap<Sprite, Image> cache = new EnumMap<>(Sprite.class);

    // Cache secundario: comparte Image por "path" SOLO si el sprite es mergeable
    private final Map<String, Image> mergeablePathCache = new HashMap<>();

    public Image get(Sprite sprite) {
        if (sprite == null) return null;
        return cache.computeIfAbsent(sprite, this::load);
    }

    private Image load(Sprite sprite) {
        final String p = sprite.path();

        // Si es mergeable, comparte instancia por ruta
        if (sprite.isMergeable()) {
            return mergeablePathCache.computeIfAbsent(p, this::loadByPath);
        }

        // Si no es mergeable, carga “normal”
        return loadByPath(p);
    }

    private Image loadByPath(String p) {
        log.debug("Loading image resource: {}", p);

        // 1) Classpath
        URL url = ImageStore.class.getResource(p);
        if (url != null) {
            return new Image(url.toExternalForm(), false);
        }

        // 2) Fallback filesystem
        try {
            Path filePath = Path.of(p);
            if (Files.exists(filePath)) {
                return new Image(filePath.toUri().toString(), false);
            }
        } catch (Exception ignored) {}

        log.warn("Sprite not found at path: {}", p);
        throw new IllegalStateException("No s'ha trobat el recurs: " + p);
    }

    public void preloadAll() {
        for (Sprite s : Sprite.values()) {
            try {
                get(s);
            } catch (Exception e) {
                log.warn("Failed to preload sprite {}", s, e);
            }
        }
    }

    public void evict(Sprite sprite) {
        if (sprite == null) return;
        cache.remove(sprite);
        // Nota: NO tocamos mergeablePathCache aquí, porque puede estar compartido por otros sprites.
    }

    public Image reload(Sprite sprite) {
        if (sprite == null) return null;

        // Quitamos el sprite del cache
        cache.remove(sprite);

        // Si es mergeable, invalida la ruta compartida para que se recargue “de verdad”
        if (sprite.isMergeable()) {
            mergeablePathCache.remove(sprite.path());
        }

        Image img = load(sprite);
        cache.put(sprite, img);
        return img;
    }

    public Image reloadPlayer() {
        return reload(Sprite.PLAYER);
    }
}
