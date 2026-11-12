package com.example.superplanningapp.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ассистент по планированию: ведёт диалог с моделью и выполняет её вызовы функций.
 *
 * <p>Один вызов {@link #send} — это цикл: отправить историю модели → если она
 * вызвала функции, выполнить их (с подтверждением пользователя) и вернуть
 * результаты → повторять, пока модель не ответит текстом. Число шагов ограничено.
 *
 * <p>История хранится между сообщениями, поэтому «добавь туда ещё задачу» работает.
 */
public final class PlanningAgent {

    public static final int MAX_STEPS = 8;
    static final int MAX_HISTORY = 60;

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("ru"));

    private final ModelClient client;
    private final BoardTools tools;
    private final Supplier<LocalDate> today;
    private final JSONArray contents = new JSONArray();

    /** Ответ ассистента: текст для чата и то, что реально создано в базе. */
    public record Reply(String text, List<ActionResult> actions) {
        public Reply {
            actions = List.copyOf(actions);
        }
    }

    public PlanningAgent(ModelClient client, BoardTools tools, Supplier<LocalDate> today) {
        this.client = client;
        this.tools = tools;
        this.today = today;
    }

    /**
     * Отправляет сообщение пользователя и доводит диалог до текстового ответа.
     *
     * @throws AiException если модель недоступна и ничего не было создано
     *                     (история при этом откатывается — можно повторить запрос)
     */
    public synchronized Reply send(String userText, ActionConfirmer confirmer) throws AiException, InterruptedException {
        int mark = contents.length();
        contents.put(userText(userText));

        List<ActionResult> actions = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        try {
            for (int step = 0; step < MAX_STEPS; step++) {
                ModelTurn turn = ModelTurn.parse(client.generate(buildRequest()));
                contents.put(turn.content());
                appendText(text, turn.text());

                if (turn.calls().isEmpty()) {
                    trimHistory();
                    return new Reply(finalText(text, actions), actions);
                }

                JSONArray responses = new JSONArray();
                for (ModelTurn.FunctionCall call : turn.calls()) {
                    BoardTools.Outcome outcome = tools.handle(call.name(), call.args(), confirmer);
                    if (outcome.result() != null) actions.add(outcome.result());
                    JSONObject functionResponse = new JSONObject()
                            .put("name", call.name())
                            .put("response", outcome.response());
                    if (call.id() != null) functionResponse.put("id", call.id());
                    responses.put(new JSONObject().put("functionResponse", functionResponse));
                }
                contents.put(new JSONObject().put("role", "user").put("parts", responses));
            }

            // Модель так и не ответила текстом — закрываем ход, чтобы история осталась корректной.
            String stop = "Я остановился: слишком много шагов подряд. Уточните, пожалуйста, что именно нужно сделать.";
            contents.put(modelText(stop));
            trimHistory();
            appendText(text, stop);
            return new Reply(text.toString(), actions);

        } catch (AiException e) {
            if (actions.isEmpty()) {
                rollback(mark);
                throw e;
            }
            // Что-то уже записано в базу: не теряем это ни в истории, ни в интерфейсе.
            String note = "Готово: " + describe(actions) + ". Ответ модели прервался: " + e.userMessage();
            replaceTurnWithSummary(mark, userText, note);
            appendText(text, note);
            return new Reply(text.toString(), actions);
        } catch (InterruptedException | RuntimeException e) {
            if (actions.isEmpty()) rollback(mark);
            else replaceTurnWithSummary(mark, userText, "Готово: " + describe(actions) + ".");
            throw e;
        }
    }

    /**
     * Ход оборвался посреди вызовов функций. Чтобы история осталась корректной для API
     * (у каждого вызова должен быть ответ), заменяем ход на пару «вопрос → итог».
     */
    private void replaceTurnWithSummary(int mark, String userText, String summary) {
        rollback(mark);
        contents.put(userText(userText));
        contents.put(modelText(summary));
        trimHistory();
    }

    /** Начать диалог заново. */
    public synchronized void reset() {
        rollback(0);
    }

    synchronized JSONArray history() {
        return new JSONArray(contents.toString());
    }

    JSONObject buildRequest() {
        JSONObject request = new JSONObject();
        request.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", systemPrompt()))));
        request.put("contents", new JSONArray(contents.toString()));
        request.put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", BoardTools.declarations())));
        request.put("toolConfig", new JSONObject().put("functionCallingConfig", new JSONObject().put("mode", "AUTO")));
        return request;
    }

    String systemPrompt() {
        return """
                Ты — ассистент по планированию в приложении SuperPlanning. Помогаешь составлять распорядок дня \
                и организовывать задачи. У пользователя есть канбан-доски: доска → списки → карточки-задачи.
                Сегодня: %s.

                Как работать с досками:
                • Если пользователь хочет организовать проект, цель, учёбу, подготовку или переезд — собери \
                доску и вызови create_board: 2–5 списков (например «Сделать», «В работе», «Готово» или этапы \
                проекта) и конкретные, выполнимые задачи с короткими описаниями. Новые задачи обычно кладутся \
                в первый список.
                • Если без важных деталей доска получится бесполезной (неясна тема, срок или объём) — сначала \
                задай 1–2 коротких уточняющих вопроса. Если запрос и так понятен, не спрашивай, а сразу \
                предлагай доску.
                • Чтобы добавить задачи в существующую доску, сначала вызови list_boards, затем add_tasks. \
                Никогда не придумывай id — бери их только из ответов функций.
                • Создание подтверждает сам пользователь. Если пришёл статус cancelled_by_user — ничего не \
                повторяй, а спроси, что изменить. Если пришла ошибка — исправь аргументы и попробуй снова.
                • После создания коротко перечисли, что получилось.

                Стиль: отвечай по-русски, кратко и по делу. Пиши обычным текстом без Markdown (без звёздочек \
                и решёток); перечисления — строками, начинающимися с «•».
                Вопросы не о планировании, задачах, расписании и продуктивности вежливо отклоняй одной фразой.
                """.formatted(today.get().format(DATE));
    }

    // ------------------------------------------------------------- helpers

    private static JSONObject userText(String text) {
        return new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    private static JSONObject modelText(String text) {
        return new JSONObject().put("role", "model")
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    private static void appendText(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (!sb.isEmpty()) sb.append("\n\n");
        sb.append(part.strip());
    }

    private static String finalText(StringBuilder text, List<ActionResult> actions) {
        if (!text.isEmpty()) return text.toString();
        return actions.isEmpty() ? "Не получилось сформулировать ответ. Попробуйте переформулировать запрос."
                : "Готово: " + describe(actions) + ".";
    }

    private static String describe(List<ActionResult> actions) {
        List<String> parts = new ArrayList<>();
        for (ActionResult a : actions) {
            parts.add(a.kind() == PlannedAction.Kind.CREATE_BOARD
                    ? "создана доска «" + a.boardTitle() + "»"
                    : "добавлено задач в «" + a.boardTitle() + "»: " + a.cards());
        }
        return String.join("; ", parts);
    }

    private void rollback(int length) {
        while (contents.length() > length) contents.remove(contents.length() - 1);
    }

    /** Обрезаем старое, но только по границе сообщения пользователя, чтобы не разорвать пару вызов/ответ. */
    private void trimHistory() {
        while (contents.length() > MAX_HISTORY) {
            contents.remove(0);
            while (!contents.isEmpty() && !isUserText(contents.getJSONObject(0))) contents.remove(0);
        }
    }

    private static boolean isUserText(JSONObject content) {
        if (!"user".equals(content.optString("role"))) return false;
        JSONArray parts = content.optJSONArray("parts");
        return parts != null && !parts.isEmpty() && parts.getJSONObject(0).has("text");
    }
}
