package ru.iaroslav.millionaire.game;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyLadder {
    private static final int[] VALUES = {
            500,
            1_000,
            2_000,
            3_000,
            5_000,
            10_000,
            15_000,
            25_000,
            50_000,
            100_000,
            200_000,
            400_000,
            800_000,
            1_500_000,
            3_000_000
    };

    private static final DecimalFormat FORMAT = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));

    private MoneyLadder() {
    }

    public static int levelCount() {
        return VALUES.length;
    }

    public static int valueForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level > VALUES.length) {
            throw new IllegalArgumentException("Уровень должен быть не больше " + VALUES.length);
        }
        return VALUES[level - 1];
    }

    public static String format(int amount) {
        if (amount == 0) {
            return "0";
        }
        return FORMAT.format(amount).replace(',', ' ');
    }

    public static String[] descendingLabels() {
        String[] labels = new String[VALUES.length];
        for (int i = 0; i < VALUES.length; i++) {
            int level = VALUES.length - i;
            labels[i] = String.format("%2d. %s", level, format(valueForLevel(level)));
        }
        return labels;
    }

    public static int listIndexForLevel(int level) {
        return VALUES.length - level;
    }
}
