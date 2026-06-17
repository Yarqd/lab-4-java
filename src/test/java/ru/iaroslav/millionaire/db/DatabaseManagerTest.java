package ru.iaroslav.millionaire.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.iaroslav.millionaire.model.Question;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesQuestionsAndRecordsInSqlite() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("game.db"));
        databaseManager.initialize();

        QuestionRepository questionRepository = new QuestionRepository(databaseManager);
        Question question = questionRepository.findRandomByLevel(15, Set.of(), new Random(42));

        assertEquals(15, question.level());
        assertFalse(question.text().isBlank());

        RecordRepository recordRepository = new RecordRepository(databaseManager);
        recordRepository.save("Test Player", 500, 1);

        assertEquals(1, recordRepository.count());
        assertEquals("Test Player", recordRepository.top(10).getFirst().playerName());

        Question saved = questionRepository.save(new Question(
                0,
                "Тестовый AI-вопрос?",
                List.of("Первый", "Второй", "Третий", "Четвёртый"),
                2,
                5
        ));

        assertEquals(5, saved.level());
        assertEquals(2, saved.rightAnswer());
        assertFalse(saved.id() == 0);
    }
}
