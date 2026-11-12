package com.example.superplanningapp.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Светлая и тёмная тема. Цвета лежат в styles/theme-*.css как looked-up colors,
 * поэтому смена темы — это просто замена одного файла стилей во всех открытых
 * сценах: перерисовывать экраны не нужно. Выбор запоминается между запусками.
 */
public final class Theme {

    private static final String BASE = "/com/example/superplanningapp/styles/";
    private static final String APP = sheet("app.css");
    private static final String LIGHT = sheet("theme-light.css");
    private static final String DARK = sheet("theme-dark.css");

    private static final Preferences PREFS = prefs();
    private static final BooleanProperty dark =
            new SimpleBooleanProperty(PREFS != null && PREFS.getBoolean("dark", false));
    private static final List<WeakReference<Object>> targets = new ArrayList<>();

    static {
        dark.addListener((obs, was, now) -> {
            if (PREFS != null) {
                try { PREFS.putBoolean("dark", now); } catch (Exception ignored) { }
            }
            refresh();
        });
    }

    private Theme() { }

    private static String sheet(String name) {
        URL url = Theme.class.getResource(BASE + name);
        if (url == null) throw new IllegalStateException("Не найден файл стилей " + BASE + name);
        return url.toExternalForm();
    }

    private static Preferences prefs() {
        try { return Preferences.userNodeForPackage(Theme.class); } catch (Exception e) { return null; }
    }

    public static boolean isDark() { return dark.get(); }
    public static void setDark(boolean value) { dark.set(value); }
    public static void toggle() { dark.set(!dark.get()); }
    public static ReadOnlyBooleanProperty darkProperty() { return dark; }

    /** Новая сцена со стилями приложения. */
    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        apply(scene);
        return scene;
    }

    public static Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        apply(scene);
        return scene;
    }

    public static void apply(Scene scene) {
        style(scene);
        targets.add(new WeakReference<>(scene));
    }

    /** Для узлов вне сцены приложения: всплывающие карточки, панели диалогов. */
    public static void apply(Parent parent) {
        style(parent);
        targets.add(new WeakReference<>(parent));
    }

    /** Оформляет стандартный диалог JavaFX в стиле приложения. */
    public static <D extends Dialog<?>> D dialog(D dialog, Window owner) {
        if (owner != null && dialog.getOwner() == null) dialog.initOwner(owner);
        dialog.setGraphic(null);
        DialogPane pane = dialog.getDialogPane();
        pane.setGraphic(null);
        apply(pane);
        if (pane.getScene() != null) apply(pane.getScene());
        return dialog;
    }

    private static String current() { return dark.get() ? DARK : LIGHT; }

    private static void style(Scene scene) {
        scene.getStylesheets().setAll(APP, current());
        scene.setFill(Color.web(dark.get() ? "#08090B" : "#FFFFFF"));
    }

    private static void style(Parent parent) {
        parent.getStylesheets().setAll(APP, current());
    }

    private static void refresh() {
        Iterator<WeakReference<Object>> it = targets.iterator();
        while (it.hasNext()) {
            Object target = it.next().get();
            if (target == null) { it.remove(); continue; }
            if (target instanceof Scene s) style(s);
            else if (target instanceof Parent p) style(p);
        }
    }
}
