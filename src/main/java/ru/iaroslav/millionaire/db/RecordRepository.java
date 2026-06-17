package ru.iaroslav.millionaire.db;

import ru.iaroslav.millionaire.model.PlayerRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class RecordRepository {
    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabaseManager databaseManager;

    public RecordRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(String playerName, int prize, int questionsAnswered) throws SQLException {
        String sql = """
                INSERT INTO records(player_name, prize, questions_answered, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeName(playerName));
            statement.setInt(2, prize);
            statement.setInt(3, questionsAnswered);
            statement.executeUpdate();
        }
    }

    public List<PlayerRecord> top(int limit) throws SQLException {
        String sql = """
                SELECT id, player_name, prize, questions_answered, created_at
                FROM records
                ORDER BY prize DESC, questions_answered DESC, created_at ASC
                LIMIT ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PlayerRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(new PlayerRecord(
                            resultSet.getInt("id"),
                            resultSet.getString("player_name"),
                            resultSet.getInt("prize"),
                            resultSet.getInt("questions_answered"),
                            parseDateTime(resultSet.getString("created_at"))
                    ));
                }
                return records;
            }
        }
    }

    public int count() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM records")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private String normalizeName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return "Игрок";
        }
        return playerName.trim();
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value, SQLITE_DATE_TIME);
    }
}
