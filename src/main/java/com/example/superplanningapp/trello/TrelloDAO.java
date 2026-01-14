package com.example.superplanningapp.trello;

import com.example.superplanningapp.DatabaseConnection;
import com.example.superplanningapp.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TrelloDAO {

    // ==========================================
    // 1. BOARDS (ДОСКИ)
    // ==========================================

    public static List<Board> getAllBoards(int userId) {
        List<Board> boards = new ArrayList<>();
        String sql = "SELECT * FROM boards WHERE user_id = ? " +
                "UNION " +
                "SELECT b.* FROM boards b " +
                "JOIN board_members bm ON b.id = bm.board_id " +
                "WHERE bm.user_id = ? " +
                "ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                boards.add(new Board(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("user_id"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return boards;
    }

    public static Board createBoard(String title, String description, int userId) {
        String sql = "INSERT INTO boards (title, description, user_id) VALUES (?, ?, ?) RETURNING *";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, description);
            pstmt.setInt(3, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Board(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("user_id"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                );
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public static boolean updateBoard(int boardId, String title, String description) {
        String sql = "UPDATE boards SET title = ?, description = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, description);
            pstmt.setInt(3, boardId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public static boolean deleteBoard(int boardId) {
        String sql = "DELETE FROM boards WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, boardId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // ==========================================
    // 2. COLLABORATION (УЧАСТНИКИ)
    // ==========================================

    public static List<User> getBoardMembers(int boardId) {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.* FROM users u JOIN board_members bm ON u.id = bm.user_id WHERE bm.board_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, boardId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                User user = new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("phone"));
                user.setFirstName(rs.getString("first_name"));
                user.setLastName(rs.getString("last_name"));
                user.setBirthday(rs.getString("birthday"));
                user.setAvatarPath(rs.getString("avatar_path"));
                members.add(user);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return members;
    }

    public static void removeBoardMember(int boardId, int userId) {
        String deleteSql = "DELETE FROM board_members WHERE board_id = ? AND user_id = ?";
        String boardName = getBoardName(boardId);
        String notifSql = "INSERT INTO notifications (user_id, message, is_read) VALUES (?, ?, FALSE)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                pstmt.setInt(1, boardId);
                pstmt.setInt(2, userId);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(notifSql)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, "Вы были исключены из доски \"" + boardName + "\".");
                pstmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void leaveBoard(int boardId, int userId, String userName) {
        String deleteSql = "DELETE FROM board_members WHERE board_id = ? AND user_id = ?";
        int ownerId = getBoardOwnerId(boardId);
        String boardName = getBoardName(boardId);
        String notifSql = "INSERT INTO notifications (user_id, message, is_read) VALUES (?, ?, FALSE)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                pstmt.setInt(1, boardId);
                pstmt.setInt(2, userId);
                pstmt.executeUpdate();
            }
            if (ownerId != -1) {
                try (PreparedStatement pstmt = conn.prepareStatement(notifSql)) {
                    pstmt.setInt(1, ownerId);
                    pstmt.setString(2, "Участник " + userName + " покинул вашу доску \"" + boardName + "\".");
                    pstmt.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ==========================================
    // 3. INVITATIONS (Входящие, Исходящие, Архив)
    // ==========================================

    public static boolean sendInvitation(int senderId, int receiverId, int boardId) {
        if (senderId == receiverId) return false;
        if (isMember(boardId, receiverId)) return false;
        String sql = "INSERT INTO invitations (sender_id, receiver_id, board_id, status) VALUES (?, ?, ?, 'PENDING') " +
                "ON CONFLICT (receiver_id, board_id) DO UPDATE SET status = 'PENDING', created_at = CURRENT_TIMESTAMP, sender_id = EXCLUDED.sender_id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            pstmt.setInt(3, boardId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ВХОДЯЩИЕ (PENDING)
    public static List<Invitation> getPendingInvitations(int userId) {
        return getInvitationsByQuery("SELECT i.id, u.username as user_name, b.title as board_name, i.status, i.created_at " +
                "FROM invitations i JOIN users u ON i.sender_id = u.id JOIN boards b ON i.board_id = b.id " +
                "WHERE i.receiver_id = ? AND i.status = 'PENDING'", userId);
    }

    // ИСХОДЯЩИЕ (Все, которые я отправил)
    public static List<Invitation> getOutgoingInvitations(int senderId) {
        // Здесь user_name будет именем ПОЛУЧАТЕЛЯ
        return getInvitationsByQuery("SELECT i.id, u.username as user_name, b.title as board_name, i.status, i.created_at " +
                "FROM invitations i JOIN users u ON i.receiver_id = u.id JOIN boards b ON i.board_id = b.id " +
                "WHERE i.sender_id = ?", senderId);
    }

    // АРХИВ ПРИГЛАШЕНИЙ (Принятые или отклоненные МНОЙ)
    public static List<Invitation> getArchivedInvitations(int userId) {
        return getInvitationsByQuery("SELECT i.id, u.username as user_name, b.title as board_name, i.status, i.created_at " +
                "FROM invitations i JOIN users u ON i.sender_id = u.id JOIN boards b ON i.board_id = b.id " +
                "WHERE i.receiver_id = ? AND i.status != 'PENDING'", userId);
    }

    private static List<Invitation> getInvitationsByQuery(String sql, int paramId) {
        List<Invitation> invites = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, paramId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                invites.add(new Invitation(
                        rs.getInt("id"),
                        rs.getString("user_name"), // Отправитель или Получатель
                        rs.getString("board_name"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return invites;
    }

    public static void acceptInvitation(int invitationId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement p = conn.prepareStatement("INSERT INTO board_members (board_id, user_id) SELECT board_id, receiver_id FROM invitations WHERE id = ?")) { p.setInt(1, invitationId); p.executeUpdate(); }
            try (PreparedStatement p = conn.prepareStatement("UPDATE invitations SET status = 'ACCEPTED' WHERE id = ?")) { p.setInt(1, invitationId); p.executeUpdate(); }
            conn.commit();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void declineInvitation(int invitationId) {
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement("UPDATE invitations SET status = 'DECLINED' WHERE id = ?")) {
            pstmt.setInt(1, invitationId); pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ==========================================
    // 4. NOTIFICATIONS (ОПОВЕЩЕНИЯ)
    // ==========================================

    // Непрочитанные уведомления (ВХОДЯЩИЕ)
    public static List<Notification> getUnreadNotifications(int userId) {
        return getNotificationsByQuery("SELECT * FROM notifications WHERE user_id = ? AND is_read = FALSE", userId);
    }

    // Прочитанные уведомления (АРХИВ)
    public static List<Notification> getReadNotifications(int userId) {
        return getNotificationsByQuery("SELECT * FROM notifications WHERE user_id = ? AND is_read = TRUE", userId);
    }

    private static List<Notification> getNotificationsByQuery(String sql, int userId) {
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) list.add(new Notification(rs.getInt("id"), rs.getString("message"), rs.getBoolean("is_read"), rs.getTimestamp("created_at").toLocalDateTime()));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // Пометить как прочитанное (В архив)
    public static void markNotificationAsRead(int notifId) {
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement("UPDATE notifications SET is_read = TRUE WHERE id = ?")) {
            pstmt.setInt(1, notifId); pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Вспомогательные методы
    private static int getBoardOwnerId(int boardId) {
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT user_id FROM boards WHERE id = ?")) {
            pstmt.setInt(1, boardId); ResultSet rs = pstmt.executeQuery();
            if(rs.next()) return rs.getInt("user_id");
        } catch(Exception e){} return -1;
    }
    private static String getBoardName(int boardId) {
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT title FROM boards WHERE id = ?")) {
            pstmt.setInt(1, boardId); ResultSet rs = pstmt.executeQuery();
            if(rs.next()) return rs.getString("title");
        } catch(Exception e){} return "Unknown";
    }
    private static boolean isMember(int boardId, int userId) {
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM board_members WHERE board_id = ? AND user_id = ?")) {
            pstmt.setInt(1, boardId); pstmt.setInt(2, userId); return pstmt.executeQuery().next();
        } catch(Exception e){ return false; }
    }

    // === LISTS & CARDS (Оставлены без изменений для краткости) ===
    public static List<TrelloList> getListsByBoard(int boardId) {
        List<TrelloList> l = new ArrayList<>();
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT * FROM lists WHERE board_id=? ORDER BY id")){p.setInt(1,boardId);ResultSet r=p.executeQuery();while(r.next())l.add(new TrelloList(r.getInt("id"),r.getString("title"),r.getInt("board_id"),r.getString("color")));}catch(Exception e){e.printStackTrace();} return l;
    }
    public static TrelloList createList(String t, int b) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO lists (title, board_id) VALUES (?, ?) RETURNING *")){p.setString(1,t);p.setInt(2,b);ResultSet r=p.executeQuery();if(r.next())return new TrelloList(r.getInt("id"),r.getString("title"),r.getInt("board_id"),r.getString("color"));}catch(Exception e){e.printStackTrace();}return null;
    }
    public static boolean updateList(int id, String t, String col) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE lists SET title=?, color=? WHERE id=?")){p.setString(1,t);p.setString(2,col);p.setInt(3,id);return p.executeUpdate()>0;}catch(Exception e){return false;}
    }
    public static boolean deleteList(int id) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM lists WHERE id=?")){p.setInt(1,id);return p.executeUpdate()>0;}catch(Exception e){return false;}
    }
    public static TrelloList getListById(int id) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT * FROM lists WHERE id=?")){p.setInt(1,id);ResultSet r=p.executeQuery();if(r.next())return new TrelloList(r.getInt("id"),r.getString("title"),r.getInt("board_id"),r.getString("color"));}catch(Exception e){e.printStackTrace();}return null;
    }
    public static List<Card> getCardsByList(int lid) {
        List<Card> l=new ArrayList<>(); try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT * FROM cards WHERE list_id=? ORDER BY card_order, id")){p.setInt(1,lid);ResultSet r=p.executeQuery();while(r.next())l.add(new Card(r.getInt("id"),r.getString("title"),r.getString("description"),r.getInt("list_id")));}catch(Exception e){}return l;
    }
    public static Card createCard(String t, String d, int lid) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO cards (title, description, list_id) VALUES (?, ?, ?) RETURNING *")){p.setString(1,t);p.setString(2,d);p.setInt(3,lid);ResultSet r=p.executeQuery();if(r.next())return new Card(r.getInt("id"),r.getString("title"),r.getString("description"),r.getInt("list_id"));}catch(Exception e){}return null;
    }
    public static boolean moveCard(int cid, int nlid) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE cards SET list_id=? WHERE id=?")){p.setInt(1,nlid);p.setInt(2,cid);return p.executeUpdate()>0;}catch(Exception e){return false;}
    }
    public static boolean deleteCard(int cid) {
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM cards WHERE id=?")){p.setInt(1,cid);return p.executeUpdate()>0;}catch(Exception e){return false;}
    }

    // === DTO Classes ===
    public static class Invitation {
        private int id; private String personName; private String boardName; private String status; private LocalDateTime createdAt;
        public Invitation(int id, String pn, String bn, String s, LocalDateTime c) { this.id=id; this.personName=pn; this.boardName=bn; this.status=s; this.createdAt=c; }
        public int getId() { return id; }
        public String getPersonName() { return personName; }
        public String getBoardName() { return boardName; }
        public String getStatus() { return status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class Notification {
        private int id; private String message; private boolean isRead; private LocalDateTime createdAt;
        public Notification(int id, String m, boolean r, LocalDateTime c) { this.id=id; this.message=m; this.isRead=r; this.createdAt=c; }
        public int getId() { return id; }
        public String getMessage() { return message; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}