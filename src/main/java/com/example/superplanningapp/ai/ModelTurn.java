package com.example.superplanningapp.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Один ответ модели: текст и/или вызовы функций.
 *
 * @param content исходный объект content из ответа. Его нужно вернуть в историю
 *                без изменений: в нём лежат thoughtSignature, без которых Gemini 3
 *                отклоняет следующий запрос с вызовами функций.
 */
public record ModelTurn(JSONObject content, String text, List<FunctionCall> calls, String finishReason) {

    public ModelTurn {
        calls = List.copyOf(calls);
    }

    public record FunctionCall(String name, JSONObject args, String id) { }

    public static ModelTurn parse(JSONObject response) throws AiException {
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            JSONObject feedback = response.optJSONObject("promptFeedback");
            String reason = feedback == null ? "" : feedback.optString("blockReason", "");
            throw new AiException(0, reason.isBlank()
                    ? "Gemini не прислал ответа. Попробуйте ещё раз."
                    : "Gemini отказался отвечать на этот запрос (" + reason + "). Попробуйте переформулировать.");
        }

        JSONObject candidate = candidates.getJSONObject(0);
        String finish = candidate.optString("finishReason", "");
        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new AiException(0, "MALFORMED_FUNCTION_CALL".equals(finish)
                    ? "Модель ошиблась при вызове функции. Попробуйте переформулировать запрос."
                    : "Gemini прислал пустой ответ" + (finish.isBlank() ? "" : " (" + finish + ")") + ". Попробуйте ещё раз.");
        }
        if (!content.has("role")) content.put("role", "model");

        StringBuilder text = new StringBuilder();
        List<FunctionCall> calls = new ArrayList<>();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            JSONObject call = part.optJSONObject("functionCall");
            if (call != null) {
                JSONObject args = call.optJSONObject("args");
                String id = call.optString("id", "");
                calls.add(new FunctionCall(call.optString("name", ""), args == null ? new JSONObject() : args,
                        id.isBlank() ? null : id));
            } else if (part.has("text") && !part.optBoolean("thought", false)) {
                text.append(part.optString("text", ""));
            }
        }
        return new ModelTurn(content, text.toString(), calls, finish);
    }
}
