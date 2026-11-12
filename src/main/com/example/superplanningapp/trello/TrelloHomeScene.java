package com.example.superplanningapp.trello;

import com.example.superplanningapp.MenuScene;
import com.example.superplanningapp.User;
import com.example.superplanningapp.UserDAO;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TrelloHomeScene {

    private static Label notificationBadge;

    public static BorderPane createTrelloHomeView(User user) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: " + MenuScene.getBackgroundStyle() + ";");

        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);

        String topBarBg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "rgba(255,255,255,0.5)";
        topBar.setStyle("-fx-background-color: " + topBarBg + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1);");

        Label titleLabel = new Label("📋 Мои доски");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + MenuScene.getTextStyle() + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane notificationBtnContainer = new StackPane();
        Button notificationBtn = new Button("🔔 Уведомления");
        String notifBtnStyle = MenuScene.isDarkTheme ? "-fx-background-color: #333; -fx-text-fill: white;" : "-fx-background-color: white; -fx-text-fill: #333;";
        notificationBtn.setStyle(notifBtnStyle + " -fx-cursor: hand; -fx-background-radius: 5;");
        notificationBtn.setOnAction(e -> showNotificationsDialog(user, mainLayout));

        notificationBadge = new Label("0");
        notificationBadge.setStyle("-fx-background-color: " + MenuScene.Theme.ERROR + "; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 5; -fx-background-radius: 10;");
        notificationBadge.setVisible(false);
        StackPane.setAlignment(notificationBadge, Pos.TOP_RIGHT);
        notificationBadge.setTranslateX(5);
        notificationBadge.setTranslateY(-5);
        notificationBtnContainer.getChildren().addAll(notificationBtn, notificationBadge);

        Button createBtn = new Button("+ Создать доску");
        createBtn.setStyle("-fx-background-color: " + MenuScene.Theme.ACCENT + "; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 15; -fx-font-weight: bold;");

        topBar.getChildren().addAll(titleLabel, spacer, notificationBtnContainer, createBtn);

        StackPane centerStack = new StackPane();
        FlowPane boardsGrid = new FlowPane();
        boardsGrid.setHgap(20);
        boardsGrid.setVgap(20);
        boardsGrid.setPadding(new Insets(30));
        boardsGrid.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(boardsGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.getStyleClass().add("edge-to-edge");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(50, 50);
        spinner.setVisible(true);

        centerStack.getChildren().addAll(scrollPane, spinner);
        mainLayout.setTop(topBar);
        mainLayout.setCenter(centerStack);

        refreshBoardsAsync(boardsGrid, user, spinner);
        checkNotificationsAsync(user.getId());

        createBtn.setOnAction(e -> showCreateBoardDialog(user, boardsGrid, spinner));

        return mainLayout;
    }

    private static void refreshBoardsAsync(FlowPane grid, User user, ProgressIndicator spinner) {
        if(spinner != null) spinner.setVisible(true);
        CompletableFuture.supplyAsync(() -> TrelloDAO.getAllBoards(user.getId()))
                .thenAccept(boards -> Platform.runLater(() -> {
                    grid.getChildren().clear();
                    if(spinner != null) spinner.setVisible(false);
                    if (boards.isEmpty()) {
                        Label noBoards = new Label("У вас еще нет досок.");
                        noBoards.setStyle("-fx-font-size: 16px; -fx-text-fill: " + MenuScene.Theme.TEXT_SEC_DARK + ";");
                        grid.getChildren().add(noBoards);
                    } else {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        for (Board board : boards) {
                            grid.getChildren().add(createBoardCard(board, user, formatter));
                        }
                    }
                })).exceptionally(ex -> {
                    Platform.runLater(() -> { if(spinner != null) spinner.setVisible(false); ex.printStackTrace(); });
                    return null;
                });
    }

    private static VBox createBoardCard(Board board, User currentUser, DateTimeFormatter formatter) {
        VBox card = new VBox(10);
        card.setPrefSize(300, 240);

        String cardBg = MenuScene.isDarkTheme ? "#232323" : "white";
        String shadow = MenuScene.isDarkTheme ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.2)";
        String textColor = MenuScene.getTextStyle();
        String descColor = MenuScene.isDarkTheme ? MenuScene.Theme.TEXT_SEC_DARK : "#666";

        card.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 10; -fx-padding: 20; -fx-effect: dropshadow(gaussian, " + shadow + ", 10, 0, 0, 2);");

        Label title = new Label(board.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
        title.setWrapText(true);

        Label desc = new Label(board.getDescription() != null ? board.getDescription() : "");
        desc.setStyle("-fx-text-fill: " + descColor + "; -fx-font-size: 12px;");
        desc.setWrapText(true);
        desc.setMaxHeight(40);

        boolean isOwner = board.getUserId() == currentUser.getId();

        Label roleLabel = new Label(isOwner ? "Владелец" : "Участник");
        if (isOwner) roleLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold; -fx-font-size: 13px;");
        else roleLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold; -fx-font-size: 13px;");

        VBox membersSection = new VBox(5);
        membersSection.setAlignment(Pos.CENTER_LEFT);

        Runnable refreshAction = () -> {
            if (card.getParent() instanceof FlowPane) refreshBoardsAsync((FlowPane) card.getParent(), currentUser, null);
        };

        CompletableFuture.supplyAsync(() -> {
            User owner = UserDAO.getUserById(board.getUserId());
            List<User> members = TrelloDAO.getBoardMembers(board.getId());
            return new Pair<>(owner, members);
        }).thenAccept(data -> Platform.runLater(() -> {
            User owner = data.getKey();
            List<User> members = data.getValue();

            if (!isOwner && owner != null) {
                HBox ownerBox = new HBox(5);
                ownerBox.setAlignment(Pos.CENTER_LEFT);
                Label lbl = new Label("Владелец:");
                lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
                Node avatar = createAvatar(owner);
                avatar.setOnMouseClicked(e -> { e.consume(); showUserInfoPopup(owner, avatar); });
                avatar.setStyle("-fx-cursor: hand;");
                ownerBox.getChildren().addAll(lbl, avatar);
                membersSection.getChildren().add(ownerBox);
            }

            if (!members.isEmpty()) {
                HBox membersBox = new HBox(5);
                membersBox.setAlignment(Pos.CENTER_LEFT);
                Label lbl = new Label("Соучастники:");
                lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
                HBox avatarsStack = createMembersStack(members);
                avatarsStack.setOnMouseClicked(e -> { e.consume(); showMembersDialog("Участники", members, currentUser, board, refreshAction); });
                avatarsStack.setStyle("-fx-cursor: hand;");
                membersBox.getChildren().addAll(lbl, avatarsStack);
                membersSection.getChildren().add(membersBox);
            }
        }));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label date = new Label("Создана: " + board.getCreatedAt().format(formatter));
        date.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");

        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_LEFT);

        String actionBtnColor = MenuScene.isDarkTheme ? MenuScene.Theme.TEXT_SEC_DARK : "black";

        if (isOwner) {
            Button editBtn = new Button("✏️");
            editBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-text-fill: " + actionBtnColor + ";");
            editBtn.setOnAction(e -> { e.consume(); showEditBoardDialog(currentUser, board, (FlowPane) card.getParent()); });

            Button deleteBtn = new Button("🗑️");
            deleteBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-text-fill: " + MenuScene.Theme.ERROR + ";");
            deleteBtn.setOnAction(e -> { e.consume(); showDeleteConfirmation(board, (FlowPane) card.getParent(), currentUser); });

            Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
            Button inviteBtn = new Button("👤+");
            inviteBtn.setStyle("-fx-background-color: " + (MenuScene.isDarkTheme ? "#444" : "#e0e0e0") + "; -fx-text-fill: " + textColor + "; -fx-background-radius: 20; -fx-cursor: hand;");
            inviteBtn.setOnAction(e -> { e.consume(); showInviteDialog(currentUser, board); });

            actions.getChildren().addAll(editBtn, deleteBtn, sep, inviteBtn);
        } else {
            Button leaveBtn = new Button("🚪");
            leaveBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 16px; -fx-text-fill: " + MenuScene.Theme.ERROR + ";");
            leaveBtn.setTooltip(new Tooltip("Покинуть доску"));
            leaveBtn.setOnAction(e -> { e.consume(); showLeaveConfirmation(board, (FlowPane) card.getParent(), currentUser); });
            actions.getChildren().add(leaveBtn);
        }

        card.getChildren().addAll(title, desc, roleLabel, membersSection, spacer, date, actions);

        // === ИСПРАВЛЕНИЕ: ПЕРЕХОД В ДОСКУ С СОХРАНЕНИЕМ КОНТЕКСТА ===
        card.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button) && !(e.getTarget() instanceof Node && ((Node)e.getTarget()).getStyleClass().contains("avatar"))) {

                // 1. Создаем действие "Открыть эту доску"
                Runnable openBoardAction = () -> MenuScene.setContent(TrelloBoardScene.createBoardView(currentUser, board));

                // 2. Говорим MenuScene: "Если сменят тему, выполни это действие"
                MenuScene.setContentReloader(openBoardAction);

                // 3. Открываем доску
                openBoardAction.run();
            }
        });

        card.setOnMouseEntered(e -> {
            String hoverBg = MenuScene.isDarkTheme ? "#2c2c2c" : "#f9f9f9";
            card.setStyle("-fx-background-color: " + hoverBg + "; -fx-background-radius: 10; -fx-padding: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 5); -fx-cursor: hand;");
        });
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 10; -fx-padding: 20; -fx-effect: dropshadow(gaussian, " + shadow + ", 10, 0, 0, 2);");
        });

        return card;
    }

    // ... (Остальной код без изменений: хелперы аватарок, попапы, диалоги и т.д.)
    private static Node createAvatar(User user) {
        double size = 24; Circle circle = new Circle(size / 2);
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            try { circle.setFill(new ImagePattern(new Image(user.getAvatarPath()))); return circle; } catch (Exception e) {}
        }
        circle.setFill(Color.web("#2196F3"));
        Label letter = new Label(user.getDisplayName().substring(0, 1).toUpperCase());
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px;");
        StackPane stack = new StackPane(circle, letter); stack.getStyleClass().add("avatar"); return stack;
    }

    private static Node createLargeAvatar(User user) {
        double size = 60;
        Circle circle = new Circle(size / 2);
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            try { circle.setFill(new ImagePattern(new Image(user.getAvatarPath()))); return circle; } catch (Exception e) {}
        }
        circle.setFill(Color.web("#2196F3"));
        Label letter = new Label(user.getDisplayName().substring(0, 1).toUpperCase());
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 24px;");
        StackPane stack = new StackPane(circle, letter);
        return stack;
    }

    private static HBox createMembersStack(List<User> members) {
        HBox stack = new HBox(2); int limit = 3;
        for (int i = 0; i < Math.min(members.size(), limit); i++) stack.getChildren().add(createAvatar(members.get(i)));
        if (members.size() > limit) { Circle c = new Circle(12); c.setFill(Color.LIGHTGRAY); Label l = new Label("+" + (members.size()-limit)); l.setStyle("-fx-font-size: 9px; font-weight: bold;"); StackPane s = new StackPane(c, l); s.getStyleClass().add("avatar"); stack.getChildren().add(s); }
        return stack;
    }

    private static void showUserInfoPopup(User u, Node anchor) {
        Popup popup = new Popup();
        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);

        String bg = MenuScene.isDarkTheme ? "#333" : "white";
        String textColor = MenuScene.getTextStyle();

        content.setStyle("-fx-background-color: " + bg + "; -fx-padding: 20; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2); -fx-border-color: #777; -fx-border-radius: 10;");

        Node avatar = createLargeAvatar(u);

        Label name = new Label(u.getDisplayName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + textColor + ";");

        content.getChildren().addAll(avatar, name, new Separator());

        VBox details = new VBox(5);
        details.setAlignment(Pos.CENTER_LEFT);

        if (u.getEmail() != null) {
            Label l = new Label("📧 " + u.getEmail());
            l.setStyle("-fx-text-fill: " + textColor + ";");
            details.getChildren().add(l);
        }
        if (u.getPhone() != null && !u.getPhone().isEmpty()) {
            Label l = new Label("📞 " + u.getPhone());
            l.setStyle("-fx-text-fill: " + textColor + ";");
            details.getChildren().add(l);
        }

        content.getChildren().add(details);

        if (u.getBirthday() != null && !u.getBirthday().isEmpty()) {
            content.getChildren().add(new Separator());
            Label bd = new Label("🎂 " + u.getBirthday());
            bd.setStyle("-fx-text-fill: " + (MenuScene.isDarkTheme ? "#aaa" : "#555") + "; -fx-font-weight: bold;");
            content.getChildren().add(bd);
        }

        popup.getContent().add(content);
        popup.setAutoHide(true);

        Point2D point = anchor.localToScreen(0, 0);
        popup.show(anchor, point.getX() + anchor.getBoundsInLocal().getWidth() + 5, point.getY());
    }

    private static void showMembersDialog(String title, List<User> users, User currentUser, Board board, Runnable onUpdate) {
        Stage dialog = new Stage(); dialog.initModality(Modality.APPLICATION_MODAL); dialog.initOwner(MenuScene.getStage()); dialog.setTitle(title);

        VBox root = new VBox(10); root.setPadding(new Insets(15));

        String bg = MenuScene.isDarkTheme ? MenuScene.Theme.BG_DARK : "white";
        String textColor = MenuScene.getTextStyle();
        root.setStyle("-fx-background-color: " + bg + ";");

        boolean isOwner = (currentUser.getId() == board.getUserId());

        for (User u : users) {
            HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 5; -fx-background-radius: 5; -fx-cursor: hand;");

            String hoverColor = MenuScene.isDarkTheme ? "#333" : "#f0f0f0";
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: " + hoverColor + "; -fx-padding: 5; -fx-background-radius: 5; -fx-cursor: hand;"));
            row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent; -fx-padding: 5; -fx-background-radius: 5; -fx-cursor: hand;"));

            Node avatar = createAvatar(u);
            VBox info = new VBox(2);
            String displayName = u.getDisplayName();
            if (u.getId() == currentUser.getId()) displayName += " (Вы)";
            Label name = new Label(displayName); name.setStyle("-fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
            Label email = new Label(u.getEmail()); email.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
            info.getChildren().addAll(name, email);
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(avatar, info, spacer);

            if (isOwner && u.getId() != currentUser.getId() && !title.equals("Владелец")) {
                Button removeBtn = new Button("🗑"); removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e74c3c;");
                removeBtn.setOnAction(e -> { e.consume(); CompletableFuture.runAsync(() -> TrelloDAO.removeBoardMember(board.getId(), u.getId())).thenRun(() -> Platform.runLater(() -> { root.getChildren().remove(row); if (onUpdate != null) onUpdate.run(); })); });
                row.getChildren().add(removeBtn);
            }

            row.setOnMouseClicked(e -> {
                if (u.getId() == currentUser.getId()) return;
                showUserInfoPopup(u, row);
            });

            root.getChildren().add(row);
        }
        ScrollPane sc = new ScrollPane(root); sc.setFitToWidth(true);
        sc.setStyle("-fx-background: " + bg + "; -fx-background-color: transparent;");
        dialog.setScene(new Scene(sc, 350, 300)); dialog.show();
    }

    private static void showLeaveConfirmation(Board board, FlowPane grid, User user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.setTitle("Покинуть"); alert.setHeaderText("Выйти из доски?");
        styleDialog(alert);
        alert.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) CompletableFuture.runAsync(() -> TrelloDAO.leaveBoard(board.getId(), user.getId(), user.getDisplayName())).thenRun(() -> Platform.runLater(() -> refreshBoardsAsync(grid, user, null))); });
    }

    private static void showDeleteConfirmation(Board board, FlowPane grid, User user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.setTitle("Удалить"); alert.setHeaderText("Удалить доску?");
        styleDialog(alert);
        alert.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) CompletableFuture.runAsync(() -> TrelloDAO.deleteBoard(board.getId())).thenRun(() -> refreshBoardsAsync(grid, user, null)); });
    }

    private static void styleDialog(Dialog<?> dialog) {
        if (MenuScene.isDarkTheme) {
            DialogPane pane = dialog.getDialogPane();
            pane.setStyle("-fx-background-color: " + MenuScene.Theme.HOVER_DARK + ";");
            pane.lookup(".content.label").setStyle("-fx-text-fill: white;");
            pane.lookup(".header-panel").setStyle("-fx-background-color: " + MenuScene.Theme.SIDEBAR_DARK + "; -fx-text-fill: white;");
        }
    }

    private static void checkNotificationsAsync(int userId) {
        CompletableFuture.supplyAsync(() -> {
            List<TrelloDAO.Invitation> i = TrelloDAO.getPendingInvitations(userId);
            List<TrelloDAO.Notification> n = TrelloDAO.getUnreadNotifications(userId);
            return i.size() + n.size();
        }).thenAccept(c -> Platform.runLater(() -> { notificationBadge.setText(String.valueOf(c)); notificationBadge.setVisible(c > 0); }));
    }

    private static void showNotificationsDialog(User user, BorderPane rootLayout) {
        Stage dialog = new Stage(); dialog.initModality(Modality.APPLICATION_MODAL); dialog.initOwner(MenuScene.getStage()); dialog.setTitle("Центр уведомлений");
        BorderPane dialogLayout = new BorderPane(); dialogLayout.setPrefSize(600, 450);

        String sbBg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "#f0f2f5";
        String contentBg = MenuScene.isDarkTheme ? MenuScene.Theme.BG_DARK : "white";
        String textColor = MenuScene.getTextStyle();

        VBox sidebar = new VBox(10); sidebar.setPadding(new Insets(15));
        sidebar.setStyle("-fx-background-color: " + sbBg + "; -fx-border-color: #ddd; -fx-border-width: 0 1 0 0;"); sidebar.setPrefWidth(150);

        ToggleButton incomingBtn = new ToggleButton("Входящие"); incomingBtn.setMaxWidth(Double.MAX_VALUE);
        ToggleButton outgoingBtn = new ToggleButton("Исходящие"); outgoingBtn.setMaxWidth(Double.MAX_VALUE);
        ToggleButton archiveBtn = new ToggleButton("Архив"); archiveBtn.setMaxWidth(Double.MAX_VALUE);
        ToggleGroup group = new ToggleGroup(); incomingBtn.setToggleGroup(group); outgoingBtn.setToggleGroup(group); archiveBtn.setToggleGroup(group); incomingBtn.setSelected(true);
        sidebar.getChildren().addAll(incomingBtn, outgoingBtn, archiveBtn); dialogLayout.setLeft(sidebar);

        VBox contentArea = new VBox(10); contentArea.setPadding(new Insets(15));
        contentArea.setStyle("-fx-background-color: " + contentBg + ";");

        HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
        Label sectionTitle = new Label("Входящие"); sectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        MenuButton sortBtn = new MenuButton("Сначала новые"); MenuItem newest = new MenuItem("Сначала новые"); MenuItem oldest = new MenuItem("Сначала старые");
        sortBtn.getItems().addAll(newest, oldest); header.getChildren().addAll(sectionTitle, sp, sortBtn); contentArea.getChildren().add(header);
        VBox listContainer = new VBox(10); ScrollPane scrollPane = new ScrollPane(listContainer); scrollPane.setFitToWidth(true); scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;"); VBox.setVgrow(scrollPane, Priority.ALWAYS); contentArea.getChildren().add(scrollPane);
        dialogLayout.setCenter(contentArea);

        class DisplayItem { Object data; LocalDateTime date; String type; DisplayItem(Object d, LocalDateTime t, String ty) { data=d; date=t; type=ty; } }
        final List<DisplayItem>[] currentItems = new List[1];

        Runnable render = () -> {
            listContainer.getChildren().clear();
            if (currentItems[0] == null || currentItems[0].isEmpty()) {
                Label l = new Label("Список пуст");
                l.setStyle("-fx-text-fill: " + textColor + ";");
                listContainer.getChildren().add(l);
                return;
            }
            if (sortBtn.getText().equals("Сначала новые")) currentItems[0].sort(Comparator.comparing(i -> i.date, Comparator.reverseOrder())); else currentItems[0].sort(Comparator.comparing(i -> i.date));
            for (DisplayItem item : currentItems[0]) {
                if (item.type.equals("IN_INVITE")) listContainer.getChildren().add(createIncomingInviteRow((TrelloDAO.Invitation)item.data, user, listContainer, rootLayout));
                else if (item.type.equals("IN_NOTIF")) listContainer.getChildren().add(createNotificationRow((TrelloDAO.Notification)item.data, user, listContainer));
                else if (item.type.equals("OUT_INVITE")) listContainer.getChildren().add(createOutgoingInviteRow((TrelloDAO.Invitation)item.data));
                else if (item.type.equals("ARCHIVED_INVITE")) listContainer.getChildren().add(createArchivedInviteRow((TrelloDAO.Invitation)item.data));
                else if (item.type.equals("ARCHIVED_NOTIF")) listContainer.getChildren().add(createArchivedNotificationRow((TrelloDAO.Notification)item.data));
            }
        };
        Runnable loadIncoming = () -> { sectionTitle.setText("Входящие"); CompletableFuture.supplyAsync(() -> { List<DisplayItem> l = new ArrayList<>(); for(TrelloDAO.Invitation i : TrelloDAO.getPendingInvitations(user.getId())) l.add(new DisplayItem(i, i.getCreatedAt(), "IN_INVITE")); for(TrelloDAO.Notification n : TrelloDAO.getUnreadNotifications(user.getId())) l.add(new DisplayItem(n, n.getCreatedAt(), "IN_NOTIF")); return l; }).thenAccept(l -> Platform.runLater(() -> { currentItems[0] = l; render.run(); })); };
        Runnable loadOutgoing = () -> { sectionTitle.setText("Исходящие"); CompletableFuture.supplyAsync(() -> { List<DisplayItem> l = new ArrayList<>(); for(TrelloDAO.Invitation i : TrelloDAO.getOutgoingInvitations(user.getId())) l.add(new DisplayItem(i, i.getCreatedAt(), "OUT_INVITE")); return l; }).thenAccept(l -> Platform.runLater(() -> { currentItems[0] = l; render.run(); })); };
        Runnable loadArchive = () -> { sectionTitle.setText("Архив"); CompletableFuture.supplyAsync(() -> { List<DisplayItem> l = new ArrayList<>(); for(TrelloDAO.Invitation i : TrelloDAO.getArchivedInvitations(user.getId())) l.add(new DisplayItem(i, i.getCreatedAt(), "ARCHIVED_INVITE")); for(TrelloDAO.Notification n : TrelloDAO.getReadNotifications(user.getId())) l.add(new DisplayItem(n, n.getCreatedAt(), "ARCHIVED_NOTIF")); return l; }).thenAccept(l -> Platform.runLater(() -> { currentItems[0] = l; render.run(); })); };

        incomingBtn.setOnAction(e -> loadIncoming.run()); outgoingBtn.setOnAction(e -> loadOutgoing.run()); archiveBtn.setOnAction(e -> loadArchive.run());
        newest.setOnAction(e -> { sortBtn.setText("Сначала новые"); render.run(); }); oldest.setOnAction(e -> { sortBtn.setText("Сначала старые"); render.run(); });
        loadIncoming.run();
        dialog.setScene(new Scene(dialogLayout)); dialog.show();
    }

    private static HBox createIncomingInviteRow(TrelloDAO.Invitation inv, User user, VBox container, BorderPane layout) {
        String bg = MenuScene.isDarkTheme ? "#1e3a5a" : "#e3f2fd";
        String textColor = MenuScene.getTextStyle();

        HBox row = new HBox(10); row.setStyle("-fx-background-color: " + bg + "; -fx-padding: 10; -fx-background-radius: 5;"); row.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2);
        Label l1 = new Label(inv.getPersonName() + " приглашает:"); l1.setStyle("-fx-text-fill: " + textColor + ";");
        Label l2 = new Label(inv.getBoardName()); l2.setStyle("-fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
        text.getChildren().addAll(l1, l2);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button yes = new Button("✔"); yes.setStyle("-fx-text-fill: white; -fx-background-color: #4CAF50;");
        Button no = new Button("✕"); no.setStyle("-fx-text-fill: white; -fx-background-color: #F44336;");
        yes.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.acceptInvitation(inv.getId())).thenRun(() -> Platform.runLater(() -> { container.getChildren().remove(row); checkNotificationsAsync(user.getId()); if(layout.getCenter() instanceof StackPane s && !s.getChildren().isEmpty() && s.getChildren().get(0) instanceof ScrollPane sc && sc.getContent() instanceof FlowPane fp) refreshBoardsAsync(fp, user, null); })));
        no.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.declineInvitation(inv.getId())).thenRun(() -> Platform.runLater(() -> { container.getChildren().remove(row); checkNotificationsAsync(user.getId()); })));
        VBox right = new VBox(5); right.setAlignment(Pos.CENTER_RIGHT); right.getChildren().addAll(new HBox(5, yes, no), new Label(inv.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))));
        row.getChildren().addAll(text, sp, right); return row;
    }

    private static HBox createNotificationRow(TrelloDAO.Notification notif, User user, VBox container) {
        String bg = MenuScene.isDarkTheme ? "#4a3b2a" : "#fff3e0";
        String textColor = MenuScene.getTextStyle();

        HBox row = new HBox(10); row.setStyle("-fx-background-color: " + bg + "; -fx-padding: 10; -fx-background-radius: 5;"); row.setAlignment(Pos.CENTER_LEFT);
        Label msg = new Label(notif.getMessage()); msg.setWrapText(true); msg.setMaxWidth(300); msg.setStyle("-fx-text-fill: " + textColor + ";");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button ok = new Button("OK"); ok.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.markNotificationAsRead(notif.getId())).thenRun(() -> Platform.runLater(() -> { container.getChildren().remove(row); checkNotificationsAsync(user.getId()); })));
        VBox right = new VBox(5); right.setAlignment(Pos.CENTER_RIGHT); right.getChildren().addAll(ok, new Label(notif.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))));
        row.getChildren().addAll(msg, sp, right); return row;
    }

    private static HBox createOutgoingInviteRow(TrelloDAO.Invitation inv) {
        String bg = MenuScene.isDarkTheme ? "#333" : "#f5f5f5";
        String textColor = MenuScene.getTextStyle();

        HBox row = new HBox(10); row.setStyle("-fx-background-color: " + bg + "; -fx-padding: 10; -fx-background-radius: 5;"); row.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2);
        Label l1 = new Label("Кому: " + inv.getPersonName()); l1.setStyle("-fx-text-fill: " + textColor + ";");
        Label l2 = new Label("Доска: " + inv.getBoardName()); l2.setStyle("-fx-text-fill: " + textColor + ";");
        text.getChildren().addAll(l1, l2);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label status = new Label(inv.getStatus()); if(inv.getStatus().equals("ACCEPTED")) status.setTextFill(Color.GREEN); else if(inv.getStatus().equals("DECLINED")) status.setTextFill(Color.RED); else status.setTextFill(Color.ORANGE);
        VBox right = new VBox(5); right.setAlignment(Pos.CENTER_RIGHT); right.getChildren().addAll(status, new Label(inv.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))));
        row.getChildren().addAll(text, sp, right); return row;
    }

    private static HBox createArchivedInviteRow(TrelloDAO.Invitation inv) { HBox row = createOutgoingInviteRow(inv); ((VBox)row.getChildren().get(0)).getChildren().set(0, new Label("От: " + inv.getPersonName())); row.setStyle(row.getStyle() + " -fx-opacity: 0.7;"); return row; }

    private static HBox createArchivedNotificationRow(TrelloDAO.Notification notif) {
        String bg = MenuScene.isDarkTheme ? "#333" : "#eeeeee";
        String textColor = MenuScene.getTextStyle();
        HBox row = new HBox(10); row.setStyle("-fx-background-color: " + bg + "; -fx-padding: 10; -fx-background-radius: 5; -fx-opacity: 0.7;"); row.setAlignment(Pos.CENTER_LEFT);
        Label msg = new Label(notif.getMessage()); msg.setWrapText(true); msg.setStyle("-fx-text-fill: " + textColor + ";");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS); row.getChildren().addAll(msg, sp, new Label(notif.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")))); return row;
    }

    private static void showCreateBoardDialog(User user, FlowPane grid, ProgressIndicator spinner) {
        TextInputDialog d = new TextInputDialog(); d.setTitle("Создать"); d.setHeaderText("Новая доска");
        styleDialog(d);
        d.showAndWait().ifPresent(n -> { if(!n.trim().isEmpty()) { if(spinner!=null)spinner.setVisible(true); CompletableFuture.runAsync(() -> TrelloDAO.createBoard(n, "", user.getId())).thenRun(() -> refreshBoardsAsync(grid, user, spinner)); }});
    }

    private static void showEditBoardDialog(User user, Board board, FlowPane grid) {
        TextInputDialog d = new TextInputDialog(board.getTitle()); d.setTitle("Редактировать");
        styleDialog(d);
        d.showAndWait().ifPresent(n -> CompletableFuture.runAsync(() -> TrelloDAO.updateBoard(board.getId(), n, board.getDescription())).thenRun(() -> refreshBoardsAsync(grid, user, null)));
    }

    private static void showInviteDialog(User u, Board b) {
        Stage d = new Stage(); d.initModality(Modality.APPLICATION_MODAL); d.initOwner(MenuScene.getStage());
        TabPane tp = new TabPane(); tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        if (MenuScene.isDarkTheme) {
            tp.setStyle("-fx-background-color: " + MenuScene.Theme.BG_DARK + ";");
        }

        tp.getTabs().addAll(createInviteTab("Ник", "Username:", "username", u, b), createInviteTab("Email", "Email:", "email", u, b), createInviteTab("Телефон", "Телефон:", "phone", u, b));
        d.setScene(new Scene(tp, 400, 250)); d.show();
    }

    private static void setupPhoneNumberFormatting(TextField textField) {
        textField.setText("+7 ");
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.startsWith("+7 ")) {
                Platform.runLater(() -> {
                    textField.setText("+7 ");
                    textField.positionCaret(3);
                });
                return;
            }

            String digits = newVal.replaceAll("[^\\d]", "");
            if (digits.length() > 11) digits = digits.substring(0, 11);

            StringBuilder formatted = new StringBuilder("+7 ");
            if (digits.length() > 1) {
                formatted.append("(");
                formatted.append(digits.substring(1, Math.min(4, digits.length())));
            }
            if (digits.length() >= 4) {
                formatted.append(") ");
                formatted.append(digits.substring(4, Math.min(7, digits.length())));
            }
            if (digits.length() >= 7) {
                formatted.append("-");
                formatted.append(digits.substring(7, Math.min(9, digits.length())));
            }
            if (digits.length() >= 9) {
                formatted.append("-");
                formatted.append(digits.substring(9));
            }

            if (!formatted.toString().equals(newVal)) {
                String finalFormatted = formatted.toString();
                Platform.runLater(() -> {
                    textField.setText(finalFormatted);
                    textField.positionCaret(finalFormatted.length());
                });
            }
        });
    }

    private static Tab createInviteTab(String t, String l, String type, User s, Board b) {
        Tab tab = new Tab(t); tab.setClosable(false);
        VBox box = new VBox(15); box.setPadding(new Insets(20)); box.setAlignment(Pos.CENTER);

        String textColor = MenuScene.getTextStyle();
        if (MenuScene.isDarkTheme) box.setStyle("-fx-background-color: " + MenuScene.Theme.BG_DARK + ";");

        TextField in = new TextField();
        in.setPromptText("Поиск...");

        if (type.equals("phone")) {
            setupPhoneNumberFormatting(in);
        }

        Label label = new Label(l);
        label.setStyle("-fx-text-fill: " + textColor + ";");

        Label st = new Label();
        Button btn = new Button("Отправить"); btn.setOnAction(e -> {
            String v = in.getText().trim(); if(v.isEmpty()) return; st.setText("Поиск...");
            CompletableFuture.supplyAsync(() -> type.equals("username") ? UserDAO.findUserIdByUsername(v) : type.equals("email") ? UserDAO.findUserIdByEmail(v) : UserDAO.findUserIdByPhone(v))
                    .thenAccept(id -> Platform.runLater(() -> {
                        if(id == -1) { st.setText("Не найден"); st.setTextFill(Color.RED); }
                        else if(id == s.getId()) { st.setText("Нельзя себя"); st.setTextFill(Color.RED); }
                        else CompletableFuture.supplyAsync(() -> TrelloDAO.sendInvitation(s.getId(), id, b.getId())).thenAccept(ok -> Platform.runLater(() -> {
                                if(ok) { st.setText("Отправлено"); st.setTextFill(Color.GREEN); } else { st.setText("Ошибка/Уже там"); st.setTextFill(Color.RED); }
                            }));
                    }));
        });
        box.getChildren().addAll(label, in, btn, st); tab.setContent(box); return tab;
    }

    private static class Pair<K, V> { private K key; private V value; public Pair(K k, V v) { key=k; value=v; } public K getKey() { return key; } public V getValue() { return value; } }
}