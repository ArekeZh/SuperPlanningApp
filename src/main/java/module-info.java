module com.example.superplanningapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires io.github.cdimascio.dotenv.java;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires jbcrypt;
    requires org.json;             // Чтобы работать с JSON
    requires java.net.http;
    requires com.zaxxer.hikari;        // Чтобы отправлять запросы в Интернет (к ИИ)


    opens com.example.superplanningapp to javafx.fxml;
    exports com.example.superplanningapp;
    exports com.example.superplanningapp.trello;
    opens com.example.superplanningapp.trello to javafx.fxml;
}