package com.example.superplanningapp.trello;

import com.example.superplanningapp.MenuScene;
import com.example.superplanningapp.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TrelloBoardScene {

    public static BorderPane createBoardView(User user, Board board) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: " + MenuScene.getBackgroundStyle() + ";");

        // --- 1. ВЕРХНЯЯ ПАНЕЛЬ ---
        HBox topBar = new HBox();
        topBar.setPadding(new Insets(15, 20, 15, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setSpacing(20);

        String topBarBg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "rgba(255,255,255,0.5)";
        topBar.setStyle("-fx-background-color: " + topBarBg + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 0, 0, 0, 1);");

        Label boardTitle = new Label(board.getTitle());
        boardTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + MenuScene.getTextStyle() + ";");

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        TextField newListField = new TextField();
        newListField.setPromptText("Название списка");
        newListField.setPrefWidth(200);
        if (MenuScene.isDarkTheme) {
            newListField.setStyle("-fx-background-color: " + MenuScene.Theme.HOVER_DARK + "; -fx-text-fill: white;");
        }

        Button addListBtn = new Button("+ Добавить список");
        addListBtn.setStyle("-fx-background-color: " + MenuScene.Theme.ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold;");

        topBar.getChildren().addAll(boardTitle, spacer1, newListField, addListBtn);

        // --- 2. ЦЕНТРАЛЬНАЯ ОБЛАСТЬ ---
        StackPane centerStack = new StackPane();

        HBox listsContainer = new HBox(15);
        listsContainer.setPadding(new Insets(20));
        listsContainer.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollPane = new ScrollPane(listsContainer);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.getStyleClass().add("edge-to-edge");

        ProgressIndicator loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(60, 60);
        loadingSpinner.setVisible(true);

        centerStack.getChildren().addAll(scrollPane, loadingSpinner);

        mainLayout.setTop(topBar);
        mainLayout.setCenter(centerStack);

        // --- ЗАГРУЗКА ДАННЫХ ---
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                Platform.runLater(() -> refreshListsAsync(listsContainer, board.getId(), loadingSpinner));
            } catch (InterruptedException e) { e.printStackTrace(); }
        });

        addListBtn.setOnAction(e -> {
            String title = newListField.getText().trim();
            if (!title.isEmpty()) {
                loadingSpinner.setVisible(true);
                CompletableFuture.runAsync(() -> {
                    TrelloDAO.createList(title, board.getId());
                }).thenRun(() -> Platform.runLater(() -> {
                    newListField.clear();
                    refreshListsAsync(listsContainer, board.getId(), loadingSpinner);
                })).exceptionally(ex -> {
                    Platform.runLater(() -> showError("Ошибка", ex.getMessage()));
                    return null;
                });
            }
        });

        return mainLayout;
    }

    private static void refreshListsAsync(HBox container, int boardId, ProgressIndicator spinner) {
        spinner.setVisible(true);

        CompletableFuture.supplyAsync(() -> TrelloDAO.getListsByBoard(boardId))
                .thenAccept(lists -> {
                    Platform.runLater(() -> {
                        container.getChildren().clear();
                        if (lists.isEmpty()) {
                            spinner.setVisible(false);
                            Label emptyLabel = new Label("Нет списков. Создайте первый!");
                            emptyLabel.setStyle("-fx-text-fill: " + MenuScene.getTextStyle() + "; -fx-font-size: 16px;");
                            container.getChildren().add(emptyLabel);
                        } else {
                            for (TrelloList list : lists) {
                                VBox listColumn = createListColumn(list, container, boardId, spinner);
                                container.getChildren().add(listColumn);
                            }
                            spinner.setVisible(false);
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        spinner.setVisible(false);
                        showError("Ошибка загрузки", ex.getMessage());
                    });
                    return null;
                });
    }

    private static VBox createListColumn(TrelloList list, HBox mainContainer, int boardId, ProgressIndicator mainSpinner) {
        VBox column = new VBox(10);
        column.setPrefWidth(300);

        column.setStyle("-fx-background-color: " + getListColor(list.getColor()) + "; -fx-background-radius: 8; -fx-padding: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1);");

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label listTitle = new Label(list.getTitle());

        String headerTextColor = MenuScene.isDarkTheme ? "#e4e6eb" : "#000000";
        listTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + headerTextColor + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        MenuButton menuBtn = new MenuButton("⋮");
        menuBtn.setStyle("-fx-background-color: transparent; -fx-mark-color: " + headerTextColor + "; -fx-font-size: 14px; -fx-cursor: hand;");

        MenuItem editItem = new MenuItem("Изменить название");
        editItem.setOnAction(e -> showEditListDialog(list, mainContainer, boardId, mainSpinner));

        MenuItem colorItem = new MenuItem("Изменить цвет");
        colorItem.setOnAction(e -> showColorDialog(list, mainContainer, boardId, mainSpinner));

        MenuItem deleteItem = new MenuItem("Удалить список");
        deleteItem.setOnAction(e -> {
            mainSpinner.setVisible(true);
            CompletableFuture.runAsync(() -> TrelloDAO.deleteList(list.getId()))
                    .thenRun(() -> Platform.runLater(() -> refreshListsAsync(mainContainer, boardId, mainSpinner)));
        });

        menuBtn.getItems().addAll(editItem, colorItem, deleteItem);
        header.getChildren().addAll(listTitle, spacer, menuBtn);

        // Cards Container
        VBox cardsContainer = new VBox(8);
        cardsContainer.setStyle("-fx-background-color: transparent;");
        ScrollPane cardsScroll = new ScrollPane(cardsContainer);
        cardsScroll.setFitToWidth(true);
        cardsScroll.setPrefHeight(500);
        cardsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        cardsScroll.getStyleClass().add("edge-to-edge");

        loadCardsForColumn(cardsContainer, list.getId(), mainContainer, boardId, mainSpinner);

        // Add Card Area
        VBox addCardSection = new VBox(5);
        TextField cardTitleField = new TextField();
        cardTitleField.setPromptText("Название задачи");
        TextArea cardDescField = new TextArea();
        cardDescField.setPromptText("Описание");
        cardDescField.setPrefRowCount(2);

        Button addCardBtn = new Button("Добавить");
        addCardBtn.setStyle("-fx-background-color: " + MenuScene.Theme.ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        if(MenuScene.isDarkTheme) {
            String fieldStyle = "-fx-background-color: " + MenuScene.Theme.HOVER_DARK + "; -fx-text-fill: white; -fx-prompt-text-fill: #888;";
            cardTitleField.setStyle(fieldStyle);
            cardDescField.setStyle("text-area-background: " + MenuScene.Theme.HOVER_DARK + "; -fx-text-fill: white; -fx-control-inner-background: " + MenuScene.Theme.HOVER_DARK + "; -fx-prompt-text-fill: #888;");
        }

        addCardBtn.setOnAction(e -> {
            String title = cardTitleField.getText().trim();
            if (!title.isEmpty()) {
                String desc = cardDescField.getText().trim();
                CompletableFuture.runAsync(() -> TrelloDAO.createCard(title, desc, list.getId()))
                        .thenRun(() -> Platform.runLater(() -> {
                            cardTitleField.clear();
                            cardDescField.clear();
                            loadCardsForColumn(cardsContainer, list.getId(), mainContainer, boardId, mainSpinner);
                        }));
            }
        });

        addCardSection.getChildren().addAll(cardTitleField, cardDescField, addCardBtn);
        column.getChildren().addAll(header, cardsScroll, addCardSection);

        setupDragAndDrop(cardsContainer, list.getId(), mainContainer, boardId, mainSpinner);

        return column;
    }

    private static void loadCardsForColumn(VBox container, int listId, HBox mainContainer, int boardId, ProgressIndicator spinner) {
        CompletableFuture.supplyAsync(() -> TrelloDAO.getCardsByList(listId))
                .thenAccept(cards -> Platform.runLater(() -> {
                    container.getChildren().clear();
                    for (Card card : cards) {
                        container.getChildren().add(createCardBox(card, listId, boardId, mainContainer, spinner));
                    }
                }));
    }

    private static VBox createCardBox(Card card, int listId, int boardId, HBox mainContainer, ProgressIndicator spinner) {
        VBox cardBox = new VBox(5);

        String cardBg = MenuScene.isDarkTheme ? "#232323" : "white";
        String textColor = MenuScene.getTextStyle();
        // Сделал еще светлее для контраста (#aaaaaa)
        String descColor = MenuScene.isDarkTheme ? "#aaaaaa" : "#666666";

        cardBox.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 5; -fx-padding: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");
        cardBox.setUserData(card);

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(card.getTitle());
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(220);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button deleteBtn = new Button("✕");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + MenuScene.Theme.ERROR + "; -fx-cursor: hand;");

        deleteBtn.setOnAction(e -> {
            CompletableFuture.runAsync(() -> TrelloDAO.deleteCard(card.getId()))
                    .thenRun(() -> Platform.runLater(() -> refreshListsAsync(mainContainer, boardId, spinner)));
        });

        titleRow.getChildren().addAll(titleLabel, spacer, deleteBtn);

        // --- ОПИСАНИЕ КАРТОЧКИ ---
        Label descLabel = new Label(card.getDescription());
        descLabel.setStyle("-fx-text-fill: " + descColor + "; -fx-font-size: 12px;");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(260);

        cardBox.getChildren().add(titleRow);

        // Добавляем описание только если оно есть
        if (card.getDescription() != null && !card.getDescription().trim().isEmpty()) {
            cardBox.getChildren().add(descLabel);
        }

        cardBox.setOnMouseEntered(e -> cardBox.setStyle("-fx-background-color: " + (MenuScene.isDarkTheme ? "#2c2c2c" : "#f9f9f9") + "; -fx-background-radius: 5; -fx-padding: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1); -fx-cursor: hand;"));
        cardBox.setOnMouseExited(e -> cardBox.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 5; -fx-padding: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);"));

        cardBox.setOnDragDetected(event -> {
            Dragboard db = cardBox.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(card.getId()));
            db.setContent(content);
            event.consume();
        });

        return cardBox;
    }

    private static void setupDragAndDrop(VBox cardsContainer, int listId, HBox mainContainer, int boardId, ProgressIndicator spinner) {
        cardsContainer.setOnDragOver(event -> {
            if (event.getGestureSource() != cardsContainer && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        cardsContainer.setOnDragDropped(event -> {
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

    private static String getListColor(String color) {
        if (color == null || color.equals("null")) return MenuScene.isDarkTheme ? "#1a1a1a" : "#f0f2f5";

        switch (color) {
            case "red":
                return MenuScene.isDarkTheme ? "#4a1a1a" : "#ffe0e0";
            case "yellow":
                return MenuScene.isDarkTheme ? "#4a4a1a" : "#fffacc";
            case "green":
                return MenuScene.isDarkTheme ? "#1a3b1a" : "#e6fbd9";
            default:
                return MenuScene.isDarkTheme ? "#1a1a1a" : "#f0f2f5";
        }
    }

    private static void styleDialog(Dialog<?> dialog) {
        if (MenuScene.isDarkTheme) {
            DialogPane pane = dialog.getDialogPane();
            pane.setStyle("-fx-background-color: " + MenuScene.Theme.HOVER_DARK + ";");
            pane.lookup(".content.label").setStyle("-fx-text-fill: white;");
            pane.lookup(".header-panel").setStyle("-fx-background-color: " + MenuScene.Theme.SIDEBAR_DARK + "; -fx-text-fill: white;");
        }
    }

    private static void showEditListDialog(TrelloList list, HBox container, int boardId, ProgressIndicator spinner) {
        TextInputDialog dialog = new TextInputDialog(list.getTitle());
        dialog.setTitle("Редактировать");
        dialog.setHeaderText("Название списка");
        dialog.initOwner(MenuScene.getStage());
        styleDialog(dialog);

        if(MenuScene.isDarkTheme) {
            dialog.getEditor().setStyle("-fx-background-color: #333; -fx-text-fill: white;");
        }

        dialog.showAndWait().ifPresent(name -> {
            spinner.setVisible(true);
            CompletableFuture.runAsync(() -> TrelloDAO.updateList(list.getId(), name, list.getColor()))
                    .thenRun(() -> Platform.runLater(() -> refreshListsAsync(container, boardId, spinner)));
        });
    }

    // === ИСПРАВЛЕННЫЙ МЕТОД: ВЫБОР ЦВЕТА ===
    private static void showColorDialog(TrelloList list, HBox container, int boardId, ProgressIndicator spinner) {
        TrelloList freshList = TrelloDAO.getListById(list.getId());
        if (freshList != null) list = freshList;

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Выбрать цвет");
        dialog.initOwner(MenuScene.getStage());
        styleDialog(dialog);

        VBox colorBox = new VBox(10);
        if (MenuScene.isDarkTheme) {
            colorBox.setStyle("-fx-background-color: " + MenuScene.Theme.HOVER_DARK + "; -fx-padding: 10;");
        } else {
            colorBox.setStyle("-fx-padding: 10;");
        }

        ToggleGroup group = new ToggleGroup();

        RadioButton redBtn = new RadioButton("Красный");
        redBtn.setUserData("red");
        redBtn.setToggleGroup(group);

        RadioButton yellowBtn = new RadioButton("Желтый");
        yellowBtn.setUserData("yellow");
        yellowBtn.setToggleGroup(group);

        RadioButton greenBtn = new RadioButton("Зеленый");
        greenBtn.setUserData("green");
        greenBtn.setToggleGroup(group);

        // ВАЖНО: Используем строку "NONE" вместо null, чтобы конвертер не ломался
        RadioButton noneBtn = new RadioButton("Без цвета");
        noneBtn.setUserData("NONE");
        noneBtn.setToggleGroup(group);

        if (MenuScene.isDarkTheme) {
            String radioStyle = "-fx-text-fill: white;";
            redBtn.setStyle(radioStyle);
            yellowBtn.setStyle(radioStyle);
            greenBtn.setStyle(radioStyle);
            noneBtn.setStyle(radioStyle);
        }

        String c = list.getColor();
        if ("red".equals(c)) group.selectToggle(redBtn);
        else if ("yellow".equals(c)) group.selectToggle(yellowBtn);
        else if ("green".equals(c)) group.selectToggle(greenBtn);
        else group.selectToggle(noneBtn);

        colorBox.getChildren().addAll(redBtn, yellowBtn, greenBtn, noneBtn);
        dialog.getDialogPane().setContent(colorBox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(b -> {
            if (b == ButtonType.OK) {
                Toggle selected = group.getSelectedToggle();
                // Если ничего не выбрано, возвращаем null (отмена)
                // Если выбрано, возвращаем UserData (строку)
                return selected != null ? (String) selected.getUserData() : null;
            }
            return null;
        });

        TrelloList finalL = list;
        dialog.showAndWait().ifPresent(col -> {
            System.out.println("Выбран цвет: " + col); // ДЕБАГ

            // Если пришло "NONE", значит превращаем это в null для базы данных
            String colorToSave = "NONE".equals(col) ? null : col;

            spinner.setVisible(true);
            CompletableFuture.runAsync(() -> TrelloDAO.updateList(finalL.getId(), finalL.getTitle(), colorToSave))
                    .thenRun(() -> Platform.runLater(() -> refreshListsAsync(container, boardId, spinner)));
        });
    }

    private static void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.initOwner(MenuScene.getStage());
        styleDialog(alert);
        alert.showAndWait();
    }
}