/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation.fix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kr.co.goms.gomsbook.ai.epub.validation.EpubFileCheckIssue;

public final class DefaultEpubFileCheckFixPlan implements EpubFileCheckFixPlan {

    private static final String UPDATE_METADATA_TOOL = "update_epub_metadata";
    private static final String UPDATE_MANIFEST_TOOL = "update_epub_manifest";
    private static final String UPDATE_SPINE_TOOL = "update_epub_spine";
    private static final String UPDATE_NAVIGATION_TOOL = "update_epub_navigation";
    private static final String UPDATE_XHTML_ATTRIBUTE_TOOL = "update_epub_xhtml_attribute";

    @Override
    public List<EpubFileCheckFixAction> plan(List<EpubFileCheckIssue> issues) {

        if (issues == null || issues.isEmpty()) return List.of();

        List<EpubFileCheckFixAction> actions = new ArrayList<>();

        for (EpubFileCheckIssue issue : issues) {

            if (issue == null) continue;

            actions.add(plan(issue));
        }

        return List.copyOf(actions);
    }

    private EpubFileCheckFixAction plan(EpubFileCheckIssue issue) {

        return switch (issue.getCategory()) {

            case METADATA -> planMetadata(issue);

            case MANIFEST -> planManifest(issue);

            case SPINE -> planSpine(issue);

            case NAVIGATION -> planNavigation(issue);

            case XHTML -> planXhtml(issue);

            case STYLESHEET -> planStylesheet(issue);

            case IMAGE, RESOURCE -> planResource(issue);

            case PACKAGE -> planPackage(issue);

            case UNKNOWN -> planUnknown(issue);
        };
    }

    private EpubFileCheckFixAction planMetadata(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.UPDATE_METADATA,
                UPDATE_METADATA_TOOL,
                null,
                "EPUB package metadata 오류입니다. metadata 수정 내용을 결정하기 위한 분석이 필요합니다.",
                fileArguments(issue));
    }

    private EpubFileCheckFixAction planManifest(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.UPDATE_MANIFEST,
                UPDATE_MANIFEST_TOOL,
                null,
                "EPUB manifest 오류입니다. ADD 또는 REMOVE 작업을 결정해야 합니다.",
                fileArguments(issue));
    }

    private EpubFileCheckFixAction planSpine(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.UPDATE_SPINE,
                UPDATE_SPINE_TOOL,
                null,
                "EPUB spine 오류입니다. ADD, REMOVE 또는 MOVE 작업을 결정해야 합니다.",
                fileArguments(issue));
    }

    private EpubFileCheckFixAction planNavigation(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.UPDATE_NAVIGATION,
                UPDATE_NAVIGATION_TOOL,
                null,
                "EPUB navigation 오류입니다. nav.xhtml 상태를 확인한 후 수정 작업을 결정해야 합니다.",
                fileArguments(issue));
    }

    private EpubFileCheckFixAction planXhtml(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.UPDATE_XHTML,
                UPDATE_XHTML_ATTRIBUTE_TOOL,
                null,
                "XHTML 오류입니다. 오류 위치와 내용을 확인한 후 XHTML 수정 작업을 결정해야 합니다.",
                locationArguments(issue));
    }

    private EpubFileCheckFixAction planStylesheet(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.INSPECT_REQUIRED,
                null,
                null,
                "Stylesheet 오류입니다. 현재 지원되는 수정 Tool을 결정하기 위한 검사가 필요합니다.",
                locationArguments(issue));
    }

    private EpubFileCheckFixAction planResource(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.INSPECT_REQUIRED,
                null,
                null,
                "Resource 오류입니다. 실제 파일, manifest, spine, navigation 참조 상태를 먼저 확인해야 합니다.",
                locationArguments(issue));
    }

    private EpubFileCheckFixAction planPackage(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.INSPECT_REQUIRED,
                null,
                null,
                "Package 오류입니다. content.opf의 실제 상태를 확인한 후 수정 영역을 결정해야 합니다.",
                fileArguments(issue));
    }

    private EpubFileCheckFixAction planUnknown(EpubFileCheckIssue issue) {

        return action(
                issue,
                EpubFileCheckFixType.UNKNOWN,
                null,
                null,
                "자동 수정 전략을 결정할 수 없는 EPUBCheck 오류입니다.",
                locationArguments(issue));
    }

    private EpubFileCheckFixAction action(
            EpubFileCheckIssue issue,
            EpubFileCheckFixType fixType,
            String toolName,
            String operation,
            String reason,
            Map<String, Object> arguments) {

        return new EpubFileCheckFixAction(
                issue.getId(),
                issue.getCategory(),
                fixType,
                toolName,
                operation,
                reason,
                arguments);
    }

    private Map<String, Object> fileArguments(EpubFileCheckIssue issue) {

        if (issue.getFile() == null || issue.getFile().isBlank()) return Map.of();

        return Map.of("file", issue.getFile());
    }

    private Map<String, Object> locationArguments(EpubFileCheckIssue issue) {

        java.util.LinkedHashMap<String, Object> arguments = new java.util.LinkedHashMap<>();

        if (issue.getFile() != null && !issue.getFile().isBlank()) arguments.put("file", issue.getFile());
        if (issue.getLine() != null) arguments.put("line", issue.getLine());
        if (issue.getColumn() != null) arguments.put("column", issue.getColumn());

        return arguments;
    }
}