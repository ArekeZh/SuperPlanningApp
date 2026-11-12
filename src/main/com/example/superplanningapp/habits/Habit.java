package com.example.superplanningapp.habits;

public class Habit {
    private int id;
    private int userId;
    private String title;
    private String color;
    private boolean completedOnSelectedDate; // Выполнена ли в выбранный день

    public Habit(int id, int userId, String title, String color, boolean completedOnSelectedDate) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.color = color;
        this.completedOnSelectedDate = completedOnSelectedDate;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getColor() { return color; }

    public boolean isCompletedOnSelectedDate() { return completedOnSelectedDate; }
    public void setCompletedOnSelectedDate(boolean completed) { this.completedOnSelectedDate = completed; }
}