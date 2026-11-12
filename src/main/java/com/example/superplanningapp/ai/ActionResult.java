package com.example.superplanningapp.ai;

/**
 * Что реально записано в базу после подтверждения.
 *
 * @param lists сколько списков создано (для ADD_TASKS — 1, если список был новым, иначе 0)
 * @param cards сколько задач создано
 */
public record ActionResult(PlannedAction.Kind kind, int boardId, String boardTitle, String boardDescription,
                           int lists, int cards) { }
