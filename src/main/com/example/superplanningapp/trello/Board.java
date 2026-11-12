package com.example.superplanningapp.trello;

import java.time.LocalDateTime;

public class Board {
    private int id;
    private String title;
    private String description;
    private int userId;
    private LocalDateTime createdAt;

    public Board(int id, String title, String description, int userId, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    // Getters
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
}
