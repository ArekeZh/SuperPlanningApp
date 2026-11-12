package com.example.superplanningapp.habits;

import com.example.superplanningapp.DatabaseConnection;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HabitDAO {

    // --- ОСНОВНЫЕ МЕТОДЫ (Сбор данных) ---

    // Получить ВСЕ привычки (глобально) и проверить статус галочки на дату date
    public static List<Habit> getHabitsForUser(int userId, LocalDate date) {
        List<Habit> habits = new ArrayList<>();
        String sql = "SELECT h.id, h.user_id, h.title, h.color, " +
                "CASE WHEN hc.id IS NOT NULL THEN 1 ELSE 0 END AS is_completed " +
                "FROM habits h " +
                "LEFT JOIN habit_completions hc ON h.id = hc.habit_id AND hc.completion_date = ? " +
                "WHERE h.user_id = ? " +
                "ORDER BY h.id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setInt(2, userId);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                habits.add(new Habit(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("title"),
                        rs.getString("color"),
                        rs.getInt("is_completed") > 0
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return habits;
    }

    // --- НОВЫЕ МЕТОДЫ ДЛЯ ПОДСВЕТКИ КАЛЕНДАРЯ ---

    // 1. Узнать, сколько всего привычек у пользователя (знаменатель для %)
    public static int getTotalHabitsCount(int userId) {
        String sql = "SELECT COUNT(*) FROM habits WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 2. Получить статистику выполнений за весь месяц сразу (Map: День -> Кол-во выполненных)
    public static Map<Integer, Integer> getMonthlyCompletionCounts(int userId, int year, int month) {
        Map<Integer, Integer> stats = new HashMap<>();
        // Группируем по дням: берем день из даты и считаем количество записей
        String sql = "SELECT EXTRACT(DAY FROM completion_date) as day_num, COUNT(*) as completed_count " +
                "FROM habit_completions hc " +
                "JOIN habits h ON hc.habit_id = h.id " +
                "WHERE h.user_id = ? AND EXTRACT(YEAR FROM completion_date) = ? AND EXTRACT(MONTH FROM completion_date) = ? " +
                "GROUP BY day_num";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, year);
            pstmt.setInt(3, month);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                stats.put(rs.getInt("day_num"), rs.getInt("completed_count"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    // --- CRUD ОПЕРАЦИИ ---

    public static void createHabit(int userId, String title) {
        String sql = "INSERT INTO habits (user_id, title, color) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, title);
            pstmt.setString(3, "blue");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteHabit(int habitId) {
        String sql = "DELETE FROM habits WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, habitId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void toggleHabitCompletion(int habitId, LocalDate date, boolean completed) {
        if (completed) {
            String sql = "INSERT INTO habit_completions (habit_id, completion_date) VALUES (?, ?) ON CONFLICT DO NOTHING";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, habitId);
                pstmt.setDate(2, Date.valueOf(date));
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        } else {
            String sql = "DELETE FROM habit_completions WHERE habit_id = ? AND completion_date = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, habitId);
                pstmt.setDate(2, Date.valueOf(date));
                pstmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }
}