package com.example.superplanningapp.habits;

import com.example.superplanningapp.MenuScene;
import com.example.superplanningapp.User;
import com.example.superplanningapp.ui.Icons;
import com.example.superplanningapp.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HabitTrackerScene {

    private static User currentUser;
    private static YearMonth currentYearMonth;
    private static LocalDate selectedDate;

    // === UI ===
    private static Label monthYearLabel;
    private static Label listTitleLabel;
    private static GridPane calendarGrid;
    private static VBox habitsListContainer;
    private static Label progressPercentLabel;
    private static Label progressCountLabel;
    private static Label perfectDaysLabel;
    private static ProgressBar dayProgress;
    private static ProgressIndicator loadingSpinner;

    // === КЭШ ДАННЫХ (ЧТОБЫ НЕ ГРУЗИТЬ БАЗУ ЛИШНИЙ РАЗ) ===
    private static Map<Integer, Integer> cachedMonthlyStats = null;
    private static int cachedTotalHabits = 0;
    private static YearMonth cachedMonth = null;
    private static List<Habit> currentHabitsList;

    private static boolean isLoading = false;

    public static Node createHabitsView(User user) {
        currentUser = user;
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        cachedMonthlyStats = null;
        cachedMonth = null;
        currentHabitsList = null;
        isLoading = false;

        // --- Заголовок ---
        Button addHabitBtn = Ui.primary("Новая привычка", Icons.PLUS);
        addHabitBtn.setOnAction(e -> showAddHabitDialog());
        HBox head = Ui.headerRow(Ui.header("Трекер привычек", "Привычки",
                "Отмечайте выполненное каждый день. Календарь подсветит дни, когда сделано всё, и дни, когда ничего."),
                addHabitBtn);

        // --- Статистика ---
        progressPercentLabel = Ui.label("0%", "stat-value");
        dayProgress = new ProgressBar(0);
        dayProgress.setMaxWidth(Double.MAX_VALUE);
        dayProgress.setPrefHeight(8);
        progressCountLabel = Ui.label("0 / 0", "stat-value");
        perfectDaysLabel = Ui.label("0", "stat-value");
        HBox stats = new HBox(16,
                stat(Icons.CHART, "skill-sky", "Прогресс за день", progressPercentLabel, dayProgress),
                stat(Icons.HABITS, "skill-green", "Выполнено", progressCountLabel,
                        Ui.label("привычек на выбранный день", "row-meta")),
                stat(Icons.FLAME, "skill-amber", "Идеальные дни", perfectDaysLabel,
                        Ui.label("в этом месяце сделано всё", "row-meta")));

        // --- Календарь ---
        monthYearLabel = Ui.label("", "h3");
        loadingSpinner = new ProgressIndicator();
        Ui.fix(loadingSpinner, 18);
        loadingSpinner.setVisible(false);

        Button prevMonthBtn = Ui.iconButton(Icons.CHEVRON_LEFT, "Предыдущий месяц");
        prevMonthBtn.setOnAction(e -> {
            currentYearMonth = currentYearMonth.minusMonths(1);
            loadData(true);
        });
        Button nextMonthBtn = Ui.iconButton(Icons.CHEVRON_RIGHT, "Следующий месяц");
        nextMonthBtn.setOnAction(e -> {
            currentYearMonth = currentYearMonth.plusMonths(1);
            loadData(true);
        });
        Button todayBtn = Ui.ghost("Сегодня", null);
        todayBtn.getStyleClass().add("btn-sm");
        todayBtn.setOnAction(e -> {
            if (isLoading) return;
            currentYearMonth = YearMonth.now();
            selectedDate = LocalDate.now();
            loadData(true);
        });
        HBox calHead = new HBox(8, monthYearLabel, loadingSpinner, Ui.hgrow(), todayBtn, prevMonthBtn, nextMonthBtn);
        calHead.setAlignment(Pos.CENTER_LEFT);

        calendarGrid = new GridPane();
        calendarGrid.setHgap(6);
        calendarGrid.setVgap(6);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            cc.setHalignment(HPos.CENTER);
            calendarGrid.getColumnConstraints().add(cc);
        }

        HBox legend = new HBox(18,
                legendItem("full", "Всё сделано"),
                legendItem("partial", "Частично"),
                legendItem("none", "Пропуск"));
        legend.setAlignment(Pos.CENTER_LEFT);

        VBox calCard = new VBox(18, calHead, calendarGrid, legend);
        calCard.getStyleClass().addAll("platform", "elev");
        calCard.setMinWidth(370);
        calCard.setPrefWidth(420);
        calCard.setMaxWidth(440);

        // --- Список привычек на день ---
        listTitleLabel = Ui.label("", "h3");
        habitsListContainer = new VBox(10);
        VBox listCard = new VBox(16, new VBox(4, Ui.label("ВЫБРАННЫЙ ДЕНЬ", "mono"), listTitleLabel), habitsListContainer);
        listCard.getStyleClass().addAll("platform", "elev");
        listCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(listCard, Priority.ALWAYS);

        // Две колонки на широком окне, одна — на узком.
        HBox wide = new HBox(20);
        VBox narrow = new VBox(20);
        StackPane body = new StackPane(wide);
        wide.getChildren().setAll(calCard, listCard);
        body.widthProperty().addListener((o, a, w) -> {
            Pane target = w.doubleValue() < 860 ? narrow : wide;
            if (calCard.getParent() != target) {
                target.getChildren().setAll(calCard, listCard);
                body.getChildren().setAll(target);
            }
        });

        updateMonthLabel();
        updateListTitle();
        Node page = Ui.page(head, stats, body);
        loadData(true);
        return page;
    }

    private static VBox stat(String icon, String variant, String title, Label value, Node extra) {
        HBox top = new HBox(10, Ui.skill(icon, variant, 30), Ui.label(title.toUpperCase(Ui.RU), "mono"));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, top, value, extra);
        box.getStyleClass().add("stat");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static HBox legendItem(String kind, String text) {
        HBox item = new HBox(7, Ui.dot(9, "legend-dot", kind), Ui.label(text, "muted", "small"));
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    /**
     * Умная загрузка данных.
     * @param forceMonthReload true — перекачать статистику месяца (смена месяца),
     *                         false — только список привычек на день (клик по дате).
     */
    private static void loadData(boolean forceMonthReload) {
        if (isLoading) return;
        isLoading = true;
        loadingSpinner.setVisible(true);

        boolean needToFetchMonth = forceMonthReload || cachedMonth == null || !cachedMonth.equals(currentYearMonth);
        CompletableFuture<Void> future;

        if (needToFetchMonth) {
            // Смена месяца: три запроса параллельно
            cachedMonth = currentYearMonth;
            YearMonth month = currentYearMonth;
            LocalDate day = selectedDate;
            CompletableFuture<Integer> totalFuture = CompletableFuture.supplyAsync(() ->
                    HabitDAO.getTotalHabitsCount(currentUser.getId()));
            CompletableFuture<Map<Integer, Integer>> statsFuture = CompletableFuture.supplyAsync(() ->
                    HabitDAO.getMonthlyCompletionCounts(currentUser.getId(), month.getYear(), month.getMonthValue()));
            CompletableFuture<List<Habit>> habitsFuture = CompletableFuture.supplyAsync(() ->
                    HabitDAO.getHabitsForUser(currentUser.getId(), day));

            future = CompletableFuture.allOf(totalFuture, statsFuture, habitsFuture).thenRun(() -> {
                try {
                    cachedTotalHabits = totalFuture.get();
                    cachedMonthlyStats = statsFuture.get();
                    currentHabitsList = habitsFuture.get();
                } catch (Exception e) { e.printStackTrace(); }
            });
        } else {
            // Смена дня: статистика уже в кэше, грузим только список
            LocalDate day = selectedDate;
            future = CompletableFuture.supplyAsync(() -> HabitDAO.getHabitsForUser(currentUser.getId(), day))
                    .thenAccept(habits -> currentHabitsList = habits);
        }

        future.whenComplete((v, err) -> Platform.runLater(() -> {
            if (err != null) err.printStackTrace();
            updateMonthLabel();
            updateCalendarUI();
            updateHabitListUI();
            updateStats();
            loadingSpinner.setVisible(false);
            isLoading = false;
        }));
    }

    private static void handleDateClick(LocalDate date) {
        if (isLoading) return;
        selectedDate = date;
        loadData(false);
    }

    private static void updateCalendarUI() {
        calendarGrid.getChildren().clear();

        Map<Integer, Integer> monthlyStats = cachedMonthlyStats != null ? cachedMonthlyStats : Collections.emptyMap();
        int totalHabits = cachedTotalHabits;

        String[] daysOfWeek = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};
        for (int i = 0; i < 7; i++) {
            calendarGrid.add(Ui.label(daysOfWeek[i], "cal-head"), i, 0);
        }

        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        int col = firstOfMonth.getDayOfWeek().getValue() - 1;
        int row = 1;
        LocalDate today = LocalDate.now();

        for (int day = 1; day <= currentYearMonth.lengthOfMonth(); day++) {
            LocalDate date = currentYearMonth.atDay(day);
            Button dayBtn = new Button(String.valueOf(day));
            dayBtn.getStyleClass().setAll("button", "day");
            dayBtn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            dayBtn.setMinHeight(38);
            dayBtn.setPrefHeight(44);

            int completed = monthlyStats.getOrDefault(day, 0);
            if (date.isAfter(today)) {
                dayBtn.getStyleClass().add("future");
            } else if (totalHabits > 0) {
                dayBtn.getStyleClass().add(completed >= totalHabits ? "full" : completed == 0 ? "none" : "partial");
                dayBtn.setTooltip(Ui.tip("Выполнено " + Math.min(completed, totalHabits) + " из " + totalHabits));
            }
            if (date.equals(today)) dayBtn.getStyleClass().add("today");
            if (date.equals(selectedDate)) dayBtn.getStyleClass().add("selected");

            dayBtn.setOnAction(e -> handleDateClick(date));
            calendarGrid.add(dayBtn, col, row);

            col++;
            if (col > 6) { col = 0; row++; }
        }
    }

    private static void handleToggleOptimistic(Habit habit, Button check, HBox row) {
        boolean isNowCompleted = !habit.isCompletedOnSelectedDate();
        habit.setCompletedOnSelectedDate(isNowCompleted);

        // Мгновенно меняем вид, запрос в базу — в фоне
        setDone(check, row, isNowCompleted);

        if (cachedMonthlyStats != null && YearMonth.from(selectedDate).equals(currentYearMonth)) {
            int day = selectedDate.getDayOfMonth();
            int count = cachedMonthlyStats.getOrDefault(day, 0);
            cachedMonthlyStats.put(day, isNowCompleted ? count + 1 : Math.max(0, count - 1));
        }
        updateStats();
        updateCalendarUI();

        LocalDate day = selectedDate;
        CompletableFuture.runAsync(() -> HabitDAO.toggleHabitCompletion(habit.getId(), day, isNowCompleted));
    }

    private static void setDone(Button check, HBox row, boolean done) {
        check.getStyleClass().remove("on");
        row.getStyleClass().remove("done");
        if (done) {
            check.getStyleClass().add("on");
            row.getStyleClass().add("done");
        }
    }

    private static void updateStats() {
        int total = currentHabitsList == null ? 0 : currentHabitsList.size();
        long done = total == 0 ? 0 : currentHabitsList.stream().filter(Habit::isCompletedOnSelectedDate).count();
        int percent = total > 0 ? (int) (done * 100 / total) : 0;
        progressPercentLabel.setText(percent + "%");
        progressCountLabel.setText(done + " / " + total);
        dayProgress.setProgress(total > 0 ? (double) done / total : 0);

        int perfect = 0;
        if (cachedMonthlyStats != null && cachedTotalHabits > 0) {
            LocalDate today = LocalDate.now();
            for (int d = 1; d <= currentYearMonth.lengthOfMonth(); d++) {
                if (!currentYearMonth.atDay(d).isAfter(today)
                        && cachedMonthlyStats.getOrDefault(d, 0) >= cachedTotalHabits) perfect++;
            }
        }
        perfectDaysLabel.setText(String.valueOf(perfect));
    }

    private static void updateHabitListUI() {
        habitsListContainer.getChildren().clear();
        updateListTitle();

        if (currentHabitsList == null || currentHabitsList.isEmpty()) {
            Button addBtn = Ui.ghost("Добавить привычку", Icons.PLUS);
            addBtn.setOnAction(e -> showAddHabitDialog());
            Label hint = Ui.wrap("Начните с простого: «Стакан воды утром», «10 минут чтения», «Прогулка».", "muted");
            hint.setMaxWidth(340);
            hint.setAlignment(Pos.CENTER);
            hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            VBox empty = new VBox(10, Ui.skill(Icons.HABITS, "skill-green", 50), Ui.label("Пока нет привычек", "h3"), hint, addBtn);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(26, 10, 20, 10));
            VBox.setMargin(addBtn, new Insets(6, 0, 0, 0));
            habitsListContainer.getChildren().add(empty);
            return;
        }

        int i = 0;
        for (Habit habit : currentHabitsList) {
            Button check = new Button();
            check.getStyleClass().setAll("button", "check");
            check.setGraphic(Icons.of(Icons.CHECK, 15));
            Ui.fix(check, 28);
            check.setTooltip(Ui.tip("Отметить выполнение"));

            Label title = Ui.wrap(habit.getTitle(), "habit-title");
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);

            Button deleteBtn = Ui.iconButton(Icons.TRASH, "Удалить привычку", "danger");
            deleteBtn.setOnAction(e -> handleDelete(habit));

            HBox row = new HBox(check, title, deleteBtn);
            row.getStyleClass().add("habit-row");
            setDone(check, row, habit.isCompletedOnSelectedDate());
            check.setOnAction(e -> handleToggleOptimistic(habit, check, row));

            habitsListContainer.getChildren().add(row);
            Ui.rise(row, i++);
        }
    }

    private static void handleDelete(Habit habit) {
        if (isLoading) return;
        boolean ok = Ui.confirm("Удалить привычку «" + habit.getTitle() + "»?",
                "История отметок этой привычки тоже удалится.", "Удалить", true, MenuScene.getStage());
        if (!ok) return;
        loadingSpinner.setVisible(true);
        CompletableFuture.runAsync(() -> HabitDAO.deleteHabit(habit.getId()))
                .thenRun(() -> Platform.runLater(() -> loadData(true)));
    }

    private static void showAddHabitDialog() {
        Ui.prompt("Новая привычка", "Привычка появится в списке на каждый день.", "Название",
                        null, "Например, «10 минут чтения»", "Создать", MenuScene.getStage())
                .ifPresent(name -> {
                    loadingSpinner.setVisible(true);
                    CompletableFuture.runAsync(() -> HabitDAO.createHabit(currentUser.getId(), name))
                            .thenRun(() -> Platform.runLater(() -> loadData(true)));
                });
    }

    private static void updateMonthLabel() {
        monthYearLabel.setText(Ui.capitalize(currentYearMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", Ui.RU))));
    }

    private static void updateListTitle() {
        String text = selectedDate.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Ui.RU));
        listTitleLabel.setText(selectedDate.equals(LocalDate.now()) ? "Сегодня · " + text : Ui.capitalize(text));
    }
}
