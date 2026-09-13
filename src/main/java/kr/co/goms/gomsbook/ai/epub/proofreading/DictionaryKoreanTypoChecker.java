/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.proofreading;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubKoreanTypoIssue;

public final class DictionaryKoreanTypoChecker implements KoreanTypoChecker {

    private final Path dictionaryPath;

    private final Map<String, String> typoDictionary = new LinkedHashMap<>();

    public DictionaryKoreanTypoChecker(Path dictionaryPath) {

        this.dictionaryPath = Objects.requireNonNull(dictionaryPath, "dictionaryPath must not be null.");

        loadDictionary();
    }

    @Override
    public List<EpubKoreanTypoIssue> check(Path xhtmlPath) {

        Objects.requireNonNull(xhtmlPath, "xhtmlPath must not be null.");

        if (!Files.isRegularFile(xhtmlPath)) {
            throw new IllegalArgumentException("XHTML file was not found: " + xhtmlPath);
        }

        try {

            String content = Files.readString(xhtmlPath, StandardCharsets.UTF_8);
            List<EpubKoreanTypoIssue> issues = new ArrayList<>();

            for (Map.Entry<String, String> entry : typoDictionary.entrySet()) {

                String original = entry.getKey();
                String suggestion = entry.getValue();

                int searchOffset = 0;

                while (searchOffset < content.length()) {

                    int index = content.indexOf(original, searchOffset);

                    if (index < 0) {
                        break;
                    }

                    issues.add(new EpubKoreanTypoIssue(
                            xhtmlPath.getFileName().toString(),
                            original,
                            suggestion,
                            "DICTIONARY",
                            "내부 한글 오타 사전에 등록된 교정 항목입니다.",
                            1.0,
                            getLineNumber(content, index),
                            index,
                            index + original.length()));

                    searchOffset = index + original.length();
                }
            }

            return List.copyOf(issues);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to check Korean typo: " + xhtmlPath, exception);
        }
    }

    private void loadDictionary() {

        if (!Files.isRegularFile(dictionaryPath)) {
            throw new IllegalStateException("Korean typo dictionary was not found: " + dictionaryPath);
        }

        try {

            for (String line : Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8)) {

                String value = line.trim();

                if (value.isEmpty()) {
                    continue;
                }

                if (value.startsWith("#")) {
                    continue;
                }

                int separator = value.indexOf('=');

                if (separator <= 0) {
                    continue;
                }

                String original = value.substring(0, separator).trim();
                String suggestion = value.substring(separator + 1).trim();

                if (original.isEmpty() || suggestion.isEmpty()) {
                    continue;
                }

                typoDictionary.put(original, suggestion);
            }

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to load Korean typo dictionary: " + dictionaryPath, exception);
        }
    }

    private int getLineNumber(String text, int offset) {

        int line = 1;

        for (int i = 0; i < offset; i++) {

            if (text.charAt(i) == '\n') {
                line++;
            }
        }

        return line;
    }
}