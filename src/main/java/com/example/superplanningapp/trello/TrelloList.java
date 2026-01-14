package com.example.superplanningapp.trello;

public class TrelloList {
    private int id;
    private String title;
    private int boardId;
    private String color; // red, yellow, green, или null

    public TrelloList(int id, String title, int boardId, String color) {
        this.id = id;
        this.title = title;
        this.boardId = boardId;
        this.color = color;
    }

    // Getters
    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getBoardId() { return boardId; }
    public String getColor() { return color; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setColor(String color) { this.color = color; }
}
