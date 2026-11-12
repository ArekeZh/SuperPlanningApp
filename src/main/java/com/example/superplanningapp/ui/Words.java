package com.example.superplanningapp.ui;

/** Тексты для интерфейса: русские окончания и очистка ответа модели. */
public final class Words {

    private Words() { }

    /** plural(3, "задача", "задачи", "задач") → «3 задачи». */
    public static String plural(int n, String one, String few, String many) {
        int mod100 = Math.abs(n) % 100;
        int mod10 = Math.abs(n) % 10;
        String word;
        if (mod100 >= 11 && mod100 <= 14) word = many;
        else if (mod10 == 1) word = one;
        else if (mod10 >= 2 && mod10 <= 4) word = few;
        else word = many;
        return n + " " + word;
    }

    public static String lists(int n) { return plural(n, "список", "списка", "списков"); }

    public static String tasks(int n) { return plural(n, "задача", "задачи", "задач"); }

    /** Убирает Markdown, который модель иногда добавляет вопреки инструкции. */
    public static String plainText(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : s.strip().split("\\R", -1)) {
            String l = line.replaceAll("^\\s*#{1,6}\\s+", "")
                    .replaceAll("^(\\s*)[*-]\\s+", "$1• ")
                    .replace("**", "")
                    .replace("__", "");
            out.append(l).append('\n');
        }
        return out.toString().strip();
    }
}
