package ru.iaroslav.millionaire.db;

import ru.iaroslav.millionaire.model.Question;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class QuestionRepository {
    private final DatabaseManager databaseManager;

    public QuestionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Question findRandomByLevel(int level, Set<Integer> excludedIds, Random random) throws SQLException {
        List<Question> allQuestions = findByLevel(level);
        List<Question> availableQuestions = allQuestions.stream()
                .filter(question -> !excludedIds.contains(question.id()))
                .toList();

        List<Question> source = availableQuestions.isEmpty() ? allQuestions : availableQuestions;
        if (source.isEmpty()) {
            throw new SQLException("В базе нет вопросов уровня " + level + ".");
        }
        return source.get(random.nextInt(source.size()));
    }

    public Question save(Question question) throws SQLException {
        String insertSql = """
                INSERT INTO questions(text, answer1, answer2, answer3, answer4, right_answer, level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, question.text());
            statement.setString(2, question.answer(1));
            statement.setString(3, question.answer(2));
            statement.setString(4, question.answer(3));
            statement.setString(5, question.answer(4));
            statement.setInt(6, question.rightAnswer());
            statement.setInt(7, question.level());
            statement.executeUpdate();

            try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                int id = resultSet.next() ? resultSet.getInt(1) : 0;
                return new Question(id, question.text(), question.answers(), question.rightAnswer(), question.level());
            }
        }
    }

    private List<Question> findByLevel(int level) throws SQLException {
        String query = """
                SELECT id, text, answer1, answer2, answer3, answer4, right_answer, level
                FROM questions
                WHERE level = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, level);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Question> questions = new ArrayList<>();
                while (resultSet.next()) {
                    questions.add(mapQuestion(resultSet));
                }
                return questions;
            }
        }
    }

    private Question mapQuestion(ResultSet resultSet) throws SQLException {
        return new Question(
                resultSet.getInt("id"),
                resultSet.getString("text"),
                List.of(
                        resultSet.getString("answer1"),
                        resultSet.getString("answer2"),
                        resultSet.getString("answer3"),
                        resultSet.getString("answer4")
                ),
                resultSet.getInt("right_answer"),
                resultSet.getInt("level")
        );
    }
}
