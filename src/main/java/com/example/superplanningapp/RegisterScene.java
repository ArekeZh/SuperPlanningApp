package com.example.superplanningapp;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class RegisterScene {

    public static Scene createRegisterScene(Stage stage) {
        BorderPane mainLayout = new BorderPane();

        // === НАСТРОЙКА ЦВЕТОВ ПО ТЕМЕ ===
        boolean isDark = MenuScene.isDarkTheme;

        String bgColor = isDark ? "#121212" : "#f0f2f5";
        String boxColor = isDark ? "#1a1a1a" : "#ffffff";
        String textColor = isDark ? "#e4e6eb" : "#1c1e21";
        String subTextColor = isDark ? "#b0b3b8" : "#606770";
        String inputBg = isDark ? "#333333" : "#f5f6f7";
        String inputText = isDark ? "white" : "black";
        String accentColor = "#00a884"; // Зеленый

        // Фон окна
        mainLayout.setStyle("-fx-background-color: " + bgColor + ";");

        VBox registerBox = new VBox(15); // Чуть меньше отступы между элементами, так как их много
        registerBox.setAlignment(Pos.CENTER);
        registerBox.setPadding(new Insets(30));
        registerBox.setMaxWidth(450);

        // Стилизация карточки
        String shadow = isDark ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.1)";
        registerBox.setStyle("-fx-background-color: " + boxColor + "; " +
                "-fx-background-radius: 15; " +
                "-fx-effect: dropshadow(gaussian, " + shadow + ", 20, 0, 0, 5);");

        // --- ЗАГОЛОВОК ---
        Label titleLabel = new Label("Создать аккаунт");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        // --- СТИЛЬ ПОЛЕЙ ВВОДА ---
        String fieldStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText + "; " +
                "-fx-background-radius: 8; -fx-border-color: transparent; -fx-font-size: 14px; -fx-padding: 10;";

        // --- ПОЛЯ ---
        TextField usernameField = new TextField();
        usernameField.setPromptText("Имя пользователя");
        usernameField.setStyle(fieldStyle);

        TextField emailField = new TextField();
        emailField.setPromptText("Email (example@mail.com)");
        emailField.setStyle(fieldStyle);

        TextField phoneField = new TextField();
        phoneField.setPromptText("+7 (___) ___-__-__");
        phoneField.setStyle(fieldStyle);
        applyPhoneMask(phoneField);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль (мин. 6 символов)");
        passwordField.setStyle(fieldStyle);

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Повторите пароль");
        confirmPasswordField.setStyle(fieldStyle);

        // --- КНОПКИ ---
        Button registerButton = new Button("Зарегистрироваться");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setPrefHeight(45);
        registerButton.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;");

        // Hover эффект
        registerButton.setOnMouseEntered(e -> registerButton.setStyle("-fx-background-color: #008f72; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;"));
        registerButton.setOnMouseExited(e -> registerButton.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 8; -fx-cursor: hand;"));


        Label loginLink = new Label("Уже есть аккаунт? Войти");
        loginLink.setStyle("-fx-text-fill: " + accentColor + "; -fx-cursor: hand; -fx-font-size: 13px; -fx-underline: true;");
        loginLink.setOnMouseClicked(e -> stage.setScene(LoginScene.createLoginScene(stage)));

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f15c6d;"); // Красный по умолчанию

        // --- ЛОГИКА ---
        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String phone = phoneField.getText().trim();
            String password = passwordField.getText();
            String confirmPass = confirmPasswordField.getText();

            messageLabel.setStyle("-fx-text-fill: #f15c6d; -fx-font-size: 12px;"); // Сброс на красный

            // 1. Валидация полей
            if (username.isEmpty() || email.isEmpty() || phone.length() < 18 || password.isEmpty()) {
                messageLabel.setText("Заполните все поля корректно!");
                return;
            }

            // 2. Валидация Email
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                messageLabel.setText("Неверный формат Email!");
                return;
            }

            // 3. Валидация паролей
            if (password.length() < 6) {
                messageLabel.setText("Пароль слишком короткий (мин. 6)!");
                return;
            }
            if (!password.equals(confirmPass)) {
                messageLabel.setText("Пароли не совпадают!");
                return;
            }

            // 4. Регистрация
            if (UserDAO.registerUser(username, email, phone, password)) {
                messageLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 12px;"); // Зеленый
                messageLabel.setText("Успешно! Входим...");

                User newUser = UserDAO.loginUser(email, password);
                if (newUser != null) {
                    stage.setScene(MenuScene.createMenuScene(stage, newUser));
                }
            } else {
                messageLabel.setText("Пользователь с таким Email или телефоном уже существует.");
            }
        });

        // Создаем маленькие лейблы над полями (опционально, для красоты и ясности)
        Label lUser = new Label("Логин"); lUser.setStyle("-fx-text-fill: " + subTextColor + "; -fx-font-size: 12px;");
        Label lEmail = new Label("Email"); lEmail.setStyle("-fx-text-fill: " + subTextColor + "; -fx-font-size: 12px;");
        Label lPhone = new Label("Телефон"); lPhone.setStyle("-fx-text-fill: " + subTextColor + "; -fx-font-size: 12px;");
        Label lPass = new Label("Пароль"); lPass.setStyle("-fx-text-fill: " + subTextColor + "; -fx-font-size: 12px;");

        // Группируем поле и подпись, чтобы было аккуратно
        VBox userBox = new VBox(2, lUser, usernameField);
        VBox emailBox = new VBox(2, lEmail, emailField);
        VBox phoneBox = new VBox(2, lPhone, phoneField);
        VBox passBox = new VBox(2, lPass, passwordField);
        VBox confBox = new VBox(2, new Label("Подтверждение"){{setStyle("-fx-text-fill: "+subTextColor+"; -fx-font-size: 12px;");}}, confirmPasswordField);

        registerBox.getChildren().addAll(
                titleLabel,
                userBox, emailBox, phoneBox, passBox, confBox,
                messageLabel,
                registerButton, loginLink
        );

        StackPane centerPane = new StackPane(registerBox);
        centerPane.setPadding(new Insets(20)); // Отступ, чтобы на маленьких экранах не прилипало к краям

        // Добавляем ScrollPane на случай маленьких экранов
        ScrollPane scrollPane = new ScrollPane(centerPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        mainLayout.setCenter(scrollPane);

        Scene scene = new Scene(mainLayout);

        mainLayout.setOnMouseClicked(e -> mainLayout.requestFocus());

        return scene;
    }

    // === МАГИЯ МАСКИ ТЕЛЕФОНА ===
    private static void applyPhoneMask(TextField textField) {
        textField.setText("+7 ");

        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.startsWith("+7 ")) {
                textField.setText("+7 ");
                return;
            }

            String digits = newValue.replaceAll("[^\\d]", "");

            if (digits.length() > 11) {
                digits = digits.substring(0, 11);
            }

            StringBuilder formatted = new StringBuilder("+7 ");
            if (digits.length() > 1) {
                formatted.append("(").append(digits.substring(1, Math.min(digits.length(), 4)));
            }
            if (digits.length() >= 5) {
                formatted.append(") ").append(digits.substring(4, Math.min(digits.length(), 7)));
            }
            if (digits.length() >= 8) {
                formatted.append("-").append(digits.substring(7, Math.min(digits.length(), 9)));
            }
            if (digits.length() >= 10) {
                formatted.append("-").append(digits.substring(9, digits.length()));
            }

            if (!formatted.toString().equals(newValue)) {
                textField.setText(formatted.toString());
                textField.positionCaret(formatted.length());
            }
        });
    }
}