package com.example.superplanningapp.ai;

import com.example.superplanningapp.DatabaseConnection;
import com.example.superplanningapp.ai.BoardPlan.CardPlan;
import com.example.superplanningapp.ai.BoardPlan.ListPlan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Доски в PostgreSQL. Создание выполняется одной транзакцией: если что-то не
 * записалось, не остаётся «половины доски».
 */
public final class DaoBoardGateway implements BoardGateway {

    @FunctionalInterface
    public interface ConnectionSource {
        Connection get() throws SQLException;
    }

    private final ConnectionSource source;

    public DaoBoardGateway() {
        this(DatabaseConnection::getConnection);
    }

    public DaoBoardGateway(ConnectionSource source) {
        this.source = source;
    }

    @Override
    public List<BoardInfo> boardsOf(int userId) throws SQLException {
        String boardsSql = "SELECT b.id, b.title, b.description, b.user_id FROM boards b "
                + "WHERE b.user_id = ? OR EXISTS (SELECT 1 FROM board_members m WHERE m.board_id = b.id AND m.user_id = ?) "
                + "ORDER BY b.id";
        String listsSql = "SELECT id, title FROM lists WHERE board_id = ? ORDER BY id";

        List<BoardInfo> result = new ArrayList<>();
        try (Connection c = source.get();
             PreparedStatement boards = c.prepareStatement(boardsSql);
             PreparedStatement lists = c.prepareStatement(listsSql)) {
            boards.setInt(1, userId);
            boards.setInt(2, userId);
            try (ResultSet rs = boards.executeQuery()) {
                while (rs.next()) {
                    int boardId = rs.getInt("id");
                    List<ListInfo> boardLists = new ArrayList<>();
                    lists.setInt(1, boardId);
                    try (ResultSet lr = lists.executeQuery()) {
                        while (lr.next()) boardLists.add(new ListInfo(lr.getInt("id"), lr.getString("title")));
                    }
                    result.add(new BoardInfo(boardId, rs.getString("title"), rs.getString("description"),
                            rs.getInt("user_id") == userId, boardLists));
                }
            }
        }
        return result;
    }

    @Override
    public CreatedBoard createBoard(int userId, BoardPlan plan) throws SQLException {
        try (Connection c = source.get()) {
            return inTransaction(c, () -> {
                int boardId = insert(c, "INSERT INTO boards (title, description, user_id) VALUES (?, ?, ?)",
                        plan.title(), blankToNull(plan.description()), userId);
                List<CreatedList> lists = new ArrayList<>();
                for (ListPlan list : plan.lists()) {
                    int listId = insert(c, "INSERT INTO lists (title, board_id) VALUES (?, ?)", list.title(), boardId);
                    insertCards(c, listId, list.cards(), 0);
                    lists.add(new CreatedList(listId, list.title(), list.cards().size(), true));
                }
                return new CreatedBoard(boardId, lists);
            });
        }
    }

    @Override
    public CreatedList addCards(int boardId, String listTitle, List<CardPlan> cards) throws SQLException {
        try (Connection c = source.get()) {
            return inTransaction(c, () -> {
                Integer listId = null;
                String title = listTitle;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, title FROM lists WHERE board_id = ? AND LOWER(title) = LOWER(?) ORDER BY id")) {
                    ps.setInt(1, boardId);
                    ps.setString(2, listTitle);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            listId = rs.getInt("id");
                            title = rs.getString("title");
                        }
                    }
                }
                boolean createdNow = listId == null;
                if (createdNow) listId = insert(c, "INSERT INTO lists (title, board_id) VALUES (?, ?)", listTitle, boardId);

                int nextOrder = 0;
                try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(card_order), -1) FROM cards WHERE list_id = ?")) {
                    ps.setInt(1, listId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) nextOrder = rs.getInt(1) + 1;
                    }
                }
                insertCards(c, listId, cards, nextOrder);
                return new CreatedList(listId, title, cards.size(), createdNow);
            });
        }
    }

    // ------------------------------------------------------------- helpers

    private interface SqlWork<T> {
        T run() throws SQLException;
    }

    private static <T> T inTransaction(Connection c, SqlWork<T> work) throws SQLException {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = work.run();
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    private static void insertCards(Connection c, int listId, List<CardPlan> cards, int firstOrder) throws SQLException {
        if (cards.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO cards (title, description, list_id, card_order) VALUES (?, ?, ?, ?)")) {
            int order = firstOrder;
            for (CardPlan card : cards) {
                ps.setString(1, card.title());
                ps.setString(2, blankToNull(card.description()));
                ps.setInt(3, listId);
                ps.setInt(4, order++);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** INSERT и возврат сгенерированного id (PostgreSQL добавит RETURNING "id"). */
    private static int insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"id"})) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p instanceof Integer n) ps.setInt(i + 1, n);
                else ps.setString(i + 1, (String) p);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("База не вернула id новой записи");
                return keys.getInt(1);
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
