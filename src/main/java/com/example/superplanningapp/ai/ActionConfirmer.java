package com.example.superplanningapp.ai;

/**
 * Спрашивает пользователя, можно ли выполнить действие. Вызывается из фонового
 * потока и может ждать ответа сколько угодно (в интерфейсе — карточка с кнопками).
 */
@FunctionalInterface
public interface ActionConfirmer {

    boolean confirm(PlannedAction action) throws InterruptedException;
}
