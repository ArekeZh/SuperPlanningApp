package com.example.superplanningapp.trello;

import com.example.superplanningapp.MenuScene;
import com.example.superplanningapp.User;
import com.example.superplanningapp.ui.Icons;
import com.example.superplanningapp.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.concurrent.CompletableFuture;

/** Доска: колонки-списки с карточками задач, перетаскивание между колонками. */
public class TrelloBoardScene {

    private static final String[][] COLORS = {
            {"NONE", "Без цвета"}, {"red", "Красный"}, {"yellow", "Жёлтый"}, {"green", "Зелёный"}
    };

    public static Node createBoardView(User user, Board board) {
        Button back = Ui.ghost("Все доски", Icons.ARROW_LEFT);
        back.getStyleClass().add("btn-sm");
        back.setOnAction(e -> MenuScene.openBoards());

        TextField newListField = new TextField();
        newListField.setPromptText("Название нового списка");
        newListField.setPrefWidth(240);
        Button addListBtn = Ui.primary("Добавить список", Icons.PLUS);
        newListField.setOnAction(e -> addListBtn.fire());

        String intro = board.getDescription() != null && !board.getDescription().isBlank()
                ? board.getDescription()
                : "Перетаскивайте карточки между списками. Цвет и название списка меняются в его меню.";
        HBox head = Ui.headerRow(Ui.header("Доска", board.getTitle(), intro), newListField, addListBtn);

        HBox listsContainer = new HBox(16);
        listsContainer.setAlignment(Pos.TOP_LEFT);
        listsContainer.setPadding(new Insets(4, 4, 14, 4));

        ScrollPane scrollPane = new ScrollPane(listsContainer);
        scrollPane.setFitToHeight(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        ProgressIndicator loadingSpinner = new ProgressIndicator();
        Ui.fix(loadingSpinner, 40);

        StackPane centre = new StackPane(scrollPane, loadingSpinner);
        VBox.setVgrow(centre, Priority.ALWAYS);

        VBox root = new VBox(20, back, head, centre);
        root.setPadding(new Insets(28, 52, 28, 52));
        Ui.rise(back, 0);
        Ui.rise(head, 1);
        Ui.rise(centre, 2);

        refreshListsAsync(listsContainer, board.getId(), loadingSpinner);

        addListBtn.setOnAction(e -> {
            String title = newListField.getText().trim();
            if (title.isEmpty()) {
                newListField.requestFocus();
                return;
            }
            loadingSpinner.setVisible(true);
            CompletableFuture.runAsync(() -> TrelloDAO.createList(title, board.getId()))
                    .thenRun(() -> Platform.runLater(() -> {
                        newListField.clear();
                        refreshListsAsync(listsContainer, board.getId(), loadingSpinner);
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> Ui.error("Не удалось создать список", ex.getMessage(), MenuScene.getStage()));
                        return null;
                    });
        });

        return root;
    }

    private static void refreshListsAsync(HBox container, int boardId, ProgressIndicator spinner) {
        spinner.setVisible(true);
        CompletableFuture.supplyAsync(() -> TrelloDAO.getListsByBoard(boardId))
                .thenAccept(lists -> Platform.runLater(() -> {
                    container.getChildren().clear();
                    spinner.setVisible(false);
                    if (lists.isEmpty()) {
                        VBox empty = new VBox(10, Ui.skill(Icons.KANBAN, "skill-sky", 48),
                                Ui.label("Списков пока нет", "h3"),
                                Ui.wrap("Начните с трёх: «Сделать», «В работе», «Готово». Название — в поле справа сверху.", "muted"));
                        empty.getStyleClass().add("platform");
                        empty.setPrefWidth(420);
                        empty.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                        container.getChildren().add(empty);
                        return;
                    }
                    for (TrelloList list : lists) {
                        container.getChildren().add(createListColumn(list, container, boardId, spinner));
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        spinner.setVisible(false);
                        Ui.error("Ошибка загрузки", ex.getMessage(), MenuScene.getStage());
                    });
                    return null;
                });
    }

    private static String colorClass(String color) {
        return "red".equals(color) || "yellow".equals(color) || "green".equals(color) ? color : null;
    }

    private static VBox createListColumn(TrelloList list, HBox mainContainer, int boardId, ProgressIndicator mainSpinner) {
        VBox column = new VBox();
        column.getStyleClass().add("list-col");
        Ui.addClasses(column, colorClass(list.getColor()));
        column.setMinWidth(300);
        column.setPrefWidth(300);
        column.setMaxWidth(300);
        column.setMaxHeight(Double.MAX_VALUE);

        // Заголовок колонки
        Label count = Ui.label("0", "chip");
        MenuButton menuBtn = new MenuButton();
        menuBtn.getStyleClass().add("plain");
        menuBtn.setGraphic(Icons.of(Icons.MORE, 18));

        MenuItem editItem = new MenuItem("Переименовать");
        editItem.setGraphic(Icons.of(Icons.PENCIL, 14));
        editItem.setOnAction(e -> showEditListDialog(list, mainContainer, boardId, mainSpinner));
        MenuItem colorItem = new MenuItem("Изменить цвет");
        colorItem.setGraphic(Icons.of(Icons.SPARKLES, 14));
        colorItem.setOnAction(e -> showColorDialog(list, mainContainer, boardId, mainSpinner));
        MenuItem deleteItem = new MenuItem("Удалить список");
        deleteItem.setGraphic(Icons.of(Icons.TRASH, 14));
        deleteItem.getStyleClass().add("danger");
        deleteItem.setOnAction(e -> {
            if (!Ui.confirm("Удалить список «" + list.getTitle() + "»?", "Все карточки в нём тоже удалятся.",
                    "Удалить", true, MenuScene.getStage())) return;
            mainSpinner.setVisible(true);
            CompletableFuture.runAsync(() -> TrelloDAO.deleteList(list.getId()))
                    .thenRun(() -> Platform.runLater(() -> refreshListsAsync(mainContainer, boardId, mainSpinner)));
        });
        menuBtn.getItems().addAll(editItem, colorItem, deleteItem);

        Label title = Ui.label(list.getTitle(), "list-title");
        title.setMinWidth(0);
        HBox header = new HBox(9, Ui.dot(10, "list-dot", colorClass(list.getColor())), title, count, Ui.hgrow(), menuBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 0, 4));

        // Карточки
        VBox cardsContainer = new VBox(8);
        cardsContainer.setPadding(new Insets(2, 2, 6, 2));
        ScrollPane cardsScroll = new ScrollPane(cardsContainer);
        cardsScroll.setFitToWidth(true);
        cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        loadCardsForColumn(cardsContainer, list.getId(), mainContainer, boardId, mainSpinner, count);

        // Добавление карточки: кнопка раскрывается в форму
        Button addToggle = Ui.ghost("Добавить задачу", Icons.PLUS);
        addToggle.getStyleClass().add("btn-sm");
        addToggle.setMaxWidth(Double.MAX_VALUE);

        TextField cardTitleField = new TextField();
        cardTitleField.setPromptText("Название задачи");
        TextArea cardDescField = new TextArea();
        cardDescField.setPromptText("Описание (необязательно)");
        cardDescField.setPrefRowCount(2);
        cardDescField.setWrapText(true);
        Button addCardBtn = Ui.primary("Добавить", null);
        addCardBtn.getStyleClass().add("btn-sm");
        Button cancelBtn = Ui.iconButton(Icons.X, "Отмена");
        HBox formButtons = new HBox(6, addCardBtn, cancelBtn);
        formButtons.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(8, cardTitleField, cardDescField, formButtons);

        VBox addSection = new VBox(addToggle);
        addToggle.setOnAction(e -> {
            addSection.getChildren().setAll(form);
            cardTitleField.requestFocus();
        });
        cancelBtn.setOnAction(e -> {
            cardTitleField.clear();
            cardDescField.clear();
            addSection.getChildren().setAll(addToggle);
        });
        Runnable addCard = () -> {
            String t = cardTitleField.getText().trim();
            if (t.isEmpty()) return;
            String d = cardDescField.getText().trim();
            CompletableFuture.runAsync(() -> TrelloDAO.createCard(t, d, list.getId()))
                    .thenRun(() -> Platform.runLater(() -> {
                        cardTitleField.clear();
                        cardDescField.clear();
                        cardTitleField.requestFocus();
                        loadCardsForColumn(cardsContainer, list.getId(), mainContainer, boardId, mainSpinner, count);
                    }));
        };
        addCardBtn.setOnAction(e -> addCard.run());
        cardTitleField.setOnAction(e -> addCard.run());

        column.setSpacing(12);
        column.getChildren().addAll(header, cardsScroll, addSection);
        setupDragAndDrop(column, list.getId(), mainContainer, boardId, mainSpinner);
        return column;
    }

    private static void loadCardsForColumn(VBox container, int listId, HBox mainContainer, int boardId,
                                           ProgressIndicator spinner, Label count) {
        CompletableFuture.supplyAsync(() -> TrelloDAO.getCardsByList(listId))
                .thenAccept(cards -> Platform.runLater(() -> {
                    container.getChildren().clear();
                    count.setText(String.valueOf(cards.size()));
                    for (Card card : cards) {
                        container.getChildren().add(createCardBox(card, boardId, mainContainer, spinner));
                    }
                }));
    }

    private static VBox createCardBox(Card card, int boardId, HBox mainContainer, ProgressIndicator spinner) {
        Label titleLabel = Ui.wrap(card.getTitle(), "task-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Button deleteBtn = Ui.iconButton(Icons.X, "Удалить задачу", "danger");
        deleteBtn.setGraphic(Icons.of(Icons.X, 14));
        deleteBtn.setStyle("-fx-padding: 3; -fx-background-radius: 8;"); // компактно, чтобы не раздувать строку

        HBox titleRow = new HBox(6, titleLabel, deleteBtn);
        titleRow.setAlignment(Pos.TOP_LEFT);

        VBox cardBox = new VBox(titleRow);
        cardBox.getStyleClass().add("task-card");
        cardBox.setUserData(card);
        if (card.getDescription() != null && !card.getDescription().isBlank()) {
            cardBox.getChildren().add(Ui.wrap(card.getDescription(), "task-desc"));
        }

        // Крестик виден только при наведении
        deleteBtn.visibleProperty().bind(cardBox.hoverProperty());
        deleteBtn.setOnAction(e -> CompletableFuture.runAsync(() -> TrelloDAO.deleteCard(card.getId()))
                .thenRun(() -> Platform.runLater(() -> refreshListsAsync(mainContainer, boardId, spinner))));

        cardBox.setOnDragDetected(event -> {
            Dragboard db = cardBox.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(card.getId()));
            db.setContent(content);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            db.setDragView(cardBox.snapshot(params, null));
            event.consume();
        });

        Ui.lift(cardBox);
        return cardBox;
    }

    private static void setupDragAndDrop(VBox column, int listId, HBox mainContainer, int boardId, ProgressIndicator spinner) {
        column.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        column.setOnDragEntered(event -> {
            if (event.getDragboard().hasString() && !column.getStyleClass().contains("drop")) {
                column.getStyleClass().add("drop");
            }
        });
        column.setOnDragExited(event -> column.getStyleClass().removeAll("drop"));
        column.setOnDragDropped(event -> {
            column.getStyleClass().removeAll("drop");
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                int cardId = Integer.parseInt(db.getString());
                CompletableFuture.runAsync(() -> TrelloDAO.moveCard(cardId, listId))
                        .thenRun(() -> Platform.runLater(() -> refreshListsAsync(mainContainer, boardId, spinner)));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private static void showEditListDialog(TrelloList list, HBox container, int boardId, ProgressIndicator spinner) {
        Ui.prompt("Переименовать список", null, "Название", list.getTitle(), "Название списка", "Сохранить",
                        MenuScene.getStage())
                .ifPresent(name -> {
                    spinner.setVisible(true);
                    CompletableFuture.runAsync(() -> TrelloDAO.updateList(list.getId(), name, list.getColor()))
                            .thenRun(() -> Platform.runLater(() -> refreshListsAsync(container, boardId, spinner)));
                });
    }

    private static void showColorDialog(TrelloList original, HBox container, int boardId, ProgressIndicator spinner) {
        TrelloList fresh = TrelloDAO.getListById(original.getId());
        TrelloList list = fresh != null ? fresh : original;

        ToggleButton[] swatches = new ToggleButton[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            ToggleButton b = new ToggleButton(COLORS[i][1]);
            b.getStyleClass().setAll("toggle-button", "swatch");
            b.setUserData(COLORS[i][0]);
            b.setGraphic(Ui.dot(12, "list-dot", COLORS[i][0].equals("NONE") ? "none" : COLORS[i][0]));
            swatches[i] = b;
        }
        Ui.segmented(swatches); // объединяем в одну группу (контейнер не нужен)
        String current = colorClass(list.getColor());
        for (ToggleButton b : swatches) {
            if ((current == null && "NONE".equals(b.getUserData())) || b.getUserData().equals(current)) b.setSelected(true);
        }

        FlowPane box = new FlowPane(8, 8, swatches);
        VBox content = new VBox(14, Ui.wrap("Цвет помогает отличать колонки, например «срочно» — красным.", "muted"), box);

        if (!Ui.form("Цвет списка «" + list.getTitle() + "»", "Применить", content, null, null, MenuScene.getStage())) return;

        ToggleButton selected = null;
        for (ToggleButton b : swatches) if (b.isSelected()) selected = b;
        if (selected == null) return;
        String chosen = (String) selected.getUserData();
        String colorToSave = "NONE".equals(chosen) ? null : chosen;

        spinner.setVisible(true);
        CompletableFuture.runAsync(() -> TrelloDAO.updateList(list.getId(), list.getTitle(), colorToSave))
                .thenRun(() -> Platform.runLater(() -> refreshListsAsync(container, boardId, spinner)));
    }
}
