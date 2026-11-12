package com.example.superplanningapp;

public class User {
    private int id;
    private String username; // Это будет логин/никнейм
    private String email;
    private String phone;
    // Новые поля
    private String firstName;
    private String lastName;
    private String birthday;
    private String avatarPath;

    public User(int id, String username, String email, String phone) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.phone = phone;
    }

    // Геттеры и Сеттеры для новых полей
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }

    // Метод для получения полного имени (или Ника, если имя пустое)
    public String getDisplayName() {
        if (firstName != null && !firstName.isEmpty()) {
            return firstName + " " + (lastName != null ? lastName : "");
        }
        return username;
    }
}