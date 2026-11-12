package com.example.superplanningapp;

import com.example.superplanningapp.ui.Theme;
import com.example.superplanningapp.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

public class RegisterScene {

    public static Scene createRegisterScene(Stage stage) {
        Label title = Ui.label("Создать аккаунт", "h2");
        Label lead = Ui.wrap("Пара полей — и можно планировать.", "lead");
        VBox heading = new VBox(6, title, lead);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Например, arlan");

        TextField emailField = new TextField();
        emailField.setPromptText("example@mail.com");

        TextField phoneField = new TextField();
        phoneField.setPromptText("+7 (___) ___-__-__");
        applyPhoneMask(phoneField);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Минимум 6 символов");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Ещё раз");

        VBox passBox = Ui.field("Пароль", passwordField, null);
        VBox confBox = Ui.field("Подтверждение", confirmPasswordField, null);
        HBox passwords = new HBox(12, passBox, confBox);
        HBox.setHgrow(passBox, Priority.ALWAYS);
        HBox.setHgrow(confBox, Priority.ALWAYS);

        Label message = Ui.wrap("", "err");
        Ui.message(message, null, false);

        Button registerButton = Ui.primary("Зарегистрироваться", null);
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setDefaultButton(true);

        Button toLogin = Ui.link("Войти");
        HBox swap = new HBox(6, Ui.label("Уже есть аккаунт?", "muted"), toLogin);
        swap.setAlignment(Pos.CENTER);

        VBox card = Ui.authCard(
                heading,
                Ui.field("Логин", usernameField, null),
                Ui.field("Email", emailField, null),
                Ui.field("Телефон", phoneField, null),
                passwords,
                message,
                registerButton,
                swap);
        VBox.setMargin(heading, new Insets(0, 0, 8, 0));
        VBox.setMargin(registerButton, new Insets(6, 0, 0, 0));

        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String email = emailField.getText().trim();
            String phone = phoneField.getText().trim();
            String password = passwordField.getText();
            String confirmPass = confirmPasswordField.getText();

            // 1. Валидация полей
            if (username.isEmpty() || email.isEmpty() || phone.length() < 18 || password.isEmpty()) {
                Ui.message(message, "Заполните все поля корректно.", false);
                return;
            }
            // 2. Валидация Email
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                Ui.message(message, "Неверный формат email.", false);
                return;
            }
            // 3. Валидация паролей
            if (password.length() < 6) {
                Ui.message(message, "Пароль слишком короткий — нужно минимум 6 символов.", false);
                return;
            }
            if (!password.equals(confirmPass)) {
                Ui.message(message, "Пароли не совпадают.", false);
                return;
            }

            // 4. Регистрация — в фоне
            Ui.message(message, null, false);
            registerButton.setDisable(true);
            registerButton.setText("Создаём аккаунт…");
            CompletableFuture.supplyAsync(() -> UserDAO.registerUser(username, email, phone, password)
                            ? UserDAO.loginUser(email, password) : null)
                    .whenComplete((newUser, err) -> Platform.runLater(() -> {
                        registerButton.setDisable(false);
                        registerButton.setText("Зарегистрироваться");
                        if (newUser != null) {
                            stage.setScene(MenuScene.createMenuScene(stage, newUser));
                        } else {
                            Ui.message(message, "Не удалось создать аккаунт: такой логин, email или телефон уже заняты, либо база недоступна (подробности в консоли).", false);
                        }
                    }));
        });

        toLogin.setOnAction(e -> stage.setScene(LoginScene.createLoginScene(stage)));

        Scene scene = Theme.scene(Ui.authScreen(card));
        Platform.runLater(usernameField::requestFocus);
        return scene;
    }

    // === МАСКА ТЕЛЕФОНА: +7 (XXX) XXX-XX-XX ===
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
                formatted.append("-").append(digits.substring(9));
            }

            if (!formatted.toString().equals(newValue)) {
                textField.setText(formatted.toString());
                textField.positionCaret(formatted.length());
            }
        });
    }
}
