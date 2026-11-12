package com.example.superplanningapp.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.example.superplanningapp.ai.ScriptedModel.boardArgs;
import static com.example.superplanningapp.ai.ScriptedModel.call;
import static com.example.superplanningapp.ai.ScriptedModel.callPart;
import static com.example.superplanningapp.ai.ScriptedModel.response;
import static com.example.superplanningapp.ai.ScriptedModel.text;
import static org.junit.jupiter.api.Assertions.*;

class PlanningAgentTest {

    private static final int ME = 7;

    private ScriptedModel model;
    private InMemoryGateway db;
    private PlanningAgent agent;

    @BeforeEach
    void setUp() {
        model = new ScriptedModel();
        db = new InMemoryGateway();
        agent = new PlanningAgent(model, new BoardTools(db, ME), () -> LocalDate.of(2026, 11, 12));
    }

    private static JSONObject lastContent(JSONObject request) {
        JSONArray contents = request.getJSONArray("contents");
        return contents.getJSONObject(contents.length() - 1);
    }

    private static JSONObject functionResponse(JSONObject content, int index) {
        return content.getJSONArray("parts").getJSONObject(index).getJSONObject("functionResponse");
    }

    @Test
    @DisplayName("Обычный вопрос: текстовый ответ, ничего не создаётся")
    void plainAnswer() throws Exception {
        model.then(text("Начните утро со стакана воды."));

        PlanningAgent.Reply reply = agent.send("Как начать утро?", a -> fail("Не должно быть подтверждений"));

        assertEquals("Начните утро со стакана воды.", reply.text());
        assertTrue(reply.actions().isEmpty());
        assertEquals(1, model.requests.size());
        assertEquals(2, agent.history().length(), "В истории: вопрос и ответ");
    }

    @Test
    @DisplayName("Запрос содержит системную инструкцию с датой, инструменты и режим AUTO")
    void requestStructure() throws Exception {
        model.then(text("ok"));
        agent.send("Привет", a -> true);

        JSONObject req = model.lastRequest();
        String system = req.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text");
        assertTrue(system.contains("12 ноября 2026"), "В инструкции должна быть сегодняшняя дата");
        assertTrue(system.contains("create_board"));
        assertEquals(3, req.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").length());
        assertEquals("AUTO", req.getJSONObject("toolConfig").getJSONObject("functionCallingConfig").getString("mode"));
        JSONObject first = req.getJSONArray("contents").getJSONObject(0);
        assertEquals("user", first.getString("role"));
        assertEquals("Привет", first.getJSONArray("parts").getJSONObject(0).getString("text"));
    }

    @Test
    @DisplayName("Главный сценарий: модель вызывает create_board → подтверждение → доска в базе → результат уходит модели")
    void createsBoardEndToEnd() throws Exception {
        model.then(call("create_board", boardArgs("Сессия",
                        new String[]{"Сделать", "Матан", "Физика"}, new String[]{"Готово"})))
                .then(text("Создал доску «Сессия» с двумя списками."));

        PlanningAgent.Reply reply = agent.send("Сделай доску для сессии", a -> true);

        InMemoryGateway.Board board = db.only();
        assertEquals("Сессия", board.title);
        assertEquals(2, board.cardCount());

        assertEquals(2, model.requests.size());
        JSONObject answer = functionResponse(lastContent(model.lastRequest()), 0);
        assertEquals("user", lastContent(model.lastRequest()).getString("role"));
        assertEquals("create_board", answer.getString("name"));
        assertEquals(board.id, answer.getJSONObject("response").getInt("board_id"),
                "Модель должна получить настоящий id созданной доски");

        assertEquals("Создал доску «Сессия» с двумя списками.", reply.text());
        assertEquals(1, reply.actions().size());
        assertEquals(board.id, reply.actions().get(0).boardId());
    }

    @Test
    @DisplayName("Отказ пользователя: в базе пусто, модель получает cancelled_by_user")
    void declinedByUser() throws Exception {
        model.then(call("create_board", boardArgs("Сессия", new String[]{"Сделать", "Матан"})))
                .then(text("Хорошо, не создаю. Что поменять?"));

        PlanningAgent.Reply reply = agent.send("Сделай доску", a -> false);

        assertTrue(db.boards.isEmpty());
        assertTrue(reply.actions().isEmpty());
        assertEquals("cancelled_by_user",
                functionResponse(lastContent(model.lastRequest()), 0).getJSONObject("response").getString("status"));
    }

    @Test
    @DisplayName("thoughtSignature из ответа Gemini 3 возвращается в историю без изменений")
    void thoughtSignaturePreserved() throws Exception {
        JSONObject part = callPart("list_boards", new JSONObject()).put("thoughtSignature", "sig-abc123");
        model.then(response(part)).then(text("У вас нет досок."));

        agent.send("Какие у меня доски?", a -> true);

        JSONArray contents = model.lastRequest().getJSONArray("contents");
        JSONObject modelTurn = contents.getJSONObject(1);
        assertEquals("model", modelTurn.getString("role"));
        assertEquals("sig-abc123", modelTurn.getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"));
    }

    @Test
    @DisplayName("id вызова функции возвращается в functionResponse")
    void functionCallIdEchoed() throws Exception {
        JSONObject part = callPart("list_boards", new JSONObject());
        part.getJSONObject("functionCall").put("id", "call-42");
        model.then(response(part)).then(text("Готово"));

        agent.send("Мои доски?", a -> true);

        assertEquals("call-42", functionResponse(lastContent(model.lastRequest()), 0).getString("id"));
    }

    @Test
    @DisplayName("Несколько вызовов в одном ответе — все ответы в одном сообщении, в том же порядке")
    void parallelCalls() throws Exception {
        db.seed(ME, "Курсовая", "Сделать");
        model.then(response(
                        callPart("list_boards", new JSONObject()),
                        callPart("create_board", boardArgs("Новая", new String[]{"Сделать", "А"}))))
                .then(text("Сделано"));

        PlanningAgent.Reply reply = agent.send("Покажи доски и создай новую", a -> true);

        JSONObject answers = lastContent(model.lastRequest());
        assertEquals(2, answers.getJSONArray("parts").length());
        assertEquals("list_boards", functionResponse(answers, 0).getString("name"));
        assertEquals("create_board", functionResponse(answers, 1).getString("name"));
        assertEquals(1, reply.actions().size());
    }

    @Test
    @DisplayName("Ошибка в аргументах уходит модели, и она может исправиться следующим вызовом")
    void modelCorrectsItself() throws Exception {
        model.then(call("create_board", new JSONObject().put("title", "Без списков")))
                .then(call("create_board", boardArgs("Со списками", new String[]{"Сделать", "А"})))
                .then(text("Исправил и создал."));

        PlanningAgent.Reply reply = agent.send("Создай доску", a -> true);

        assertEquals("error", functionResponse(lastContent(model.requests.get(1)), 0)
                .getJSONObject("response").getString("status"));
        assertEquals("Со списками", db.only().title);
        assertEquals(1, reply.actions().size());
    }

    @Test
    @DisplayName("Бесконечные вызовы функций обрываются на MAX_STEPS, история остаётся корректной")
    void stepLimit() throws Exception {
        for (int i = 0; i < PlanningAgent.MAX_STEPS; i++) model.then(call("list_boards", new JSONObject()));

        PlanningAgent.Reply reply = agent.send("Зациклись", a -> true);

        assertEquals(PlanningAgent.MAX_STEPS, model.requests.size());
        assertTrue(reply.text().contains("слишком много шагов"));
        JSONArray history = agent.history();
        assertEquals("model", history.getJSONObject(history.length() - 1).getString("role"),
                "История должна заканчиваться ответом модели");
    }

    @Test
    @DisplayName("Сбой API до каких-либо действий: исключение, история откатывается, можно повторить")
    void failureBeforeActionsRollsBack() throws Exception {
        model.thenFail(new AiException(429, "quota")).then(text("Теперь работает"));

        AiException e = assertThrows(AiException.class, () -> agent.send("Первый", a -> true));
        assertEquals(429, e.status());
        assertEquals(0, agent.history().length());

        agent.send("Второй", a -> true);
        JSONArray contents = model.lastRequest().getJSONArray("contents");
        assertEquals(1, contents.length(), "Неудачное сообщение не должно остаться в истории");
        assertEquals("Второй", contents.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"));
    }

    @Test
    @DisplayName("Сбой API после создания доски: результат не теряется, история корректна")
    void failureAfterBoardCreatedKeepsResult() throws Exception {
        model.then(call("create_board", boardArgs("Сессия", new String[]{"Сделать", "А"})))
                .thenFail(new AiException(503, "unavailable"))
                .then(text("Дальше"));

        PlanningAgent.Reply reply = agent.send("Создай доску", a -> true);

        assertEquals(1, db.boards.size());
        assertEquals(1, reply.actions().size());
        assertTrue(reply.text().contains("Сессия"));

        JSONArray history = agent.history();
        assertEquals(2, history.length(), "Ход заменён парой «вопрос → итог»");
        assertEquals("user", history.getJSONObject(0).getString("role"));
        assertEquals("model", history.getJSONObject(1).getString("role"));
        assertTrue(history.getJSONObject(1).toString().contains("Сессия"));

        agent.send("Что дальше?", a -> true); // следующий запрос проходит
    }

    @Test
    @DisplayName("Прерывание во время подтверждения: ничего не создано, история откатывается")
    void interruptedWhileConfirming() {
        model.then(call("create_board", boardArgs("Сессия", new String[]{"Сделать", "А"})));

        assertThrows(InterruptedException.class,
                () -> agent.send("Создай доску", a -> { throw new InterruptedException(); }));
        assertTrue(db.boards.isEmpty());
        assertEquals(0, agent.history().length());
    }

    @Test
    @DisplayName("История сохраняется между сообщениями: второй запрос видит первый обмен")
    void historyCarriesOver() throws Exception {
        model.then(text("Ответ 1")).then(text("Ответ 2"));

        agent.send("Вопрос 1", a -> true);
        agent.send("Вопрос 2", a -> true);

        JSONArray contents = model.lastRequest().getJSONArray("contents");
        assertEquals(3, contents.length());
        assertTrue(contents.toString().contains("Ответ 1"));
    }

    @Test
    @DisplayName("Длинная история обрезается по границе сообщения пользователя")
    void historyTrimmed() throws Exception {
        int rounds = PlanningAgent.MAX_HISTORY;
        for (int i = 0; i < rounds; i++) model.then(text("ответ " + i));
        for (int i = 0; i < rounds; i++) agent.send("вопрос " + i, a -> true);

        JSONArray history = agent.history();
        assertTrue(history.length() <= PlanningAgent.MAX_HISTORY);
        JSONObject first = history.getJSONObject(0);
        assertEquals("user", first.getString("role"));
        assertTrue(first.getJSONArray("parts").getJSONObject(0).has("text"));
        assertTrue(history.toString().contains("вопрос " + (rounds - 1)), "Последние сообщения сохраняются");
    }

    @Test
    @DisplayName("Текст до и после вызова функции объединяется")
    void textAroundCallsJoined() throws Exception {
        model.then(response(new JSONObject().put("text", "Сейчас создам."),
                        callPart("create_board", boardArgs("Сессия", new String[]{"Сделать", "А"}))))
                .then(text("Готово!"));

        assertEquals("Сейчас создам.\n\nГотово!", agent.send("Создай", a -> true).text());
    }

    @Test
    @DisplayName("reset() очищает историю")
    void resetClearsHistory() throws Exception {
        model.then(text("ok"));
        agent.send("Привет", a -> true);
        agent.reset();
        assertEquals(0, agent.history().length());
    }
}
