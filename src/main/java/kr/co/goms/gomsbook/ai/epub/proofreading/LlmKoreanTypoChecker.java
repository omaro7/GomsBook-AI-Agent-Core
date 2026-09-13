/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.proofreading;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import kr.co.goms.gomsbook.ai.epub.model.EpubKoreanTypoIssue;
import kr.co.goms.gomsbook.ai.llm.LlmClient;
import kr.co.goms.gomsbook.ai.llm.LlmRequest;
import kr.co.goms.gomsbook.ai.llm.LlmResponse;

public final class LlmKoreanTypoChecker implements KoreanTypoChecker {

    private static final double DEFAULT_TEMPERATURE = 0.0;

    private static final int DEFAULT_MAX_TOKENS = 4096;

    private static final double DEFAULT_MIN_CONFIDENCE = 0.80;

    private final LlmClient llmClient;

    private final String model;

    private final Gson gson;

    private final double minConfidence;

    public LlmKoreanTypoChecker(
            LlmClient llmClient,
            String model) {

        this(
                llmClient,
                model,
                DEFAULT_MIN_CONFIDENCE,
                new Gson());
    }

    public LlmKoreanTypoChecker(
            LlmClient llmClient,
            String model,
            double minConfidence,
            Gson gson) {

        this.llmClient =
                Objects.requireNonNull(
                        llmClient,
                        "llmClient must not be null.");

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                    "model must not be blank.");
        }

        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException(
                    "minConfidence must be between 0.0 and 1.0.");
        }

        this.gson =
                Objects.requireNonNull(
                        gson,
                        "gson must not be null.");

        this.model = model.trim();

        this.minConfidence = minConfidence;
    }

    @Override
    public List<EpubKoreanTypoIssue> check(
            Path xhtmlPath) {

        Objects.requireNonNull(
                xhtmlPath,
                "xhtmlPath must not be null.");

        if (!Files.isRegularFile(xhtmlPath)) {
            throw new IllegalArgumentException(
                    "XHTML file was not found: " + xhtmlPath);
        }

        try {

            String content =
                    Files.readString(
                            xhtmlPath,
                            StandardCharsets.UTF_8);

            if (content.isBlank()) {
                return List.of();
            }

            String responseContent =
                    requestLlm(
                            xhtmlPath,
                            content);

            List<LlmIssue> llmIssues =
                    parseResponse(
                            responseContent);

            return mapIssues(
                    xhtmlPath,
                    content,
                    llmIssues);

        } catch (RuntimeException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to check Korean typo: "
                            + xhtmlPath,
                    exception);
        }
    }

    private String requestLlm(
            Path xhtmlPath,
            String content) {

        LlmRequest request =
                LlmRequest.builder()
                        .model(model)
                        .systemMessage(
                                createSystemPrompt())
                        .userMessage(
                                createUserPrompt(
                                        xhtmlPath,
                                        content))
                        .temperature(
                                DEFAULT_TEMPERATURE)
                        .maxTokens(
                                DEFAULT_MAX_TOKENS)
                        .stream(
                                false)
                        .build();

        llmClient.requireAvailable();

        LlmResponse response =
                llmClient.chat(
                        request);

        if (response == null) {
            throw new IllegalStateException(
                    "LLM returned null response.");
        }

        if (!response.hasContent()) {
            throw new IllegalStateException(
                    "LLM returned empty response.");
        }

        String result =
                response.getContent();

        if (result == null || result.isBlank()) {
            throw new IllegalStateException(
                    "LLM returned empty Korean typo result.");
        }

        return normalizeJson(
                result);
    }

    private String createSystemPrompt() {

        return """
                You are a professional Korean proofreading engine for EPUB publications.

                Analyze only human-readable Korean text contained in the supplied XHTML.

                Detect only clear proofreading issues:
                - Korean spelling errors
                - spacing errors
                - obvious typographical errors
                - incorrect particles or endings
                - grammatical errors
                - contextually incorrect Korean words

                Do not report:
                - authorial style
                - intentional colloquial expressions
                - literary expressions
                - proper nouns
                - person names
                - place names
                - foreign words
                - XHTML tags
                - HTML attributes
                - CSS class names
                - element IDs
                - file paths
                - URLs

                The original value must exactly match text present in the supplied XHTML.

                Do not rewrite complete sentences.
                Return only the smallest incorrect text span and its correction.

                confidence must be between 0.0 and 1.0.

                Return JSON only.

                Required response format:

                {
                  "issues": [
                    {
                      "original": "incorrect text",
                      "suggestion": "corrected text",
                      "type": "SPELLING",
                      "reason": "short explanation",
                      "confidence": 0.95
                    }
                  ]
                }

                Allowed type values:
                SPELLING
                SPACING
                TYPO
                GRAMMAR
                PARTICLE
                CONTEXT

                If no issue exists, return:

                {
                  "issues": []
                }
                """;
    }

    private String createUserPrompt(
            Path xhtmlPath,
            String content) {

        return """
                Check the following EPUB XHTML document for Korean proofreading issues.

                File:
                %s

                XHTML:
                ---BEGIN XHTML---
                %s
                ---END XHTML---

                Return JSON only.
                """.formatted(
                xhtmlPath.getFileName(),
                content);
    }

    private List<LlmIssue> parseResponse(
            String responseContent) {

        JsonElement root =
                JsonParser.parseString(
                        responseContent);

        if (!root.isJsonObject()) {
            throw new IllegalStateException(
                    "Invalid LLM typo response: root must be an object.");
        }

        JsonObject object =
                root.getAsJsonObject();

        JsonArray issues =
                object.has("issues")
                        && object.get("issues").isJsonArray()
                        ? object.getAsJsonArray("issues")
                        : new JsonArray();

        List<LlmIssue> result =
                new ArrayList<>();

        for (JsonElement element : issues) {

            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject issue =
                    element.getAsJsonObject();

            String original =
                    getString(
                            issue,
                            "original");

            String suggestion =
                    getString(
                            issue,
                            "suggestion");

            String type =
                    getString(
                            issue,
                            "type");

            String reason =
                    getString(
                            issue,
                            "reason");

            double confidence =
                    getDouble(
                            issue,
                            "confidence");

            if (original.isBlank()) {
                continue;
            }

            if (suggestion.isBlank()) {
                continue;
            }

            if (original.equals(suggestion)) {
                continue;
            }

            if (confidence < minConfidence) {
                continue;
            }

            result.add(
                    new LlmIssue(
                            original,
                            suggestion,
                            type,
                            reason,
                            confidence));
        }

        return result;
    }

    private List<EpubKoreanTypoIssue> mapIssues(
            Path file,
            String content,
            List<LlmIssue> llmIssues) {

        List<EpubKoreanTypoIssue> result =
                new ArrayList<>();

        for (LlmIssue issue : llmIssues) {

            int searchOffset = 0;

            while (searchOffset < content.length()) {

                int index =
                        content.indexOf(
                                issue.original(),
                                searchOffset);

                if (index < 0) {
                    break;
                }

                result.add(new EpubKoreanTypoIssue(
                        file.getFileName().toString(),
                        issue.original(),
                        issue.suggestion(),
                        issue.type(),
                        issue.reason(),
                        issue.confidence(),
                        getLineNumber(content, index),
                        index,
                        index + issue.original().length()));

                searchOffset =
                        index + issue.original().length();
            }
        }

        return List.copyOf(
                result);
    }

    private int getLineNumber(
            String text,
            int offset) {

        int line = 1;

        for (int i = 0; i < offset; i++) {

            if (text.charAt(i) == '\n') {
                line++;
            }
        }

        return line;
    }

    private String normalizeJson(
            String value) {

        String normalized =
                value.trim();

        if (normalized.startsWith("```json")) {
            normalized =
                    normalized.substring(7);
        } else if (normalized.startsWith("```")) {
            normalized =
                    normalized.substring(3);
        }

        if (normalized.endsWith("```")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 3);
        }

        return normalized.trim();
    }

    private String getString(
            JsonObject object,
            String name) {

        if (!object.has(name)) {
            return "";
        }

        JsonElement value =
                object.get(name);

        if (value == null || value.isJsonNull()) {
            return "";
        }

        return value.getAsString().trim();
    }

    private double getDouble(
            JsonObject object,
            String name) {

        if (!object.has(name)) {
            return 0.0;
        }

        JsonElement value =
                object.get(name);

        if (value == null || value.isJsonNull()) {
            return 0.0;
        }

        try {
            return value.getAsDouble();
        } catch (RuntimeException exception) {
            return 0.0;
        }
    }

    private record LlmIssue(
            String original,
            String suggestion,
            String type,
            String reason,
            double confidence) {
    }
}