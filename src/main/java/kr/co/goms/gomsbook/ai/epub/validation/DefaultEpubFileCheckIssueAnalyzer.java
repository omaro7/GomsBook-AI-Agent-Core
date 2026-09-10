/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckMessage;
import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;

public class DefaultEpubFileCheckIssueAnalyzer implements EpubFileCheckIssueAnalyzer {

    @Override
    public List<EpubFileCheckIssue> analyze(EpubCheckResult result) {

        if (result == null) throw new IllegalArgumentException("result must not be null.");

        List<EpubFileCheckIssue> issues = new ArrayList<>();

        for (EpubCheckMessage message : result.getMessages()) {

            if (message == null) continue;

            issues.add(analyze(message));
        }

        return List.copyOf(issues);
    }

    private EpubFileCheckIssue analyze(EpubCheckMessage message) {

        return new EpubFileCheckIssue(
                message.getId(),
                message.getSeverity(),
                message.getFile(),
                message.getLine(),
                message.getColumn(),
                message.getMessage(),
                resolveCategory(message));
    }

    private EpubFileCheckIssueCategory resolveCategory(EpubCheckMessage message) {

        String file = safeLower(message.getFile());
        String text = safeLower(message.getMessage());

        if (file.endsWith(".opf")) return resolvePackageCategory(text);
        if (file.endsWith("nav.xhtml")) return EpubFileCheckIssueCategory.NAVIGATION;
        if (file.endsWith(".xhtml") || file.endsWith(".html")) return EpubFileCheckIssueCategory.XHTML;
        if (file.endsWith(".css")) return EpubFileCheckIssueCategory.STYLESHEET;
        if (isImage(file)) return EpubFileCheckIssueCategory.IMAGE;
        if (isResourceIssue(text)) return EpubFileCheckIssueCategory.RESOURCE;

        return EpubFileCheckIssueCategory.UNKNOWN;
    }

    private EpubFileCheckIssueCategory resolvePackageCategory(String text) {

        if (text.contains("manifest")) return EpubFileCheckIssueCategory.MANIFEST;
        if (text.contains("spine") || text.contains("itemref")) return EpubFileCheckIssueCategory.SPINE;
        if (text.contains("metadata") || text.contains("meta")) return EpubFileCheckIssueCategory.METADATA;

        return EpubFileCheckIssueCategory.PACKAGE;
    }

    private boolean isResourceIssue(String text) {

        return text.contains("resource")
                || text.contains("referenced resource")
                || text.contains("could not be found");
    }

    private boolean isImage(String file) {

        return file.endsWith(".jpg")
                || file.endsWith(".jpeg")
                || file.endsWith(".png")
                || file.endsWith(".gif")
                || file.endsWith(".svg")
                || file.endsWith(".webp");
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}