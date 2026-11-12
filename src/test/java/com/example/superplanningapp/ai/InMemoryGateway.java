package com.example.superplanningapp.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Доски в памяти — вместо базы данных в тестах. */
final class InMemoryGateway implements BoardGateway {

    static final class Board {
        final int id;
        final int owner;
        final String title;
        final String description;
        final Set<Integer> members = new HashSet<>();
        final List<ListRow> lists = new ArrayList<>();

        Board(int id, int owner, String title, String description) {
            this.id = id;
            this.owner = owner;
            this.title = title;
            this.description = description;
        }

        ListRow list(String title) {
            return lists.stream().filter(l -> l.title.equalsIgnoreCase(title)).findFirst().orElse(null);
        }

        int cardCount() {
            return lists.stream().mapToInt(l -> l.cards.size()).sum();
        }
    }

    static final class ListRow {
        final int id;
        final String title;
        final List<BoardPlan.CardPlan> cards = new ArrayList<>();

        ListRow(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    final List<Board> boards = new ArrayList<>();
    private int nextId = 100;
    /** Следующая запись упадёт с ошибкой — проверка обработки сбоев базы. */
    boolean failWrites;

    Board seed(int owner, String title, String... listTitles) {
        Board b = new Board(nextId++, owner, title, "");
        for (String t : listTitles) b.lists.add(new ListRow(nextId++, t));
        boards.add(b);
        return b;
    }

    Board only() {
        if (boards.size() != 1) throw new AssertionError("Ожидалась одна доска, а их " + boards.size());
        return boards.get(0);
    }

    @Override
    public List<BoardInfo> boardsOf(int userId) {
        List<BoardInfo> result = new ArrayList<>();
        for (Board b : boards) {
            if (b.owner != userId && !b.members.contains(userId)) continue;
            List<ListInfo> lists = b.lists.stream().map(l -> new ListInfo(l.id, l.title)).toList();
            result.add(new BoardInfo(b.id, b.title, b.description, b.owner == userId, lists));
        }
        return result;
    }

    @Override
    public CreatedBoard createBoard(int userId, BoardPlan plan) throws Exception {
        if (failWrites) throw new Exception("connection refused");
        Board b = new Board(nextId++, userId, plan.title(), plan.description());
        List<CreatedList> created = new ArrayList<>();
        for (BoardPlan.ListPlan lp : plan.lists()) {
            ListRow row = new ListRow(nextId++, lp.title());
            row.cards.addAll(lp.cards());
            b.lists.add(row);
            created.add(new CreatedList(row.id, row.title, lp.cards().size(), true));
        }
        boards.add(b);
        return new CreatedBoard(b.id, created);
    }

    @Override
    public CreatedList addCards(int boardId, String listTitle, List<BoardPlan.CardPlan> cards) throws Exception {
        if (failWrites) throw new Exception("connection refused");
        Board b = boards.stream().filter(x -> x.id == boardId).findFirst().orElseThrow();
        ListRow row = b.list(listTitle);
        boolean createdNow = row == null;
        if (createdNow) {
            row = new ListRow(nextId++, listTitle);
            b.lists.add(row);
        }
        row.cards.addAll(cards);
        return new CreatedList(row.id, row.title, cards.size(), createdNow);
    }
}
