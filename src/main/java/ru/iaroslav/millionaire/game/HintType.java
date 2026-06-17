package ru.iaroslav.millionaire.game;

public enum HintType {
    FIFTY_FIFTY("50/50"),
    AUDIENCE("Помощь зала"),
    PHONE("Звонок другу"),
    MISTAKE("Право на ошибку"),
    REPLACE("Замена вопроса");

    private final String title;

    HintType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
