/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectAccessibilityValidator;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

/**
 * EPUB 파일 생성 전 현재 EPUB 프로젝트의 접근성을 검증합니다.
 *
 * <p>실제 접근성 규칙 검증은 {@link AccessibilityValidator}에 위임하며,
 * 이 클래스는 EPUB 프로젝트의 검증 대상 문서를 탐색하고
 * 프로젝트 단위 {@link AccessibilityValidationResult}로 집계합니다.</p>
 */
public final class DefaultEpubProjectAccessibilityValidator implements EpubProjectAccessibilityValidator {

    private static final String CONTENT_OPF = "OEBPS/content.opf";
    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";
    private static final String SVG_MEDIA_TYPE = "image/svg+xml";

    private final AccessibilityValidator accessibilityValidator;

    public DefaultEpubProjectAccessibilityValidator(AccessibilityValidator accessibilityValidator) {
        this.accessibilityValidator = Objects.requireNonNull(accessibilityValidator, "accessibilityValidator must not be null.");
    }

    @Override
    public AccessibilityValidationResult validate(Path projectRoot) {

        Path root = normalizeProjectRoot(projectRoot);
        Path packagePath = root.resolve(CONTENT_OPF).normalize();

        validatePackagePath(root, packagePath);

        List<Path> documentPaths = resolveDocumentPaths(root, packagePath);

        AccessibilityValidationResult.Builder result = AccessibilityValidationResult.builder()
                .projectRoot(root)
                .validatorName(getClass().getSimpleName())
                .metadata("packagePath", toProjectRelativePath(root, packagePath));

        boolean validationCompleted = true;

        for (Path documentPath : documentPaths) {

            AccessibilityValidationResult documentResult = accessibilityValidator.validate(root, documentPath);

            result.issues(documentResult.getIssues());
            result.warnings(documentResult.getWarnings());

            if (!documentResult.isValidationCompleted()) validationCompleted = false;
        }

        return result.validationCompleted(validationCompleted).build();
    }

    private Path normalizeProjectRoot(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path root = projectRoot.toAbsolutePath().normalize();

        if (!Files.exists(root)) throw new IllegalStateException("EPUB project root does not exist: " + root);
        if (!Files.isDirectory(root)) throw new IllegalStateException("EPUB project root is not a directory: " + root);

        return root;
    }

    private void validatePackagePath(Path projectRoot, Path packagePath) {

        if (!packagePath.startsWith(projectRoot)) throw new IllegalStateException("content.opf path escapes project root.");
        if (!Files.isRegularFile(packagePath)) throw new IllegalStateException("content.opf does not exist: " + packagePath);
    }

    private List<Path> resolveDocumentPaths(Path projectRoot, Path packagePath) {

        Set<Path> documentPaths = new LinkedHashSet<>();

        documentPaths.add(packagePath);

        Document document = EpubXmlUtil.readDocument(packagePath);
        NodeList items = document.getElementsByTagNameNS("*", "item");

        for (int index = 0; index < items.getLength(); index++) {

            if (!(items.item(index) instanceof Element item)) continue;

            String href = normalize(item.getAttribute("href"));
            String mediaType = normalize(item.getAttribute("media-type"));

            if (href == null) continue;
            if (!isAccessibilityDocument(mediaType, href)) continue;

            Path documentPath = resolveDocumentPath(projectRoot, packagePath.getParent(), href);

            if (documentPath == null) continue;
            if (!Files.isRegularFile(documentPath)) continue;

            documentPaths.add(documentPath);
        }

        return List.copyOf(documentPaths);
    }

    private Path resolveDocumentPath(Path projectRoot, Path packageDirectory, String href) {

        String normalizedHref = removeFragmentAndQuery(href);

        if (normalizedHref.isBlank()) return null;

        Path documentPath = packageDirectory.resolve(normalizedHref).normalize();

        if (!documentPath.startsWith(projectRoot)) return null;

        return documentPath;
    }

    private boolean isAccessibilityDocument(String mediaType, String href) {

        if (XHTML_MEDIA_TYPE.equals(mediaType)) return true;
        if (SVG_MEDIA_TYPE.equals(mediaType)) return true;

        String value = removeFragmentAndQuery(href).toLowerCase();

        return value.endsWith(".xhtml") || value.endsWith(".html") || value.endsWith(".htm") || value.endsWith(".svg");
    }

    private String removeFragmentAndQuery(String value) {

        if (value == null) return "";

        int fragmentIndex = value.indexOf('#');
        int queryIndex = value.indexOf('?');
        int endIndex = value.length();

        if (fragmentIndex >= 0) endIndex = Math.min(endIndex, fragmentIndex);
        if (queryIndex >= 0) endIndex = Math.min(endIndex, queryIndex);

        return value.substring(0, endIndex);
    }

    private String toProjectRelativePath(Path projectRoot, Path path) {
        return projectRoot.relativize(path).toString().replace('\\', '/');
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}