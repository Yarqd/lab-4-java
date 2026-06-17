package ru.iaroslav.millionaire.model;

import java.time.LocalDateTime;

public record PlayerRecord(
        int id,
        String playerName,
        int prize,
        int questionsAnswered,
        LocalDateTime createdAt
) {
}
