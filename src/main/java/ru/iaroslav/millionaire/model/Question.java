package ru.iaroslav.millionaire.model;

import java.util.List;
import java.util.Objects;

public final class Question {
    private final int id;
    private final String text;
    private final List<String> answers;
    private final int rightAnswer;
    private final int level;

    public Question(int id, String text, List<String> answers, int rightAnswer, int level) {
        if (answers.size() != 4) {
            throw new IllegalArgumentException("У вопроса должно быть ровно 4 варианта ответа.");
        }
        if (rightAnswer < 1 || rightAnswer > 4) {
            throw new IllegalArgumentException("Номер правильного ответа должен быть от 1 до 4.");
        }
        if (level < 1 || level > 15) {
            throw new IllegalArgumentException("Уровень вопроса должен быть от 1 до 15.");
        }

        this.id = id;
        this.text = Objects.requireNonNull(text).trim();
        this.answers = List.copyOf(answers);
        this.rightAnswer = rightAnswer;
        this.level = level;
    }

    public int id() {
        return id;
    }

    public String text() {
        return text;
    }

    public List<String> answers() {
        return answers;
    }

    public String answer(int answerNumber) {
        if (answerNumber < 1 || answerNumber > 4) {
            throw new IllegalArgumentException("Номер ответа должен быть от 1 до 4.");
        }
        return answers.get(answerNumber - 1);
    }

    public int rightAnswer() {
        return rightAnswer;
    }

    public int level() {
        return level;
    }
}
