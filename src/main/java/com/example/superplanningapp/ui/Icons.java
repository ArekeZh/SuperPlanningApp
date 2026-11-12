package com.example.superplanningapp.ui;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * Линейные иконки 24×24 в духе Lucide (как в Nalu.Prep). Цвет и толщину линии
 * задаёт CSS-класс .icon, поэтому иконки сами перекрашиваются со сменой темы.
 */
public final class Icons {

    public static final String HOME = "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z";
    public static final String KANBAN = "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8 7v7M12 7v4M16 7v9";
    public static final String HABITS = "M22 11.08V12a10 10 0 1 1-5.93-9.14M22 4L12 14.01l-3-3";
    public static final String SPARKLES = "M12 3l1.9 5.8L20 11l-6.1 2.2L12 19l-1.9-5.8L4 11l6.1-2.2zM19 3v4M17 5h4M5 17v4M3 19h4";
    public static final String WAVE = "M2 9c2.5 0 2.5-3 5-3s2.5 3 5 3 2.5-3 5-3 2.5 3 5 3M2 15c2.5 0 2.5-3 5-3s2.5 3 5 3 2.5-3 5-3 2.5 3 5 3M2 21c2.5 0 2.5-3 5-3s2.5 3 5 3 2.5-3 5-3 2.5 3 5 3";
    public static final String LOGOUT = "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9";
    public static final String SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41";
    public static final String MOON = "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z";
    public static final String MENU = "M4 6h16M4 12h16M4 18h16";
    public static final String PLUS = "M12 5v14M5 12h14";
    public static final String X = "M18 6L6 18M6 6l12 12";
    public static final String CHECK = "M20 6L9 17l-5-5";
    public static final String CHEVRON_LEFT = "M15 18l-6-6 6-6";
    public static final String CHEVRON_RIGHT = "M9 18l6-6-6-6";
    public static final String ARROW_LEFT = "M19 12H5M12 19l-7-7 7-7";
    public static final String ARROW_RIGHT = "M5 12h14M12 5l7 7-7 7";
    public static final String TRASH = "M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6";
    public static final String PENCIL = "M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z";
    public static final String BELL = "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0";
    public static final String USER_PLUS = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM19 8v6M22 11h-6";
    public static final String USERS = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75";
    public static final String USER = "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2M12 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8z";
    public static final String SEND = "M22 2L11 13M22 2l-7 20-4-9-9-4z";
    public static final String CALENDAR = "M8 2v4M16 2v4M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM3 10h18";
    public static final String PHONE = "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z";
    public static final String MAIL = "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM22 6l-10 7L2 6";
    public static final String CAMERA = "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3zM12 10a3 3 0 1 0 0 6a3 3 0 1 0 0-6z";
    public static final String MORE = "M12 4.5a1 1 0 1 0 0 2a1 1 0 1 0 0-2zM12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2zM12 17.5a1 1 0 1 0 0 2a1 1 0 1 0 0-2z";
    public static final String CHART = "M3 3v18h18M7 15l4-4 3 3 5-6";
    public static final String FLAME = "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3 0 1.4 1.1 2.5 2.5 2.5z";
    public static final String COPY = "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2zM4 16a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2";
    public static final String INBOX = "M22 12h-6l-2 3h-4l-2-3H2M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z";

    private Icons() { }

    /** Иконка нужного размера в квадратной рамке; клики проходят сквозь неё к кнопке. */
    public static StackPane of(String path, double size) {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.getStyleClass().add("icon");
        double k = size / 24.0;
        shape.setScaleX(k);
        shape.setScaleY(k);

        StackPane box = new StackPane(new Group(shape));
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }
}
