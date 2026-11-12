package com.example.superplanningapp.ui;

import com.example.superplanningapp.User;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.event.EventTarget;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Locale;
import java.util.Optional;

/** Готовые элементы интерфейса в стиле Nalu.Prep. */
public final class Ui {

    /** Можно выключить анимации (например, для снимков экрана). */
    public static boolean animations = true;

    public static final Locale RU = Locale.forLanguageTag("ru");
    private static final Interpolator EASE = Interpolator.SPLINE(0.22, 1, 0.36, 1);

    private Ui() { }

    // ------------------------------------------------------------ basics

    public static Label label(String text, String... classes) {
        Label label = new Label(text);
        addClasses(label, classes);
        return label;
    }

    /** Метка с переносом строк, которая не сжимается в «…». */
    public static Label wrap(String text, String... classes) {
        Label label = label(text, classes);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    public static void addClasses(Node node, String... classes) {
        for (String c : classes) {
            if (c != null && !c.isBlank()) node.getStyleClass().add(c);
        }
    }

    public static Region hgrow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region vgrow() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static void fix(Region region, double size) {
        region.setMinSize(size, size);
        region.setPrefSize(size, size);
        region.setMaxSize(size, size);
    }

    public static Region dot(double size, String... classes) {
        Region dot = new Region();
        fix(dot, size);
        addClasses(dot, classes);
        return dot;
    }

    // ----------------------------------------------------------- buttons

    /** Главная кнопка: тёмная «пилюля». */
    public static Button primary(String text, String icon) {
        return button(text, icon, "btn");
    }

    /** Второстепенная кнопка: светлая «пилюля» с обводкой. */
    public static Button ghost(String text, String icon) {
        return button(text, icon, "btn-ghost");
    }

    private static Button button(String text, String icon, String cls) {
        Button b = new Button(text);
        b.getStyleClass().add(cls);
        b.setMnemonicParsing(false);
        if (icon != null) b.setGraphic(Icons.of(icon, 16));
        return b;
    }

    public static Button iconButton(String icon, String tooltip, String... extra) {
        Button b = new Button();
        b.getStyleClass().setAll("button", "icon-btn");
        addClasses(b, extra);
        b.setGraphic(Icons.of(icon, 18));
        if (tooltip != null) b.setTooltip(tip(tooltip));
        return b;
    }

    public static Button link(String text) {
        Button b = new Button(text);
        b.getStyleClass().setAll("button", "link-btn");
        b.setMnemonicParsing(false);
        return b;
    }

    public static Tooltip tip(String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(350));
        return t;
    }

    // ------------------------------------------------------------- tiles

    /** Квадратная плитка с иконкой на цветном градиенте (skill-sky, skill-purple…). */
    public static StackPane skill(String icon, String variant, double size) {
        StackPane tile = new StackPane(Icons.of(icon, Math.round(size * 0.52)));
        tile.getStyleClass().addAll("skill", variant);
        fix(tile, size);
        tile.setStyle("-fx-background-radius: " + Math.round(size * 0.3) + ";");
        return tile;
    }

    /** Плитка пункта меню: стеклянная «морская» заливка. */
    public static StackPane navTile(String icon, double size) {
        StackPane tile = new StackPane(Icons.of(icon, Math.round(size * 0.56)));
        tile.getStyleClass().add("nav-tile");
        fix(tile, size);
        return tile;
    }

    public static StackPane avatar(User user, double size) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar");
        fix(avatar, size);

        String path = user == null ? null : user.getAvatarPath();
        if (path != null && !path.isBlank()) {
            try {
                Image img = new Image(path, size * 2, size * 2, true, true);
                if (!img.isError() && img.getWidth() > 0) {
                    ImageView view = new ImageView(img);
                    double side = Math.min(img.getWidth(), img.getHeight());
                    view.setViewport(new Rectangle2D((img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side));
                    view.setFitWidth(size);
                    view.setFitHeight(size);
                    view.setSmooth(true);
                    view.setClip(new Circle(size / 2, size / 2, size / 2));
                    avatar.getChildren().add(view);
                    return avatar;
                }
            } catch (Exception ignored) {
                // битый путь к фото — показываем инициал
            }
        }
        Label letter = label(initial(user), "avatar-letter");
        letter.setStyle("-fx-font-size: " + Math.round(size * 0.4) + "px;");
        avatar.getChildren().add(letter);
        return avatar;
    }

    public static String initial(User user) {
        if (user == null) return "U";
        String src = user.getFirstName() != null && !user.getFirstName().isBlank() ? user.getFirstName() : user.getUsername();
        return src == null || src.isBlank() ? "U" : src.substring(0, 1).toUpperCase(RU);
    }

    /** Логотип: плитка с волнами и название. */
    public static HBox brand() {
        HBox brand = new HBox(12, skill(Icons.WAVE, "skill-sky", 38), label("SuperPlanning", "brand-name"));
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    // ------------------------------------------------- headings and pages

    /** Подпись раздела: моноширинные капители и «волна» после них. */
    public static HBox crumb(String text) {
        HBox crumb = new HBox(12, label(text.toUpperCase(RU), "crumb-text"), surf());
        crumb.setAlignment(Pos.CENTER_LEFT);
        return crumb;
    }

    /** Бегущая волна (surf line) с лендинга Nalu.Prep. */
    public static Pane surf() {
        Pane pane = new Pane();
        pane.setMinWidth(0);
        pane.setPrefWidth(60);
        pane.setMaxWidth(380);
        pane.setMinHeight(8);
        pane.setPrefHeight(8);
        pane.setMaxHeight(8);
        HBox.setHgrow(pane, Priority.ALWAYS);

        StringBuilder d = new StringBuilder("M0 4");
        for (int x = 0; x < 1600; x += 64) {
            d.append(String.format(Locale.ROOT, " Q%d 0 %d 4 T%d 4", x + 16, x + 32, x + 64));
        }
        SVGPath path = new SVGPath();
        path.setContent(d.toString());
        path.getStyleClass().add("surf-path");
        pane.getChildren().add(path);
        clip(pane);

        TranslateTransition move = new TranslateTransition(Duration.seconds(9), path);
        move.setFromX(0);
        move.setToX(-64);
        move.setCycleCount(Animation.INDEFINITE);
        move.setInterpolator(Interpolator.LINEAR);
        runWhileShowing(pane, move);
        return pane;
    }

    /** Заголовок страницы: подпись, крупный заголовок, вводный текст. */
    public static VBox header(String crumb, String title, String intro) {
        VBox box = new VBox();
        box.setMinWidth(0);
        HBox c = crumb(crumb);
        VBox.setMargin(c, new Insets(0, 0, 14, 0));
        box.getChildren().addAll(c, wrap(title, "h1"));
        if (intro != null && !intro.isBlank()) {
            Label lead = wrap(intro, "lead");
            lead.setMaxWidth(680);
            VBox.setMargin(lead, new Insets(10, 0, 0, 0));
            box.getChildren().add(lead);
        }
        return box;
    }

    /** Заголовок слева, кнопки действий справа, выровнены по нижнему краю. */
    public static HBox headerRow(VBox header, Node... actions) {
        HBox right = new HBox(10, actions);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(24, header, right);
        row.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(header, Priority.ALWAYS);
        return row;
    }

    /** Прокручиваемая страница с отступами; блоки «всплывают» по очереди. */
    public static ScrollPane page(Node... children) {
        VBox content = new VBox(30, children);
        content.setPadding(new Insets(40, 52, 64, 52));
        content.setMaxWidth(1240);
        for (int i = 0; i < children.length; i++) rise(children[i], i);

        StackPane holder = new StackPane(content);
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        ScrollPane scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    // ------------------------------------------------------------- forms

    public static VBox field(String label, Control input, String hint) {
        VBox box = new VBox(7, label(label, "fld-label"), input);
        if (hint != null) box.getChildren().add(wrap(hint, "hint"));
        input.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Показывает сообщение под формой (ошибка или успех) либо прячет его. */
    public static void message(Label label, String text, boolean ok) {
        boolean show = text != null && !text.isBlank();
        label.setText(show ? text : "");
        label.getStyleClass().removeAll("err", "ok");
        label.getStyleClass().add(ok ? "ok" : "err");
        label.setVisible(show);
        label.setManaged(show);
    }

    // -------------------------------------------------- segmented control

    public static ToggleButton seg(String text, String icon) {
        ToggleButton b = new ToggleButton(text);
        b.setMnemonicParsing(false);
        if (icon != null) b.setGraphic(Icons.of(icon, 15));
        return b;
    }

    /** Переключатель-«пилюля»: один вариант выбран всегда. */
    public static HBox segmented(ToggleButton... buttons) {
        ToggleGroup group = new ToggleGroup();
        for (ToggleButton b : buttons) b.setToggleGroup(group);
        group.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null && was != null) was.setSelected(true);
        });
        HBox box = new HBox(buttons);
        box.getStyleClass().add("ctl");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /** Переключатель темы: солнце / луна. */
    public static HBox themeSwitch() {
        ToggleButton light = seg(null, Icons.SUN);
        ToggleButton dark = seg(null, Icons.MOON);
        light.setTooltip(tip("Светлая тема"));
        dark.setTooltip(tip("Тёмная тема"));
        HBox box = segmented(light, dark);
        (Theme.isDark() ? dark : light).setSelected(true);

        light.selectedProperty().addListener((o, a, on) -> { if (on) Theme.setDark(false); });
        dark.selectedProperty().addListener((o, a, on) -> { if (on) Theme.setDark(true); });

        // Если тему сменили в другом месте — показать это и здесь.
        ChangeListener<Boolean> sync = (o, a, isDark) -> (isDark ? dark : light).setSelected(true);
        box.getProperties().put("themeSync", sync);
        Theme.darkProperty().addListener(new WeakChangeListener<>(sync));
        return box;
    }

    // ------------------------------------------------------------- sea

    /** Три слоя волн, медленно качающихся — «море» внизу экрана. */
    public static Pane sea(double height) {
        Pane pane = new Pane();
        pane.setMinWidth(0);
        pane.setPrefWidth(10);
        pane.setMinHeight(height);
        pane.setPrefHeight(height);
        pane.setMaxHeight(height);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setMouseTransparent(true);
        clip(pane);

        double[][] layers = {
                // амплитуда, длина волны, линия (доля высоты), секунд на цикл, направление
                {7, 230, 0.34, 26, 1},
                {9, 310, 0.55, 19, -1},
                {6, 190, 0.74, 14, 1},
        };
        Animation[] moves = new Animation[layers.length];
        for (int i = 0; i < layers.length; i++) {
            double[] l = layers[i];
            SVGPath wave = new SVGPath();
            wave.setContent(wavePath(2800, l[1], l[0], height * l[2], height));
            wave.getStyleClass().add("wave-" + (i + 1));
            pane.getChildren().add(wave);

            TranslateTransition move = new TranslateTransition(Duration.seconds(l[3]), wave);
            move.setFromX(l[4] > 0 ? 0 : -l[1]);
            move.setToX(l[4] > 0 ? -l[1] : 0);
            move.setCycleCount(Animation.INDEFINITE);
            move.setInterpolator(Interpolator.LINEAR);
            moves[i] = move;
        }
        runWhileShowing(pane, moves);
        return pane;
    }

    private static String wavePath(double width, double length, double amp, double y0, double h) {
        StringBuilder d = new StringBuilder(String.format(Locale.ROOT, "M0 %.1f", y0));
        double x = 0;
        for (; x < width; x += length) {
            d.append(String.format(Locale.ROOT, " Q%.1f %.1f %.1f %.1f T%.1f %.1f",
                    x + length / 4, y0 - amp, x + length / 2, y0, x + length, y0));
        }
        d.append(String.format(Locale.ROOT, " L%.1f %.1f L0 %.1f Z", x, h, h));
        return d.toString();
    }

    // ------------------------------------------------------------ auth

    /** Экран входа: небо, море внизу, логотип и тема сверху, карточка по центру. */
    public static StackPane authScreen(VBox card) {
        StackPane screen = new StackPane();
        screen.getStyleClass().add("auth-screen");

        Pane water = sea(170);
        StackPane.setAlignment(water, Pos.BOTTOM_CENTER);

        HBox top = new HBox(12, brand(), hgrow(), themeSwitch());
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(20, 28, 20, 28));

        StackPane centre = new StackPane(card);
        centre.setPadding(new Insets(16, 24, 90, 24));
        ScrollPane scroll = new ScrollPane(centre);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        BorderPane layout = new BorderPane(scroll);
        layout.setTop(top);
        screen.getChildren().addAll(water, layout);
        rise(card, 1);
        return screen;
    }

    public static VBox authCard(Node... children) {
        VBox card = new VBox(14, children);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(452);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        return card;
    }

    // --------------------------------------------------------- dialogs

    /** Подтверждение «Удалить?» / «Покинуть?». */
    public static boolean confirm(String header, String content, String okText, boolean danger, Window owner) {
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.NONE, content, cancel, ok);
        alert.setTitle(header);
        alert.setHeaderText(header);
        Theme.dialog(alert, owner);
        if (danger) alert.getDialogPane().lookupButton(ok).getStyleClass().add("danger");
        return alert.showAndWait().orElse(cancel) == ok;
    }

    public static void error(String header, String content, Window owner) {
        ButtonType ok = new ButtonType("Понятно", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.NONE, content, ok);
        alert.setTitle("Ошибка");
        alert.setHeaderText(header);
        Theme.dialog(alert, owner);
        alert.showAndWait();
    }

    /** Окно с одним полем ввода. Пустое значение не принимается. */
    public static Optional<String> prompt(String header, String lead, String fieldLabel, String initial,
                                          String placeholder, String okText, Window owner) {
        TextField input = new TextField(initial == null ? "" : initial);
        input.setPromptText(placeholder);
        VBox box = new VBox(14);
        if (lead != null) box.getChildren().add(wrap(lead, "muted"));
        box.getChildren().add(field(fieldLabel, input, null));
        boolean ok = form(header, okText, box,
                Bindings.createBooleanBinding(() -> input.getText().trim().isEmpty(), input.textProperty()),
                input, owner);
        return ok ? Optional.of(input.getText().trim()) : Optional.empty();
    }

    /** Окно с произвольной формой; кнопка подтверждения заблокирована, пока invalid = true. */
    public static boolean form(String header, String okText, Node content, ObservableValue<Boolean> invalid,
                               Node focus, Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(header);
        dialog.setHeaderText(header);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, ok);
        if (content instanceof Region r) r.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        if (invalid != null) dialog.getDialogPane().lookupButton(ok).disableProperty().bind(Bindings.createBooleanBinding(
                () -> Boolean.TRUE.equals(invalid.getValue()), invalid));
        Theme.dialog(dialog, owner);
        if (focus != null) Platform.runLater(focus::requestFocus);
        return dialog.showAndWait().orElse(cancel) == ok;
    }

    // ------------------------------------------------------------ motion

    /** Плавный подъём при наведении (вместе с тенью из CSS). */
    public static void lift(Node node) {
        TranslateTransition t = new TranslateTransition(Duration.millis(240), node);
        t.setInterpolator(EASE);
        node.hoverProperty().addListener((o, was, hover) -> {
            if (!animations) return;
            t.stop();
            t.setToY(hover ? -2 : 0);
            t.play();
        });
    }

    /** Блок «всплывает из воды», как на лендинге: с небольшой задержкой по порядку. */
    public static void rise(Node node, int index) {
        if (!animations) return;
        node.setOpacity(0);
        node.setTranslateY(18);
        FadeTransition fade = new FadeTransition(Duration.millis(700), node);
        fade.setToValue(1);
        fade.setInterpolator(EASE);
        TranslateTransition up = new TranslateTransition(Duration.millis(700), node);
        up.setToY(0);
        up.setInterpolator(EASE);
        ParallelTransition all = new ParallelTransition(fade, up);
        all.setDelay(Duration.millis(70L * Math.min(index, 6)));
        all.play();
    }

    /** Бесконечные анимации идут только пока узел виден в окне — без утечек. */
    public static void runWhileShowing(Node node, Animation... list) {
        Runnable update = () -> {
            Scene scene = node.getScene();
            boolean on = animations && scene != null && scene.getWindow() != null;
            for (Animation a : list) {
                if (on) {
                    if (a.getStatus() != Animation.Status.RUNNING) a.play();
                } else {
                    a.stop();
                }
            }
        };
        ChangeListener<Window> windowListener = (o, a, b) -> update.run();
        node.sceneProperty().addListener((o, old, scene) -> {
            if (old != null) old.windowProperty().removeListener(windowListener);
            if (scene != null) scene.windowProperty().addListener(windowListener);
            update.run();
        });
        if (node.getScene() != null) node.getScene().windowProperty().addListener(windowListener);
        update.run();
    }

    // ------------------------------------------------------------ misc

    public static void clip(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    /** Клик пришёлся на кнопку (или помеченный узел) внутри карточки? */
    public static boolean isInteractive(EventTarget target, Node stopAt) {
        Node n = target instanceof Node node ? node : null;
        while (n != null && n != stopAt) {
            if (n instanceof ButtonBase || n.getStyleClass().contains("interactive")) return true;
            n = n.getParent();
        }
        return false;
    }

    public static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase(RU) + s.substring(1);
    }
}
