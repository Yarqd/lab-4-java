package ru.iaroslav.millionaire.db;

import ru.iaroslav.millionaire.model.Question;
import ru.iaroslav.millionaire.model.QuestionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final Path databasePath;

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
    }

    public void initialize() throws SQLException, IOException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        try (Connection connection = getConnection()) {
            createTables(connection);
            if (countQuestions(connection) == 0) {
                seedQuestions(connection);
            }
        }
    }

    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS questions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        text TEXT NOT NULL,
                        answer1 TEXT NOT NULL,
                        answer2 TEXT NOT NULL,
                        answer3 TEXT NOT NULL,
                        answer4 TEXT NOT NULL,
                        right_answer INTEGER NOT NULL CHECK(right_answer BETWEEN 1 AND 4),
                        level INTEGER NOT NULL CHECK(level BETWEEN 1 AND 15)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_name TEXT NOT NULL,
                        prize INTEGER NOT NULL,
                        questions_answered INTEGER NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private int countQuestions(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM questions")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void seedQuestions(Connection connection) throws SQLException, IOException {
        InputStream inputStream = DatabaseManager.class.getResourceAsStream("/questions.tsv");
        if (inputStream == null) {
            throw new IOException("Не найден ресурс questions.tsv для первичного заполнения базы.");
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        String insertSql = """
                INSERT INTO questions(text, answer1, answer2, answer3, answer4, right_answer, level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             PreparedStatement statement = connection.prepareStatement(insertSql)) {
            String line;
            int sourceLine = 0;
            int inserted = 0;
            while ((line = reader.readLine()) != null) {
                sourceLine++;
                if (line.isBlank()) {
                    continue;
                }

                Question question = QuestionParser.fromTsvLine(sourceLine, line);
                statement.setString(1, question.text());
                statement.setString(2, question.answer(1));
                statement.setString(3, question.answer(2));
                statement.setString(4, question.answer(3));
                statement.setString(5, question.answer(4));
                statement.setInt(6, question.rightAnswer());
                statement.setInt(7, question.level());
                statement.addBatch();
                inserted++;
            }
            statement.executeBatch();
            connection.commit();

            if (inserted == 0) {
                throw new IOException("Файл questions.tsv пустой.");
            }
        } catch (SQLException | IOException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
