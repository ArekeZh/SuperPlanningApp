package com.example.superplanningapp.ai;

import java.util.List;

/**
 * План доски, который предложила модель: название, описание и списки с задачами.
 * Уже проверен и нормализован в {@link BoardTools} (пробелы обрезаны, длины ограничены).
 */
public record BoardPlan(String title, String description, List<ListPlan> lists) {

    public BoardPlan {
        lists = List.copyOf(lists);
    }

    public record ListPlan(String title, List<CardPlan> cards) {
        public ListPlan {
            cards = List.copyOf(cards);
        }
    }

    public record CardPlan(String title, String description) { }

    public int cardCount() {
        return lists.stream().mapToInt(l -> l.cards().size()).sum();
    }
}
