package com.example.superplanningapp;

import com.example.superplanningapp.habits.HabitTrackerScene;
import com.example.superplanningapp.trello.Board;
import com.example.superplanningapp.trello.TrelloBoardScene;
import com.example.superplanningapp.trello.TrelloHomeScene;
import com.example.superplanningapp.ui.Icons;
import com.example.superplanningapp.ui.Theme;
import com.example.superplanningapp.ui.Ui;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Оболочка приложения: боковое меню слева, текущий раздел справа. */
public class MenuScene {

    private static final double WIDE = 284;
    private static final double NARROW = 88;

    private static Stage currentStage;
    private static User currentUser;
    private static StackPane rootStack;
    private static StackPane contentHost;
    private static VBox sidebar;
    private static boolean collapsed;

    private static final Map<String, HBox> navItems = new LinkedHashMap<>();
    private static final List<Node> expandedOnly = new ArrayList<>();
    private static final List<Node> collapsedOnly = new ArrayList<>();
    private static HBox topBar;
    private static Button logoutBtn;
    private static StackPane acctAvatarHost;
    private static Label acctName;

    public static Scene createMenuScene(Stage stage, User user) {
        currentStage = stage;
        currentUser = user;
        collapsed = false;
        ChatScene.resetSession(); // новый вход — новая переписка с ассистентом
        navItems.clear();
        expandedOnly.clear();
        collapsedOnly.clear();

        contentHost = new StackPane();
        contentHost.getStyleClass().add("main");
        sidebar = buildSidebar();

        BorderPane shell = new BorderPane(contentHost);
        shell.setLeft(sidebar);
        rootStack = new StackPane(shell);

        showHome();
        return Theme.scene(rootStack, 1400, 900);
    }

    public static Stage getStage() { return currentStage; }

    public static User getCurrentUser() { return currentUser; }

    /** Показать вид в правой части окна. */
    public static void setContent(Node view) {
        contentHost.getChildren().setAll(view);
    }

    // ---------------------------------------------------------- navigation

    public static void showHome() {
        select("home");
        String name = currentUser.getFirstName() != null && !currentUser.getFirstName().isBlank()
                ? currentUser.getFirstName() : currentUser.getUsername();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Ui.RU));

        VBox header = Ui.header("Главная", "Привет, " + name + "!",
                "Сегодня " + today + ". С чего начнём?");

        VBox rows = new VBox(10,
                homeRow(Icons.KANBAN, "skill-sky", "Доски",
                        "Канбан-доски, списки и задачи. Приглашайте друзей и работайте вместе.", MenuScene::openBoards),
                homeRow(Icons.HABITS, "skill-green", "Трекер привычек",
                        "Календарь месяца, отметки по дням и статистика прогресса.", MenuScene::openHabits),
                homeRow(Icons.SPARKLES, "skill-purple", "AI-ассистент",
                        "Gemini поможет составить распорядок дня и расставить приоритеты.", MenuScene::openAI));
        VBox sections = new VBox(16, Ui.crumb("Разделы"), rows);

        HBox note = new HBox(Ui.wrap("Подсказка: на досках карточки можно перетаскивать между списками, "
                + "а тему оформления — переключить в левом нижнем углу.", "small"));
        note.getStyleClass().add("note");

        setContent(Ui.page(header, sections, note));
    }

    public static void openBoards() {
        select("boards");
        setContent(TrelloHomeScene.createTrelloHomeView(currentUser));
    }

    /** Открыть конкретную доску (например, только что созданную ассистентом). */
    public static void openBoard(Board board) {
        select("boards");
        setContent(TrelloBoardScene.createBoardView(currentUser, board));
    }

    public static void openHabits() {
        select("habits");
        setContent(HabitTrackerScene.createHabitsView(currentUser));
    }

    public static void openAI() {
        select("ai");
        setContent(ChatScene.createChatView(currentUser));
    }

    private static void select(String key) {
        navItems.forEach((k, item) -> {
            item.getStyleClass().remove("active");
            if (k.equals(key)) item.getStyleClass().add("active");
        });
    }

    private static HBox homeRow(String icon, String variant, String title, String meta, Runnable action) {
        VBox text = new VBox(3, Ui.label(title, "row-title"), Ui.wrap(meta, "row-meta"));
        text.setMinWidth(0);
        HBox row = new HBox(Ui.skill(icon, variant, 42), text, Ui.hgrow(), Ui.label("Открыть", "status-pill", "ready"));
        row.getStyleClass().add("row");
        row.setOnMouseClicked(e -> action.run());
        Ui.lift(row);
        return row;
    }

    // ------------------------------------------------------------- sidebar

    private static VBox buildSidebar() {
        VBox side = new VBox();
        side.getStyleClass().add("sidebar");
        setSidebarWidth(side, WIDE);
        Ui.clip(side);

        // Логотип и кнопка сворачивания
        HBox brand = Ui.brand();
        Button collapseBtn = Ui.iconButton(Icons.MENU, "Свернуть / развернуть меню");
        collapseBtn.setOnAction(e -> toggleSidebar());
        Region brandSpacer = Ui.hgrow();
        topBar = new HBox(8, brand, brandSpacer, collapseBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(22, 14, 0, 20));
        expandedOnly.add(brand);
        expandedOnly.add(brandSpacer);

        HBox sectionLabel = Ui.crumb("Разделы");
        sectionLabel.setPadding(new Insets(0, 10, 4, 10));
        expandedOnly.add(sectionLabel);

        VBox nav = new VBox(4,
                sectionLabel,
                navItem("home", Icons.HOME, "Главная", MenuScene::showHome),
                navItem("boards", Icons.KANBAN, "Доски", MenuScene::openBoards),
                navItem("habits", Icons.HABITS, "Привычки", MenuScene::openHabits),
                navItem("ai", Icons.SPARKLES, "AI-ассистент", MenuScene::openAI));
        nav.setPadding(new Insets(26, 14, 0, 14));

        // Низ: тема, аккаунт, выход
        HBox themeCtl = Ui.themeSwitch();
        expandedOnly.add(themeCtl);
        Button themeBtn = Ui.iconButton(Icons.MOON, "Сменить тему");
        themeBtn.setOnAction(e -> Theme.toggle());
        themeBtn.setVisible(false);
        themeBtn.setManaged(false);
        collapsedOnly.add(themeBtn);
        HBox themeRow = new HBox(themeCtl, themeBtn);
        themeRow.setAlignment(Pos.CENTER_LEFT);

        HBox acct = buildAccountCard();

        logoutBtn = Ui.ghost("Выйти", Icons.LOGOUT);
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setTooltip(Ui.tip("Выйти из аккаунта"));
        logoutBtn.setOnAction(e -> currentStage.setScene(LoginScene.createLoginScene(currentStage)));

        VBox foot = new VBox(10, themeRow, acct, logoutBtn);
        foot.setPadding(new Insets(18, 14, 8, 14));

        side.getChildren().addAll(topBar, nav, Ui.vgrow(), foot, Ui.sea(64));
        return side;
    }

    private static HBox navItem(String key, String icon, String text, Runnable action) {
        Label label = Ui.label(text, "nav-text");
        expandedOnly.add(label);
        HBox item = new HBox(Ui.navTile(icon, 36), label);
        item.getStyleClass().add("nav-item");
        Tooltip.install(item, Ui.tip(text));
        item.setOnMouseClicked(e -> action.run());
        navItems.put(key, item);
        return item;
    }

    private static HBox buildAccountCard() {
        acctAvatarHost = new StackPane(Ui.avatar(currentUser, 40));
        acctName = Ui.label(currentUser.getDisplayName(), "acct-name");
        VBox who = new VBox(1, acctName, Ui.label("Профиль и настройки", "muted", "small"));
        who.setMinWidth(0);
        expandedOnly.add(who);
        HBox acct = new HBox(acctAvatarHost, who);
        acct.getStyleClass().add("acct");
        acct.setOnMouseClicked(e -> showProfileOverlay());
        Tooltip.install(acct, Ui.tip("Профиль"));
        return acct;
    }

    private static void refreshAccountCard() {
        acctAvatarHost.getChildren().setAll(Ui.avatar(currentUser, 40));
        acctName.setText(currentUser.getDisplayName());
    }

    private static void toggleSidebar() {
        collapsed = !collapsed;
        DoubleProperty width = new SimpleDoubleProperty(sidebar.getWidth() > 0 ? sidebar.getWidth() : (collapsed ? WIDE : NARROW));
        width.addListener((o, a, w) -> setSidebarWidth(sidebar, w.doubleValue()));

        if (collapsed) applyCollapsed(); // прячем подписи сразу
        Timeline anim = new Timeline(new KeyFrame(Duration.millis(Ui.animations ? 240 : 1),
                new KeyValue(width, collapsed ? NARROW : WIDE, Interpolator.EASE_BOTH)));
        anim.setOnFinished(e -> { if (!collapsed) applyCollapsed(); }); // показываем после раскрытия
        anim.play();
    }

    private static void applyCollapsed() {
        for (Node n : expandedOnly) { n.setVisible(!collapsed); n.setManaged(!collapsed); }
        for (Node n : collapsedOnly) { n.setVisible(collapsed); n.setManaged(collapsed); }
        topBar.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        topBar.setPadding(collapsed ? new Insets(22, 0, 0, 0) : new Insets(22, 14, 0, 20));
        navItems.values().forEach(i -> i.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT));
        logoutBtn.setContentDisplay(collapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
    }

    private static void setSidebarWidth(VBox side, double w) {
        side.setMinWidth(w);
        side.setPrefWidth(w);
        side.setMaxWidth(w);
    }

    // ------------------------------------------------------------- profile

    private static void showProfileOverlay() {
        StackPane scrim = new StackPane();
        scrim.getStyleClass().add("scrim");

        VBox panel = new VBox();
        panel.getStyleClass().add("profile-panel");
        panel.setPrefWidth(400);
        panel.setMaxWidth(400);
        StackPane.setAlignment(panel, Pos.CENTER_LEFT);

        Button closeBtn = Ui.iconButton(Icons.ARROW_LEFT, "Закрыть");
        HBox head = new HBox(10, closeBtn, Ui.label("Профиль", "h3"));
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(20, 22, 6, 18));

        VBox body = new VBox(24);
        body.setPadding(new Insets(10, 26, 28, 26));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        panel.getChildren().addAll(head, scroll);
        scrim.getChildren().add(panel);
        fillProfile(body);

        Runnable close = () -> {
            TranslateTransition out = new TranslateTransition(Duration.millis(200), panel);
            out.setToX(-400);
            FadeTransition fade = new FadeTransition(Duration.millis(200), scrim);
            fade.setToValue(0);
            ParallelTransition both = new ParallelTransition(out, fade);
            both.setOnFinished(ev -> rootStack.getChildren().remove(scrim));
            both.play();
        };
        closeBtn.setOnAction(e -> close.run());
        scrim.setOnMouseClicked(e -> { if (e.getTarget() == scrim) close.run(); });
        scrim.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) close.run(); });

        rootStack.getChildren().add(scrim);
        scrim.requestFocus();

        panel.setTranslateX(-400);
        TranslateTransition in = new TranslateTransition(Duration.millis(Ui.animations ? 280 : 1), panel);
        in.setToX(0);
        in.setInterpolator(Interpolator.SPLINE(0.22, 1, 0.36, 1));
        scrim.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(Ui.animations ? 240 : 1), scrim);
        fadeIn.setToValue(1);
        new ParallelTransition(in, fadeIn).play();
    }

    private static void fillProfile(VBox body) {
        body.getChildren().clear();

        Button camBtn = new Button();
        camBtn.getStyleClass().setAll("button", "cam-btn");
        camBtn.setGraphic(Icons.of(Icons.CAMERA, 16));
        camBtn.setTooltip(Ui.tip("Сменить фото"));
        camBtn.setOnAction(e -> {
            if (handlePhotoEdit()) { fillProfile(body); refreshAccountCard(); }
        });
        StackPane avatar = new StackPane(Ui.avatar(currentUser, 116), camBtn);
        StackPane.setAlignment(camBtn, Pos.BOTTOM_RIGHT);
        avatar.setMaxSize(122, 122);

        VBox who = new VBox(4, Ui.label(currentUser.getDisplayName(), "h2"),
                Ui.label("@" + currentUser.getUsername(), "mono"));
        who.setAlignment(Pos.CENTER);
        VBox top = new VBox(16, avatar, who);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(8, 0, 0, 0));

        String bday = currentUser.getBirthday() != null && !currentUser.getBirthday().isBlank()
                ? currentUser.getBirthday() : "Не указан";
        String phone = currentUser.getPhone() != null && !currentUser.getPhone().isBlank()
                ? currentUser.getPhone() : "Не указан";

        VBox rows = new VBox(8,
                Ui.crumb("Данные"),
                profileRow(Icons.USER, currentUser.getDisplayName(), "Имя и фамилия", () -> {
                    if (handleNameEdit()) { fillProfile(body); refreshAccountCard(); }
                }),
                profileRow(Icons.CALENDAR, bday, "День рождения", () -> {
                    if (handleBirthdayEdit()) fillProfile(body);
                }),
                profileRow(Icons.PHONE, phone, "Телефон", null),
                profileRow(Icons.MAIL, currentUser.getEmail(), "E-mail", null));

        body.getChildren().addAll(top, rows);
    }

    private static HBox profileRow(String icon, String value, String subtitle, Runnable onEdit) {
        VBox text = new VBox(2, Ui.label(value, "semi"), Ui.label(subtitle.toUpperCase(Ui.RU), "mono"));
        text.setMinWidth(0);
        HBox row = new HBox(14, Ui.navTile(icon, 36), text, Ui.hgrow());
        row.getStyleClass().addAll("row", "static");
        row.setPadding(new Insets(10, 10, 10, 10));
        if (onEdit != null) {
            Button edit = Ui.iconButton(Icons.PENCIL, "Изменить");
            edit.setOnAction(e -> onEdit.run());
            row.getChildren().add(edit);
        }
        return row;
    }

    private static boolean handleNameEdit() {
        TextField first = new TextField(currentUser.getFirstName());
        first.setPromptText("Имя");
        TextField last = new TextField(currentUser.getLastName());
        last.setPromptText("Фамилия");
        VBox form = new VBox(12, Ui.field("Имя", first, null), Ui.field("Фамилия", last, null));
        boolean ok = Ui.form("Как вас зовут?", "Сохранить", form,
                Bindings.createBooleanBinding(() -> first.getText().trim().isEmpty(), first.textProperty()),
                first, currentStage);
        if (!ok) return false;
        currentUser.setFirstName(first.getText().trim());
        currentUser.setLastName(last.getText().trim());
        UserDAO.updateUserProfile(currentUser);
        return true;
    }

    private static boolean handleBirthdayEdit() {
        return Ui.prompt("День рождения", "Укажите дату в формате ДД.ММ.ГГГГ.", "Дата",
                        currentUser.getBirthday(), "01.01.2000", "Сохранить", currentStage)
                .map(date -> {
                    currentUser.setBirthday(date);
                    UserDAO.updateUserProfile(currentUser);
                    return true;
                }).orElse(false);
    }

    private static boolean handlePhotoEdit() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите фото");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(currentStage);
        if (selectedFile == null) return false;
        currentUser.setAvatarPath(selectedFile.toURI().toString());
        UserDAO.updateUserProfile(currentUser);
        return true;
    }
}
