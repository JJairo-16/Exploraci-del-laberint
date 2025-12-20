package com.jairo.app.i18n;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.jairo.app.Controller;
import com.jairo.app.gfx.Drawer;
import com.jairo.services.Simulator;

import java.io.IOException;
import java.util.*;

public final class LanguageManager {

    private static final String BUNDLE_BASE = "i18n.messages";
    private static final String START_FXML = "/com/jairo/app/start-view.fxml";
    private static final String MAIN_FXML = "/com/jairo/app/main-view.fxml";
    private static final String END_FXML = "/com/jairo/app/end-view.fxml";

    private static final Map<String, Locale> DISPLAY_TO_LOCALE;
    private static final Map<String, String> CODE_TO_DISPLAY;

    static {
        Map<String, Locale> map = new LinkedHashMap<>();
        map.put("Català", Locale.of("ca"));
        map.put("Español", Locale.of("es"));
        map.put("English", Locale.of("en"));
        DISPLAY_TO_LOCALE = Collections.unmodifiableMap(map);

        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, Locale> e : DISPLAY_TO_LOCALE.entrySet()) {
            reverse.put(e.getValue().getLanguage(), e.getKey());
        }
        CODE_TO_DISPLAY = Collections.unmodifiableMap(reverse);
    }

    private LanguageManager() {
    }

    public static List<String> getDisplayNames() {
        return new ArrayList<>(DISPLAY_TO_LOCALE.keySet());
    }

    public static String getCurrentDisplayName() {
        String lang = Locale.getDefault().getLanguage();
        return CODE_TO_DISPLAY.getOrDefault(lang, getDisplayNames().get(0));
    }

    public static String getCodeFromDisplayName(String display) {
        if (display == null)
            return null;
        Locale locale = DISPLAY_TO_LOCALE.get(display);
        return locale == null ? null : locale.getLanguage();
    }

    public static void setLocale(String code) {
        if (code == null)
            return;
        Locale.setDefault(Locale.of(code));
    }

    public static void switchToStartView(Scene scene) {
        switchTo(scene, START_FXML);
    }

    public static void switchToMainView(Scene scene) {
        switchTo(scene, MAIN_FXML);
    }

    public static void switchToEndView(Scene scene) {
        switchTo(scene, END_FXML);
    }

    public static void changeLanguageAndReloadStart(Scene scene, String code) {
        if (scene == null || code == null)
            return;
        setLocale(code);
        switchToStartView(scene);
    }

    public static void changeLanguageAndReloadMain(Scene scene, String code, Simulator simulator,
            Drawer.CameraState cameraState) {
        if (scene == null || code == null)
            return;

        setLocale(code);

        Locale locale = Locale.getDefault();
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);

        try {
            FXMLLoader loader = new FXMLLoader(LanguageManager.class.getResource(MAIN_FXML), bundle);
            Parent root = loader.load();

            Controller controller = loader.getController();
            if (controller != null) {
                controller.initState(simulator, cameraState);
            }

            scene.setRoot(root);

            Stage stage = (Stage) scene.getWindow();
            if (stage != null) {
                stage.setTitle(bundle.getString("app.title"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load view: " + MAIN_FXML, e);
        }
    }

    private static void switchTo(Scene scene, String fxmlAbsolutePath) {
        if (scene == null)
            return;

        Locale locale = Locale.getDefault();
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale);

        try {
            FXMLLoader loader = new FXMLLoader(LanguageManager.class.getResource(fxmlAbsolutePath), bundle);
            Parent root = loader.load();
            scene.setRoot(root);

            Stage stage = (Stage) scene.getWindow();
            if (stage != null) {
                stage.setTitle(bundle.getString("app.title"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load view: " + fxmlAbsolutePath, e);
        }
    }
}
