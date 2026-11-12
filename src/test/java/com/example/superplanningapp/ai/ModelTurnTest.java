package com.example.superplanningapp.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelTurnTest {

    private static JSONObject candidate(JSONObject content, String finish) {
        JSONObject c = new JSONObject().put("finishReason", finish);
        if (content != null) c.put("content", content);
        return new JSONObject().put("candidates", new JSONArray().put(c));
    }

    @Test
    @DisplayName("Текст и вызовы функций разбираются; «мысли» модели в текст не попадают")
    void parsesTextCallsAndSkipsThoughts() throws Exception {
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", "внутреннее рассуждение").put("thought", true))
                .put(new JSONObject().put("text", "Создаю доску."))
                .put(new JSONObject().put("functionCall", new JSONObject().put("name", "create_board")
                        .put("args", new JSONObject().put("title", "X")).put("id", "c1")))
                .put(new JSONObject().put("functionCall", new JSONObject().put("name", "list_boards")));

        ModelTurn turn = ModelTurn.parse(candidate(new JSONObject().put("role", "model").put("parts", parts), "STOP"));

        assertEquals("Создаю доску.", turn.text());
        assertEquals(2, turn.calls().size());
        assertEquals("create_board", turn.calls().get(0).name());
        assertEquals("X", turn.calls().get(0).args().getString("title"));
        assertEquals("c1", turn.calls().get(0).id());
        assertTrue(turn.calls().get(1).args().isEmpty(), "Без args — пустой объект, а не null");
        assertNull(turn.calls().get(1).id());
        assertEquals("STOP", turn.finishReason());
    }

    @Test
    @DisplayName("Если у content нет role — подставляется model (нужно для истории)")
    void addsMissingRole() throws Exception {
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", "hi")));
        assertEquals("model", ModelTurn.parse(candidate(content, "STOP")).content().getString("role"));
    }

    @Test
    @DisplayName("Заблокированный запрос — понятная ошибка с причиной")
    void blockedPrompt() {
        JSONObject response = new JSONObject().put("promptFeedback", new JSONObject().put("blockReason", "SAFETY"));
        AiException e = assertThrows(AiException.class, () -> ModelTurn.parse(response));
        assertTrue(e.getMessage().contains("SAFETY"));
    }

    @Test
    @DisplayName("Пустой ответ и испорченный вызов функции — ошибки")
    void emptyAndMalformed() {
        assertThrows(AiException.class, () -> ModelTurn.parse(candidate(null, "STOP")));
        AiException malformed = assertThrows(AiException.class,
                () -> ModelTurn.parse(candidate(new JSONObject().put("parts", new JSONArray()), "MALFORMED_FUNCTION_CALL")));
        assertTrue(malformed.getMessage().contains("вызове функции"));
    }
}
