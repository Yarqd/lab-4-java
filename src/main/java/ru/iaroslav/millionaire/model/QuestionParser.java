package ru.iaroslav.millionaire.model;

import java.util.List;

public final class QuestionParser {
    private QuestionParser() {
    }

    public static Question fromTsvLine(int id, String line) {
        String[] parts = line.split("\t");
        if (parts.length != 7) {
            throw new IllegalArgumentException("Ожидалось 7 полей в строке вопроса, получено: " + parts.length);
        }

        return new Question(
                id,
                parts[0].trim(),
                List.of(parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()),
                Integer.parseInt(parts[5].trim()),
                Integer.parseInt(parts[6].trim())
        );
    }
}
