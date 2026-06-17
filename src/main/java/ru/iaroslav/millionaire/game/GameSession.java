package ru.iaroslav.millionaire.game;

import ru.iaroslav.millionaire.db.QuestionRepository;
import ru.iaroslav.millionaire.model.Question;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class GameSession {
    private static final int MAX_HINTS_PER_GAME = 4;

    private final QuestionRepository questionRepository;
    private final Random random;
    private final Set<Integer> shownQuestionIds = new HashSet<>();
    private final Set<Integer> hiddenAnswers = new HashSet<>();
    private final EnumSet<HintType> usedHints = EnumSet.noneOf(HintType.class);

    private Question currentQuestion;
    private int level;
    private int guaranteedLevel;
    private boolean mistakeChanceActive;

    public GameSession(QuestionRepository questionRepository) {
        this(questionRepository, new Random());
    }

    GameSession(QuestionRepository questionRepository, Random random) {
        this.questionRepository = questionRepository;
        this.random = random;
    }

    public void start(int guaranteedLevel) throws SQLException {
        if (guaranteedLevel < 0 || guaranteedLevel > MoneyLadder.levelCount()) {
            throw new IllegalArgumentException("Некорректная несгораемая сумма.");
        }

        this.level = 0;
        this.guaranteedLevel = guaranteedLevel;
        this.currentQuestion = null;
        this.mistakeChanceActive = false;
        this.shownQuestionIds.clear();
        this.hiddenAnswers.clear();
        this.usedHints.clear();
        loadNextQuestion();
    }

    public AnswerResult answer(int answerNumber) throws SQLException {
        ensureGameStarted();
        if (hiddenAnswers.contains(answerNumber)) {
            return new AnswerResult(
                    AnswerResult.Status.WRONG_TRY_AGAIN,
                    currentPrize(),
                    questionsAnswered(),
                    "Этот вариант уже недоступен."
            );
        }

        if (answerNumber == currentQuestion.rightAnswer()) {
            int answeredLevel = level;
            int prize = MoneyLadder.valueForLevel(answeredLevel);
            if (answeredLevel == MoneyLadder.levelCount()) {
                return new AnswerResult(
                        AnswerResult.Status.WON,
                        prize,
                        answeredLevel,
                        "Поздравляем! Вы ответили на все вопросы."
                );
            }

            loadNextQuestion();
            return new AnswerResult(
                    AnswerResult.Status.CORRECT,
                    prize,
                    answeredLevel,
                    "Верно! Следующий вопрос."
            );
        }

        if (mistakeChanceActive) {
            mistakeChanceActive = false;
            hiddenAnswers.add(answerNumber);
            return new AnswerResult(
                    AnswerResult.Status.WRONG_TRY_AGAIN,
                    currentPrize(),
                    questionsAnswered(),
                    "Ответ неверный, но право на ошибку спасло игру. Попробуйте ещё раз."
            );
        }

        int finalPrize = guaranteedPrize();
        return new AnswerResult(
                AnswerResult.Status.GAME_OVER,
                finalPrize,
                questionsAnswered(),
                "Неверный ответ. Ваш выигрыш: " + MoneyLadder.format(finalPrize)
        );
    }

    public List<Integer> useFiftyFifty() {
        consumeHint(HintType.FIFTY_FIFTY);

        List<Integer> wrongAnswers = new ArrayList<>();
        for (int answerNumber = 1; answerNumber <= 4; answerNumber++) {
            if (answerNumber != currentQuestion.rightAnswer() && !hiddenAnswers.contains(answerNumber)) {
                wrongAnswers.add(answerNumber);
            }
        }

        Collections.shuffle(wrongAnswers, random);
        List<Integer> newlyHidden = wrongAnswers.stream().limit(2).toList();
        hiddenAnswers.addAll(newlyHidden);
        return newlyHidden;
    }

    public Map<Integer, Integer> useAudiencePoll() {
        consumeHint(HintType.AUDIENCE);

        List<Integer> visibleAnswers = visibleAnswers();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int answerNumber = 1; answerNumber <= 4; answerNumber++) {
            result.put(answerNumber, 0);
        }

        if (visibleAnswers.size() == 1) {
            result.put(visibleAnswers.get(0), 100);
            return result;
        }

        int correctAnswer = currentQuestion.rightAnswer();
        int correctShare = Math.max(35, 82 - level * 3 + random.nextInt(13));
        if (!visibleAnswers.contains(correctAnswer)) {
            correctShare = 0;
        }
        correctShare = Math.min(correctShare, 90);
        result.put(correctAnswer, correctShare);

        int remaining = 100 - correctShare;
        List<Integer> visibleWrongAnswers = visibleAnswers.stream()
                .filter(answerNumber -> answerNumber != correctAnswer)
                .toList();

        for (int i = 0; i < visibleWrongAnswers.size(); i++) {
            int answerNumber = visibleWrongAnswers.get(i);
            int share = i == visibleWrongAnswers.size() - 1
                    ? remaining
                    : random.nextInt(remaining + 1);
            result.put(answerNumber, share);
            remaining -= share;
        }

        return result;
    }

    public String usePhoneFriend() {
        consumeHint(HintType.PHONE);

        int correctProbability = Math.max(45, 92 - level * 3);
        boolean friendKnows = random.nextInt(100) < correctProbability;
        int suggestedAnswer;
        int confidence;

        if (friendKnows) {
            suggestedAnswer = currentQuestion.rightAnswer();
            confidence = Math.max(55, correctProbability + random.nextInt(9) - 4);
        } else {
            List<Integer> wrongVisibleAnswers = visibleAnswers().stream()
                    .filter(answerNumber -> answerNumber != currentQuestion.rightAnswer())
                    .toList();
            suggestedAnswer = wrongVisibleAnswers.isEmpty()
                    ? currentQuestion.rightAnswer()
                    : wrongVisibleAnswers.get(random.nextInt(wrongVisibleAnswers.size()));
            confidence = 35 + random.nextInt(26);
        }

        return "Друг выбирает вариант " + answerLetter(suggestedAnswer)
                + " (" + currentQuestion.answer(suggestedAnswer) + "), уверенность " + confidence + "%.";
    }

    public void activateMistakeChance() {
        consumeHint(HintType.MISTAKE);
        mistakeChanceActive = true;
    }

    public Question replaceQuestion() throws SQLException {
        consumeHint(HintType.REPLACE);
        mistakeChanceActive = false;
        hiddenAnswers.clear();
        currentQuestion = questionRepository.findRandomByLevel(level, shownQuestionIds, random);
        shownQuestionIds.add(currentQuestion.id());
        return currentQuestion;
    }

    public void replaceCurrentQuestion(Question question) {
        ensureGameStarted();
        if (question.level() != level) {
            throw new IllegalArgumentException("Сгенерированный вопрос должен иметь текущий уровень сложности.");
        }
        mistakeChanceActive = false;
        hiddenAnswers.clear();
        currentQuestion = question;
        shownQuestionIds.add(question.id());
    }

    public Question currentQuestion() {
        ensureGameStarted();
        return currentQuestion;
    }

    public int level() {
        return level;
    }

    public int guaranteedLevel() {
        return guaranteedLevel;
    }

    public int currentPrize() {
        return MoneyLadder.valueForLevel(questionsAnswered());
    }

    public int guaranteedPrize() {
        return questionsAnswered() >= guaranteedLevel ? MoneyLadder.valueForLevel(guaranteedLevel) : 0;
    }

    public int questionsAnswered() {
        return Math.max(0, level - 1);
    }

    public int usedHintCount() {
        return usedHints.size();
    }

    public boolean isHintUsed(HintType hintType) {
        return usedHints.contains(hintType);
    }

    public boolean canUseHint(HintType hintType) {
        return currentQuestion != null
                && !usedHints.contains(hintType)
                && usedHints.size() < MAX_HINTS_PER_GAME;
    }

    public boolean isMistakeChanceActive() {
        return mistakeChanceActive;
    }

    public Set<Integer> hiddenAnswers() {
        return Set.copyOf(hiddenAnswers);
    }

    public boolean isAnswerHidden(int answerNumber) {
        return hiddenAnswers.contains(answerNumber);
    }

    private void loadNextQuestion() throws SQLException {
        level++;
        hiddenAnswers.clear();
        mistakeChanceActive = false;
        currentQuestion = questionRepository.findRandomByLevel(level, shownQuestionIds, random);
        shownQuestionIds.add(currentQuestion.id());
    }

    private void consumeHint(HintType hintType) {
        ensureGameStarted();
        if (usedHints.contains(hintType)) {
            throw new IllegalStateException("Эта подсказка уже использована.");
        }
        if (usedHints.size() >= MAX_HINTS_PER_GAME) {
            throw new IllegalStateException("За игру можно использовать только 4 подсказки из 5.");
        }
        usedHints.add(hintType);
    }

    private List<Integer> visibleAnswers() {
        List<Integer> result = new ArrayList<>();
        for (int answerNumber = 1; answerNumber <= 4; answerNumber++) {
            if (!hiddenAnswers.contains(answerNumber)) {
                result.add(answerNumber);
            }
        }
        return result;
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

    private void ensureGameStarted() {
        if (currentQuestion == null) {
            throw new IllegalStateException("Игра ещё не начата.");
        }
    }
}
