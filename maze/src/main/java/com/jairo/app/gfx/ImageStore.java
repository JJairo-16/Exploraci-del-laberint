package com.jairo.app.gfx;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

public final class ImageStore {
    private ImageStore() {
    }

    public static ImageStore getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final ImageStore INSTANCE = new ImageStore();
    }

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    private final EnumMap<Sprite, Image> cache = new EnumMap<>(Sprite.class);

    public Image get(Sprite sprite) {
        if (sprite == null)
            return null;
        return cache.computeIfAbsent(sprite, this::load);
    }

    private Image load(Sprite sprite) {
        String p = sprite.path();
        log.debug("Loading sprite: {}", sprite);

        // 1) Classpath primero (lo normal en un juego empaquetado)
        URL url = ImageStore.class.getResource(p);
        if (url != null) {
            return new Image(url.toExternalForm(), false);
        }

        // 2) Fallback filesystem (solo si realmente existe)
        try {
            Path filePath = Path.of(p);
            if (Files.exists(filePath)) {
                return new Image(filePath.toUri().toString(), false);
            }
        } catch (Exception ignored) {
        }

        log.warn("Sprite not found: {}", sprite);
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
        cache.remove(sprite);
    }

    public Image reload(Sprite sprite) {
        evict(sprite);
        Image img = load(sprite);
        cache.put(sprite, img);
        return img;
    }

    public Image reloadPlayer() {
        return reload(Sprite.PLAYER);
    }
}
