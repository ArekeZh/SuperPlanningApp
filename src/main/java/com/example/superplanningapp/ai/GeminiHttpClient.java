package com.example.superplanningapp.ai;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Клиент Gemini API (generateContent). Ключ передаётся в заголовке x-goog-api-key,
 * а не в адресе — так он не попадает в логи и историю прокси.
 */
public final class GeminiHttpClient implements ModelClient {

    public static final URI DEFAULT_BASE = URI.create("https://generativelanguage.googleapis.com/");
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final String apiKey;
    private final String model;
    private final URI base;
    private final Duration timeout;
    private final HttpClient http;

    public GeminiHttpClient(String apiKey, String model) {
        this(apiKey, model, DEFAULT_BASE, Duration.ofSeconds(90),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    GeminiHttpClient(String apiKey, String model, URI base, Duration timeout, HttpClient http) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Пустой ключ API");
        if (model == null || !MODEL_NAME.matcher(model).matches()) {
            throw new IllegalArgumentException("Некорректное имя модели в GEMINI_MODEL: «" + model + "»");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.base = base;
        this.timeout = timeout;
        this.http = http;
    }

    public String model() {
        return model;
    }

    @Override
    public JSONObject generate(JSONObject request) throws AiException, InterruptedException {
        try {
            HttpResponse<String> response = http.send(toHttpRequest(request),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            throw new AiException(0, "Gemini не ответил вовремя. Попробуйте ещё раз.");
        } catch (IOException e) {
            throw new AiException(0, "Нет соединения с Gemini: " + e.getMessage());
        }
    }

    HttpRequest toHttpRequest(JSONObject body) {
        URI uri = base.resolve("v1beta/models/" + model + ":generateContent");
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    static JSONObject parseResponse(int status, String body) throws AiException {
        if (status == 200) {
            try {
                return new JSONObject(body);
            } catch (JSONException e) {
                throw new AiException(status, "Gemini вернул ответ, который не удалось разобрать.");
            }
        }
        String message = body == null ? "" : body.trim();
        try {
            JSONObject error = body == null ? null : new JSONObject(body).optJSONObject("error");
            if (error != null && !error.optString("message").isBlank()) message = error.getString("message");
        } catch (JSONException ignored) {
            // не JSON — покажем начало тела как есть
        }
        if (message.length() > 300) message = message.substring(0, 300) + "…";
        throw new AiException(status, message);
    }
}
