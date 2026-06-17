package ru.iaroslav.millionaire;

import ru.iaroslav.millionaire.ai.GeminiQuestionGenerator;
import ru.iaroslav.millionaire.ai.QuestionGenerator;
import ru.iaroslav.millionaire.db.DatabaseManager;
import ru.iaroslav.millionaire.db.QuestionRepository;
import ru.iaroslav.millionaire.db.RecordRepository;
import ru.iaroslav.millionaire.game.GameSession;
import ru.iaroslav.millionaire.ui.GameFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "--init-db".equals(args[0])) {
            initializeDatabaseOnly();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
                // Default Swing look and feel is good enough if Nimbus is unavailable.
            }

            try {
                DatabaseManager databaseManager = new DatabaseManager(Path.of("WhoWantsToBeAMillionaire.db"));
                databaseManager.initialize();

                QuestionRepository questionRepository = new QuestionRepository(databaseManager);
                RecordRepository recordRepository = new RecordRepository(databaseManager);
                GameSession gameSession = new GameSession(questionRepository);
                QuestionGenerator questionGenerator = new GeminiQuestionGenerator();

                GameFrame frame = new GameFrame(gameSession, recordRepository, questionRepository, questionGenerator);
                frame.setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        null,
                        "Не удалось запустить игру:\n" + ex.getMessage(),
                        "Ошибка запуска",
                        JOptionPane.ERROR_MESSAGE
                );
                ex.printStackTrace();
            }
        });
    }

    private static void initializeDatabaseOnly() {
        try {
            DatabaseManager databaseManager = new DatabaseManager(Path.of("WhoWantsToBeAMillionaire.db"));
            databaseManager.initialize();
            System.out.println("SQLite database is ready: WhoWantsToBeAMillionaire.db");
        } catch (Exception ex) {
            System.err.println("Не удалось подготовить базу данных: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
