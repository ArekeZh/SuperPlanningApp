package com.example.superplanningapp;

import com.example.superplanningapp.ui.Icons;
import com.example.superplanningapp.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;

/** AI-помощник: переписка «пузырями», быстрые подсказки, поле ввода-пилюля. */
public class ChatScene {

    private static final String[] SUGGESTIONS = {
            "Спланируй продуктивное утро",
            "Распредели 5 задач на день",
            "Как не прокрастинировать?",
            "Расписание учёбы на неделю"
    };

    public static Node createChatView(User user) {
        VBox header = Ui.header("AI-ассистент", "Планируем день",
                "Gemini поможет составить распорядок, расставить приоритеты и не забыть о главном. "
                        + "Отвечает только на вопросы о планировании.");

        VBox messages = new VBox(14);
        messages.setPadding(new Insets(4, 6, 4, 2));
        ScrollPane scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));

        String name = user.getFirstName() != null && !user.getFirstName().isBlank()
                ? user.getFirstName().trim() : user.getUsername();
        messages.getChildren().add(bubble("Привет, " + name
                + "! Расскажите, что нужно успеть сегодня, — составлю план по часам.", false, scroll, null));

        TextField input = new TextField();
        input.setPromptText("Например: как лучше спланировать утро?");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button();
        send.getStyleClass().setAll("button", "send-btn");
        send.setGraphic(Icons.of(Icons.SEND, 17));
        send.setTooltip(Ui.tip("Отправить (Enter)"));
        Ui.fix(send, 42);
        send.disableProperty().bind(input.textProperty().isEmpty());

        HBox composer = new HBox(input, send);
        composer.getStyleClass().add("composer");

        FlowPane chips = new FlowPane(8, 8);
        for (String s : SUGGESTIONS) {
            Button chip = Ui.ghost(s, null);
            chip.getStyleClass().add("btn-sm");
            chip.setOnAction(e -> {
                input.setText(s);
                send.fire();
            });
            chips.getChildren().add(chip);
        }

        VBox card = new VBox(16, scroll, chips, composer);
        card.getStyleClass().addAll("platform", "elev");
        VBox.setVgrow(card, Priority.ALWAYS);

        Runnable ask = () -> {
            String question = input.getText().trim();
            if (question.isEmpty()) return;

            messages.getChildren().add(bubble(question, true, scroll, null));
            input.clear();
            input.setDisable(true);
            chips.setDisable(true);
            HBox typing = bubble("AI думает…", false, scroll, "bubble-typing");
            messages.getChildren().add(typing);

            CompletableFuture<String> answer;
            try {
                answer = AIService.askAI(question);
            } catch (Throwable t) {
                // Например, нет GOOGLE_API_KEY в .env — AIService не инициализируется.
                answer = CompletableFuture.completedFuture(
                        "Ошибка: AI недоступен. Проверьте GOOGLE_API_KEY в файле .env.");
            }
            answer.thenAccept(text -> Platform.runLater(() -> {
                boolean failed = text == null || text.startsWith("Ошибка");
                int i = messages.getChildren().indexOf(typing);
                HBox reply = bubble(text == null ? "Ошибка: пустой ответ." : text.trim(), false, scroll,
                        failed ? "bubble-error" : null);
                if (i >= 0) messages.getChildren().set(i, reply); else messages.getChildren().add(reply);
                input.setDisable(false);
                chips.setDisable(false);
                input.requestFocus();
            }));
        };
        send.setOnAction(e -> ask.run());
        input.setOnAction(e -> ask.run());

        VBox root = new VBox(26, header, card);
        root.setPadding(new Insets(40, 52, 36, 52));
        root.setMaxWidth(1000);
        Ui.rise(header, 0);
        Ui.rise(card, 1);

        StackPane wrap = new StackPane(root);
        StackPane.setAlignment(root, Pos.TOP_LEFT);
        Platform.runLater(input::requestFocus);
        return wrap;
    }

    private static HBox bubble(String text, boolean fromUser, ScrollPane scroll, String extra) {
        Label label = Ui.wrap(text, "bubble", fromUser ? "bubble-user" : "bubble-ai", extra);
        label.maxWidthProperty().bind(scroll.widthProperty().multiply(0.72));

        MenuItem copy = new MenuItem("Копировать");
        copy.setGraphic(Icons.of(Icons.COPY, 14));
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(label.getText());
            Clipboard.getSystemClipboard().setContent(c);
        });
        label.setContextMenu(new ContextMenu(copy));

        HBox row;
        if (fromUser) {
            row = new HBox(Ui.hgrow(), label);
            row.setAlignment(Pos.BOTTOM_RIGHT);
        } else {
            row = new HBox(10, Ui.skill(Icons.SPARKLES, "skill-purple", 32), label);
            row.setAlignment(Pos.BOTTOM_LEFT);
        }
        Ui.rise(row, 0);
        return row;
    }
}
