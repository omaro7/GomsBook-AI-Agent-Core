/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityIssue;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilitySeverity;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidationResult;
import kr.co.goms.gomsbook.ai.accessibility.validation.AccessibilityValidator;
import kr.co.goms.gomsbook.ai.epub.model.EpubProjectAccessibilityValidationIssue;
import kr.co.goms.gomsbook.ai.epub.model.EpubProjectAccessibilityValidationResult;

/**
 * EPUB 파일 생성 전 현재 EPUB 프로젝트의 접근성을 검증합니다.
 */
public final class DefaultEpubProjectAccessibilityValidator implements EpubProjectAccessibilityValidator {

    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final String EPUB_NAMESPACE = "http://www.idpf.org/2007/ops";
    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    private final AccessibilityValidator accessibilityValidator;

    public DefaultEpubProjectAccessibilityValidator(AccessibilityValidator accessibilityValidator) {
        this.accessibilityValidator = Objects.requireNonNull(accessibilityValidator, "accessibilityValidator must not be null.");
    }

    @Override
    public EpubProjectAccessibilityValidationResult validate(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        EpubProjectAccessibilityValidationResult.Builder result = EpubProjectAccessibilityValidationResult.builder().projectRoot(normalizedProjectRoot);

        if (!Files.isDirectory(normalizedProjectRoot)) {
            result.issue(error("EPUB_ACCESSIBILITY_PROJECT_ROOT_INVALID", "EPUB project root is not available: " + normalizedProjectRoot));
            return result.build();
        }

        Path containerPath = normalizedProjectRoot.resolve(CONTAINER_PATH).normalize();

        if (!Files.isRegularFile(containerPath)) {
            result.issue(error("EPUB_ACCESSIBILITY_CONTAINER_MISSING", "EPUB container.xml is not available: " + containerPath));
            return result.build();
        }

        Document containerDocument = parseXml(containerPath, "EPUB_ACCESSIBILITY_CONTAINER_XML_INVALID", result);

        if (containerDocument == null) return result.build();

        String packageRelativePath = resolvePackageRelativePath(containerDocument);

        if (packageRelativePath == null) {
            result.issue(error("EPUB_ACCESSIBILITY_PACKAGE_PATH_MISSING", "EPUB package document path cannot be resolved from container.xml."));
            return result.build();
        }

        Path packagePath = resolveProjectPath(normalizedProjectRoot, normalizedProjectRoot, packageRelativePath);

        if (packagePath == null || !Files.isRegularFile(packagePath)) {
            result.issue(error("EPUB_ACCESSIBILITY_PACKAGE_MISSING", "EPUB package document is not available: " + packageRelativePath));
            return result.build();
        }

        result.packagePath(packagePath);

        Document packageDocument = parseXml(packagePath, "EPUB_ACCESSIBILITY_PACKAGE_XML_INVALID", result);

        if (packageDocument == null) return result.build();

        Path packageDirectory = packagePath.getParent();

        if (packageDirectory == null) return result.build();

        validateAccessibilityMetadata(packageDocument, result);

        List<ManifestDocument> documents = readXhtmlDocuments(packageDocument);

        for (ManifestDocument document : documents) validateDocument(normalizedProjectRoot, packageDirectory, document, result);

        validateNavigationAccessibility(normalizedProjectRoot, packageDirectory, documents, result);
        validateCoverAccessibility(normalizedProjectRoot, packageDirectory, documents, result);

        return result.build();
    }

    private void validateAccessibilityMetadata(Document packageDocument, EpubProjectAccessibilityValidationResult.Builder result) {

        Element metadata = firstElement(packageDocument, "metadata");

        if (metadata == null) return;

        List<String> accessModes = metadataValues(metadata, "schema:accessMode");
        List<String> accessModeSufficient = metadataValues(metadata, "schema:accessModeSufficient");
        List<String> accessibilityFeatures = metadataValues(metadata, "schema:accessibilityFeature");
        List<String> accessibilityHazards = metadataValues(metadata, "schema:accessibilityHazard");
        List<String> accessibilitySummaries = metadataValues(metadata, "schema:accessibilitySummary");

        if (accessModes.isEmpty()) result.issue(error("EPUB_ACCESSIBILITY_ACCESS_MODE_MISSING", "Accessibility metadata schema:accessMode is missing."));
        if (accessModeSufficient.isEmpty()) result.issue(error("EPUB_ACCESSIBILITY_ACCESS_MODE_SUFFICIENT_MISSING", "Accessibility metadata schema:accessModeSufficient is missing."));
        if (accessibilityFeatures.isEmpty()) result.issue(error("EPUB_ACCESSIBILITY_FEATURE_MISSING", "Accessibility metadata schema:accessibilityFeature is missing."));
        else if (!containsValue(accessibilityFeatures, "structuralNavigation")) result.issue(warning("EPUB_ACCESSIBILITY_STRUCTURAL_NAVIGATION_MISSING", "Accessibility metadata schema:accessibilityFeature does not contain structuralNavigation."));
        if (accessibilityHazards.isEmpty()) result.issue(warning("EPUB_ACCESSIBILITY_HAZARD_MISSING", "Accessibility metadata schema:accessibilityHazard is missing."));
        if (accessibilitySummaries.isEmpty()) result.issue(warning("EPUB_ACCESSIBILITY_SUMMARY_MISSING", "Accessibility metadata schema:accessibilitySummary is missing."));
    }

    private List<ManifestDocument> readXhtmlDocuments(Document packageDocument) {

        List<ManifestDocument> result = new ArrayList<>();
        Element manifest = firstElement(packageDocument, "manifest");

        if (manifest == null) return result;

        for (Element item : childElements(manifest, "item")) {

            String href = attribute(item, "href");
            String mediaType = attribute(item, "media-type");
            String properties = attribute(item, "properties");

            if (href == null || !XHTML_MEDIA_TYPE.equalsIgnoreCase(mediaType)) continue;

            result.add(new ManifestDocument(href, properties));
        }

        return result;
    }

    private void validateDocument(
            Path projectRoot,
            Path packageDirectory,
            ManifestDocument document,
            EpubProjectAccessibilityValidationResult.Builder result) {

        Path documentPath = resolveProjectPath(projectRoot, packageDirectory, document.href());

        if (documentPath == null || !Files.isRegularFile(documentPath)) return;

        try {

            AccessibilityValidationResult documentResult = accessibilityValidator.validate(projectRoot, documentPath);

            addDocumentIssues(documentResult, result);

            if (!documentResult.isValidationCompleted()) {
                result.issue(error("EPUB_ACCESSIBILITY_DOCUMENT_VALIDATION_INCOMPLETE", "Accessibility validation did not complete: " + relative(projectRoot, documentPath)));
            }

            for (String warning : documentResult.getWarnings()) {
                if (warning != null && !warning.isBlank()) result.issue(warning("EPUB_ACCESSIBILITY_DOCUMENT_VALIDATION_WARNING", relative(projectRoot, documentPath) + ": " + warning.trim()));
            }

        } catch (RuntimeException exception) {

            result.issue(error("EPUB_ACCESSIBILITY_DOCUMENT_VALIDATION_FAILED", "Failed to validate accessibility document: " + relative(projectRoot, documentPath) + " / " + safeMessage(exception)));
        }
    }

    private void addDocumentIssues(
            AccessibilityValidationResult documentResult,
            EpubProjectAccessibilityValidationResult.Builder result) {

        if (documentResult == null || documentResult.getIssues() == null) return;

        for (AccessibilityIssue issue : documentResult.getIssues()) {

            if (issue == null || issue.getSeverity() == AccessibilitySeverity.INFO) continue;

            String code = issue.getCode() == null ? "EPUB_ACCESSIBILITY_DOCUMENT_ISSUE" : issue.getCode().getCode();
            String message = createDocumentIssueMessage(documentResult, issue);

            if (issue.getSeverity() == AccessibilitySeverity.ERROR) result.issue(error(code, message));
            else result.issue(warning(code, message));
        }
    }

    private String createDocumentIssueMessage(AccessibilityValidationResult documentResult, AccessibilityIssue issue) {

        String path = documentResult.getProjectRelativePath();
        String message = issue.getMessage();

        if (message == null || message.isBlank()) message = issue.getCode() == null ? "Accessibility issue detected." : issue.getCode().getDisplayName();

        return path == null || path.isBlank() ? message : path + ": " + message;
    }

    private void validateNavigationAccessibility(
            Path projectRoot,
            Path packageDirectory,
            List<ManifestDocument> documents,
            EpubProjectAccessibilityValidationResult.Builder result) {

        ManifestDocument navigationDocument = documents.stream().filter(document -> containsToken(document.properties(), "nav")).findFirst().orElse(null);

        if (navigationDocument == null) return;

        Path navigationPath = resolveProjectPath(projectRoot, packageDirectory, navigationDocument.href());

        if (navigationPath == null || !Files.isRegularFile(navigationPath)) return;

        Document document = parseXml(navigationPath, "EPUB_ACCESSIBILITY_NAV_XML_INVALID", result);

        if (document == null) return;

        Element toc = findTocNav(document);

        if (toc == null) return;

        if (!"doc-toc".equalsIgnoreCase(attribute(toc, "role"))) {
            result.issue(error("EPUB_ACCESSIBILITY_NAV_ROLE_MISSING", "Navigation TOC must contain role=\"doc-toc\": " + relative(projectRoot, navigationPath)));
        }

        if (attribute(toc, "aria-label") == null && !hasHeading(toc)) {
            result.issue(warning("EPUB_ACCESSIBILITY_NAV_LABEL_MISSING", "Navigation TOC does not contain aria-label or a heading: " + relative(projectRoot, navigationPath)));
        }
    }

    private void validateCoverAccessibility(
            Path projectRoot,
            Path packageDirectory,
            List<ManifestDocument> documents,
            EpubProjectAccessibilityValidationResult.Builder result) {

        ManifestDocument coverDocument = documents.stream().filter(document -> "cover.xhtml".equalsIgnoreCase(fileName(document.href()))).findFirst().orElse(null);

        if (coverDocument == null) return;

        Path coverPath = resolveProjectPath(projectRoot, packageDirectory, coverDocument.href());

        if (coverPath == null || !Files.isRegularFile(coverPath)) return;

        Document document = parseXml(coverPath, "EPUB_ACCESSIBILITY_COVER_XML_INVALID", result);

        if (document == null) return;

        if (!containsRole(document, "doc-cover")) {
            result.issue(error("EPUB_ACCESSIBILITY_COVER_ROLE_MISSING", "Cover document must contain role=\"doc-cover\": " + relative(projectRoot, coverPath)));
        }

        NodeList images = elements(document, "img");

        for (int index = 0; index < images.getLength(); index++) {

            Element image = (Element) images.item(index);

            if (!image.hasAttribute("alt") || image.getAttribute("alt").isBlank()) {
                result.issue(error("EPUB_ACCESSIBILITY_COVER_ALT_MISSING", "Cover image must contain meaningful alt text: " + relative(projectRoot, coverPath) + " -> " + safe(attribute(image, "src"))));
            }
        }
    }

    private Element findTocNav(Document document) {

        NodeList navElements = elements(document, "nav");

        for (int index = 0; index < navElements.getLength(); index++) {

            Element nav = (Element) navElements.item(index);
            String epubType = trimToNull(nav.getAttributeNS(EPUB_NAMESPACE, "type"));

            if (epubType == null) epubType = attribute(nav, "epub:type");
            if (containsToken(epubType, "toc")) return nav;
        }

        return null;
    }

    private boolean hasHeading(Element parent) {

        for (int level = 1; level <= 6; level++) if (elements(parent, "h" + level).getLength() > 0) return true;

        return false;
    }

    private boolean containsRole(Document document, String role) {

        NodeList nodes = document.getElementsByTagNameNS("*", "*");

        if (nodes.getLength() == 0) nodes = document.getElementsByTagName("*");

        for (int index = 0; index < nodes.getLength(); index++) {

            Node node = nodes.item(index);

            if (!(node instanceof Element element)) continue;
            if (containsToken(attribute(element, "role"), role)) return true;
        }

        return false;
    }

    private List<String> metadataValues(Element metadata, String propertyName) {

        List<String> values = new ArrayList<>();
        NodeList metaElements = elements(metadata, "meta");

        for (int index = 0; index < metaElements.getLength(); index++) {

            Element meta = (Element) metaElements.item(index);

            if (!propertyName.equals(attribute(meta, "property"))) continue;

            String value = trimToNull(meta.getTextContent());

            if (value != null) values.add(value);
        }

        return values;
    }

    private boolean containsValue(List<String> values, String expected) {

        for (String value : values) for (String token : value.split("[,\\s]+")) if (expected.equalsIgnoreCase(token)) return true;

        return false;
    }

    private String resolvePackageRelativePath(Document containerDocument) {

        NodeList rootfiles = elements(containerDocument, "rootfile");

        if (rootfiles.getLength() == 0) return null;

        return attribute((Element) rootfiles.item(0), "full-path");
    }

    private Document parseXml(Path path, String errorCode, EpubProjectAccessibilityValidationResult.Builder result) {

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
            }

            DocumentBuilder builder = factory.newDocumentBuilder();

            try (InputStream inputStream = Files.newInputStream(path)) {
                return builder.parse(inputStream);
            }

        } catch (Exception exception) {

            result.issue(error(errorCode, "Failed to read XML document: " + path + " / " + safeMessage(exception)));

            return null;
        }
    }

    private Path resolveProjectPath(Path projectRoot, Path baseDirectory, String reference) {

        String normalizedReference = removeQueryAndFragment(reference);

        if (normalizedReference == null || isExternalReference(normalizedReference)) return null;

        try {

            Path path = baseDirectory.resolve(normalizedReference.replace('/', java.io.File.separatorChar)).normalize();

            return path.startsWith(projectRoot) ? path : null;

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Element firstElement(Document document, String localName) {

        NodeList nodes = elements(document, localName);

        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private List<Element> childElements(Element parent, String localName) {

        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element element)) continue;
            if (!localName.equals(localName(element))) continue;

            result.add(element);
        }

        return result;
    }

    private NodeList elements(Document document, String localName) {

        NodeList nodes = document.getElementsByTagNameNS("*", localName);

        return nodes.getLength() == 0 ? document.getElementsByTagName(localName) : nodes;
    }

    private NodeList elements(Element element, String localName) {

        NodeList nodes = element.getElementsByTagNameNS("*", localName);

        return nodes.getLength() == 0 ? element.getElementsByTagName(localName) : nodes;
    }

    private String localName(Element element) {

        String value = element.getLocalName();

        if (value != null && !value.isBlank()) return value.toLowerCase(Locale.ROOT);

        value = element.getNodeName();

        int separator = value.indexOf(':');

        if (separator >= 0) value = value.substring(separator + 1);

        return value.toLowerCase(Locale.ROOT);
    }

    private String attribute(Element element, String name) {

        if (element == null || name == null || !element.hasAttribute(name)) return null;

        return trimToNull(element.getAttribute(name));
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private boolean containsToken(String values, String target) {

        if (values == null || target == null) return false;

        for (String token : values.trim().split("\\s+")) if (target.equalsIgnoreCase(token)) return true;

        return false;
    }

    private String fileName(String href) {

        String value = removeQueryAndFragment(href);

        if (value == null) return "";

        int index = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));

        return index < 0 ? value : value.substring(index + 1);
    }

    private boolean isExternalReference(String reference) {

        if (reference == null || reference.isBlank()) return false;

        String value = reference.trim().toLowerCase(Locale.ROOT);

        return value.startsWith("http:") || value.startsWith("https:") || value.startsWith("mailto:") || value.startsWith("tel:") || value.startsWith("data:") || value.startsWith("urn:");
    }

    private String removeQueryAndFragment(String value) {

        if (value == null || value.isBlank()) return null;

        String normalized = value.trim();
        int queryIndex = normalized.indexOf('?');
        int fragmentIndex = normalized.indexOf('#');
        int endIndex = normalized.length();

        if (queryIndex >= 0) endIndex = Math.min(endIndex, queryIndex);
        if (fragmentIndex >= 0) endIndex = Math.min(endIndex, fragmentIndex);

        String result = normalized.substring(0, endIndex);

        return result.isBlank() ? null : result;
    }

    private String relative(Path projectRoot, Path path) {

        try {
            return projectRoot.relativize(path).toString().replace('\\', '/');
        } catch (RuntimeException exception) {
            return path.toString();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown accessibility validation error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
    }

    private EpubProjectAccessibilityValidationIssue error(String code, String message) {
        return EpubProjectAccessibilityValidationIssue.error(code, message);
    }

    private EpubProjectAccessibilityValidationIssue warning(String code, String message) {
        return EpubProjectAccessibilityValidationIssue.warning(code, message);
    }

    private record ManifestDocument(String href, String properties) {
    }
}