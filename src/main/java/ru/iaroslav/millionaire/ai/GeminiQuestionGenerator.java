package ru.iaroslav.millionaire.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import ru.iaroslav.millionaire.model.Question;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class GeminiQuestionGenerator implements QuestionGenerator {
    private static final String DEFAULT_MODEL = "gemini-3.5-flash";
    private static final String API_KEY_ENV = "GEMINI_API_KEY";
    private static final String MODEL_ENV = "GEMINI_MODEL";
    private static final URI BASE_URI = URI.create("https://generativelanguage.googleapis.com/v1beta/models/");
    private static final int MAX_ATTEMPTS = 3;

    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public GeminiQuestionGenerator() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                System.getenv(API_KEY_ENV),
                System.getenv(MODEL_ENV)
        );
    }

    GeminiQuestionGenerator(HttpClient httpClient, String apiKey, String model) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public Question generateQuestion(int level) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Не задан GEMINI_API_KEY.");
        }

        RuntimeException lastParseError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(endpoint())
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(level), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini API вернул HTTP " + response.statusCode() + ": " + response.body());
            }

            try {
                GeneratedQuestion generatedQuestion = parseGeneratedQuestion(response.body());
                return generatedQuestion.toQuestion(level);
            } catch (RuntimeException ex) {
                lastParseError = ex;
            }
        }

        throw new IllegalStateException(
                "Gemini несколько раз вернул некорректный JSON. Последняя ошибка: " + lastParseError.getMessage(),
                lastParseError
        );
    }

    private URI endpoint() {
        return URI.create(BASE_URI + model + ":generateContent");
    }

    private String buildRequestBody(int level) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt(level));

        JsonArray parts = new JsonArray();
        parts.add(textPart);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject request = new JsonObject();
        request.add("contents", contents);
        request.add("generationConfig", generationConfig());
        return gson.toJson(request);
    }

    private JsonObject generationConfig() {
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.5);
        generationConfig.addProperty("maxOutputTokens", 1200);
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.add("responseSchema", responseSchema());
        return generationConfig;
    }

    private JsonObject responseSchema() {
        JsonObject answerItems = new JsonObject();
        answerItems.addProperty("type", "string");

        JsonObject answers = new JsonObject();
        answers.addProperty("type", "array");
        answers.add("items", answerItems);
        answers.addProperty("minItems", 4);
        answers.addProperty("maxItems", 4);

        JsonObject text = new JsonObject();
        text.addProperty("type", "string");

        JsonObject rightAnswer = new JsonObject();
        rightAnswer.addProperty("type", "integer");
        rightAnswer.addProperty("minimum", 1);
        rightAnswer.addProperty("maximum", 4);

        JsonObject properties = new JsonObject();
        properties.add("text", text);
        properties.add("answers", answers);
        properties.add("rightAnswer", rightAnswer);

        JsonArray required = new JsonArray();
        required.add("text");
        required.add("answers");
        required.add("rightAnswer");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    private String prompt(int level) {
        return """
                Сгенерируй один новый вопрос для русскоязычной игры "Кто хочет стать миллионером?".
                Уровень сложности: %d из 15, где 1 — очень простой, 15 — очень сложный.
                Требования:
                - вопрос должен быть на русском языке;
                - ровно 4 варианта ответа;
                - только один вариант должен быть правильным;
                - не используй неоднозначные, спорные и устаревшие факты;
                - не добавляй пояснения, комментарии, markdown или блоки кода;
                - значения полей не должны содержать переносы строк;
                - верни только один валидный JSON-объект по схеме: text, answers, rightAnswer.
                """.formatted(level);
    }

    private GeneratedQuestion parseGeneratedQuestion(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini API не вернул кандидатов ответа.");
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        String finishReason = candidate.has("finishReason") ? candidate.get("finishReason").getAsString() : "";
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new IllegalStateException("Gemini обрезал JSON из-за лимита токенов.");
        }

        JsonObject content = candidate.getAsJsonObject("content");
        JsonArray parts = content == null ? null : content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("Gemini API не вернул текст вопроса.");
        }

        String json = parts.get(0).getAsJsonObject().get("text").getAsString();
        try {
            return gson.fromJson(json, GeneratedQuestion.class).validate();
        } catch (JsonSyntaxException ex) {
            throw new IllegalStateException("Gemini вернул невалидный JSON: " + preview(json), ex);
        }
    }

    private String preview(String value) {
        String compact = value.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 220 ? compact.substring(0, 220) + "..." : compact;
    }

    private record GeneratedQuestion(String text, List<String> answers, int rightAnswer) {
        private GeneratedQuestion validate() {
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("AI вернул пустой текст вопроса.");
            }
            if (answers == null || answers.size() != 4 || answers.stream().anyMatch(answer -> answer == null || answer.isBlank())) {
                throw new IllegalStateException("AI должен вернуть ровно 4 непустых варианта ответа.");
            }
            if (rightAnswer < 1 || rightAnswer > 4) {
                throw new IllegalStateException("AI вернул некорректный номер правильного ответа.");
            }
            return this;
        }

        private Question toQuestion(int level) {
            return new Question(
                    0,
                    text.trim(),
                    answers.stream().map(String::trim).toList(),
                    rightAnswer,
                    level
            );
        }
    }
}
