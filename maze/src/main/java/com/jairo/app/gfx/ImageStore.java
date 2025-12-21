package com.jairo.app.gfx;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

public final class ImageStore {
    private static ImageStore instance;

    private ImageStore() {}

    public static ImageStore getInstance() {
        if (instance == null) {
            instance = new ImageStore();
        }
        return instance;
    }

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    private final EnumMap<Sprite, Image> cache = new EnumMap<>(Sprite.class);

    public Image get(Sprite sprite) {
        return cache.computeIfAbsent(sprite, this::load);
    }

    private Image load(Sprite sprite) {
        log.info("Loading sprite: {}", sprite);

        String p = sprite.path();

        try {
            Path filePath = Path.of(p);
            if (Files.exists(filePath)) {
                return new Image(filePath.toUri().toString(), false);
            }
        } catch (Exception ignored) {
        }

        URL url = ImageStore.class.getResource(p);
        if (url == null) {
            log.warn("Sprite not found: {}", sprite);
            throw new IllegalStateException("No s'ha trobat el recurs: " + p);
        }
        return new Image(url.toExternalForm(), false);
    }

    public void preloadAll() {
        for (Sprite s : Sprite.values()) {
            try {
                get(s);
            } catch (Exception ignored) {
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
