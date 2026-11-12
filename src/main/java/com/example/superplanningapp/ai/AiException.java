package com.example.superplanningapp.ai;

/** Ошибка общения с моделью: сеть, HTTP-статус или непонятный ответ. */
public class AiException extends Exception {

    private final int status;

    /** @param status HTTP-статус ответа или 0, если до сервера не дошли */
    public AiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Понятное пользователю сообщение для чата. */
    public String userMessage() {
        String msg = getMessage() == null ? "" : getMessage();
        if (status == 400 && msg.toLowerCase().contains("api key")) {
            return "Ключ GOOGLE_API_KEY не подходит. Проверьте его в файле .env.";
        }
        return switch (status) {
            case 0 -> msg;
            case 400 -> "Gemini отклонил запрос: " + msg;
            case 401, 403 -> "Нет доступа к Gemini — проверьте GOOGLE_API_KEY в файле .env. (" + msg + ")";
            case 404 -> "Модель недоступна: " + msg + " Укажите другую модель в GEMINI_MODEL в файле .env.";
            case 429 -> "Слишком много запросов к Gemini. Подождите минуту и попробуйте снова.";
            default -> status >= 500
                    ? "Сервис Gemini временно недоступен (код " + status + "). Попробуйте позже."
                    : "Ошибка Gemini (код " + status + "): " + msg;
        };
    }
}
