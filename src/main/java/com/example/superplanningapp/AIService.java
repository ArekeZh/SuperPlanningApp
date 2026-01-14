package com.example.superplanningapp;

import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class AIService {

    // API-Key загружается из .env файла
    private static final String API_KEY;
    private static final String API_URL;

    static {
        try {
            Dotenv dotenv = Dotenv.load();
            API_KEY = dotenv.get("GOOGLE_API_KEY");
            if (API_KEY == null || API_KEY.isEmpty()) {
                throw new RuntimeException("GOOGLE_API_KEY не найден в .env файле! Пожалуйста, добавьте GOOGLE_API_KEY=your_key в .env");
            }
            API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + API_KEY;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки GOOGLE_API_KEY из .env: " + e.getMessage(), e);
        }
    }

    public static CompletableFuture<String> askAI(String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Создаем JSON-структуру для запроса
                JSONObject textPart = new JSONObject();

                // --- НАЧАЛО ИЗМЕНЕНИЙ ---
                // Задаю промпт для иишки чтобы только по теме отвечал
                String systemPrompt =
                        "Ты — строгий помощник по планированию распорядка дня. " +
                                "Твоя единственная цель — помогать составлять расписание и управлять временем. " +
                                "ВАЖНОЕ ПРАВИЛО: Если вопрос пользователя не касается планирования, расписания, задач или продуктивности, " +
                                "ты ДОЛЖЕН ответить только одной фразой: 'Я могу отвечать лишь на вопросы о распорядке дня.'. " +
                                "Не давай ответов на отвлеченные темы (погода, политика, развлечения и т.д.). " +
                                "Отвечай на русском языке. " +
                                "\n\nСообщение пользователя: " + userMessage;

                textPart.put("text", systemPrompt);
                // --- КОНЕЦ ИЗМЕНЕНИЙ ---

                JSONObject parts = new JSONObject();
                parts.put("parts", new JSONArray().put(textPart));

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("contents", new JSONArray().put(parts));

                // 2. Настраиваем HTTP-клиент
                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString(), StandardCharsets.UTF_8))
                        .build();

                // 3. Отправляем запрос
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // 4. Обрабатываем ответ
                if (response.statusCode() == 200) {
                    JSONObject responseJson = new JSONObject(response.body());

                    return responseJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
                } else {
                    System.err.println("Ошибка API (" + response.statusCode() + "): " + response.body());
                    return "Ошибка сервера: " + response.statusCode();
                }

            } catch (Exception e) {
                e.printStackTrace();
                return "Ошибка соединения: " + e.getMessage();
            }
        });
    }
}