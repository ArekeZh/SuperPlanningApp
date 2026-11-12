package com.example.superplanningapp.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.superplanningapp.ai.ScriptedModel.boardArgs;
import static org.junit.jupiter.api.Assertions.*;

class BoardToolsTest {

    private static final int ME = 7;
    private static final int STRANGER = 99;

    private InMemoryGateway db;
    private BoardTools tools;
    private final List<PlannedAction> asked = new ArrayList<>();
    private final ActionConfirmer yes = a -> { asked.add(a); return true; };
    private final ActionConfirmer no = a -> { asked.add(a); return false; };
    private final ActionConfirmer mustNotAsk = a -> { throw new AssertionError("Пользователя не должны были спрашивать"); };

    @BeforeEach
    void setUp() {
        db = new InMemoryGateway();
        tools = new BoardTools(db, ME);
    }

    // ------------------------------------------------------------- schema

    @Test
    @DisplayName("Модель видит ровно три функции и не может указать id пользователя")
    void declarationsDoNotExposeUserId() {
        JSONArray decl = BoardTools.declarations();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < decl.length(); i++) {
            JSONObject d = decl.getJSONObject(i);
            names.add(d.getString("name"));
            assertFalse(d.getString("description").isBlank());
            assertFalse(d.toString().contains("user_id"), "Схема не должна принимать user_id: " + d);
        }
        assertEquals(Set.of("list_boards", "create_board", "add_tasks"), names);
    }

    // ------------------------------------------------------- create_board

    @Test
    @DisplayName("create_board: после подтверждения доска со списками и задачами сохраняется")
    void createBoardAfterConfirmation() throws Exception {
        JSONObject args = boardArgs("  Экзамен по физике  ",
                new String[]{"Теория", "Механика", "Оптика"},
                new String[]{"Практика", "Решить вариант 2023"},
                new String[]{"Готово"});
        args.put("description", "Подготовка за две недели");

        BoardTools.Outcome out = tools.handle("create_board", args, yes);

        assertEquals("created", out.response().getString("status"));
        InMemoryGateway.Board board = db.only();
        assertEquals(ME, board.owner, "Доска создаётся от имени текущего пользователя");
        assertEquals("Экзамен по физике", board.title, "Пробелы по краям обрезаются");
        assertEquals(List.of("Теория", "Практика", "Готово"), board.lists.stream().map(l -> l.title).toList());
        assertEquals(3, board.cardCount());
        assertEquals(board.id, out.response().getInt("board_id"));
        assertEquals(3, out.response().getJSONArray("lists").length());

        assertEquals(1, asked.size());
        PlannedAction action = asked.get(0);
        assertEquals(PlannedAction.Kind.CREATE_BOARD, action.kind());
        assertEquals("Подготовка за две недели", action.plan().description());
        assertEquals(3, action.plan().cardCount());

        ActionResult result = out.result();
        assertNotNull(result);
        assertEquals(board.id, result.boardId());
        assertEquals(3, result.lists());
        assertEquals(3, result.cards());
    }

    @Test
    @DisplayName("create_board: отказ пользователя — в базу ничего не пишется, модель узнаёт об отмене")
    void createBoardDeclined() throws Exception {
        BoardTools.Outcome out = tools.handle("create_board", boardArgs("Переезд", new String[]{"Сделать", "Коробки"}), no);

        assertEquals("cancelled_by_user", out.response().getString("status"));
        assertTrue(db.boards.isEmpty());
        assertNull(out.result());
        assertEquals(1, asked.size());
    }

    @Test
    @DisplayName("create_board: без названия — ошибка для модели, пользователя не спрашиваем")
    void createBoardWithoutTitle() throws Exception {
        JSONObject args = boardArgs("   ", new String[]{"Сделать", "Задача"});

        BoardTools.Outcome out = tools.handle("create_board", args, mustNotAsk);

        assertEquals("error", out.response().getString("status"));
        assertTrue(out.response().getString("error").contains("названия"));
        assertTrue(db.boards.isEmpty());
    }

    @Test
    @DisplayName("create_board: без списков — ошибка")
    void createBoardWithoutLists() throws Exception {
        BoardTools.Outcome out = tools.handle("create_board", new JSONObject().put("title", "Пусто"), mustNotAsk);
        assertEquals("error", out.response().getString("status"));
        assertTrue(db.boards.isEmpty());
    }

    @Test
    @DisplayName("create_board: слишком много списков — ошибка с подсказкой лимита")
    void createBoardTooManyLists() throws Exception {
        String[][] lists = new String[BoardTools.MAX_LISTS + 1][];
        for (int i = 0; i < lists.length; i++) lists[i] = new String[]{"Список " + i};

        BoardTools.Outcome out = tools.handle("create_board", boardArgs("Много", lists), mustNotAsk);

        assertTrue(out.response().getString("error").contains(String.valueOf(BoardTools.MAX_LISTS)));
    }

    @Test
    @DisplayName("create_board: слишком много задач на доске — ошибка")
    void createBoardTooManyCardsTotal() throws Exception {
        String[][] lists = new String[3][];
        for (int i = 0; i < 3; i++) {
            lists[i] = new String[BoardTools.MAX_CARDS_PER_LIST + 1];
            lists[i][0] = "Список " + i;
            for (int j = 1; j < lists[i].length; j++) lists[i][j] = "Задача " + j;
        }
        // 3 × 25 = 75 > 60
        BoardTools.Outcome out = tools.handle("create_board", boardArgs("Огромная", lists), mustNotAsk);
        assertEquals("error", out.response().getString("status"));
    }

    @Test
    @DisplayName("create_board: задача без названия — ошибка с номером задачи и списком")
    void createBoardCardWithoutTitle() throws Exception {
        JSONObject args = new JSONObject().put("title", "Доска").put("lists", new JSONArray().put(new JSONObject()
                .put("title", "Сделать")
                .put("cards", new JSONArray().put(new JSONObject().put("title", "Ок")).put(new JSONObject().put("description", "без названия")))));

        String error = tools.handle("create_board", args, mustNotAsk).response().getString("error");

        assertTrue(error.contains("№2") && error.contains("Сделать"), error);
    }

    @Test
    @DisplayName("create_board: задачи строками тоже принимаются")
    void createBoardCardsAsPlainStrings() throws Exception {
        JSONObject args = new JSONObject().put("title", "Доска").put("lists", new JSONArray().put(new JSONObject()
                .put("title", "Сделать").put("cards", new JSONArray().put("Первая").put("  Вторая "))));

        tools.handle("create_board", args, yes);

        assertEquals(List.of("Первая", "Вторая"),
                db.only().lists.get(0).cards.stream().map(BoardPlan.CardPlan::title).toList());
    }

    @Test
    @DisplayName("create_board: длинные названия обрезаются до размера колонки в базе")
    void createBoardLongTitleTruncated() throws Exception {
        tools.handle("create_board", boardArgs("я".repeat(400), new String[]{"Сделать"}), yes);
        assertEquals(BoardTools.MAX_TITLE, db.only().title.length());
    }

    @Test
    @DisplayName("Сбой базы превращается в понятную ошибку для модели, а не в исключение")
    void databaseFailureReportedToModel() throws Exception {
        db.failWrites = true;
        BoardTools.Outcome out = tools.handle("create_board", boardArgs("Доска", new String[]{"Сделать"}), yes);

        assertEquals("error", out.response().getString("status"));
        assertTrue(out.response().getString("error").contains("connection refused"));
        assertNull(out.result());
    }

    // ---------------------------------------------------------- add_tasks

    @Test
    @DisplayName("add_tasks: в существующий список (без учёта регистра) — новый список не создаётся")
    void addTasksToExistingList() throws Exception {
        InMemoryGateway.Board board = db.seed(ME, "Курсовая", "Сделать", "Готово");
        JSONObject args = new JSONObject().put("board_id", board.id).put("list_title", "сделать")
                .put("cards", new JSONArray().put(new JSONObject().put("title", "Написать введение")));

        BoardTools.Outcome out = tools.handle("add_tasks", args, yes);

        assertEquals("added", out.response().getString("status"));
        assertFalse(out.response().getBoolean("list_created"));
        assertEquals(2, board.lists.size());
        assertEquals(1, board.list("Сделать").cards.size());
        assertEquals(PlannedAction.Kind.ADD_TASKS, asked.get(0).kind());
        assertEquals(board.id, asked.get(0).boardId());
        assertEquals(0, out.result().lists());
        assertEquals(1, out.result().cards());
    }

    @Test
    @DisplayName("add_tasks: несуществующий список создаётся")
    void addTasksCreatesList() throws Exception {
        InMemoryGateway.Board board = db.seed(ME, "Курсовая", "Сделать");
        JSONObject args = new JSONObject().put("board_id", board.id).put("list_title", "Идеи")
                .put("cards", new JSONArray().put("Добавить тёмную тему"));

        BoardTools.Outcome out = tools.handle("add_tasks", args, yes);

        assertTrue(out.response().getBoolean("list_created"));
        assertNotNull(board.list("Идеи"));
        assertEquals(1, out.result().lists());
    }

    @Test
    @DisplayName("add_tasks: чужая доска недоступна — ничего не пишется и пользователя не спрашивают")
    void addTasksToForeignBoardRejected() throws Exception {
        InMemoryGateway.Board foreign = db.seed(STRANGER, "Чужая", "Сделать");
        JSONObject args = new JSONObject().put("board_id", foreign.id).put("list_title", "Сделать")
                .put("cards", new JSONArray().put("Взлом"));

        BoardTools.Outcome out = tools.handle("add_tasks", args, mustNotAsk);

        assertEquals("error", out.response().getString("status"));
        assertTrue(out.response().getString("error").contains("list_boards"));
        assertEquals(0, foreign.cardCount());
    }

    @Test
    @DisplayName("add_tasks: на доске, где пользователь участник, — можно")
    void addTasksToMemberBoardAllowed() throws Exception {
        InMemoryGateway.Board shared = db.seed(STRANGER, "Общая", "Сделать");
        shared.members.add(ME);
        JSONObject args = new JSONObject().put("board_id", shared.id).put("list_title", "Сделать")
                .put("cards", new JSONArray().put("Моя часть"));

        assertEquals("added", tools.handle("add_tasks", args, yes).response().getString("status"));
        assertEquals(1, shared.cardCount());
    }

    @Test
    @DisplayName("add_tasks: board_id строкой или дробным числом 5.0 тоже понимается")
    void addTasksBoardIdFormats() throws Exception {
        InMemoryGateway.Board board = db.seed(ME, "Курсовая", "Сделать");
        JSONObject asString = new JSONObject().put("board_id", String.valueOf(board.id)).put("list_title", "Сделать")
                .put("cards", new JSONArray().put("А"));
        JSONObject asDouble = new JSONObject().put("board_id", (double) board.id).put("list_title", "Сделать")
                .put("cards", new JSONArray().put("Б"));

        assertEquals("added", tools.handle("add_tasks", asString, yes).response().getString("status"));
        assertEquals("added", tools.handle("add_tasks", asDouble, yes).response().getString("status"));
        assertEquals(2, board.cardCount());
    }

    @Test
    @DisplayName("add_tasks: без задач или с нечисловым board_id — ошибка")
    void addTasksInvalidArgs() throws Exception {
        InMemoryGateway.Board board = db.seed(ME, "Курсовая", "Сделать");
        JSONObject noCards = new JSONObject().put("board_id", board.id).put("list_title", "Сделать");
        JSONObject badId = new JSONObject().put("board_id", "abc").put("list_title", "Сделать")
                .put("cards", new JSONArray().put("А"));

        assertEquals("error", tools.handle("add_tasks", noCards, mustNotAsk).response().getString("status"));
        assertEquals("error", tools.handle("add_tasks", badId, mustNotAsk).response().getString("status"));
    }

    // -------------------------------------------------------- list_boards

    @Test
    @DisplayName("list_boards: только доступные доски, с ролью и списками; без подтверждения")
    void listBoardsOnlyAccessible() throws Exception {
        db.seed(ME, "Моя", "Сделать", "Готово");
        InMemoryGateway.Board shared = db.seed(STRANGER, "Общая", "Сделать");
        shared.members.add(ME);
        db.seed(STRANGER, "Чужая", "Сделать");

        JSONObject response = tools.handle("list_boards", new JSONObject(), mustNotAsk).response();

        assertEquals(2, response.getInt("count"));
        JSONArray boards = response.getJSONArray("boards");
        assertEquals("Моя", boards.getJSONObject(0).getString("title"));
        assertEquals("владелец", boards.getJSONObject(0).getString("role"));
        assertEquals(2, boards.getJSONObject(0).getJSONArray("lists").length());
        assertEquals("участник", boards.getJSONObject(1).getString("role"));
    }

    @Test
    @DisplayName("Неизвестная функция — ошибка со списком доступных")
    void unknownFunction() throws Exception {
        JSONObject response = tools.handle("delete_everything", new JSONObject(), mustNotAsk).response();
        assertTrue(response.getString("error").contains("create_board"));
    }

    @Test
    @DisplayName("Аргументы null не роняют обработку")
    void nullArgs() throws Exception {
        assertEquals("error", tools.handle("create_board", null, mustNotAsk).response().getString("status"));
    }
}
