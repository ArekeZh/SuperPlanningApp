package com.example.superplanningapp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    // Единственный экземпляр пула соединений
    private static HikariDataSource dataSource;

    // Статический блок инициализации (запускается один раз при старте)
    static {
        try {
            Dotenv dotenv = Dotenv.load();

            // Настройка конфигурации HikariCP
            HikariConfig config = new HikariConfig();

            // Данные для подключения из .env
            String url = "jdbc:postgresql://" +
                    dotenv.get("DB_HOST") + ":" +
                    dotenv.get("DB_PORT") + "/" +
                    dotenv.get("DB_NAME");

            config.setJdbcUrl(url);
            config.setUsername(dotenv.get("DB_USER"));
            config.setPassword(dotenv.get("DB_PASSWORD"));
            config.setDriverClassName("org.postgresql.Driver");

            // ОПТИМИЗАЦИЯ ПОД AWS (ОБЛАКО)(Arlan делал(я))

            // Максимальное число соединений в пуле (выбрал 10)
            config.setMaximumPoolSize(10);

            // Минимальное кол-во "горячих" соединений, готовых к работе
            config.setMinimumIdle(2);

            // Время жизни простаивающего соединения (30 сек)
            config.setIdleTimeout(30000);

            // Максимальное время жизни соединения (10 минут)
            config.setMaxLifetime(600000);

            // Сколько ждать свободного соединения, если все заняты (10 сек)
            config.setConnectionTimeout(10000);

            // Создаю пул
            dataSource = new HikariDataSource(config);
            System.out.println("Успешное подключение к пулу AWS RDS");

        } catch (Exception e) {
            System.err.println("Ошибка инициализации пула соединений: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Метод теперь берет готовое соединение из пула, а не создает новое
    // Это происходит за миллисекунды, а не за секунды
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource не инициализирован (проверьте .env и логи)");
        }
        return dataSource.getConnection();
    }

    // Метод для закрытия пула при выходе из приложения (опционально)
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}