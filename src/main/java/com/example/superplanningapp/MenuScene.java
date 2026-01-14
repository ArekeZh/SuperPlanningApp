package com.example.superplanningapp;

import com.example.superplanningapp.habits.HabitTrackerScene;
import com.example.superplanningapp.trello.TrelloHomeScene;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MenuScene {

    private static StackPane rootStack;
    private static BorderPane mainContentLayout;
    private static VBox sidebar;
    private static boolean isExpanded = false;

    public static boolean isDarkTheme = true;

    // === ВАЖНОЕ ИЗМЕНЕНИЕ: Храним функцию для перезагрузки текущего вида ===
    private static Runnable currentViewReloader;

    private static final double WIDTH_COLLAPSED = 70;
    private static final double WIDTH_EXPANDED = 260;

    private static Button themeSwitchBtn;
    private static VBox welcomeBox;
    private static Label welcomeHello;
    private static Label welcomeSub;

    public static class Theme {
        public static final String BG_DARK = "#121212";
        public static final String SIDEBAR_DARK = "#1a1a1a";
        public static final String TEXT_MAIN_DARK = "#e4e6eb";
        public static final String TEXT_SEC_DARK = "#b0b3b8";
        public static final String HOVER_DARK = "#232323";
        public static final String BORDER_DARK = "#333333";

        public static final String BG_LIGHT = "#f0f2f5";
        public static final String SIDEBAR_LIGHT = "#ffffff";
        public static final String TEXT_MAIN_LIGHT = "#050505";
        public static final String TEXT_SEC_LIGHT = "#65676b";
        public static final String HOVER_LIGHT = "#e4e6eb";
        public static final String BORDER_LIGHT = "#ced0d4";

        public static final String ACCENT = "#00a884";
        public static final String ERROR = "#f15c6d";
    }

    private static List<MenuButtonData> menuButtons = new ArrayList<>();
    private static Stage currentStage;
    private static User currentUser;

    public static Scene createMenuScene(Stage stage, User user) {
        currentStage = stage;
        currentUser = user;
        menuButtons.clear();

        rootStack = new StackPane();
        mainContentLayout = new BorderPane();

        createSidebar();
        mainContentLayout.setLeft(sidebar);

        showWelcomePage();

        rootStack.getChildren().add(mainContentLayout);
        refreshTheme();

        Scene scene = new Scene(rootStack, 1400, 900);
        try { scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet()); } catch (Exception e) {}

        return scene;
    }

    public static void setContent(Node view) {
        if (view instanceof Region) {
            ((Region) view).setStyle("-fx-background-color: " + getBackgroundStyle() + ";");
        }
        mainContentLayout.setCenter(view);
    }

    // === НОВЫЙ МЕТОД: Позволяет любой странице сказать "При смене темы перезагрузи меня вот так" ===
    public static void setContentReloader(Runnable reloader) {
        currentViewReloader = reloader;
    }

    public static String getBackgroundStyle() {
        return isDarkTheme ? Theme.BG_DARK : Theme.BG_LIGHT;
    }

    public static String getTextStyle() {
        return isDarkTheme ? Theme.TEXT_MAIN_DARK : Theme.TEXT_MAIN_LIGHT;
    }

    private static void toggleTheme() {
        isDarkTheme = !isDarkTheme;

        // 1. Обновляем базовые стили
        refreshTheme();

        // 2. ПЕРЕЗАГРУЖАЕМ ТЕКУЩУЮ СТРАНИЦУ
        // Если задан специфичный релоадер (например, открыта конкретная доска), используем его
        if (currentViewReloader != null) {
            currentViewReloader.run();
        } else {
            showWelcomePage();
        }
    }

    private static void refreshTheme() {
        String bg = isDarkTheme ? Theme.BG_DARK : Theme.BG_LIGHT;
        String sbBg = isDarkTheme ? Theme.SIDEBAR_DARK : Theme.SIDEBAR_LIGHT;
        String border = isDarkTheme ? Theme.BORDER_DARK : Theme.BORDER_LIGHT;
        String textMain = isDarkTheme ? Theme.TEXT_MAIN_DARK : Theme.TEXT_MAIN_LIGHT;
        String textSec = isDarkTheme ? Theme.TEXT_SEC_DARK : Theme.TEXT_SEC_LIGHT;

        rootStack.setStyle("-fx-background-color: " + bg + ";");
        mainContentLayout.setStyle("-fx-background-color: " + bg + ";");
        sidebar.setStyle("-fx-background-color: " + sbBg + "; -fx-border-color: " + border + "; -fx-border-width: 0 1 0 0;");

        for (MenuButtonData data : menuButtons) {
            if (data.button != null) {
                boolean isActive = data.button.getStyle().contains("-fx-border-width");
                if (isActive) {
                    data.button.setStyle(getBtnActiveStyle());
                } else {
                    if (data.text.equals("Выход")) {
                        data.button.setStyle("-fx-text-fill: " + Theme.ERROR + "; -fx-background-color: transparent; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 12 15;");
                    } else {
                        data.button.setStyle(getBtnDefaultStyle());
                    }
                }
            }
            if (data.nameLabel != null) {
                data.nameLabel.setStyle("-fx-text-fill: " + textMain + "; -fx-font-size: 15px; -fx-font-weight: bold;");
            }
        }

        if (themeSwitchBtn != null) {
            themeSwitchBtn.setText(isDarkTheme ? "☀" : "☾");
            themeSwitchBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textSec + "; -fx-font-size: 20px; -fx-cursor: hand;");
            themeSwitchBtn.setTooltip(new Tooltip(isDarkTheme ? "Светлая тема" : "Темная тема"));
        }
    }

    private static String getBtnDefaultStyle() {
        String textColor = isDarkTheme ? Theme.TEXT_SEC_DARK : Theme.TEXT_SEC_LIGHT;
        return "-fx-background-color: transparent; -fx-text-fill: " + textColor + "; -fx-font-size: 14px; -fx-alignment: " + (isExpanded ? "CENTER_LEFT" : "CENTER") + "; -fx-cursor: hand; -fx-padding: 12 15;";
    }

    private static String getBtnActiveStyle() {
        String hoverBg = isDarkTheme ? Theme.HOVER_DARK : Theme.HOVER_LIGHT;
        return "-fx-background-color: " + hoverBg + "; -fx-text-fill: " + Theme.ACCENT + "; -fx-font-size: 14px; -fx-alignment: " + (isExpanded ? "CENTER_LEFT" : "CENTER") + "; -fx-cursor: hand; -fx-padding: 12 15; -fx-border-color: " + Theme.ACCENT + "; -fx-border-width: 0 0 0 4;";
    }

    public static Stage getStage() {
        return currentStage;
    }

    private static void showWelcomePage() {
        // Сброс релоадера на дефолтный
        currentViewReloader = MenuScene::showWelcomePage;

        welcomeBox = new VBox(20);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeHello = new Label("Добро пожаловать, " + currentUser.getDisplayName() + "!");
        welcomeSub = new Label("Выберите раздел в меню слева, чтобы начать работу.");
        updateWelcomeStyles();

        for(MenuButtonData d : menuButtons) {
            if(d.button!=null && !d.text.equals("Выход")) d.button.setStyle(getBtnDefaultStyle());
        }
        welcomeBox.getChildren().addAll(welcomeHello, welcomeSub);
        mainContentLayout.setCenter(welcomeBox);
    }

    private static void updateWelcomeStyles() {
        if (welcomeBox == null) return;
        welcomeBox.setStyle("-fx-background-color: " + (isDarkTheme ? Theme.BG_DARK : Theme.BG_LIGHT) + ";");
        if (welcomeHello != null)
            welcomeHello.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + (isDarkTheme ? Theme.TEXT_MAIN_DARK : Theme.TEXT_MAIN_LIGHT) + ";");
        if (welcomeSub != null)
            welcomeSub.setStyle("-fx-font-size: 16px; -fx-text-fill: " + (isDarkTheme ? Theme.TEXT_SEC_DARK : Theme.TEXT_SEC_LIGHT) + ";");
    }

    private static void toggleSidebar() {
        isExpanded = !isExpanded;
        updateSidebarVisuals();
    }

    private static void updateSidebarVisuals() {
        if (sidebar == null) return;
        refreshTheme();
        if (isExpanded) {
            sidebar.setPrefWidth(WIDTH_EXPANDED);
            for (MenuButtonData data : menuButtons) {
                if (data.button != null) data.button.setText("   " + data.text);
                if (data.isProfile && data.nameLabel != null) data.nameLabel.setVisible(true);
            }
        } else {
            sidebar.setPrefWidth(WIDTH_COLLAPSED);
            for (MenuButtonData data : menuButtons) {
                if (data.button != null) {
                    data.button.setText("");
                    data.button.setGraphic(data.iconLabel);
                }
                if (data.isProfile && data.nameLabel != null) data.nameLabel.setVisible(false);
            }
        }
    }

    private static void createSidebar() {
        sidebar = new VBox(10);
        sidebar.setPadding(new Insets(15, 10, 15, 10));
        sidebar.setPrefWidth(WIDTH_COLLAPSED);

        Button burgerBtn = new Button("☰");
        burgerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b0b3b8; -fx-font-size: 20px; -fx-cursor: hand;");
        burgerBtn.setMaxWidth(Double.MAX_VALUE);
        burgerBtn.setOnAction(e -> toggleSidebar());

        VBox profileBox = new VBox(8);
        profileBox.setAlignment(Pos.CENTER);
        profileBox.setPadding(new Insets(10, 0, 10, 0));
        profileBox.setStyle("-fx-cursor: hand;");
        profileBox.setOnMouseClicked(e -> showProfileOverlay());

        Label avatar = new Label(getUserInitial());
        avatar.setStyle("-fx-background-color: " + Theme.ACCENT + "; -fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 50; -fx-min-width: 45; -fx-min-height: 45;");

        if (currentUser.getAvatarPath() != null) {
            try {
                Image img = new Image(currentUser.getAvatarPath());
                ImageView imgView = new ImageView(img);
                imgView.setFitWidth(45); imgView.setFitHeight(45);
                Circle clip = new Circle(22.5, 22.5, 22.5);
                imgView.setClip(clip);
                avatar.setGraphic(imgView);
                avatar.setText("");
            } catch (Exception e) {}
        }

        Label nameLabel = new Label(currentUser.getDisplayName());
        nameLabel.setVisible(false);

        profileBox.getChildren().addAll(avatar, nameLabel);
        MenuButtonData profileData = new MenuButtonData(null, "", "", false);
        profileData.isProfile = true;
        profileData.nameLabel = nameLabel;
        menuButtons.add(profileData);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333333; -fx-opacity: 0.3;");

        Button btnTrello = createNavButton("📋", "Trello (Доски)", () -> loadTrello());
        Button btnHabits = createNavButton("✅", "Трекер привычек", () -> loadHabits());
        Button btnAI = createNavButton("🤖", "AI Чат", () -> loadAI());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        themeSwitchBtn = new Button();
        themeSwitchBtn.setMaxWidth(Double.MAX_VALUE);
        themeSwitchBtn.setOnAction(e -> toggleTheme());

        Button btnLogout = createNavButton("🚪", "Выход", () -> currentStage.setScene(LoginScene.createLoginScene(currentStage)));

        sidebar.getChildren().addAll(burgerBtn, profileBox, sep, btnTrello, btnHabits, btnAI, spacer, themeSwitchBtn, btnLogout);
    }

    private static Button createNavButton(String icon, String text, Runnable action) {
        Button btn = new Button();
        Label lbl = new Label(icon);
        lbl.setStyle("-fx-font-size: 18px; -fx-text-fill: inherit;");
        btn.setGraphic(lbl);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setTooltip(new Tooltip(text));

        MenuButtonData data = new MenuButtonData(btn, text, icon, false);
        data.iconLabel = lbl;
        menuButtons.add(data);

        btn.setOnAction(e -> {
            for(MenuButtonData d : menuButtons) {
                if(d.button!=null && !d.text.equals("Выход")) d.button.setStyle(getBtnDefaultStyle());
            }
            if(!text.equals("Выход")) btn.setStyle(getBtnActiveStyle());
            action.run();
        });
        return btn;
    }

    // === ОБНОВЛЕННЫЕ МЕТОДЫ ЗАГРУЗКИ (Устанавливают reloader) ===
    private static void loadTrello() {
        currentViewReloader = MenuScene::loadTrello; // Запоминаем: "Если что, грузи список досок"
        try {
            mainContentLayout.setCenter(TrelloHomeScene.createTrelloHomeView(currentUser));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void loadHabits() {
        currentViewReloader = MenuScene::loadHabits; // Запоминаем: "Грузи привычки"
        try {
            mainContentLayout.setCenter(HabitTrackerScene.createHabitsView(currentUser));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void loadAI() {
        currentViewReloader = MenuScene::loadAI; // Запоминаем: "Грузи AI"
        try {
            mainContentLayout.setCenter(ChatScene.createChatView(currentUser));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ==========================================
    // === ЛОГИКА ПРОФИЛЯ ===
    // ==========================================
    private static void showProfileOverlay() {
        String bg = isDarkTheme ? "#232323" : "#ffffff";
        String headerBg = isDarkTheme ? Theme.SIDEBAR_DARK : "#f7f8fa";
        String textMain = isDarkTheme ? Theme.TEXT_MAIN_DARK : Theme.TEXT_MAIN_LIGHT;
        String textSec = isDarkTheme ? Theme.TEXT_SEC_DARK : Theme.TEXT_SEC_LIGHT;
        String border = isDarkTheme ? Theme.BORDER_DARK : Theme.BORDER_LIGHT;

        StackPane overlayBackground = new StackPane();
        overlayBackground.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        overlayBackground.setOnMouseClicked(e -> {
            if (e.getTarget() == overlayBackground) rootStack.getChildren().remove(overlayBackground);
        });

        VBox profilePanel = new VBox();
        profilePanel.setPrefWidth(350);
        profilePanel.setMaxWidth(350);
        profilePanel.setMaxHeight(Double.MAX_VALUE);
        profilePanel.setStyle("-fx-background-color: " + bg + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 15, 0, 0, 0);");
        StackPane.setAlignment(profilePanel, Pos.TOP_LEFT);

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: " + headerBg + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 1 0;");

        Button closeBtn = new Button("←");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + textMain + "; -fx-font-size: 20px; -fx-cursor: hand; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> rootStack.getChildren().remove(overlayBackground));

        Label title = new Label("Профиль");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + textMain + ";");
        header.getChildren().addAll(closeBtn, title);

        VBox content = new VBox(20);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(30, 20, 20, 20));

        StackPane avatarPane = new StackPane();
        Label bigAvatar = new Label(getUserInitial());
        bigAvatar.setStyle("-fx-background-color: " + Theme.BORDER_DARK + "; -fx-text-fill: #e4e6eb; -fx-font-size: 60px; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 100; -fx-min-width: 150; -fx-min-height: 150;");

        if (currentUser.getAvatarPath() != null) {
            try {
                Image img = new Image(currentUser.getAvatarPath());
                ImageView imgView = new ImageView(img);
                imgView.setFitWidth(150); imgView.setFitHeight(150);
                Circle clip = new Circle(75, 75, 75);
                imgView.setClip(clip);
                bigAvatar.setGraphic(imgView);
                bigAvatar.setText("");
            } catch (Exception e) {}
        }

        Button editPhotoBtn = new Button("📷");
        editPhotoBtn.setStyle("-fx-background-color: " + Theme.ACCENT + "; -fx-text-fill: white; -fx-background-radius: 50; -fx-cursor: hand; -fx-padding: 8;");
        editPhotoBtn.setOnAction(e -> {
            handlePhotoEdit();
            rootStack.getChildren().remove(overlayBackground);
            showProfileOverlay();
        });
        StackPane.setAlignment(editPhotoBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(editPhotoBtn, new Insets(0, 10, 10, 0));
        avatarPane.getChildren().addAll(bigAvatar, editPhotoBtn);
        avatarPane.setMaxSize(150, 150);

        Separator sep1 = new Separator(); sep1.setStyle("-fx-background-color: " + border + "; -fx-opacity: 0.5;");
        Separator sep2 = new Separator(); sep2.setStyle("-fx-background-color: " + border + "; -fx-opacity: 0.5;");
        Separator sep3 = new Separator(); sep3.setStyle("-fx-background-color: " + border + "; -fx-opacity: 0.5;");

        HBox nameBox = createEditableRow("👤", currentUser.getDisplayName(), "Имя", textMain, textSec, () -> {
            handleNameEdit();
            rootStack.getChildren().remove(overlayBackground);
            showProfileOverlay();
        });
        String bday = (currentUser.getBirthday() != null) ? currentUser.getBirthday() : "Не указано";
        HBox bdayBox = createEditableRow("🎂", bday, "День рождения", textMain, textSec, () -> {
            handleBirthdayEdit();
            rootStack.getChildren().remove(overlayBackground);
            showProfileOverlay();
        });
        String phone = (currentUser.getPhone() != null) ? currentUser.getPhone() : "Не указан";
        HBox phoneBox = createStaticRow("📞", phone, "Телефон", textMain, textSec);
        HBox emailBox = createStaticRow("📧", currentUser.getEmail(), "E-mail", textMain, textSec);

        content.getChildren().addAll(avatarPane, sep1, nameBox, sep2, bdayBox, sep3, phoneBox, emailBox);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.getStyleClass().add("edge-to-edge");

        profilePanel.getChildren().addAll(header, scrollPane);
        overlayBackground.getChildren().add(profilePanel);

        TranslateTransition tt = new TranslateTransition(Duration.millis(250), profilePanel);
        tt.setFromX(-350); tt.setToX(0); tt.play();

        FadeTransition ft = new FadeTransition(Duration.millis(250), overlayBackground);
        ft.setFromValue(0); ft.setToValue(1);
        ft.play();

        rootStack.getChildren().add(overlayBackground);
    }

    private static void handleNameEdit() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Изменить имя");
        dialog.setHeaderText("Введите имя и фамилию");
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        String bg = isDarkTheme ? Theme.HOVER_DARK : "#ffffff";
        dialogPane.setStyle("-fx-background-color: " + bg + ";");

        TextField firstNameField = new TextField(currentUser.getFirstName());
        TextField lastNameField = new TextField(currentUser.getLastName());

        VBox box = new VBox(10, new Label("Имя:"), firstNameField, new Label("Фамилия:"), lastNameField);
        box.setPadding(new Insets(20));
        dialogPane.setContent(box);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            currentUser.setFirstName(firstNameField.getText());
            currentUser.setLastName(lastNameField.getText());
            UserDAO.updateUserProfile(currentUser);
        }
    }

    private static void handleBirthdayEdit() {
        TextInputDialog dialog = new TextInputDialog(currentUser.getBirthday());
        dialog.setTitle("День рождения");
        dialog.setHeaderText("Введите дату");
        dialog.setContentText("Формат (ДД.ММ.ГГГГ):");

        if(isDarkTheme) {
            dialog.getDialogPane().setStyle("-fx-background-color: #333333;");
            dialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: white;");
        }

        dialog.showAndWait().ifPresent(date -> {
            currentUser.setBirthday(date);
            UserDAO.updateUserProfile(currentUser);
        });
    }

    private static void handlePhotoEdit() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(currentStage);
        if (selectedFile != null) {
            currentUser.setAvatarPath(selectedFile.toURI().toString());
            UserDAO.updateUserProfile(currentUser);
        }
    }

    private static HBox createEditableRow(String icon, String value, String subtitle, String mainColor, String secColor, Runnable onEdit) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 10, 5));

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 22px; -fx-text-fill: " + secColor + ";");

        VBox textBox = new VBox(2);
        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 15px; -fx-text-fill: " + mainColor + "; -fx-font-weight: bold;");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + secColor + ";");

        textBox.getChildren().addAll(valueLbl, subLbl);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editBtn = new Button("✎");
        editBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + Theme.ACCENT + "; -fx-font-size: 18px; -fx-cursor: hand;");
        editBtn.setOnAction(e -> onEdit.run());

        row.getChildren().addAll(iconLbl, textBox, spacer, editBtn);
        return row;
    }

    private static HBox createStaticRow(String icon, String value, String subtitle, String mainColor, String secColor) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 10, 5));

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 22px; -fx-text-fill: " + secColor + ";");

        VBox textBox = new VBox(2);
        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 15px; -fx-text-fill: " + mainColor + ";");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + secColor + ";");

        textBox.getChildren().addAll(valueLbl, subLbl);
        row.getChildren().addAll(iconLbl, textBox);
        return row;
    }

    private static String getUserInitial() {
        if (currentUser.getFirstName() != null && !currentUser.getFirstName().isEmpty())
            return currentUser.getFirstName().substring(0, 1).toUpperCase();
        return currentUser.getUsername().length() > 0 ? currentUser.getUsername().substring(0, 1).toUpperCase() : "U";
    }

    private static class MenuButtonData {
        Button button; String text; Label iconLabel; boolean isProfile = false; Label nameLabel;
        public MenuButtonData(Button b, String t, String i, boolean a) { button=b; text=t; }
    }
}