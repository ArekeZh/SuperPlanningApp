package com.example.superplanningapp.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** «Модель» по сценарию: отдаёт заранее заданные ответы и запоминает все запросы. */
final class ScriptedModel implements ModelClient {

    final List<JSONObject> requests = new ArrayList<>();
    private final Deque<Object> script = new ArrayDeque<>();

    ScriptedModel then(JSONObject response) {
        script.add(response);
        return this;
    }

    ScriptedModel thenFail(AiException error) {
        script.add(error);
        return this;
    }

    @Override
    public JSONObject generate(JSONObject request) throws AiException {
        requests.add(new JSONObject(request.toString())); // копия: агент меняет историю дальше
        Object next = script.poll();
        if (next == null) throw new AssertionError("Модель вызвана больше раз, чем предусмотрено сценарием");
        if (next instanceof AiException e) throw e;
        return (JSONObject) next;
    }

    JSONObject lastRequest() {
        return requests.get(requests.size() - 1);
    }

    // ---------------------------------------------------- building responses

    static JSONObject text(String text) {
        return response(new JSONObject().put("text", text));
    }

    static JSONObject call(String name, JSONObject args) {
        return response(callPart(name, args));
    }

    static JSONObject callPart(String name, JSONObject args) {
        return new JSONObject().put("functionCall", new JSONObject().put("name", name).put("args", args));
    }

    static JSONObject response(JSONObject... parts) {
        JSONArray p = new JSONArray();
        for (JSONObject part : parts) p.put(part);
        return new JSONObject().put("candidates", new JSONArray().put(new JSONObject()
                .put("content", new JSONObject().put("role", "model").put("parts", p))
                .put("finishReason", "STOP")));
    }

    /** Аргументы create_board: списки вида {"Сделать", "задача 1", "задача 2"}. */
    static JSONObject boardArgs(String title, String[]... lists) {
        JSONArray ls = new JSONArray();
        for (String[] list : lists) {
            JSONArray cards = new JSONArray();
            for (int i = 1; i < list.length; i++) cards.put(new JSONObject().put("title", list[i]));
            ls.put(new JSONObject().put("title", list[0]).put("cards", cards));
        }
        return new JSONObject().put("title", title).put("lists", ls);
    }
}
