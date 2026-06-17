package ru.iaroslav.millionaire.ai;

import ru.iaroslav.millionaire.model.Question;

public interface QuestionGenerator {
    boolean isConfigured();

    Question generateQuestion(int level) throws Exception;
}
