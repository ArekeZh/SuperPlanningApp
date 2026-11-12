package com.example.superplanningapp.trello;

import com.example.superplanningapp.MenuScene;
import com.example.superplanningapp.User;
import com.example.superplanningapp.UserDAO;
import com.example.superplanningapp.ui.Icons;
import com.example.superplanningapp.ui.Theme;
import com.example.superplanningapp.ui.Ui;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Список досок пользователя, приглашения и уведомления. */
public class TrelloHomeScene {

    private static final String[] VARIANTS = {"skill-sky", "skill-purple", "skill-pink", "skill-amber", "skill-green"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private static Label notificationBadge;
    private static FlowPane currentGrid;
    private static ProgressIndicator currentSpinner;
    private static User currentUser;

    public static Node createTrelloHomeView(User user) {
        currentUser = user;

        Button notificationBtn = Ui.ghost("Уведомления", Icons.BELL);
        notificationBadge = Ui.label("0", "badge");
        notificationBadge.setVisible(false);
        notificationBadge.setMouseTransparent(true);
        StackPane notificationWrap = new StackPane(notificationBtn, notificationBadge);
        StackPane.setAlignment(notificationBadge, Pos.TOP_RIGHT);
        notificationBadge.setTranslateX(4);
        notificationBadge.setTranslateY(-5);

        Button createBtn = Ui.primary("Создать доску", Icons.PLUS);

        HBox head = Ui.headerRow(Ui.header("Доски", "Мои доски",
                "Канбан-доски для проектов: списки, задачи и совместная работа. "
                        + "Владелец доски может пригласить участников по нику, email или телефону."),
                notificationWrap, createBtn);

        FlowPane grid = new FlowPane(18, 18);
        grid.setAlignment(Pos.TOP_LEFT);
        ProgressIndicator spinner = new ProgressIndicator();
        Ui.fix(spinner, 34);
        StackPane gridHost = new StackPane(grid, spinner);
        StackPane.setAlignment(spinner, Pos.TOP_CENTER);
        currentGrid = grid;
        currentSpinner = spinner;

        notificationBtn.setOnAction(e -> showNotificationsDialog(user));
        createBtn.setOnAction(e -> showCreateBoardDialog(user));

        refreshBoardsAsync();
        checkNotificationsAsync(user.getId());
        return Ui.page(head, gridHost);
    }

    // ------------------------------------------------------------- boards

    private static void refreshBoardsAsync() {
        FlowPane grid = currentGrid;
        ProgressIndicator spinner = currentSpinner;
        User user = currentUser;
        if (grid == null || user == null) return;
        spinner.setVisible(true);

        CompletableFuture.supplyAsync(() -> TrelloDAO.getAllBoards(user.getId()))
                .whenComplete((boards, err) -> Platform.runLater(() -> {
                    spinner.setVisible(false);
                    grid.getChildren().clear();
                    if (err != null) err.printStackTrace();
                    if (boards == null || boards.isEmpty()) {
                        grid.getChildren().add(emptyState(user));
                        return;
                    }
                    int i = 0;
                    for (Board board : boards) {
                        VBox card = createBoardCard(board, user);
                        grid.getChildren().add(card);
                        Ui.rise(card, i++);
                    }
                }));
    }

    private static VBox emptyState(User user) {
        Button create = Ui.primary("Создать доску", Icons.PLUS);
        create.setOnAction(e -> showCreateBoardDialog(user));
        Label hint = Ui.wrap("Создайте первую доску или примите приглашение от друга — оно появится в уведомлениях.", "muted");
        hint.setMaxWidth(380);
        VBox empty = new VBox(12, Ui.skill(Icons.KANBAN, "skill-sky", 52), Ui.label("Пока нет досок", "h3"), hint, create);
        empty.getStyleClass().add("platform");
        empty.setPrefWidth(520);
        empty.setPadding(new Insets(32));
        VBox.setMargin(create, new Insets(6, 0, 0, 0));
        return empty;
    }

    private static VBox createBoardCard(Board board, User user) {
        boolean isOwner = board.getUserId() == user.getId();

        StackPane tile = Ui.skill(Icons.KANBAN, VARIANTS[Math.floorMod(board.getId(), VARIANTS.length)], 42);
        Label role = Ui.label(isOwner ? "ВЛАДЕЛЕЦ" : "УЧАСТНИК", "chip", isOwner ? "accent" : null);
        HBox top = new HBox(10, tile, Ui.hgrow(), role);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = Ui.wrap(board.getTitle(), "board-title");
        String descText = board.getDescription() != null && !board.getDescription().isBlank()
                ? board.getDescription() : "Без описания";
        Label desc = Ui.wrap(descText, "board-desc");
        desc.setMaxHeight(38);
        desc.setMinHeight(Region.USE_COMPUTED_SIZE);

        VBox members = new VBox(6);

        Label date = Ui.label("СОЗДАНА " + board.getCreatedAt().format(DATE), "mono");
        HBox actions = new HBox(2);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (isOwner) {
            Button edit = Ui.iconButton(Icons.PENCIL, "Редактировать");
            edit.setOnAction(e -> showEditBoardDialog(board));
            Button invite = Ui.iconButton(Icons.USER_PLUS, "Пригласить участника");
            invite.setOnAction(e -> showInviteDialog(user, board));
            Button delete = Ui.iconButton(Icons.TRASH, "Удалить доску", "danger");
            delete.setOnAction(e -> showDeleteConfirmation(board));
            actions.getChildren().addAll(edit, invite, delete);
        } else {
            Button leave = Ui.iconButton(Icons.LOGOUT, "Покинуть доску", "danger");
            leave.setOnAction(e -> showLeaveConfirmation(board, user));
            actions.getChildren().add(leave);
        }
        HBox footer = new HBox(8, date, Ui.hgrow(), actions);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, top, title, desc, members, Ui.vgrow(), new Separator(), footer);
        card.getStyleClass().add("board-card");
        card.setPrefWidth(300);
        card.setMinHeight(250);
        Ui.lift(card);

        // Участники подгружаются в фоне
        CompletableFuture.supplyAsync(() -> new Object[]{
                UserDAO.getUserById(board.getUserId()), TrelloDAO.getBoardMembers(board.getId())
        }).thenAccept(data -> Platform.runLater(() -> {
            User owner = (User) data[0];
            @SuppressWarnings("unchecked") List<User> list = (List<User>) data[1];

            if (!isOwner && owner != null) {
                StackPane avatar = Ui.avatar(owner, 26);
                avatar.getStyleClass().addAll("avatar-ring", "interactive");
                avatar.setOnMouseClicked(e -> showUserInfoPopup(owner, avatar));
                HBox ownerBox = new HBox(8, Ui.label("ВЛАДЕЛЕЦ", "mono"), avatar, Ui.label(owner.getDisplayName(), "small", "muted"));
                ownerBox.setAlignment(Pos.CENTER_LEFT);
                members.getChildren().add(ownerBox);
            }
            if (!list.isEmpty()) {
                HBox stack = new HBox(-8);
                for (int i = 0; i < Math.min(list.size(), 4); i++) {
                    StackPane a = Ui.avatar(list.get(i), 26);
                    a.getStyleClass().add("avatar-ring");
                    stack.getChildren().add(a);
                }
                if (list.size() > 4) stack.getChildren().add(Ui.label("+" + (list.size() - 4), "chip"));
                stack.getStyleClass().add("interactive");
                stack.setOnMouseClicked(e -> showMembersDialog(list, user, board));
                HBox membersBox = new HBox(8, Ui.label("УЧАСТНИКИ", "mono"), stack);
                membersBox.setAlignment(Pos.CENTER_LEFT);
                membersBox.setStyle("-fx-cursor: hand;");
                members.getChildren().add(membersBox);
            }
        }));

        card.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || Ui.isInteractive(e.getTarget(), card)) return;
            MenuScene.setContent(TrelloBoardScene.createBoardView(user, board));
        });
        return card;
    }

    // ---------------------------------------------------- board dialogs

    private static void showCreateBoardDialog(User user) {
        boardForm("Новая доска", "Создать", "", "").ifPresent(v -> {
            currentSpinner.setVisible(true);
            CompletableFuture.runAsync(() -> TrelloDAO.createBoard(v[0], v[1], user.getId()))
                    .thenRun(() -> Platform.runLater(TrelloHomeScene::refreshBoardsAsync));
        });
    }

    private static void showEditBoardDialog(Board board) {
        boardForm("Редактировать доску", "Сохранить", board.getTitle(), board.getDescription()).ifPresent(v ->
                CompletableFuture.runAsync(() -> TrelloDAO.updateBoard(board.getId(), v[0], v[1]))
                        .thenRun(() -> Platform.runLater(TrelloHomeScene::refreshBoardsAsync)));
    }

    private static java.util.Optional<String[]> boardForm(String header, String okText, String title0, String desc0) {
        TextField title = new TextField(title0);
        title.setPromptText("Например, «Курсовой проект»");
        TextArea desc = new TextArea(desc0 == null ? "" : desc0);
        desc.setPromptText("Коротко: о чём эта доска");
        desc.setPrefRowCount(3);
        desc.setWrapText(true);
        VBox form = new VBox(14, Ui.field("Название", title, null), Ui.field("Описание", desc, "Необязательно"));
        boolean ok = Ui.form(header, okText, form,
                Bindings.createBooleanBinding(() -> title.getText().trim().isEmpty(), title.textProperty()),
                title, MenuScene.getStage());
        return ok ? java.util.Optional.of(new String[]{title.getText().trim(), desc.getText().trim()})
                : java.util.Optional.empty();
    }

    private static void showDeleteConfirmation(Board board) {
        if (!Ui.confirm("Удалить доску «" + board.getTitle() + "»?",
                "Списки, карточки и приглашения этой доски удалятся без возможности восстановления.",
                "Удалить", true, MenuScene.getStage())) return;
        CompletableFuture.runAsync(() -> TrelloDAO.deleteBoard(board.getId()))
                .thenRun(() -> Platform.runLater(TrelloHomeScene::refreshBoardsAsync));
    }

    private static void showLeaveConfirmation(Board board, User user) {
        if (!Ui.confirm("Покинуть доску «" + board.getTitle() + "»?",
                "Владелец получит уведомление. Вернуться можно будет только по новому приглашению.",
                "Покинуть", true, MenuScene.getStage())) return;
        CompletableFuture.runAsync(() -> TrelloDAO.leaveBoard(board.getId(), user.getId(), user.getDisplayName()))
                .thenRun(() -> Platform.runLater(TrelloHomeScene::refreshBoardsAsync));
    }

    // ---------------------------------------------------------- people

    private static void showUserInfoPopup(User u, Node anchor) {
        VBox content = new VBox(12);
        content.getStyleClass().addAll("popup-card", "themed");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPrefWidth(260);
        Theme.apply(content);

        VBox who = new VBox(3, Ui.label(u.getDisplayName(), "h3"), Ui.label("@" + u.getUsername(), "mono"));
        who.setAlignment(Pos.CENTER);
        content.getChildren().addAll(Ui.avatar(u, 64), who, new Separator());

        VBox details = new VBox(8);
        if (u.getEmail() != null) details.getChildren().add(infoLine(Icons.MAIL, u.getEmail()));
        if (u.getPhone() != null && !u.getPhone().isBlank()) details.getChildren().add(infoLine(Icons.PHONE, u.getPhone()));
        if (u.getBirthday() != null && !u.getBirthday().isBlank()) details.getChildren().add(infoLine(Icons.CALENDAR, u.getBirthday()));
        content.getChildren().add(details);

        Popup popup = new Popup();
        popup.getContent().add(content);
        popup.setAutoHide(true);
        Point2D p = anchor.localToScreen(0, 0);
        popup.show(anchor, p.getX() + anchor.getBoundsInLocal().getWidth() + 8, p.getY() - 10);
    }

    private static HBox infoLine(String icon, String text) {
        HBox line = new HBox(10, Icons.of(icon, 16), Ui.label(text, "muted"));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private static void showMembersDialog(List<User> users, User me, Board board) {
        Stage dialog = modal("Участники доски");
        boolean isOwner = me.getId() == board.getUserId();

        VBox list = new VBox(8);
        for (User u : users) {
            String name = u.getDisplayName() + (u.getId() == me.getId() ? " (вы)" : "");
            VBox text = new VBox(1, Ui.label(name, "semi"), Ui.label(u.getEmail(), "muted", "small"));
            HBox row = new HBox(12, Ui.avatar(u, 36), text, Ui.hgrow());
            row.getStyleClass().addAll("row", "static");
            row.setPadding(new Insets(10, 10, 10, 12));
            if (isOwner && u.getId() != me.getId()) {
                Button remove = Ui.iconButton(Icons.TRASH, "Исключить из доски", "danger");
                remove.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.removeBoardMember(board.getId(), u.getId()))
                        .thenRun(() -> Platform.runLater(() -> {
                            list.getChildren().remove(row);
                            refreshBoardsAsync();
                        })));
                row.getChildren().add(remove);
            }
            if (u.getId() != me.getId()) row.setOnMouseClicked(e -> {
                if (!Ui.isInteractive(e.getTarget(), row)) showUserInfoPopup(u, row);
            });
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox root = new VBox(16, new VBox(4, Ui.label("Участники", "h2"),
                Ui.label("Доска «" + board.getTitle() + "» · " + users.size() + " чел.", "muted")), scroll);
        root.getStyleClass().add("dialog-root");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        dialog.setScene(Theme.scene(root, 420, 420));
        dialog.show();
    }

    // ---------------------------------------------------- notifications

    private static void checkNotificationsAsync(int userId) {
        Label badge = notificationBadge;
        CompletableFuture.supplyAsync(() ->
                TrelloDAO.getPendingInvitations(userId).size() + TrelloDAO.getUnreadNotifications(userId).size()
        ).thenAccept(c -> Platform.runLater(() -> {
            badge.setText(String.valueOf(c));
            badge.setVisible(c > 0);
        }));
    }

    private record Item(Object data, LocalDateTime date, String type) { }

    private static void showNotificationsDialog(User user) {
        Stage dialog = modal("Центр уведомлений");

        ToggleButton incoming = Ui.seg("Входящие", Icons.INBOX);
        ToggleButton outgoing = Ui.seg("Исходящие", Icons.SEND);
        ToggleButton archive = Ui.seg("Архив", null);
        HBox tabs = Ui.segmented(incoming, outgoing, archive);
        incoming.setSelected(true);

        ToggleButton newest = Ui.seg("Сначала новые", null);
        ToggleButton oldest = Ui.seg("Старые", null);
        HBox sort = Ui.segmented(newest, oldest);
        newest.setSelected(true);

        HBox titleRow = new HBox(12, Ui.label("Уведомления", "h2"), Ui.hgrow(), sort);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(10);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        BorderPane.setMargin(scroll, new Insets(18, 0, 0, 0));

        List<Item> current = new ArrayList<>();
        Runnable render = () -> {
            list.getChildren().clear();
            if (current.isEmpty()) {
                VBox empty = new VBox(10, Ui.skill(Icons.BELL, "skill-slate", 44), Ui.label("Здесь пока пусто", "muted"));
                empty.setAlignment(Pos.CENTER);
                empty.setPadding(new Insets(40));
                list.getChildren().add(empty);
                return;
            }
            Comparator<Item> byDate = Comparator.comparing(Item::date);
            current.sort(newest.isSelected() ? byDate.reversed() : byDate);
            for (Item item : current) {
                list.getChildren().add(switch (item.type()) {
                    case "IN_INVITE" -> incomingInviteRow((TrelloDAO.Invitation) item.data(), user, list);
                    case "IN_NOTIF" -> notificationRow((TrelloDAO.Notification) item.data(), user, list);
                    case "OUT_INVITE" -> inviteStatusRow((TrelloDAO.Invitation) item.data(), "Кому: ", Icons.SEND, "skill-purple", false);
                    case "ARCHIVED_INVITE" -> inviteStatusRow((TrelloDAO.Invitation) item.data(), "От: ", Icons.USER_PLUS, "skill-slate", true);
                    default -> archivedNotificationRow((TrelloDAO.Notification) item.data());
                });
            }
        };

        Runnable load = () -> CompletableFuture.supplyAsync(() -> {
            List<Item> items = new ArrayList<>();
            if (incoming.isSelected()) {
                for (TrelloDAO.Invitation i : TrelloDAO.getPendingInvitations(user.getId())) items.add(new Item(i, i.getCreatedAt(), "IN_INVITE"));
                for (TrelloDAO.Notification n : TrelloDAO.getUnreadNotifications(user.getId())) items.add(new Item(n, n.getCreatedAt(), "IN_NOTIF"));
            } else if (outgoing.isSelected()) {
                for (TrelloDAO.Invitation i : TrelloDAO.getOutgoingInvitations(user.getId())) items.add(new Item(i, i.getCreatedAt(), "OUT_INVITE"));
            } else {
                for (TrelloDAO.Invitation i : TrelloDAO.getArchivedInvitations(user.getId())) items.add(new Item(i, i.getCreatedAt(), "ARCHIVED_INVITE"));
                for (TrelloDAO.Notification n : TrelloDAO.getReadNotifications(user.getId())) items.add(new Item(n, n.getCreatedAt(), "ARCHIVED_NOTIF"));
            }
            return items;
        }).thenAccept(items -> Platform.runLater(() -> {
            current.clear();
            current.addAll(items);
            render.run();
        }));

        for (ToggleButton t : new ToggleButton[]{incoming, outgoing, archive}) {
            t.setOnAction(e -> load.run());
        }
        newest.setOnAction(e -> render.run());
        oldest.setOnAction(e -> render.run());

        BorderPane root = new BorderPane(scroll);
        root.setTop(new VBox(16, titleRow, tabs));
        root.getStyleClass().add("dialog-root");
        dialog.setScene(Theme.scene(root, 680, 520));
        load.run();
        dialog.show();
    }

    private static HBox noticeRow(String icon, String variant, Node text, Node right) {
        HBox row = new HBox(14, Ui.skill(icon, variant, 38), text, Ui.hgrow(), right);
        row.getStyleClass().addAll("row", "static");
        HBox.setHgrow(text, Priority.SOMETIMES);
        return row;
    }

    private static VBox twoLines(String first, String second) {
        VBox box = new VBox(2, Ui.wrap(first, "semi"), Ui.wrap(second, "muted", "small"));
        box.setMinWidth(0);
        return box;
    }

    private static VBox rightSide(Node top, LocalDateTime when) {
        VBox box = new VBox(6, top, Ui.label(when.format(TIME), "mono"));
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static HBox incomingInviteRow(TrelloDAO.Invitation inv, User user, VBox container) {
        Button accept = Ui.primary("Принять", Icons.CHECK);
        accept.getStyleClass().add("btn-sm");
        Button decline = Ui.ghost("Отклонить", null);
        decline.getStyleClass().add("btn-sm");
        HBox buttons = new HBox(6, decline, accept);
        HBox row = noticeRow(Icons.USER_PLUS, "skill-sky",
                twoLines(inv.getPersonName() + " приглашает вас", "Доска «" + inv.getBoardName() + "»"),
                rightSide(buttons, inv.getCreatedAt()));

        accept.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.acceptInvitation(inv.getId()))
                .thenRun(() -> Platform.runLater(() -> {
                    container.getChildren().remove(row);
                    checkNotificationsAsync(user.getId());
                    refreshBoardsAsync();
                })));
        decline.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.declineInvitation(inv.getId()))
                .thenRun(() -> Platform.runLater(() -> {
                    container.getChildren().remove(row);
                    checkNotificationsAsync(user.getId());
                })));
        return row;
    }

    private static HBox notificationRow(TrelloDAO.Notification notif, User user, VBox container) {
        Button read = Ui.ghost("Прочитано", Icons.CHECK);
        read.getStyleClass().add("btn-sm");
        HBox row = noticeRow(Icons.BELL, "skill-amber", Ui.wrap(notif.getMessage(), "semi"),
                rightSide(read, notif.getCreatedAt()));
        read.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.markNotificationAsRead(notif.getId()))
                .thenRun(() -> Platform.runLater(() -> {
                    container.getChildren().remove(row);
                    checkNotificationsAsync(user.getId());
                })));
        return row;
    }

    private static HBox inviteStatusRow(TrelloDAO.Invitation inv, String prefix, String icon, String variant, boolean faded) {
        String status = inv.getStatus();
        Label chip = switch (status) {
            case "ACCEPTED" -> Ui.label("ПРИНЯТО", "chip", "ok");
            case "DECLINED" -> Ui.label("ОТКЛОНЕНО", "chip", "bad");
            default -> Ui.label("ОЖИДАЕТ", "chip", "warn");
        };
        HBox row = noticeRow(icon, variant,
                twoLines(prefix + inv.getPersonName(), "Доска «" + inv.getBoardName() + "»"),
                rightSide(chip, inv.getCreatedAt()));
        if (faded) row.setOpacity(0.75);
        return row;
    }

    private static HBox archivedNotificationRow(TrelloDAO.Notification notif) {
        HBox row = noticeRow(Icons.BELL, "skill-slate", Ui.wrap(notif.getMessage(), "muted"),
                rightSide(new Region(), notif.getCreatedAt()));
        row.setOpacity(0.75);
        return row;
    }

    // ----------------------------------------------------------- invite

    private static void showInviteDialog(User sender, Board board) {
        Stage dialog = modal("Пригласить участника");

        ToggleButton byName = Ui.seg("Ник", Icons.USER);
        ToggleButton byEmail = Ui.seg("Email", Icons.MAIL);
        ToggleButton byPhone = Ui.seg("Телефон", Icons.PHONE);
        HBox tabs = Ui.segmented(byName, byEmail, byPhone);
        byName.setSelected(true);

        TextField nameField = new TextField();
        nameField.setPromptText("username пользователя");
        TextField emailField = new TextField();
        emailField.setPromptText("example@mail.com");
        TextField phoneField = new TextField();
        setupPhoneNumberFormatting(phoneField);
        StackPane fields = new StackPane(nameField, emailField, phoneField);

        Runnable showField = () -> {
            nameField.setVisible(byName.isSelected());
            emailField.setVisible(byEmail.isSelected());
            phoneField.setVisible(byPhone.isSelected());
            TextField active = byName.isSelected() ? nameField : byEmail.isSelected() ? emailField : phoneField;
            Platform.runLater(active::requestFocus);
        };
        byName.setOnAction(e -> showField.run());
        byEmail.setOnAction(e -> showField.run());
        byPhone.setOnAction(e -> showField.run());
        showField.run();

        Label status = Ui.wrap("", "err");
        Ui.message(status, null, false);

        Button close = Ui.ghost("Закрыть", null);
        close.setOnAction(e -> dialog.close());
        Button send = Ui.primary("Отправить приглашение", Icons.SEND);
        send.setDefaultButton(true);
        HBox buttons = new HBox(10, Ui.hgrow(), close, send);

        send.setOnAction(e -> {
            String type = byName.isSelected() ? "username" : byEmail.isSelected() ? "email" : "phone";
            String v = (type.equals("username") ? nameField : type.equals("email") ? emailField : phoneField).getText().trim();
            if (v.isEmpty() || v.equals("+7")) return;
            Ui.message(status, "Ищем пользователя…", true);
            CompletableFuture.supplyAsync(() -> type.equals("username") ? UserDAO.findUserIdByUsername(v)
                            : type.equals("email") ? UserDAO.findUserIdByEmail(v) : UserDAO.findUserIdByPhone(v))
                    .thenAccept(id -> Platform.runLater(() -> {
                        if (id == -1) {
                            Ui.message(status, "Пользователь не найден.", false);
                        } else if (id == sender.getId()) {
                            Ui.message(status, "Нельзя пригласить самого себя.", false);
                        } else {
                            CompletableFuture.supplyAsync(() -> TrelloDAO.sendInvitation(sender.getId(), id, board.getId()))
                                    .thenAccept(ok -> Platform.runLater(() -> Ui.message(status,
                                            ok ? "Приглашение отправлено." : "Не удалось: пользователь уже участник доски.", ok)));
                        }
                    }));
        });

        VBox root = new VBox(14,
                new VBox(4, Ui.label("Пригласить в «" + board.getTitle() + "»", "h2"),
                        Ui.wrap("Найдём пользователя по нику, email или телефону и отправим приглашение.", "muted")),
                tabs, fields, status, buttons);
        root.getStyleClass().add("dialog-root");
        VBox.setMargin(buttons, new Insets(6, 0, 0, 0));
        dialog.setScene(Theme.scene(root, 480, 360));
        dialog.show();
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
                formatted.append(digits, 1, Math.min(4, digits.length()));
            }
            if (digits.length() >= 4) {
                formatted.append(") ");
                formatted.append(digits, 4, Math.min(7, digits.length()));
            }
            if (digits.length() >= 7) {
                formatted.append("-");
                formatted.append(digits, 7, Math.min(9, digits.length()));
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

    private static Stage modal(String title) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(MenuScene.getStage());
        stage.setTitle(title);
        return stage;
    }
}
