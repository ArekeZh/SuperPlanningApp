package com.example.superplanningapp.ai;

import com.example.superplanningapp.ai.BoardGateway.BoardInfo;
import com.example.superplanningapp.ai.BoardGateway.CreatedBoard;
import com.example.superplanningapp.ai.BoardGateway.CreatedList;
import com.example.superplanningapp.ai.BoardPlan.CardPlan;
import com.example.superplanningapp.ai.BoardPlan.ListPlan;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Инструменты, которые видит модель, и их выполнение.
 *
 * <p>Правила безопасности:
 * <ul>
 *   <li>id пользователя модели не передаётся и не принимается от неё — он задан здесь;</li>
 *   <li>всё, что пишет в базу, выполняется только после подтверждения пользователя;</li>
 *   <li>аргументы проверяются до вопроса пользователю; ошибка возвращается модели текстом,
 *       чтобы она могла исправиться;</li>
 *   <li>добавлять задачи можно только в доски, доступные пользователю.</li>
 * </ul>
 */
public final class BoardTools {

    public static final String LIST_BOARDS = "list_boards";
    public static final String CREATE_BOARD = "create_board";
    public static final String ADD_TASKS = "add_tasks";

    public static final int MAX_LISTS = 8;
    public static final int MAX_CARDS_PER_LIST = 25;
    public static final int MAX_CARDS_TOTAL = 60;
    static final int MAX_TITLE = 255;   // VARCHAR(255) в таблицах boards, lists, cards
    static final int MAX_DESCRIPTION = 2000;

    private final BoardGateway gateway;
    private final int userId;

    public BoardTools(BoardGateway gateway, int userId) {
        this.gateway = gateway;
        this.userId = userId;
    }

    /** Результат вызова: ответ для модели и (если что-то записано) итог для интерфейса. */
    public record Outcome(JSONObject response, ActionResult result) { }

    // ------------------------------------------------------------ schema

    public static JSONArray declarations() {
        JSONObject card = object()
                .put("properties", new JSONObject()
                        .put("title", string("Короткое конкретное название задачи"))
                        .put("description", string("Что именно сделать, 1–2 предложения (необязательно)")))
                .put("required", new JSONArray().put("title"));

        JSONObject list = object()
                .put("properties", new JSONObject()
                        .put("title", string("Название списка: «Сделать», «В работе», «Готово» или этап проекта"))
                        .put("cards", array("Задачи этого списка по порядку", card)))
                .put("required", new JSONArray().put("title"));

        JSONObject createBoard = new JSONObject()
                .put("name", CREATE_BOARD)
                .put("description", "Создаёт новую канбан-доску пользователя со списками и задачами. "
                        + "Перед созданием пользователь видит план и подтверждает его.")
                .put("parameters", object()
                        .put("properties", new JSONObject()
                                .put("title", string("Название доски"))
                                .put("description", string("Короткое описание цели доски"))
                                .put("lists", array("Списки доски (обычно 2–5) с задачами", list)))
                        .put("required", new JSONArray().put("title").put("lists")));

        JSONObject addTasks = new JSONObject()
                .put("name", ADD_TASKS)
                .put("description", "Добавляет задачи в существующую доску пользователя. Если списка с таким "
                        + "названием нет, он будет создан. board_id бери только из ответа list_boards.")
                .put("parameters", object()
                        .put("properties", new JSONObject()
                                .put("board_id", new JSONObject().put("type", "integer").put("description", "id доски из list_boards"))
                                .put("list_title", string("Название списка, куда добавить задачи"))
                                .put("cards", array("Добавляемые задачи", card)))
                        .put("required", new JSONArray().put("board_id").put("list_title").put("cards")));

        JSONObject listBoards = new JSONObject()
                .put("name", LIST_BOARDS)
                .put("description", "Возвращает доски пользователя (id, название, описание, роль) и их списки. "
                        + "Вызывай, когда нужно найти существующую доску.");

        return new JSONArray().put(listBoards).put(createBoard).put(addTasks);
    }

    private static JSONObject object() { return new JSONObject().put("type", "object"); }

    private static JSONObject string(String description) {
        return new JSONObject().put("type", "string").put("description", description);
    }

    private static JSONObject array(String description, JSONObject items) {
        return new JSONObject().put("type", "array").put("description", description).put("items", items);
    }

    // --------------------------------------------------------- execution

    public Outcome handle(String name, JSONObject args, ActionConfirmer confirmer) throws InterruptedException {
        JSONObject a = args == null ? new JSONObject() : args;
        try {
            return switch (name == null ? "" : name) {
                case LIST_BOARDS -> listBoards();
                case CREATE_BOARD -> createBoard(a, confirmer);
                case ADD_TASKS -> addTasks(a, confirmer);
                default -> error("Неизвестная функция «" + name + "». Доступны: list_boards, create_board, add_tasks.");
            };
        } catch (InvalidArgs e) {
            return error(e.getMessage());
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return error("Не удалось выполнить действие в базе данных: " + e.getMessage());
        }
    }

    private Outcome listBoards() throws Exception {
        JSONArray boards = new JSONArray();
        for (BoardInfo b : gateway.boardsOf(userId)) {
            JSONArray lists = new JSONArray();
            for (BoardGateway.ListInfo l : b.lists()) lists.put(new JSONObject().put("id", l.id()).put("title", l.title()));
            boards.put(new JSONObject()
                    .put("id", b.id())
                    .put("title", b.title())
                    .put("description", b.description() == null ? "" : b.description())
                    .put("role", b.owner() ? "владелец" : "участник")
                    .put("lists", lists));
        }
        return new Outcome(new JSONObject().put("boards", boards).put("count", boards.length()), null);
    }

    private Outcome createBoard(JSONObject args, ActionConfirmer confirmer) throws Exception {
        BoardPlan plan = parseNewBoard(args);
        if (!confirmer.confirm(new PlannedAction(PlannedAction.Kind.CREATE_BOARD, plan, null))) return cancelled();

        CreatedBoard created = gateway.createBoard(userId, plan);
        JSONArray lists = new JSONArray();
        for (CreatedList l : created.lists()) {
            lists.put(new JSONObject().put("list_id", l.id()).put("title", l.title()).put("cards_created", l.cards()));
        }
        JSONObject response = new JSONObject()
                .put("status", "created")
                .put("board_id", created.id())
                .put("title", plan.title())
                .put("lists", lists);
        return new Outcome(response, new ActionResult(PlannedAction.Kind.CREATE_BOARD, created.id(), plan.title(),
                plan.description(), created.lists().size(), plan.cardCount()));
    }

    private Outcome addTasks(JSONObject args, ActionConfirmer confirmer) throws Exception {
        int boardId = requiredInt(args, "board_id");
        String listTitle = requiredText(args, "list_title", "Не указан список (list_title).");
        List<CardPlan> cards = parseCards(args.opt("cards"), listTitle, true);

        BoardInfo board = gateway.boardsOf(userId).stream()
                .filter(b -> b.id() == boardId)
                .findFirst()
                .orElseThrow(() -> new InvalidArgs("Доска с id " + boardId + " не найдена среди досок пользователя. "
                        + "Вызови list_boards, чтобы узнать правильный id."));

        BoardPlan plan = new BoardPlan(board.title(), board.description(), List.of(new ListPlan(listTitle, cards)));
        if (!confirmer.confirm(new PlannedAction(PlannedAction.Kind.ADD_TASKS, plan, boardId))) return cancelled();

        CreatedList list = gateway.addCards(boardId, listTitle, cards);
        JSONObject response = new JSONObject()
                .put("status", "added")
                .put("board_id", boardId)
                .put("list_id", list.id())
                .put("list_title", list.title())
                .put("list_created", list.createdNow())
                .put("cards_added", cards.size());
        return new Outcome(response, new ActionResult(PlannedAction.Kind.ADD_TASKS, boardId, board.title(),
                board.description(), list.createdNow() ? 1 : 0, cards.size()));
    }

    // -------------------------------------------------------- validation

    private BoardPlan parseNewBoard(JSONObject args) throws InvalidArgs {
        String title = requiredText(args, "title", "У доски нет названия (title).");
        String description = optionalText(args, "description", MAX_DESCRIPTION);

        JSONArray lists = args.optJSONArray("lists");
        if (lists == null || lists.isEmpty()) {
            throw new InvalidArgs("Нужен хотя бы один список (lists) с задачами.");
        }
        if (lists.length() > MAX_LISTS) {
            throw new InvalidArgs("Слишком много списков: " + lists.length() + ". Максимум — " + MAX_LISTS + ".");
        }

        List<ListPlan> result = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < lists.length(); i++) {
            JSONObject l = lists.optJSONObject(i);
            if (l == null) throw new InvalidArgs("Список №" + (i + 1) + " должен быть объектом с полями title и cards.");
            String listTitle = requiredText(l, "title", "У списка №" + (i + 1) + " нет названия.");
            List<CardPlan> cards = parseCards(l.opt("cards"), listTitle, false);
            total += cards.size();
            result.add(new ListPlan(listTitle, cards));
        }
        if (total > MAX_CARDS_TOTAL) {
            throw new InvalidArgs("Слишком много задач: " + total + ". Максимум — " + MAX_CARDS_TOTAL + " на доску.");
        }
        return new BoardPlan(title, description, result);
    }

    private static List<CardPlan> parseCards(Object raw, String listTitle, boolean required) throws InvalidArgs {
        JSONArray cards = raw instanceof JSONArray arr ? arr : null;
        if (cards == null || cards.isEmpty()) {
            if (required) throw new InvalidArgs("Нужна хотя бы одна задача (cards).");
            return List.of();
        }
        if (cards.length() > MAX_CARDS_PER_LIST) {
            throw new InvalidArgs("В списке «" + listTitle + "» слишком много задач: " + cards.length()
                    + ". Максимум — " + MAX_CARDS_PER_LIST + ".");
        }
        List<CardPlan> result = new ArrayList<>();
        for (int j = 0; j < cards.length(); j++) {
            String missing = "У задачи №" + (j + 1) + " в списке «" + listTitle + "» нет названия.";
            JSONObject c = cards.optJSONObject(j);
            if (c == null) {
                // Модель иногда присылает задачи просто строками — принимаем и так.
                String text = cards.optString(j, "").trim();
                if (text.isEmpty()) throw new InvalidArgs(missing);
                result.add(new CardPlan(cap(text, MAX_TITLE), ""));
            } else {
                result.add(new CardPlan(requiredText(c, "title", missing), optionalText(c, "description", MAX_DESCRIPTION)));
            }
        }
        return result;
    }

    private static String requiredText(JSONObject o, String key, String errorMessage) throws InvalidArgs {
        String value = text(o.opt(key));
        if (value.isEmpty()) throw new InvalidArgs(errorMessage);
        return cap(value, MAX_TITLE);
    }

    private static String optionalText(JSONObject o, String key, int max) {
        return cap(text(o.opt(key)), max);
    }

    private static String text(Object v) {
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v).strip();
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).strip();
    }

    private static int requiredInt(JSONObject o, String key) throws InvalidArgs {
        Object v = o.opt(key);
        try {
            if (v instanceof Number n) {
                double d = n.doubleValue();
                if (d == Math.rint(d) && d > 0 && d <= Integer.MAX_VALUE) return (int) d;
            } else if (v instanceof String s && !s.isBlank()) {
                int parsed = Integer.parseInt(s.trim());
                if (parsed > 0) return parsed;
            }
        } catch (NumberFormatException ignored) {
            // ниже — общее сообщение
        }
        throw new InvalidArgs("Нужен числовой " + key + ". Узнай его через list_boards.");
    }

    private static Outcome error(String message) {
        return new Outcome(new JSONObject().put("status", "error").put("error", message), null);
    }

    private static Outcome cancelled() {
        return new Outcome(new JSONObject()
                .put("status", "cancelled_by_user")
                .put("note", "Пользователь отказался. Ничего не создано. Не повторяй вызов — спроси, что изменить."), null);
    }

    private static final class InvalidArgs extends Exception {
        InvalidArgs(String message) { super(message); }
    }
}
