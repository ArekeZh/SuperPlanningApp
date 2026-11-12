package com.example.superplanningapp;

import com.example.superplanningapp.ai.AiException;
import com.example.superplanningapp.ai.BoardTools;
import com.example.superplanningapp.ai.DaoBoardGateway;
import com.example.superplanningapp.ai.GeminiHttpClient;
import com.example.superplanningapp.ai.PlanningAgent;
import io.github.cdimascio.dotenv.Dotenv;

import java.time.LocalDate;

/**
 * Точка входа в AI-функции: читает настройки из .env и собирает ассистента.
 *
 * <pre>
 * GOOGLE_API_KEY=...                      обязательно
 * GEMINI_MODEL=gemini-3.5-flash-lite      необязательно, это значение по умолчанию
 * </pre>
 */
public final class AIService {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    private AIService() { }

    /** Новый ассистент с пустой историей для этого пользователя. */
    public static PlanningAgent newAgent(User user) throws AiException {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String key = env.get("GOOGLE_API_KEY");
        if (key == null || key.isBlank()) {
            throw new AiException(0, "Не найден GOOGLE_API_KEY в файле .env. Добавьте строку GOOGLE_API_KEY=ваш_ключ "
                    + "(получить ключ: https://aistudio.google.com/apikey).");
        }
        String model = env.get("GEMINI_MODEL", DEFAULT_MODEL).trim();
        try {
            return new PlanningAgent(new GeminiHttpClient(key.trim(), model),
                    new BoardTools(new DaoBoardGateway(), user.getId()), LocalDate::now);
        } catch (IllegalArgumentException e) {
            throw new AiException(0, e.getMessage());
        }
    }
}
