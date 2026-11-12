package com.example.superplanningapp;

import javafx.application.Application;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("SuperPlanningApp");

        // Устанавливаем начальный размер (чтобы было что показать)
        stage.setWidth(1400);
        stage.setHeight(800);

        // Минимальные размеры окна
        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        stage.setScene(LoginScene.createLoginScene(stage));

        // Максимизируем ПОСЛЕ установки сцены и ПЕРЕД show()
        stage.setMaximized(true);

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
