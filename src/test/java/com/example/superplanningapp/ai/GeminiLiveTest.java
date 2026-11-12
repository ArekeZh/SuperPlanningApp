package com.example.superplanningapp.ai;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка на настоящем Gemini (тратит несколько запросов вашего ключа).
 * По умолчанию пропускается. Запуск:
 * <pre>
 *   PowerShell:  $env:GEMINI_LIVE="1"; .\mvnw.cmd test -Dtest=GeminiLiveTest
 * </pre>
 * Доски создаются в памяти — ваша база не трогается.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE", matches = "1")
class GeminiLiveTest {

    @Test
    @DisplayName("Настоящий Gemini создаёт доску, а затем добавляет в неё задачу по контексту диалога")
    void createsBoardThenAddsTask() throws Exception {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String key = env.get("GOOGLE_API_KEY");
        assertNotNull(key, "Нужен GOOGLE_API_KEY в .env");
        String model = env.get("GEMINI_MODEL", "gemini-3.5-flash-lite");

        InMemoryGateway db = new InMemoryGateway();
        PlanningAgent agent = new PlanningAgent(new GeminiHttpClient(key.trim(), model),
                new BoardTools(db, 1), LocalDate::now);

        PlanningAgent.Reply first = agent.send("Создай доску «Экзамен по физике» со списками «Теория», «Задачи», "
                + "«Готово». В «Теория» — задачи про механику и оптику, в «Задачи» — две задачи на решение вариантов. "
                + "Ничего не уточняй, сразу создавай.", a -> true);
        System.out.println("[1] " + first.text());

        InMemoryGateway.Board board = db.only();
        assertEquals(1, first.actions().size(), "Должна быть создана одна доска");
        assertTrue(board.lists.size() >= 2, "Списков: " + board.lists.size());
        assertTrue(board.cardCount() >= 3, "Задач: " + board.cardCount());
        int before = board.cardCount();

        PlanningAgent.Reply second = agent.send("Добавь в эту доску в список «Задачи» задачу "
                + "«Решить вариант 2023 года». Ничего не уточняй.", a -> true);
        System.out.println("[2] " + second.text());

        assertEquals(1, db.boards.size(), "Новая доска не должна создаваться");
        assertEquals(before + 1, board.cardCount(), "Должна добавиться ровно одна задача");
        assertNotNull(board.list("Задачи"));
        assertTrue(board.list("Задачи").cards.stream().anyMatch(c -> c.title().contains("2023")));
    }
}
