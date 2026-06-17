package ru.iaroslav.millionaire.game;

public record AnswerResult(Status status, int prize, int questionsAnswered, String message) {
    public enum Status {
        CORRECT,
        WRONG_TRY_AGAIN,
        GAME_OVER,
        WON
    }
}
