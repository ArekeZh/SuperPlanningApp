package com.example.superplanningapp;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LoginScene {

    public static Scene createLoginScene(Stage stage) {
        BorderPane mainLayout = new BorderPane();

        // === НАСТРОЙКА ЦВЕТОВ ПО ТЕМЕ ===
        // Определяем базовые цвета, используя константы из MenuScene (если они доступны)
        // или жестко заданные цвета, чтобы соответствовать стилю
        boolean isDark = MenuScene.isDarkTheme;

        String bgColor = isDark ? "#121212" : "#f0f2f5";
        String boxColor = isDark ? "#1a1a1a" : "#ffffff"; // Карточка входа
        String textColor = isDark ? "#e4e6eb" : "#1c1e21";
        String subTextColor = isDark ? "#b0b3b8" : "#606770";
        String inputBg = isDark ? "#333333" : "#f5f6f7";
        String inputText = isDark ? "white" : "black";
        String accentColor = "#00a884"; // Зеленый акцент

        // Применяем фон к главному окну
        mainLayout.setStyle("-fx-background-color: " + bgColor + ";");

        VBox loginBox = new VBox(20);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setPadding(new Insets(40));
        loginBox.setMaxWidth(450);

        // Стилизация карточки входа (Тень и закругления)
        String shadow = isDark ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.1)";
        loginBox.setStyle("-fx-background-color: " + boxColor + "; " +
                "-fx-background-radius: 15; " +
                "-fx-effect: dropshadow(gaussian, " + shadow + ", 20, 0, 0, 5);");

        // --- ЗАГОЛОВОК ---
        Label titleLabel = new Label("SuperPlanningApp");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        Label subtitleLabel = new Label("Добро пожаловать!");
        subtitleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: " + subTextColor + ";");

        // --- ПОЛЯ ВВОДА ---
        String fieldStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText + "; " +
                "-fx-background-radius: 8; -fx-border-color: transparent; -fx-font-size: 14px; -fx-padding: 10;";
        // Доп. стиль для placeholder (подсказки) в темной теме сложно задать без CSS файла,
        // но основной текст будет белым.

        TextField loginField = new TextField();
        loginField.setPromptText("Имя пользователя");
        loginField.setPrefHeight(45);
        loginField.setStyle(fieldStyle);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");
        passwordField.setPrefHeight(45);
        passwordField.setStyle(fieldStyle);

        // --- КНОПКИ ---
        Button loginButton = new Button("Войти");
        loginButton.setPrefWidth(350);
        loginButton.setPrefHeight(45);
        loginButton.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;");

        // Эффект нажатия для кнопки
        loginButton.setOnMouseEntered(e -> loginButton.setStyle("-fx-background-color: #008f72; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;"));
        loginButton.setOnMouseExited(e -> loginButton.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;"));

        Button registerButton = new Button("Создать аккаунт");
        registerButton.setPrefWidth(350);
        registerButton.setPrefHeight(45);
        registerButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + accentColor + "; -fx-font-size: 14px; -fx-cursor: hand; -fx-underline: true;");

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setStyle("-fx-text-fill: #f15c6d; -fx-font-size: 13px;"); // Красный цвет ошибок

        // --- ЛОГИКА ---
        loginButton.setOnAction(e -> {
            String loginInput = loginField.getText().trim();
            String password = passwordField.getText();

            if (loginInput.isEmpty() || password.isEmpty()) {
                messageLabel.setText("⚠ Заполните все поля!");
                return;
            }

            User user = UserDAO.loginUser(loginInput, password);

            if (user != null) {
                stage.setScene(MenuScene.createMenuScene(stage, user));
            } else {
                messageLabel.setText("⚠ Неверный логин или пароль!");
            }
        });

        registerButton.setOnAction(e -> {
            stage.setScene(RegisterScene.createRegisterScene(stage));
        });

        loginBox.getChildren().addAll(
                titleLabel, subtitleLabel,
                loginField, passwordField,
                loginButton, registerButton, messageLabel
        );

        StackPane centerPane = new StackPane(loginBox);
        centerPane.setPadding(new Insets(50));
        mainLayout.setCenter(centerPane);

        // Мы больше не подключаем CSS файл, так как стили заданы выше
        Scene scene = new Scene(mainLayout);

        mainLayout.setOnMouseClicked(e -> mainLayout.requestFocus());

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                loginButton.fire();
            }
        });

        return scene;
    }
}