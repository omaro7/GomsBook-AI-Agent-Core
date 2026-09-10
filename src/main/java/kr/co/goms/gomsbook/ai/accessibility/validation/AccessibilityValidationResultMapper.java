/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AccessibilityValidationResult를 Tool 출력 데이터로 변환합니다.
 */
public final class AccessibilityValidationResultMapper {

    private AccessibilityValidationResultMapper() {
    }

    public static Map<String, Object> toData(AccessibilityValidationResult result) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("projectRoot", result.getProjectRoot() == null ? null : result.getProjectRoot().toString());
        data.put("documentPath", result.getProjectRelativePath());
        data.put("validationCompleted", result.isValidationCompleted());
        data.put("passed", result.isPassed());
        data.put("clean", result.isClean());
        data.put("blocksPublication", result.blocksPublication());
        data.put("totalIssueCount", result.getTotalIssueCount());
        data.put("errorCount", result.getErrorCount());
        data.put("warningCount", result.getWarningCount());
        data.put("infoCount", result.getInfoCount());
        data.put("automaticallyFixableCount", result.getAutomaticallyFixableCount());
        data.put("manualReviewCount", result.getManualReviewCount());
        data.put("validatorName", result.getValidatorName());
        data.put("durationMillis", result.getDuration() == null ? null : result.getDuration().toMillis());
        data.put("issues", toIssues(result.getIssues()));
        data.put("warnings", result.getWarnings());
        data.put("severityCounts", toSeverityCounts(result));
        data.put("categoryCounts", toCategoryCounts(result));
        data.put("metadata", result.getMetadata());

        return Collections.unmodifiableMap(data);
    }

    public static List<Map<String, Object>> toIssues(List<AccessibilityIssue> issues) {

        if (issues == null || issues.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> data = new ArrayList<>();

        for (AccessibilityIssue issue : issues) {

            if (issue == null) continue;

            data.add(toIssue(issue));
        }

        return Collections.unmodifiableList(data);
    }

    public static Map<String, Object> toIssue(AccessibilityIssue issue) {

        if (issue == null) return Collections.emptyMap();

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("code", issue.getCode().getCode());
        data.put("displayName", issue.getCode().getDisplayName());
        data.put("category", issue.getCode().getCategory().getCode());
        data.put("severity", issue.getSeverity().getCode());
        data.put("message", issue.getMessage());
        data.put("description", issue.getDescription());
        data.put("recommendation", issue.getRecommendation());
        data.put("automaticallyFixable", issue.isAutomaticallyFixable());
        data.put("manualReviewRequired", issue.isManualReviewRequired());
        data.put("blocksPublication", issue.blocksPublication());
        data.put("ruleId", issue.getRuleId());
        data.put("currentValue", issue.getCurrentValue());
        data.put("suggestedValue", issue.getSuggestedValue());
        data.put("relatedValues", issue.getRelatedValues());
        data.put("location", toLocation(issue.getLocation()));
        data.put("metadata", issue.getMetadata());

        return Collections.unmodifiableMap(data);
    }

    public static Map<String, Object> toLocation(AccessibilityLocation location) {

        if (location == null) return Collections.emptyMap();

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("documentPath", location.getProjectRelativePath());
        data.put("elementName", location.getElementName());
        data.put("elementId", location.getElementId());
        data.put("attributeName", location.getAttributeName());
        data.put("attributeValue", location.getAttributeValue());
        data.put("lineNumber", location.getLineNumber());
        data.put("columnNumber", location.getColumnNumber());
        data.put("xpath", location.getXpath());
        data.put("textExcerpt", location.getTextExcerpt());
        data.put("display", location.toDisplayString());
        data.put("metadata", location.getMetadata());

        return Collections.unmodifiableMap(data);
    }

    public static Map<String, Integer> toSeverityCounts(AccessibilityValidationResult result) {

        Map<String, Integer> data = new LinkedHashMap<>();

        for (Map.Entry<AccessibilitySeverity, Integer> entry : result.getSeverityCounts().entrySet()) {
            data.put(entry.getKey().getCode(), entry.getValue());
        }

        return Collections.unmodifiableMap(data);
    }

    public static Map<String, Integer> toCategoryCounts(AccessibilityValidationResult result) {

        Map<String, Integer> data = new LinkedHashMap<>();

        for (Map.Entry<AccessibilityIssueCode.AccessibilityCategory, Integer> entry : result.getCategoryCounts().entrySet()) {
            data.put(entry.getKey().getCode(), entry.getValue());
        }

        return Collections.unmodifiableMap(data);
    }
}