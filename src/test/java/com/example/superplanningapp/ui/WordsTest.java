package com.example.superplanningapp.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordsTest {

    @Test
    @DisplayName("Русские окончания: 1 задача, 2 задачи, 5 задач, 11 задач, 21 задача")
    void plural() {
        assertEquals("1 задача", Words.tasks(1));
        assertEquals("2 задачи", Words.tasks(2));
        assertEquals("4 задачи", Words.tasks(4));
        assertEquals("5 задач", Words.tasks(5));
        assertEquals("11 задач", Words.tasks(11));
        assertEquals("14 задач", Words.tasks(14));
        assertEquals("21 задача", Words.tasks(21));
        assertEquals("0 задач", Words.tasks(0));
        assertEquals("3 списка", Words.lists(3));
        assertEquals("1 список", Words.lists(1));
    }

    @Test
    @DisplayName("Markdown из ответа модели превращается в обычный текст")
    void plainText() {
        assertEquals("План\n• не звёздочки\n• пункт два",
                Words.plainText("### План\n* **не** звёздочки\n- пункт два"));
        assertEquals("Жирный текст", Words.plainText("**Жирный** текст"));
        assertEquals("", Words.plainText(null));
    }
}
