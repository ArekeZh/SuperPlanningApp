package com.example.superplanningapp.trello;

public class Card {
    private int id;
    private String title;
    private String description;
    private int listId;

    public Card(int id, String title, String description, int listId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.listId = listId;
    }

    // Getters
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getListId() { return listId; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setListId(int listId) { this.listId = listId; }
}
