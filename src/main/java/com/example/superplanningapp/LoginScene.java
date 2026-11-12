package com.example.superplanningapp;

import com.example.superplanningapp.ui.Theme;
import com.example.superplanningapp.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

public class LoginScene {

    public static Scene createLoginScene(Stage stage) {
        Label title = Ui.label("С возвращением", "h2");
        Label lead = Ui.wrap("Войдите, чтобы продолжить: доски, привычки и AI-помощник в одном месте.", "lead");
        VBox heading = new VBox(6, title, lead);

        TextField loginField = new TextField();
        loginField.setPromptText("username, email или телефон");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Ваш пароль");

        Label message = Ui.wrap("", "err");
        Ui.message(message, null, false);

        Button loginButton = Ui.primary("Войти", null);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setDefaultButton(true); // Enter в любом поле

        Button toRegister = Ui.link("Создать аккаунт");
        HBox swap = new HBox(6, Ui.label("Нет аккаунта?", "muted"), toRegister);
        swap.setAlignment(Pos.CENTER);

        VBox card = Ui.authCard(
                heading,
                Ui.field("Логин", loginField, null),
                Ui.field("Пароль", passwordField, null),
                message,
                loginButton,
                swap);
        VBox.setMargin(heading, new javafx.geometry.Insets(0, 0, 8, 0));
        VBox.setMargin(loginButton, new javafx.geometry.Insets(6, 0, 0, 0));

        loginButton.setOnAction(e -> {
            String loginInput = loginField.getText().trim();
            String password = passwordField.getText();
            if (loginInput.isEmpty() || password.isEmpty()) {
                Ui.message(message, "Заполните оба поля.", false);
                return;
            }
            Ui.message(message, null, false);
            loginButton.setDisable(true);
            loginButton.setText("Входим…");

            // Запрос к базе — в фоне, чтобы окно не «замерзало».
            CompletableFuture.supplyAsync(() -> UserDAO.loginUser(loginInput, password))
                    .whenComplete((user, err) -> Platform.runLater(() -> {
                        loginButton.setDisable(false);
                        loginButton.setText("Войти");
                        if (user != null) {
                            stage.setScene(MenuScene.createMenuScene(stage, user));
                        } else {
                            Ui.message(message, "Неверный логин или пароль. Если данные верны — проверьте подключение к базе (подробности в консоли).", false);
                        }
                    }));
        });

        toRegister.setOnAction(e -> stage.setScene(RegisterScene.createRegisterScene(stage)));

        Scene scene = Theme.scene(Ui.authScreen(card));
        Platform.runLater(loginField::requestFocus);
        return scene;
    }
}
