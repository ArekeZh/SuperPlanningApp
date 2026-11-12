package com.example.superplanningapp.ai;

/**
 * Действие, которое модель хочет выполнить и которое пользователь должен подтвердить.
 *
 * @param plan    для CREATE_BOARD — вся новая доска; для ADD_TASKS — название
 *                существующей доски и один список с добавляемыми задачами
 * @param boardId id существующей доски (только для ADD_TASKS), иначе null
 */
public record PlannedAction(Kind kind, BoardPlan plan, Integer boardId) {

    public enum Kind { CREATE_BOARD, ADD_TASKS }
}
