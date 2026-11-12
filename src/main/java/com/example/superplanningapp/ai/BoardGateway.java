package com.example.superplanningapp.ai;

import java.util.List;

/**
 * Всё, что ассистенту можно делать с досками. Реальная реализация —
 * {@link DaoBoardGateway} (PostgreSQL), в тестах — хранилище в памяти.
 */
public interface BoardGateway {

    /** Доски, к которым у пользователя есть доступ: свои и те, где он участник. */
    List<BoardInfo> boardsOf(int userId) throws Exception;

    /** Создаёт доску со списками и задачами целиком (всё или ничего). */
    CreatedBoard createBoard(int userId, BoardPlan plan) throws Exception;

    /**
     * Добавляет задачи в список доски. Список ищется по названию без учёта регистра;
     * если его нет — создаётся. Всё или ничего.
     */
    CreatedList addCards(int boardId, String listTitle, List<BoardPlan.CardPlan> cards) throws Exception;

    record BoardInfo(int id, String title, String description, boolean owner, List<ListInfo> lists) {
        public BoardInfo {
            lists = List.copyOf(lists);
        }
    }

    record ListInfo(int id, String title) { }

    record CreatedBoard(int id, List<CreatedList> lists) {
        public CreatedBoard {
            lists = List.copyOf(lists);
        }
    }

    /** @param createdNow true — список создан этим вызовом, false — найден существующий */
    record CreatedList(int id, String title, int cards, boolean createdNow) { }
}
