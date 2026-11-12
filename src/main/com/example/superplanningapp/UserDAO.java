package com.example.superplanningapp;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;

public class UserDAO {

    // 1. АВТОРИЗАЦИЯ И РЕГИСТРАЦИЯ

    // Регистрация нового пользователя
    public static boolean registerUser(String username, String email, String phone, String password) {
        String sql = "INSERT INTO users (username, email, phone, password_hash) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Хешируем пароль перед сохранением (для безопасности)
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, phone);
            pstmt.setString(4, hashedPassword);

            pstmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("Ошибка регистрации: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Вход в систему (поиск по email, телефону или никнейму)
    public static User loginUser(String loginInput, String password) {
        String sql = "SELECT * FROM users WHERE email = ? OR phone = ? OR username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loginInput);
            pstmt.setString(2, loginInput);
            pstmt.setString(3, loginInput);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");

                // Проверяем совпадение пароля с хешем
                if (BCrypt.checkpw(password, storedHash)) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка входа: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ==========================================
    // 2. УПРАВЛЕНИЕ ПРОФИЛЕМ
    // ==========================================

    // Обновление данных профиля (Имя, Фамилия, ДР, Фото)
    public static boolean updateUserProfile(User user) {
        String sql = "UPDATE users SET first_name = ?, last_name = ?, birthday = ?, avatar_path = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getFirstName());
            pstmt.setString(2, user.getLastName());
            pstmt.setString(3, user.getBirthday());
            pstmt.setString(4, user.getAvatarPath());

            // Важно: обновляем именно этого пользователя по ID
            pstmt.setInt(5, user.getId());

            int rowsUpdated = pstmt.executeUpdate();
            return rowsUpdated > 0;

        } catch (SQLException e) {
            System.err.println("Ошибка обновления профиля: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Получить полные данные пользователя по ID (Используется для отображения владельца доски)
    public static User getUserById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapUser(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ==========================================
    // 3. ПОИСК ПОЛЬЗОВАТЕЛЕЙ (ДЛЯ ПРИГЛАШЕНИЙ)
    // ==========================================

    public static int findUserIdByUsername(String username) {
        return findUserIdByField("username", username);
    }

    public static int findUserIdByEmail(String email) {
        return findUserIdByField("email", email);
    }

    public static int findUserIdByPhone(String phone) {
        return findUserIdByField("phone", phone);
    }

    // Вспомогательный метод для поиска ID по любому полю (чтобы не дублировать код)
    private static int findUserIdByField(String field, String value) {
        String sql = "SELECT id FROM users WHERE " + field + " = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, value);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // Пользователь не найден
    }

    // ==========================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ==========================================

    // Превращает строку из ResultSet в объект User (маппинг)
    private static User mapUser(ResultSet rs) throws SQLException {
        User user = new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("phone")
        );

        // Заполняем дополнительные поля (в базе они могут быть NULL, rs.getString вернет null, это ок)
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setBirthday(rs.getString("birthday"));
        user.setAvatarPath(rs.getString("avatar_path"));

        return user;
    }
}