package com.example.superplanningapp.ai;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.*;

class GeminiHttpClientTest {

    @Test
    @DisplayName("Запрос: POST на generateContent нужной модели, ключ в заголовке, а не в адресе")
    void buildsRequest() {
        GeminiHttpClient client = new GeminiHttpClient("secret-key", "gemini-3.5-flash-lite");

        HttpRequest request = client.toHttpRequest(new JSONObject().put("contents", "x"));

        assertEquals("POST", request.method());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",
                request.uri().toString());
        assertFalse(request.uri().toString().contains("secret-key"), "Ключ не должен попадать в URL");
        assertEquals("secret-key", request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertTrue(request.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
    }

    @Test
    @DisplayName("Некорректная конфигурация отклоняется сразу")
    void rejectsBadConfig() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiHttpClient(" ", "gemini-3.5-flash-lite"));
        assertThrows(IllegalArgumentException.class, () -> new GeminiHttpClient("k", "gemini 3.5/../x"));
    }

    @Test
    @DisplayName("200 — JSON ответа")
    void parsesSuccess() throws Exception {
        assertEquals(1, GeminiHttpClient.parseResponse(200, "{\"a\":1}").getInt("a"));
    }

    @Test
    @DisplayName("200 с битым телом — ошибка, а не падение")
    void rejectsBrokenJson() {
        assertThrows(AiException.class, () -> GeminiHttpClient.parseResponse(200, "<html>"));
    }

    @Test
    @DisplayName("404 из реального ответа Gemini: сообщение сервера и подсказка про GEMINI_MODEL")
    void notFoundModel() {
        String body = """
                {"error":{"code":404,"message":"This model models/gemini-2.5-flash-lite is no longer available to new users.","status":"NOT_FOUND"}}""";

        AiException e = assertThrows(AiException.class, () -> GeminiHttpClient.parseResponse(404, body));

        assertEquals(404, e.status());
        assertTrue(e.getMessage().contains("no longer available"));
        assertTrue(e.userMessage().contains("GEMINI_MODEL"));
    }

    @Test
    @DisplayName("Понятные сообщения для типичных ошибок")
    void userMessages() {
        assertTrue(new AiException(400, "API key not valid. Please pass a valid API key.").userMessage().contains("GOOGLE_API_KEY"));
        assertTrue(new AiException(429, "quota").userMessage().contains("Подождите"));
        assertTrue(new AiException(503, "x").userMessage().contains("временно недоступен"));
        assertEquals("нет сети", new AiException(0, "нет сети").userMessage());
    }

    @Test
    @DisplayName("Ошибка не в JSON — показывается начало тела, длинное обрезается")
    void nonJsonError() {
        AiException e = assertThrows(AiException.class, () -> GeminiHttpClient.parseResponse(502, "x".repeat(1000)));
        assertTrue(e.getMessage().length() <= 301);
    }
}
