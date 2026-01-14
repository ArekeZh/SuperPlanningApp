package com.example.superplanningapp;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ChatScene {

    public static BorderPane createChatView(User user) {
        BorderPane mainLayout = new BorderPane();
        // === ПРИМЕНЕНИЕ ТЕМЫ ФОНА ===
        mainLayout.setStyle("-fx-background-color: " + MenuScene.getBackgroundStyle() + ";");

        // Цвета в зависимости от темы
        String textColor = MenuScene.getTextStyle();
        String contentBg = MenuScene.isDarkTheme ? "#232323" : "white";
        String inputBg = MenuScene.isDarkTheme ? "#333333" : "white"; // Фон полей ввода
        String inputText = MenuScene.isDarkTheme ? "white" : "black";

        // CSS для TextArea чтобы перекрасить её внутренности
        String textAreaStyle = "-fx-font-size: 14px; -fx-background-color: transparent; " +
                "-fx-control-inner-background: " + inputBg + "; " +
                "-fx-text-fill: " + inputText + ";";

        Label headerLabel = new Label("AI Помощник");
        headerLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + textColor + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1);");
        BorderPane.setAlignment(headerLabel, Pos.CENTER);
        BorderPane.setMargin(headerLabel, new Insets(20, 0, 0, 0));
        mainLayout.setTop(headerLabel);

        TextArea chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle(textAreaStyle);

        // Убираем стандартные рамки TextArea, чтобы смотрелось чище
        chatArea.getStyleClass().add("edge-to-edge");

        chatArea.appendText("🤖 Gemini: Привет, " + user.getDisplayName() + "! Я готов помочь тебе спланировать день. Спрашивай!\n\n");

        TextField inputField = new TextField();
        inputField.setPromptText("Например: Как лучше спланировать утро?");
        inputField.setPrefHeight(45);

        // Стилизация поля ввода
        String inputStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText + "; -fx-background-radius: 5; -fx-border-color: #ccc; -fx-border-radius: 5;";
        if (MenuScene.isDarkTheme) {
            inputStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: white; -fx-prompt-text-fill: #888; -fx-background-radius: 5; -fx-border-color: #555; -fx-border-radius: 5;";
        }
        inputField.setStyle(inputStyle);

        Button sendButton = new Button("Отправить");
        sendButton.setPrefHeight(45);
        sendButton.setPrefWidth(100);
        // Акцентный цвет кнопки
        sendButton.setStyle("-fx-background-color: " + MenuScene.Theme.ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 5;");

        HBox inputBox = new HBox(10, inputField, sendButton);
        inputBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        VBox contentBox = new VBox(15, chatArea, inputBox);
        contentBox.setPadding(new Insets(20));
        contentBox.setMaxWidth(700);
        contentBox.setMaxHeight(500);

        // Стилизация контейнера чата (карточки)
        contentBox.setStyle("-fx-background-color: " + contentBg + "; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 0);");

        VBox.setVgrow(chatArea, Priority.ALWAYS);

        StackPane centerPane = new StackPane(contentBox);
        centerPane.setPadding(new Insets(20));
        mainLayout.setCenter(centerPane);

        sendButton.setOnAction(e -> {
            String question = inputField.getText().trim();
            if (question.isEmpty()) return;

            chatArea.appendText("Вы: " + question + "\n");
            inputField.clear();
            inputField.setDisable(true);
            sendButton.setDisable(true);
            chatArea.appendText("⏳ AI думает...\n");

            AIService.askAI(question).thenAccept(answer -> {
                Platform.runLater(() -> {
                    chatArea.appendText("🤖 Gemini: " + answer + "\n\n");
                    chatArea.setScrollTop(Double.MAX_VALUE);
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                    inputField.requestFocus();
                });
            });
        });

        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) sendButton.fire();
        });

        return mainLayout;
    }
}