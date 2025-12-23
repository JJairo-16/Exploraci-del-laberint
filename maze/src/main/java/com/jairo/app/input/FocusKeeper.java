package com.jairo.app.input;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * Mantiene el foco de teclado en un nodo target (normalmente root o leftPane).
 * Soluciona el problema típico de JavaFX: al clicar UI (ChoiceBox, paneles, etc.)
 * el foco se pierde y el juego deja de leer teclas.
 */
public final class FocusKeeper {

    private FocusKeeper() {}

    /**
     * Instala handlers para recuperar foco:
     * - Click en cualquier nodo del "área de juego" -> target.requestFocus()
     * - Cuando se cierra el desplegable del ChoiceBox -> target.requestFocus()
     * - Si el foco se va a un sitio raro, intentamos recuperarlo (opcional y suave)
     */
    public static void install(Node target, Node... clickToRefocus) {
        if (target == null) return;

        // Click en área de juego => recuperar foco
        if (clickToRefocus != null) {
            for (Node n : clickToRefocus) {
                if (n == null) continue;
                n.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                    // runLater para no pelear con el foco que pone el control clickado
                    Platform.runLater(target::requestFocus);
                });
            }
        }

        // Si el target entra en escena, intenta coger foco
        target.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(target::requestFocus);
            }
        });

        // Extra: si la escena procesa una tecla y el target no tiene foco, recupéralo
        // (ayuda cuando el foco se pierde por diálogos/choicebox)
        target.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (!target.isFocused()) {
                    Platform.runLater(target::requestFocus);
                }
            });
        });
    }

    /**
     * Helper específico para ChoiceBox: al esconder el menú, devolver foco al target.
     */
    public static void bindChoiceBoxRefocus(ChoiceBox<?> choiceBox, Node target) {
        if (choiceBox == null || target == null) return;
        choiceBox.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (wasShowing && !isShowing) {
                Platform.runLater(target::requestFocus);
            }
        });
    }
}
