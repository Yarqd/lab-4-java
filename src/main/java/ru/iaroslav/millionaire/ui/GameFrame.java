package ru.iaroslav.millionaire.ui;

import ru.iaroslav.millionaire.ai.QuestionGenerator;
import ru.iaroslav.millionaire.db.QuestionRepository;
import ru.iaroslav.millionaire.db.RecordRepository;
import ru.iaroslav.millionaire.game.AnswerResult;
import ru.iaroslav.millionaire.game.GameSession;
import ru.iaroslav.millionaire.game.HintType;
import ru.iaroslav.millionaire.game.MoneyLadder;
import ru.iaroslav.millionaire.model.PlayerRecord;
import ru.iaroslav.millionaire.model.Question;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public final class GameFrame extends JFrame {
    private static final Color BACKGROUND = new Color(10, 12, 35);
    private static final Color PANEL = new Color(19, 28, 72);
    private static final Color PANEL_LIGHT = new Color(30, 43, 102);
    private static final Color GOLD = new Color(238, 185, 72);
    private static final Color MUTED_GOLD = new Color(176, 142, 80);
    private static final Color TEXT = new Color(245, 247, 255);
    private static final Color DISABLED = new Color(74, 79, 102);
    private static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 28);
    private static final Font QUESTION_FONT = new Font("Arial", Font.BOLD, 20);
    private static final Font ANSWER_FONT = new Font("Arial", Font.BOLD, 16);
    private static final DateTimeFormatter RECORD_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final GameSession gameSession;
    private final RecordRepository recordRepository;
    private final QuestionRepository questionRepository;
    private final QuestionGenerator questionGenerator;
    private final JList<String> levelList = new JList<>(MoneyLadder.descendingLabels());
    private final JTextArea questionText = new JTextArea();
    private final JButton[] answerButtons = new JButton[4];
    private final JButton fiftyButton = new JButton(HintType.FIFTY_FIFTY.title());
    private final JButton audienceButton = new JButton(HintType.AUDIENCE.title());
    private final JButton phoneButton = new JButton(HintType.PHONE.title());
    private final JButton mistakeButton = new JButton(HintType.MISTAKE.title());
    private final JButton replaceButton = new JButton(HintType.REPLACE.title());
    private final JButton takeMoneyButton = new JButton("Забрать деньги");
    private final JButton newGameButton = new JButton("Новая игра");
    private final JButton topButton = new JButton("TOP 10");
    private final JButton aiQuestionButton = new JButton("AI-вопрос");
    private final JLabel playerLabel = new JLabel();
    private final JLabel progressLabel = new JLabel();
    private final JLabel currentPrizeLabel = new JLabel();
    private final JLabel guaranteedLabel = new JLabel();
    private final JLabel hintsLabel = new JLabel();
    private final JLabel messageLabel = new JLabel(" ");

    private String playerName = "Игрок";

    public GameFrame(
            GameSession gameSession,
            RecordRepository recordRepository,
            QuestionRepository questionRepository,
            QuestionGenerator questionGenerator
    ) {
        this.gameSession = gameSession;
        this.recordRepository = recordRepository;
        this.questionRepository = questionRepository;
        this.questionGenerator = questionGenerator;

        setTitle("Кто хочет стать миллионером?");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));
        setSize(1160, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(BACKGROUND);

        add(createHeader(), BorderLayout.NORTH);
        add(createGamePanel(), BorderLayout.CENTER);
        add(createLevelPanel(), BorderLayout.EAST);
        add(createBottomPanel(), BorderLayout.SOUTH);

        SwingUtilities.invokeLater(this::startNewGame);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(12, 14, 0, 14));

        JLabel imageLabel = new JLabel(loadLogo());
        imageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        header.add(imageLabel, BorderLayout.WEST);

        JPanel titlePanel = new JPanel(new GridLayout(3, 1, 0, 4));
        titlePanel.setOpaque(false);

        JLabel title = new JLabel("Кто хочет стать миллионером?");
        title.setForeground(GOLD);
        title.setFont(TITLE_FONT);

        messageLabel.setForeground(TEXT);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 15));

        JPanel actions = new JPanel(new GridLayout(1, 4, 8, 0));
        actions.setOpaque(false);
        styleUtilityButton(newGameButton);
        styleUtilityButton(topButton);
        styleUtilityButton(aiQuestionButton);
        styleUtilityButton(takeMoneyButton);
        newGameButton.addActionListener(event -> startNewGame());
        topButton.addActionListener(event -> showTopRecords());
        aiQuestionButton.addActionListener(event -> generateAiQuestion());
        takeMoneyButton.addActionListener(event -> takeMoney());
        actions.add(newGameButton);
        actions.add(topButton);
        actions.add(aiQuestionButton);
        actions.add(takeMoneyButton);

        titlePanel.add(title);
        titlePanel.add(messageLabel);
        titlePanel.add(actions);
        header.add(titlePanel, BorderLayout.CENTER);

        return header;
    }

    private JPanel createGamePanel() {
        JPanel gamePanel = new JPanel(new BorderLayout(12, 12));
        gamePanel.setBackground(BACKGROUND);
        gamePanel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));

        questionText.setEditable(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setOpaque(true);
        questionText.setBackground(PANEL);
        questionText.setForeground(TEXT);
        questionText.setFont(QUESTION_FONT);
        questionText.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 2),
                BorderFactory.createEmptyBorder(22, 24, 22, 24)
        ));
        gamePanel.add(questionText, BorderLayout.CENTER);

        JPanel answersPanel = new JPanel(new GridLayout(2, 2, 12, 12));
        answersPanel.setOpaque(false);
        for (int i = 0; i < answerButtons.length; i++) {
            JButton button = new JButton();
            button.setActionCommand(String.valueOf(i + 1));
            button.addActionListener(this::answerQuestion);
            styleAnswerButton(button);
            answerButtons[i] = button;
            answersPanel.add(button);
        }
        gamePanel.add(answersPanel, BorderLayout.SOUTH);

        return gamePanel;
    }

    private JPanel createLevelPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        panel.setPreferredSize(new Dimension(230, 0));

        levelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        levelList.setEnabled(false);
        levelList.setFixedCellHeight(34);
        levelList.setFont(new Font("Monospaced", Font.BOLD, 16));
        levelList.setBackground(PANEL);
        levelList.setForeground(TEXT);
        levelList.setSelectionBackground(GOLD);
        levelList.setSelectionForeground(Color.BLACK);
        levelList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setHorizontalAlignment(SwingConstants.RIGHT);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 12));
                return label;
            }
        });

        JLabel title = new JLabel("Лестница выигрыша", SwingConstants.CENTER);
        title.setForeground(GOLD);
        title.setFont(new Font("Arial", Font.BOLD, 17));
        panel.add(title, BorderLayout.NORTH);
        panel.add(new JScrollPane(levelList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(12, 10));
        bottom.setBackground(BACKGROUND);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));

        JPanel status = new JPanel(new GridLayout(1, 5, 8, 0));
        status.setOpaque(false);
        for (JLabel label : List.of(playerLabel, progressLabel, currentPrizeLabel, guaranteedLabel, hintsLabel)) {
            label.setForeground(TEXT);
            label.setFont(new Font("Arial", Font.BOLD, 13));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            status.add(label);
        }

        JPanel hints = new JPanel(new GridLayout(1, 5, 8, 0));
        hints.setOpaque(false);
        styleHintButton(fiftyButton, HintType.FIFTY_FIFTY);
        styleHintButton(audienceButton, HintType.AUDIENCE);
        styleHintButton(phoneButton, HintType.PHONE);
        styleHintButton(mistakeButton, HintType.MISTAKE);
        styleHintButton(replaceButton, HintType.REPLACE);
        hints.add(fiftyButton);
        hints.add(audienceButton);
        hints.add(phoneButton);
        hints.add(mistakeButton);
        hints.add(replaceButton);

        bottom.add(status, BorderLayout.NORTH);
        bottom.add(hints, BorderLayout.SOUTH);
        return bottom;
    }

    private ImageIcon loadLogo() {
        java.net.URL resource = GameFrame.class.getResource("/millionaire.jpg");
        if (resource == null) {
            return new ImageIcon();
        }
        Image image = new ImageIcon(resource).getImage().getScaledInstance(260, 150, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }

    private void styleAnswerButton(JButton button) {
        button.setFont(ANSWER_FONT);
        button.setForeground(TEXT);
        button.setBackground(PANEL_LIGHT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MUTED_GOLD, 2),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
    }

    private void styleHintButton(JButton button, HintType hintType) {
        button.setFont(new Font("Arial", Font.BOLD, 13));
        button.setForeground(Color.BLACK);
        button.setBackground(GOLD);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        switch (hintType) {
            case FIFTY_FIFTY -> button.addActionListener(event -> useFiftyFifty());
            case AUDIENCE -> button.addActionListener(event -> useAudiencePoll());
            case PHONE -> button.addActionListener(event -> usePhoneFriend());
            case MISTAKE -> button.addActionListener(event -> activateMistakeChance());
            case REPLACE -> button.addActionListener(event -> replaceQuestion());
        }
    }

    private void styleUtilityButton(JButton button) {
        button.setFont(new Font("Arial", Font.BOLD, 13));
        button.setForeground(TEXT);
        button.setBackground(PANEL_LIGHT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    private void startNewGame() {
        StartSettings settings = requestStartSettings();
        if (settings == null) {
            if (gameSession.level() == 0) {
                settings = new StartSettings("Игрок", 0);
            } else {
                return;
            }
        }

        try {
            playerName = settings.playerName();
            gameSession.start(settings.guaranteedLevel());
            refreshGame("Игра началась. Вопрос " + gameSession.level() + " из " + MoneyLadder.levelCount() + ".");
        } catch (Exception ex) {
            showError("Не удалось начать игру", ex);
        }
    }

    private StartSettings requestStartSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Имя игрока"), constraints);

        JTextField nameField = new JTextField(playerName, 18);
        constraints.gridx = 1;
        panel.add(nameField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(new JLabel("Несгораемая сумма"), constraints);

        JComboBox<GuaranteedOption> guaranteedBox = new JComboBox<>(guaranteedOptions());
        guaranteedBox.setSelectedIndex(5);
        constraints.gridx = 1;
        panel.add(guaranteedBox, constraints);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Новая игра",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        GuaranteedOption selected = (GuaranteedOption) guaranteedBox.getSelectedItem();
        String name = nameField.getText().isBlank() ? "Игрок" : nameField.getText().trim();
        return new StartSettings(name, selected == null ? 0 : selected.level());
    }

    private GuaranteedOption[] guaranteedOptions() {
        GuaranteedOption[] options = new GuaranteedOption[MoneyLadder.levelCount() + 1];
        options[0] = new GuaranteedOption(0, "Без несгораемой суммы");
        for (int level = 1; level <= MoneyLadder.levelCount(); level++) {
            options[level] = new GuaranteedOption(level, MoneyLadder.format(MoneyLadder.valueForLevel(level)));
        }
        return options;
    }

    private void answerQuestion(ActionEvent event) {
        try {
            int answerNumber = Integer.parseInt(event.getActionCommand());
            AnswerResult result = gameSession.answer(answerNumber);
            switch (result.status()) {
                case CORRECT -> refreshGame(result.message());
                case WRONG_TRY_AGAIN -> {
                    JOptionPane.showMessageDialog(this, result.message(), "Право на ошибку", JOptionPane.INFORMATION_MESSAGE);
                    refreshGame(result.message());
                }
                case GAME_OVER, WON -> finishRound(result);
            }
        } catch (Exception ex) {
            showError("Не удалось обработать ответ", ex);
        }
    }

    private void useFiftyFifty() {
        try {
            gameSession.useFiftyFifty();
            refreshGame("Подсказка 50/50 использована.");
        } catch (Exception ex) {
            showError("Подсказка недоступна", ex);
        }
    }

    private void useAudiencePoll() {
        try {
            Map<Integer, Integer> poll = gameSession.useAudiencePoll();
            StringBuilder text = new StringBuilder("Результаты голосования:\n\n");
            for (int answerNumber = 1; answerNumber <= 4; answerNumber++) {
                text.append(answerLetter(answerNumber))
                        .append(": ")
                        .append(poll.get(answerNumber))
                        .append("% — ")
                        .append(gameSession.currentQuestion().answer(answerNumber))
                        .append('\n');
            }
            JOptionPane.showMessageDialog(this, text.toString(), "Помощь зала", JOptionPane.INFORMATION_MESSAGE);
            refreshGame("Зал проголосовал.");
        } catch (Exception ex) {
            showError("Подсказка недоступна", ex);
        }
    }

    private void usePhoneFriend() {
        try {
            String message = gameSession.usePhoneFriend();
            JOptionPane.showMessageDialog(this, message, "Звонок другу", JOptionPane.INFORMATION_MESSAGE);
            refreshGame("Друг дал совет.");
        } catch (Exception ex) {
            showError("Подсказка недоступна", ex);
        }
    }

    private void activateMistakeChance() {
        try {
            gameSession.activateMistakeChance();
            refreshGame("Право на ошибку активно для текущего вопроса.");
        } catch (Exception ex) {
            showError("Подсказка недоступна", ex);
        }
    }

    private void replaceQuestion() {
        try {
            gameSession.replaceQuestion();
            refreshGame("Вопрос заменён.");
        } catch (Exception ex) {
            showError("Не удалось заменить вопрос", ex);
        }
    }

    private void generateAiQuestion() {
        if (gameSession.level() == 0) {
            return;
        }

        if (!questionGenerator.isConfigured()) {
            JOptionPane.showMessageDialog(
                    this,
                    """
                            Для AI-генерации нужен бесплатный ключ Gemini API.

                            1. Получите ключ в Google AI Studio.
                            2. Запустите проект из терминала так:
                               export GEMINI_API_KEY="ваш_ключ"
                               ./gradlew run

                            Без ключа игра продолжает работать на вопросах из SQLite.
                            """,
                    "GEMINI_API_KEY не задан",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        setControlsEnabled(false);
        aiQuestionButton.setText("Генерация...");
        messageLabel.setText("AI генерирует вопрос текущего уровня...");

        new SwingWorker<Question, Void>() {
            @Override
            protected Question doInBackground() throws Exception {
                Question generatedQuestion = questionGenerator.generateQuestion(gameSession.level());
                return questionRepository.save(generatedQuestion);
            }

            @Override
            protected void done() {
                try {
                    Question savedQuestion = get();
                    gameSession.replaceCurrentQuestion(savedQuestion);
                    refreshGame("AI сгенерировал новый вопрос и сохранил его в SQLite.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    restoreAfterAiFailure(ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    restoreAfterAiFailure(cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
                } finally {
                    aiQuestionButton.setText("AI-вопрос");
                }
            }
        }.execute();
    }

    private void restoreAfterAiFailure(Exception ex) {
        refreshGame("AI-генерация не удалась.");
        showError("Не удалось сгенерировать AI-вопрос", ex);
    }

    private void takeMoney() {
        if (gameSession.level() == 0) {
            return;
        }

        int prize = gameSession.currentPrize();
        int result = JOptionPane.showConfirmDialog(
                this,
                "Забрать " + MoneyLadder.format(prize) + " и завершить игру?",
                "Забрать деньги",
                JOptionPane.YES_NO_OPTION
        );
        if (result == JOptionPane.YES_OPTION) {
            finishRound(new AnswerResult(
                    AnswerResult.Status.GAME_OVER,
                    prize,
                    gameSession.questionsAnswered(),
                    "Вы завершили игру с выигрышем " + MoneyLadder.format(prize) + "."
            ));
        }
    }

    private void finishRound(AnswerResult result) {
        try {
            recordRepository.save(playerName, result.prize(), result.questionsAnswered());
        } catch (SQLException ex) {
            showError("Рекорд не удалось сохранить", ex);
        }

        JOptionPane.showMessageDialog(this, result.message(), "Игра завершена", JOptionPane.INFORMATION_MESSAGE);
        showTopRecords();

        int again = JOptionPane.showConfirmDialog(this, "Начать новую игру?", "Продолжить", JOptionPane.YES_NO_OPTION);
        if (again == JOptionPane.YES_OPTION) {
            startNewGame();
        } else {
            setControlsEnabled(false);
            refreshStatus("Игра завершена.");
        }
    }

    private void showTopRecords() {
        try {
            List<PlayerRecord> records = recordRepository.top(10);
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"#", "Игрок", "Выигрыш", "Ответов", "Дата"},
                    0
            ) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            for (int i = 0; i < records.size(); i++) {
                PlayerRecord record = records.get(i);
                model.addRow(new Object[]{
                        i + 1,
                        record.playerName(),
                        MoneyLadder.format(record.prize()),
                        record.questionsAnswered(),
                        record.createdAt().format(RECORD_DATE)
                });
            }

            JTable table = new JTable(model);
            table.setRowHeight(28);
            table.setFont(new Font("Arial", Font.PLAIN, 14));
            table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
            table.setAutoCreateRowSorter(false);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setPreferredSize(new Dimension(620, 290));
            JOptionPane.showMessageDialog(this, scrollPane, "TOP 10 игроков", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) {
            showError("Не удалось открыть TOP 10", ex);
        }
    }

    private void refreshGame(String message) {
        Question question = gameSession.currentQuestion();
        questionText.setText(question.text());
        questionText.setCaretPosition(0);

        for (int i = 0; i < answerButtons.length; i++) {
            int answerNumber = i + 1;
            JButton button = answerButtons[i];
            button.setText("<html><b>" + answerLetter(answerNumber) + ".</b> "
                    + escapeHtml(question.answer(answerNumber)) + "</html>");
            button.setEnabled(!gameSession.isAnswerHidden(answerNumber));
            button.setBackground(button.isEnabled() ? PANEL_LIGHT : DISABLED);
        }

        levelList.setSelectedIndex(MoneyLadder.listIndexForLevel(gameSession.level()));
        levelList.ensureIndexIsVisible(MoneyLadder.listIndexForLevel(gameSession.level()));
        refreshStatus(message);
        refreshHints();
        setControlsEnabled(true);
    }

    private void refreshStatus(String message) {
        playerLabel.setText("Игрок: " + playerName);
        progressLabel.setText("Вопрос: " + gameSession.level() + "/" + MoneyLadder.levelCount());
        currentPrizeLabel.setText("Выигрыш: " + MoneyLadder.format(gameSession.currentPrize()));
        int selectedGuaranteed = MoneyLadder.valueForLevel(gameSession.guaranteedLevel());
        String guaranteedText = MoneyLadder.format(selectedGuaranteed);
        if (gameSession.guaranteedPrize() == 0 && selectedGuaranteed > 0) {
            guaranteedText += " не достигнута";
        }
        guaranteedLabel.setText("Несгораемая: " + guaranteedText);
        hintsLabel.setText("Подсказки: " + gameSession.usedHintCount() + "/4");
        messageLabel.setText(gameSession.isMistakeChanceActive() ? message + " Право на ошибку активно." : message);
    }

    private void refreshHints() {
        refreshHintButton(fiftyButton, HintType.FIFTY_FIFTY);
        refreshHintButton(audienceButton, HintType.AUDIENCE);
        refreshHintButton(phoneButton, HintType.PHONE);
        refreshHintButton(mistakeButton, HintType.MISTAKE);
        refreshHintButton(replaceButton, HintType.REPLACE);
    }

    private void refreshHintButton(JButton button, HintType hintType) {
        boolean used = gameSession.isHintUsed(hintType);
        button.setText((used ? "✓ " : "") + hintType.title());
        button.setEnabled(gameSession.canUseHint(hintType));
        button.setBackground(button.isEnabled() ? GOLD : DISABLED);
        button.setForeground(button.isEnabled() ? Color.BLACK : Color.LIGHT_GRAY);
    }

    private void setControlsEnabled(boolean enabled) {
        for (JButton button : answerButtons) {
            button.setEnabled(enabled && !gameSession.isAnswerHidden(Integer.parseInt(button.getActionCommand())));
        }
        takeMoneyButton.setEnabled(enabled);
        aiQuestionButton.setEnabled(enabled);
        if (enabled) {
            refreshHints();
        } else {
            for (JButton button : List.of(fiftyButton, audienceButton, phoneButton, mistakeButton, replaceButton)) {
                button.setEnabled(false);
                button.setBackground(DISABLED);
                button.setForeground(Color.LIGHT_GRAY);
            }
        }
    }

    private String answerLetter(int answerNumber) {
        return switch (answerNumber) {
            case 1 -> "A";
            case 2 -> "B";
            case 3 -> "C";
            case 4 -> "D";
            default -> "?";
        };
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }

    private record StartSettings(String playerName, int guaranteedLevel) {
    }

    private record GuaranteedOption(int level, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
