package com.example.superplanningapp.habits;

import com.example.superplanningapp.MenuScene;
import com.example.superplanningapp.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HabitTrackerScene {

    private static User currentUser;
    private static YearMonth currentYearMonth;
    private static LocalDate selectedDate;

    // === UI ЭЛЕМЕНТЫ ===
    private static Label monthYearLabel;
    private static GridPane calendarGrid;
    private static VBox habitsListContainer;
    private static Label progressPercentLabel;
    private static Label progressCountLabel;
    private static StackPane contentStack;
    private static ProgressIndicator loadingSpinner;

    // === КЭШ ДАННЫХ (ЧТОБЫ НЕ ГРУЗИТЬ БАЗУ ЛИШНИЙ РАЗ) ===
    private static Map<Integer, Integer> cachedMonthlyStats = null;
    private static int cachedTotalHabits = 0;
    private static YearMonth cachedMonth = null; // Какой месяц сейчас в памяти
    private static List<Habit> currentHabitsList; // Текущий список привычек

    private static boolean isLoading = false;

    public static BorderPane createHabitsView(User user) {
        currentUser = user;
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();

        // Сбрасываем кэш при открытии страницы
        cachedMonthlyStats = null;
        cachedMonth = null;

        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: " + MenuScene.getBackgroundStyle() + ";");

        VBox topSection = createTopSection();
        mainLayout.setTop(topSection);

        // --- КАЛЕНДАРЬ ---
        calendarGrid = new GridPane();
        calendarGrid.setAlignment(Pos.CENTER);
        calendarGrid.setHgap(10);
        calendarGrid.setVgap(10);
        calendarGrid.setPadding(new Insets(20));

        String cardBg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "white";
        calendarGrid.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1);");

        // --- СПИСОК ---
        habitsListContainer = new VBox(15);
        habitsListContainer.setPadding(new Insets(20));

        VBox contentBox = new VBox(20, calendarGrid, habitsListContainer);
        contentBox.setPadding(new Insets(20));
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.getStyleClass().add("edge-to-edge");

        loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(50, 50);
        loadingSpinner.setVisible(false);

        contentStack = new StackPane(scrollPane, loadingSpinner);
        mainLayout.setCenter(contentStack);

        // Первая полная загрузка
        loadData(true);

        return mainLayout;
    }

    /**
     * Умная загрузка данных.
     * @param forceMonthReload - Если true, то перекачиваем статистику за весь месяц (при смене месяца).
     * Если false, то качаем только список привычек на день (при клике на дату).
     */
    private static void loadData(boolean forceMonthReload) {
        if (isLoading) return;
        isLoading = true;
        loadingSpinner.setVisible(true);

        // Если месяц изменился, нужно обновить кэш статистики
        boolean needToFetchMonth = forceMonthReload || cachedMonth == null || !cachedMonth.equals(currentYearMonth);

        CompletableFuture<Void> future;

        if (needToFetchMonth) {
            // === СЦЕНАРИЙ 1: СМЕНА МЕСЯЦА (Грузим всё ПАРАЛЛЕЛЬНО) ===
            cachedMonth = currentYearMonth;

            // Запускаем 3 задачи одновременно
            CompletableFuture<Integer> totalFuture = CompletableFuture.supplyAsync(() ->
                    HabitDAO.getTotalHabitsCount(currentUser.getId())
            );
            CompletableFuture<Map<Integer, Integer>> statsFuture = CompletableFuture.supplyAsync(() ->
                    HabitDAO.getMonthlyCompletionCounts(currentUser.getId(), currentYearMonth.getYear(), currentYearMonth.getMonthValue())
            );
            CompletableFuture<List<Habit>> habitsFuture = CompletableFuture.supplyAsync(() ->
                    HabitDAO.getHabitsForUser(currentUser.getId(), selectedDate)
            );

            future = CompletableFuture.allOf(totalFuture, statsFuture, habitsFuture).thenRun(() -> {
                try {
                    cachedTotalHabits = totalFuture.get();
                    cachedMonthlyStats = statsFuture.get();
                    currentHabitsList = habitsFuture.get();
                } catch (Exception e) { e.printStackTrace(); }
            });

        } else {
            // === СЦЕНАРИЙ 2: СМЕНА ДНЯ (Грузим только список) ===
            // Статистику не трогаем, она в кэше!
            future = CompletableFuture.supplyAsync(() -> HabitDAO.getHabitsForUser(currentUser.getId(), selectedDate))
                    .thenAccept(habits -> currentHabitsList = habits)
                    .thenRun(() -> {}); // Пустышка для совместимости типов
        }

        // Когда всё загрузилось, обновляем UI
        future.thenRun(() -> Platform.runLater(() -> {
            updateCalendarUI(); // Перерисовка календаря теперь использует кэш и работает мгновенно
            updateHabitListUI();
            recalculateStatisticsLocally();
            loadingSpinner.setVisible(false);
            isLoading = false;
        }));
    }

    private static void handleDateClick(LocalDate date) {
        if (isLoading) return;
        selectedDate = date;
        // При клике на дату внутри месяца НЕ перезагружаем статистику месяца, только список
        loadData(false);
    }

    private static void updateCalendarUI() {
        calendarGrid.getChildren().clear();
        updateMonthLabel();

        // Используем кэшированные данные
        Map<Integer, Integer> monthlyStats = (cachedMonthlyStats != null) ? cachedMonthlyStats : Collections.emptyMap();
        int totalHabits = cachedTotalHabits;

        String dayNameColor = MenuScene.isDarkTheme ? MenuScene.Theme.TEXT_SEC_DARK : "#6b778c";
        String defaultText = MenuScene.isDarkTheme ? MenuScene.Theme.TEXT_MAIN_DARK : "black";
        String[] daysOfWeek = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

        for (int i = 0; i < 7; i++) {
            Label dayName = new Label(daysOfWeek[i]);
            dayName.setStyle("-fx-text-fill: " + dayNameColor + "; -fx-font-weight: bold;");
            GridPane.setHalignment(dayName, javafx.geometry.HPos.CENTER);
            calendarGrid.add(dayName, i, 0);
        }

        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        int dayOfWeekOffset = firstOfMonth.getDayOfWeek().getValue() - 1;
        if (dayOfWeekOffset < 0) dayOfWeekOffset = 6;
        int daysInMonth = currentYearMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        int row = 1; int col = dayOfWeekOffset;
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentYearMonth.atDay(day);
            Button dayBtn = new Button(String.valueOf(day));
            dayBtn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            String baseStyle = "-fx-background-color: transparent; -fx-text-fill: " + defaultText + "; -fx-cursor: hand; -fx-background-radius: 5;";
            int completedCount = monthlyStats.getOrDefault(day, 0);
            String style = baseStyle;

            // Логика выделения
            if (date.equals(selectedDate)) {
                // Выбранный день
                style = "-fx-background-color: transparent; -fx-text-fill: " + MenuScene.Theme.ACCENT + "; -fx-border-color: " + MenuScene.Theme.ACCENT + "; -fx-border-radius: 5; -fx-font-weight: bold;";
            } else if (date.equals(today)) {
                style += "-fx-font-weight: bold; -fx-underline: true;";
            }

            // Логика цветов успеха (зеленый/красный)
            if (totalHabits > 0 && !date.isAfter(today) && !date.equals(selectedDate)) {
                if (completedCount == totalHabits) {
                    style = "-fx-background-color: #0f5132; -fx-text-fill: #d1e7dd; -fx-background-radius: 5;";
                    if (!MenuScene.isDarkTheme) style = "-fx-background-color: #d1e7dd; -fx-text-fill: #0f5132; -fx-background-radius: 5;";
                } else if (completedCount == 0) {
                    style = "-fx-background-color: #842029; -fx-text-fill: #f8d7da; -fx-background-radius: 5;";
                    if (!MenuScene.isDarkTheme) style = "-fx-background-color: #f8d7da; -fx-text-fill: #842029; -fx-background-radius: 5;";
                }
            }

            dayBtn.setStyle(style);
            dayBtn.setOnAction(e -> handleDateClick(date)); // Вызываем быстрый обработчик
            calendarGrid.add(dayBtn, col, row);

            col++;
            if (col > 6) { col = 0; row++; }
        }
    }

    private static void handleToggleOptimistic(Habit habit, Button checkBox, HBox row, Label title) {
        boolean isNowCompleted = !habit.isCompletedOnSelectedDate();
        habit.setCompletedOnSelectedDate(isNowCompleted);

        // Обновляем визуально мгновенно
        updateCheckBoxStyle(checkBox, isNowCompleted);

        String rowBg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "white";
        if (isNowCompleted) {
            row.setStyle("-fx-background-color: " + (MenuScene.isDarkTheme ? "#1e2a24" : "#e8f5e9") + "; -fx-background-radius: 8;");
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (MenuScene.isDarkTheme ? "#555" : "#aaa") + ";");
        } else {
            row.setStyle("-fx-background-color: " + rowBg + "; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2, 0, 0, 1);");
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + MenuScene.getTextStyle() + ";");
        }
        recalculateStatisticsLocally();

        // Обновляем кэш статистики в памяти (чтобы календарь не врал, если мы переключим день туда-сюда)
        if (cachedMonthlyStats != null) {
            int day = selectedDate.getDayOfMonth();
            int currentCount = cachedMonthlyStats.getOrDefault(day, 0);
            cachedMonthlyStats.put(day, isNowCompleted ? currentCount + 1 : Math.max(0, currentCount - 1));
            // Календарь не перерисовываем сразу, чтобы не моргал, он обновится при следующем клике
        }

        CompletableFuture.runAsync(() -> HabitDAO.toggleHabitCompletion(habit.getId(), selectedDate, isNowCompleted));
    }

    private static void recalculateStatisticsLocally() {
        if (currentHabitsList == null || currentHabitsList.isEmpty()) {
            progressCountLabel.setText("0");
            progressPercentLabel.setText("0%");
            return;
        }
        long completedCount = currentHabitsList.stream().filter(Habit::isCompletedOnSelectedDate).count();
        int total = currentHabitsList.size();
        progressCountLabel.setText(String.valueOf(completedCount));
        int percent = (total > 0) ? (int)((completedCount * 100) / total) : 0;
        progressPercentLabel.setText(percent + "%");
    }

    private static void updateCheckBoxStyle(Button checkBox, boolean isCompleted) {
        checkBox.setText(isCompleted ? "✔" : "");
        checkBox.setStyle("-fx-background-color: " + (isCompleted ? MenuScene.Theme.ACCENT : "transparent") + "; " +
                "-fx-text-fill: white; " +
                "-fx-border-color: " + MenuScene.Theme.ACCENT + "; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-min-width: 30; -fx-max-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
    }

    private static void updateHabitListUI() {
        habitsListContainer.getChildren().clear();
        DateTimeFormatter listFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));

        Label dateLabel = new Label("Привычки на " + selectedDate.format(listFormatter));
        dateLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + MenuScene.getTextStyle() + ";");
        habitsListContainer.getChildren().add(dateLabel);

        if (currentHabitsList == null || currentHabitsList.isEmpty()) {
            Label emptyLabel = new Label("У вас пока нет привычек. Добавьте первую!");
            emptyLabel.setStyle("-fx-text-fill: " + (MenuScene.isDarkTheme ? MenuScene.Theme.TEXT_SEC_DARK : "#6b778c") + "; -fx-font-style: italic;");
            habitsListContainer.getChildren().add(emptyLabel);
            return;
        }

        String rowBg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "white";
        String textColor = MenuScene.getTextStyle();

        for (Habit habit : currentHabitsList) {
            HBox habitRow = new HBox(15);
            habitRow.setAlignment(Pos.CENTER_LEFT);
            habitRow.setPadding(new Insets(10));

            if (habit.isCompletedOnSelectedDate()) {
                habitRow.setStyle("-fx-background-color: " + (MenuScene.isDarkTheme ? "#1e2a24" : "#e8f5e9") + "; -fx-background-radius: 8;");
            } else {
                habitRow.setStyle("-fx-background-color: " + rowBg + "; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2, 0, 0, 1);");
            }

            Button checkBox = new Button();
            updateCheckBoxStyle(checkBox, habit.isCompletedOnSelectedDate());

            Label title = new Label(habit.getTitle());
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
            if (habit.isCompletedOnSelectedDate()) {
                title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (MenuScene.isDarkTheme ? "#555" : "#aaa") + ";");
            }

            checkBox.setOnAction(e -> handleToggleOptimistic(habit, checkBox, habitRow, title));

            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Button deleteBtn = new Button("🗑");
            deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + MenuScene.Theme.ERROR + "; -fx-cursor: hand; -fx-font-size: 14px;");
            deleteBtn.setOnAction(e -> handleDelete(habit));

            habitRow.getChildren().addAll(checkBox, title, spacer, deleteBtn);
            habitsListContainer.getChildren().add(habitRow);
        }
    }

    private static void handleDelete(Habit habit) {
        if (isLoading) return;
        loadingSpinner.setVisible(true);
        CompletableFuture.runAsync(() -> HabitDAO.deleteHabit(habit.getId()))
                .thenRun(() -> Platform.runLater(() -> loadData(true))); // Тут нужен полный релоад, чтобы обновить статистику
    }

    private static void showAddHabitDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Создать привычку");
        dialog.setHeaderText("Новая глобальная привычка");
        dialog.setContentText("Название:");
        dialog.initOwner(MenuScene.getStage());

        if (MenuScene.isDarkTheme) {
            DialogPane pane = dialog.getDialogPane();
            pane.setStyle("-fx-background-color: " + MenuScene.Theme.HOVER_DARK + ";");
            pane.lookup(".content.label").setStyle("-fx-text-fill: white;");
            pane.lookup(".header-panel").setStyle("-fx-background-color: " + MenuScene.Theme.SIDEBAR_DARK + "; -fx-text-fill: white;");
        }

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                loadingSpinner.setVisible(true);
                CompletableFuture.runAsync(() -> HabitDAO.createHabit(currentUser.getId(), name.trim()))
                        .thenRun(() -> Platform.runLater(() -> loadData(true)));
            }
        });
    }

    private static VBox createTopSection() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        String bg = MenuScene.isDarkTheme ? MenuScene.Theme.SIDEBAR_DARK : "white";
        root.setStyle("-fx-background-color: " + bg + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");

        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER);

        String navBtnStyle = "-fx-background-color: transparent; -fx-text-fill: " + MenuScene.getTextStyle() + "; -fx-font-size: 18px; -fx-cursor: hand; -fx-border-color: " + (MenuScene.isDarkTheme ? "#333" : "#ccc") + "; -fx-border-radius: 5;";

        Button prevMonthBtn = new Button("<");
        prevMonthBtn.setStyle(navBtnStyle);
        prevMonthBtn.setOnAction(e -> {
            currentYearMonth = currentYearMonth.minusMonths(1);
            loadData(true); // Смена месяца - полная загрузка
        });

        monthYearLabel = new Label();
        monthYearLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + MenuScene.getTextStyle() + ";");
        updateMonthLabel();

        Button nextMonthBtn = new Button(">");
        nextMonthBtn.setStyle(navBtnStyle);
        nextMonthBtn.setOnAction(e -> {
            currentYearMonth = currentYearMonth.plusMonths(1);
            loadData(true); // Смена месяца - полная загрузка
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addHabitBtn = new Button("+ Новая привычка");
        addHabitBtn.setStyle("-fx-background-color: " + MenuScene.Theme.ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        addHabitBtn.setOnAction(e -> showAddHabitDialog());

        header.getChildren().addAll(prevMonthBtn, monthYearLabel, nextMonthBtn, spacer, addHabitBtn);

        HBox statsBox = new HBox(40);
        statsBox.setAlignment(Pos.CENTER);
        VBox stat1 = createStatCard("ПРОГРЕСС ЗА ДЕНЬ", true);
        VBox stat2 = createStatCard("ВЫПОЛНЕНО СЕГОДНЯ", false);
        statsBox.getChildren().addAll(stat1, stat2);

        root.getChildren().addAll(header, statsBox);
        return root;
    }

    private static VBox createStatCard(String title, boolean isPercent) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(15, 30, 15, 30));
        String cardBg = MenuScene.isDarkTheme ? MenuScene.Theme.HOVER_DARK : "#f4f5f7";
        String subTextColor = MenuScene.isDarkTheme ? MenuScene.Theme.TEXT_SEC_DARK : "#6b778c";
        box.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 8;");

        Label valueLabel = new Label("...");
        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #0079bf;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + subTextColor + "; -fx-font-weight: bold;");

        if (isPercent) progressPercentLabel = valueLabel;
        else progressCountLabel = valueLabel;
        box.getChildren().addAll(valueLabel, titleLabel);
        return box;
    }

    private static void updateMonthLabel() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("ru"));
        String monthText = currentYearMonth.format(formatter);
        monthYearLabel.setText(monthText.substring(0, 1).toUpperCase() + monthText.substring(1));
    }
}