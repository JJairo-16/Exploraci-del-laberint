package com.jairo.app.gfx;

import javafx.scene.image.Image;

import java.util.EnumMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImageStore {
    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    private final EnumMap<Sprite, Image> cache = new EnumMap<>(Sprite.class);

    public Image get(Sprite sprite) {
        return cache.computeIfAbsent(sprite, this::load);
    }

    private Image load(Sprite sprite) {
        log.info("Loading sprite: {}", sprite);

        var url = ImageStore.class.getResource(sprite.path());
        if (url == null) {
            log.warn("Sprite not found: {}", sprite);
            throw new IllegalStateException("No s'ha trobat el recurs: " + sprite.path());
        }
        return new Image(url.toExternalForm(), false);
    }

    public void preloadAll() {
        for (Sprite s : Sprite.values()) {
            try {
                get(s);
            } catch (Exception e) {
            }
        }
    }

}
