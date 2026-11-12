package com.example.superplanningapp.ai;

import org.json.JSONObject;

/** Транспорт к модели: отправить запрос generateContent, получить JSON-ответ. */
@FunctionalInterface
public interface ModelClient {

    JSONObject generate(JSONObject request) throws AiException, InterruptedException;
}
