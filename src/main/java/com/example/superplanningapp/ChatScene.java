package com.example.superplanningapp;

import com.example.superplanningapp.ai.ActionResult;
import com.example.superplanningapp.ai.AiException;
import com.example.superplanningapp.ai.BoardPlan;
import com.example.superplanningapp.ai.PlannedAction;
import com.example.superplanningapp.ai.PlanningAgent;
import com.example.superplanningapp.trello.Board;
import com.example.superplanningapp.ui.Icons;
import com.example.superplanningapp.ui.Ui;
import com.example.superplanningapp.ui.Words;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * AI-ассистент. Помимо советов умеет сам создавать доски и добавлять задачи:
 * модель предлагает план, пользователь видит его карточкой в чате и подтверждает.
 */
public class ChatScene {

    private static final String[] SUGGESTIONS = {
            "Создай доску для подготовки к сессии",
            "Разбей курсовую работу на задачи",
            "Добавь задачи в мою доску",
            "Спланируй мой день по часам"
    };

    /** Переписка живёт, пока пользователь не выйдет из аккаунта. */
    private static ChatView session;

    public static Node createChatView(User user) {
        if (session == null || session.user.getId() != user.getId()) {
            if (session != null) session.dispose();
            session = new ChatView(user);
        }
        return session.root;
    }

    /** Вызывается при входе в аккаунт: новая сессия — новая переписка. */
    public static void resetSession() {
        if (session != null) session.dispose();
        session = null;
    }

    private static final class ChatView {
        final User user;
        final StackPane root;
        final VBox messages = new VBox(14);
        final ScrollPane scroll;
        final TextField input = new TextField();
        final HBox typing;
        final Label typingLabel;
        final BooleanProperty busy = new SimpleBooleanProperty(false);
        volatile PlanningAgent agent; // создаётся в фоновом потоке при первом сообщении
        volatile CompletableFuture<Boolean> pendingDecision;

        ChatView(User user) {
            this.user = user;

            Button newChat = Ui.ghost("Новый диалог", Icons.PLUS);
            newChat.disableProperty().bind(busy);
            newChat.setOnAction(e -> restart());
            HBox head = Ui.headerRow(Ui.header("AI-ассистент", "Планируем вместе",
                            "Опишите цель — ассистент уточнит детали, предложит структуру и после вашего "
                                    + "подтверждения сам создаст доску со списками и задачами."),
                    newChat);

            messages.setPadding(new Insets(4, 6, 4, 2));
            scroll = new ScrollPane(messages);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));

            typingLabel = Ui.wrap("", "bubble", "bubble-ai", "bubble-typing");
            typing = new HBox(10, Ui.skill(Icons.SPARKLES, "skill-purple", 32), typingLabel);
            typing.setAlignment(Pos.BOTTOM_LEFT);

            input.setPromptText("Например: подготовка к экзамену по физике за две недели");
            input.disableProperty().bind(busy);
            HBox.setHgrow(input, Priority.ALWAYS);

            Button send = new Button();
            send.getStyleClass().setAll("button", "send-btn");
            send.setGraphic(Icons.of(Icons.SEND, 17));
            send.setTooltip(Ui.tip("Отправить (Enter)"));
            Ui.fix(send, 42);
            send.disableProperty().bind(input.textProperty().isEmpty().or(busy));
            send.setOnAction(e -> ask(input.getText()));
            input.setOnAction(e -> ask(input.getText()));

            HBox composer = new HBox(input, send);
            composer.getStyleClass().add("composer");

            FlowPane chips = new FlowPane(8, 8);
            for (String s : SUGGESTIONS) {
                Button chip = Ui.ghost(s, null);
                chip.getStyleClass().add("btn-sm");
                chip.disableProperty().bind(busy);
                chip.setOnAction(e -> ask(s));
                chips.getChildren().add(chip);
            }

            VBox card = new VBox(16, scroll, chips, composer);
            card.getStyleClass().addAll("platform", "elev");
            VBox.setVgrow(card, Priority.ALWAYS);

            VBox page = new VBox(26, head, card);
            page.setPadding(new Insets(40, 52, 36, 52));
            page.setMaxWidth(1000);
            Ui.rise(head, 0);
            Ui.rise(card, 1);

            root = new StackPane(page);
            StackPane.setAlignment(page, Pos.TOP_LEFT);
            greet();
            Platform.runLater(input::requestFocus);
        }

        void greet() {
            String name = user.getFirstName() != null && !user.getFirstName().isBlank()
                    ? user.getFirstName().trim() : user.getUsername();
            messages.getChildren().setAll(bubble("Привет, " + name + "! Помогу спланировать день, а ещё могу сам "
                    + "собрать доску с задачами. Опишите цель — например, «подготовка к экзамену по физике за две недели».",
                    false, null));
        }

        void restart() {
            if (agent != null) agent.reset();
            greet();
            input.requestFocus();
        }

        void dispose() {
            CompletableFuture<Boolean> pending = pendingDecision;
            if (pending != null) pending.complete(false);
        }

        // ---------------------------------------------------------- dialog

        void ask(String raw) {
            String question = raw == null ? "" : raw.trim();
            if (question.isEmpty() || busy.get()) return;

            messages.getChildren().add(bubble(question, true, null));
            input.clear();
            busy.set(true);
            showTyping("Думаю…");

            CompletableFuture.supplyAsync(() -> {
                try {
                    if (agent == null) agent = AIService.newAgent(user);
                    return agent.send(question, this::confirm);
                } catch (AiException e) {
                    throw new CompletionException(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
            }).whenComplete((reply, err) -> Platform.runLater(() -> {
                hideTyping();
                if (err != null) {
                    Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                    String text = cause instanceof AiException ae ? ae.userMessage()
                            : "Что-то пошло не так: " + cause.getMessage();
                    if (!(cause instanceof AiException)) cause.printStackTrace();
                    messages.getChildren().add(bubble(text, false, "bubble-error"));
                } else {
                    if (!reply.text().isBlank()) {
                        messages.getChildren().add(bubble(Words.plainText(reply.text()), false, null));
                    }
                    for (ActionResult action : reply.actions()) messages.getChildren().add(resultCard(action));
                }
                busy.set(false);
                input.requestFocus();
            }));
        }

        /** Вызывается из фонового потока агента: показывает план и ждёт решения пользователя. */
        boolean confirm(PlannedAction action) throws InterruptedException {
            CompletableFuture<Boolean> decision = new CompletableFuture<>();
            pendingDecision = decision;
            Platform.runLater(() -> {
                hideTyping();
                messages.getChildren().add(planCard(action, decision));
            });
            try {
                boolean ok = decision.get();
                Platform.runLater(() -> showTyping(ok ? "Сохраняю…" : "Думаю…"));
                return ok;
            } catch (ExecutionException e) {
                return false;
            } finally {
                pendingDecision = null;
            }
        }

        // ------------------------------------------------------------ views

        Node planCard(PlannedAction action, CompletableFuture<Boolean> decision) {
            BoardPlan plan = action.plan();
            boolean isNew = action.kind() == PlannedAction.Kind.CREATE_BOARD;

            String headline = isNew ? "Создать доску «" + plan.title() + "»?"
                    : "Добавить задачи в «" + plan.title() + "»?";
            String sub = isNew
                    ? (plan.description() == null || plan.description().isBlank() ? "Новая доска" : plan.description())
                    : "Список «" + plan.lists().get(0).title() + "» — если его нет, он появится";
            VBox headText = new VBox(3, Ui.wrap(headline, "h3"), Ui.wrap(sub, "muted", "small"));
            HBox.setHgrow(headText, Priority.ALWAYS);
            HBox top = new HBox(12, Ui.skill(Icons.KANBAN, "skill-sky", 40), headText);
            top.setAlignment(Pos.TOP_LEFT);

            FlowPane lists = new FlowPane(10, 10);
            for (BoardPlan.ListPlan l : plan.lists()) {
                HBox listHead = new HBox(8, Ui.label(l.title(), "semi"), Ui.hgrow(),
                        Ui.label(String.valueOf(l.cards().size()), "chip"));
                listHead.setAlignment(Pos.CENTER_LEFT);
                VBox col = new VBox(6, listHead);
                col.getStyleClass().add("plan-list");
                col.setPrefWidth(214);
                int shown = Math.min(l.cards().size(), 6);
                for (int i = 0; i < shown; i++) col.getChildren().add(Ui.wrap("• " + l.cards().get(i).title(), "small"));
                if (l.cards().size() > shown) {
                    col.getChildren().add(Ui.label("и ещё " + Words.tasks(l.cards().size() - shown), "muted", "small"));
                }
                if (l.cards().isEmpty()) col.getChildren().add(Ui.label("пока без задач", "faint", "small"));
                lists.getChildren().add(col);
            }

            String summary = isNew ? Words.lists(plan.lists().size()) + " · " + Words.tasks(plan.cardCount())
                    : Words.tasks(plan.cardCount());
            Button ok = Ui.primary(isNew ? "Создать доску" : "Добавить задачи", Icons.CHECK);
            Button cancel = Ui.ghost("Отмена", null);
            HBox buttons = new HBox(10, Ui.label(summary.toUpperCase(Ui.RU), "mono"), Ui.hgrow(), cancel, ok);
            buttons.setAlignment(Pos.CENTER_LEFT);

            VBox card = new VBox(14, top, lists, buttons);
            card.getStyleClass().add("plan-card");
            ok.setOnAction(e -> decide(card, buttons, decision, true));
            cancel.setOnAction(e -> decide(card, buttons, decision, false));
            // Если решение отменили извне (например, выход из аккаунта) — показать это.
            decision.thenAccept(v -> Platform.runLater(() -> {
                if (!card.getStyleClass().contains("decided")) markDecided(card, buttons, v);
            }));

            Ui.rise(card, 0);
            return card;
        }

        void decide(VBox card, HBox buttons, CompletableFuture<Boolean> decision, boolean ok) {
            markDecided(card, buttons, ok);
            decision.complete(ok);
        }

        void markDecided(VBox card, HBox buttons, boolean ok) {
            card.getStyleClass().add("decided");
            buttons.getChildren().setAll(Ui.label(ok ? "ПОДТВЕРЖДЕНО" : "ОТМЕНЕНО", "chip", ok ? "ok" : "bad"));
        }

        Node resultCard(ActionResult a) {
            boolean isNew = a.kind() == PlannedAction.Kind.CREATE_BOARD;
            String title = isNew ? "Доска «" + a.boardTitle() + "» создана" : "Задачи добавлены в «" + a.boardTitle() + "»";
            String meta = isNew ? Words.lists(a.lists()) + " · " + Words.tasks(a.cards())
                    : Words.tasks(a.cards()) + (a.lists() > 0 ? " · новый список" : "");

            Button open = Ui.ghost("Открыть доску", Icons.ARROW_RIGHT);
            open.getStyleClass().add("btn-sm");
            open.setOnAction(e -> MenuScene.openBoard(new Board(a.boardId(), a.boardTitle(), a.boardDescription(),
                    user.getId(), LocalDateTime.now())));

            VBox text = new VBox(2, Ui.wrap(title, "semi"), Ui.label(meta, "muted", "small"));
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(14, Ui.skill(Icons.CHECK, "skill-green", 36), text, open);
            row.getStyleClass().addAll("row", "static");
            row.setAlignment(Pos.CENTER_LEFT);
            Ui.rise(row, 0);
            return row;
        }

        void showTyping(String text) {
            typingLabel.setText(text);
            messages.getChildren().remove(typing);
            messages.getChildren().add(typing);
        }

        void hideTyping() {
            messages.getChildren().remove(typing);
        }

        HBox bubble(String text, boolean fromUser, String extra) {
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
}
