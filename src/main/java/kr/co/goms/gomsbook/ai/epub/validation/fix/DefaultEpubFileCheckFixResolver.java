/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.validation.fix;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

public final class DefaultEpubFileCheckFixResolver implements EpubFileCheckFixResolver {

    private static final String CONTENT_DIRECTORY = "OEBPS";
    private static final String PACKAGE_FILE_NAME = "content.opf";
    private static final String NAV_FILE_NAME = "nav.xhtml";

    private static final String UPDATE_MANIFEST_TOOL = "update_epub_manifest";
    private static final String UPDATE_SPINE_TOOL = "update_epub_spine";
    private static final String UPDATE_NAVIGATION_TOOL = "update_epub_navigation";

    @Override
    public List<EpubFileCheckFixAction> resolve(Path projectRoot, List<EpubFileCheckFixAction> actions) {

        Path normalizedProjectRoot = requireProjectRoot(projectRoot);

        if (actions == null || actions.isEmpty()) return List.of();

        ProjectState state = inspectProject(normalizedProjectRoot);

        List<EpubFileCheckFixAction> resolved = new ArrayList<>();

        for (EpubFileCheckFixAction action : actions) {

            if (action == null) continue;

            if (!action.requiresInspection()) {
                resolved.add(action);
                continue;
            }

            resolved.addAll(resolve(state, action));
        }

        return List.copyOf(resolved);
    }

    private List<EpubFileCheckFixAction> resolve(ProjectState state, EpubFileCheckFixAction action) {

        String file = readStringArgument(action, "file");

        if (file == null) return List.of(manualRequired(action, "EPUBCheck 오류의 대상 file 정보를 확인할 수 없습니다."));

        String normalizedFile = normalizePath(file);
        String packageHref = resolvePackageHref(state.contentRoot(), normalizedFile);
        Path targetFile = resolveTargetFile(state.projectRoot(), state.contentRoot(), normalizedFile);

        boolean fileExists = Files.isRegularFile(targetFile);

        ManifestReference manifestReference = findManifestReference(state.packageDocument(), packageHref);
        boolean manifestExists = manifestReference != null;
        boolean spineExists = manifestExists && containsSpineReference(state.packageDocument(), manifestReference.id());
        boolean navigationExists = containsNavigationReference(state.navigationDocument(), packageHref, normalizedFile);

        if (!fileExists) return resolveMissingFile(action, normalizedFile, packageHref, manifestReference, spineExists, navigationExists);

        return resolveExistingFile(action, normalizedFile, packageHref, manifestReference, spineExists, navigationExists);
    }

    private List<EpubFileCheckFixAction> resolveMissingFile(
            EpubFileCheckFixAction source,
            String file,
            String href,
            ManifestReference manifestReference,
            boolean spineExists,
            boolean navigationExists) {

        List<EpubFileCheckFixAction> actions = new ArrayList<>();

        if (spineExists && manifestReference != null) actions.add(removeSpine(source, file, manifestReference.id()));
        if (navigationExists) actions.add(removeNavigation(source, file, href));
        if (manifestReference != null) actions.add(removeManifest(source, file, manifestReference.id(), href));

        if (!actions.isEmpty()) return List.copyOf(actions);

        return List.of(manualRequired(source, "실제 파일이 존재하지 않지만 manifest, spine, navigation 참조도 발견되지 않았습니다: " + file));
    }

    private List<EpubFileCheckFixAction> resolveExistingFile(
            EpubFileCheckFixAction source,
            String file,
            String href,
            ManifestReference manifestReference,
            boolean spineExists,
            boolean navigationExists) {

        if (manifestReference == null) return List.of(addManifest(source, file, href));

        if (requiresSpine(file) && !spineExists) return List.of(addSpine(source, file, manifestReference.id()));

        if (requiresNavigation(file) && !navigationExists) {
            return List.of(manualRequired(source, "XHTML 파일은 존재하지만 nav.xhtml 참조가 없습니다. 목차 위치와 제목을 자동 결정할 수 없어 추가 판단이 필요합니다: " + file));
        }

        return List.of(manualRequired(source, "파일과 package 참조가 정상적으로 존재합니다. EPUBCheck 메시지의 상세 원인을 기준으로 추가 분석이 필요합니다: " + file));
    }

    private EpubFileCheckFixAction removeManifest(EpubFileCheckFixAction source, String file, String id, String href) {

        Map<String, Object> arguments = new LinkedHashMap<>();

        arguments.put("operation", "REMOVE");
        arguments.put("id", id);
        arguments.put("href", href);

        return action(
                source,
                EpubFileCheckFixType.UPDATE_MANIFEST,
                UPDATE_MANIFEST_TOOL,
                "REMOVE",
                "실제 파일이 존재하지 않지만 manifest에서 참조하고 있어 manifest 항목을 제거해야 합니다: " + file,
                arguments);
    }

    private EpubFileCheckFixAction addManifest(EpubFileCheckFixAction source, String file, String href) {

        String id = resolveResourceId(file);
        String mediaType = resolveMediaType(file);

        if (mediaType == null) return manualRequired(source, "manifest에 등록되지 않은 파일이지만 media-type을 자동 결정할 수 없습니다: " + file);

        Map<String, Object> arguments = new LinkedHashMap<>();

        arguments.put("operation", "ADD");
        arguments.put("id", id);
        arguments.put("href", href);
        arguments.put("mediaType", mediaType);

        if (NAV_FILE_NAME.equalsIgnoreCase(fileName(file))) arguments.put("properties", "nav");

        return action(
                source,
                EpubFileCheckFixType.UPDATE_MANIFEST,
                UPDATE_MANIFEST_TOOL,
                "ADD",
                "실제 파일은 존재하지만 manifest 항목이 없어 manifest 등록이 필요합니다: " + file,
                arguments);
    }

    private EpubFileCheckFixAction removeSpine(EpubFileCheckFixAction source, String file, String idref) {

        Map<String, Object> arguments = new LinkedHashMap<>();

        arguments.put("operation", "REMOVE");
        arguments.put("idref", idref);

        return action(
                source,
                EpubFileCheckFixType.UPDATE_SPINE,
                UPDATE_SPINE_TOOL,
                "REMOVE",
                "실제 파일이 존재하지 않지만 spine에서 참조하고 있어 spine itemref를 제거해야 합니다: " + file,
                arguments);
    }

    private EpubFileCheckFixAction addSpine(EpubFileCheckFixAction source, String file, String idref) {

        Map<String, Object> arguments = new LinkedHashMap<>();

        arguments.put("operation", "ADD");
        arguments.put("idref", idref);

        return action(
                source,
                EpubFileCheckFixType.UPDATE_SPINE,
                UPDATE_SPINE_TOOL,
                "ADD",
                "XHTML 파일과 manifest 항목은 존재하지만 spine itemref가 없어 spine 등록이 필요합니다: " + file,
                arguments);
    }

    private EpubFileCheckFixAction removeNavigation(EpubFileCheckFixAction source, String file, String href) {

        Map<String, Object> arguments = new LinkedHashMap<>();

        arguments.put("operation", "REMOVE");
        arguments.put("fileName", NAV_FILE_NAME);
        arguments.put("href", navigationHref(href));

        return action(
                source,
                EpubFileCheckFixType.UPDATE_NAVIGATION,
                UPDATE_NAVIGATION_TOOL,
                "REMOVE",
                "실제 파일이 존재하지 않지만 nav.xhtml에서 참조하고 있어 navigation 항목을 제거해야 합니다: " + file,
                arguments);
    }

    private EpubFileCheckFixAction manualRequired(EpubFileCheckFixAction source, String reason) {

        return action(
                source,
                EpubFileCheckFixType.MANUAL_REQUIRED,
                null,
                null,
                reason,
                source.getArguments());
    }

    private EpubFileCheckFixAction action(
            EpubFileCheckFixAction source,
            EpubFileCheckFixType fixType,
            String toolName,
            String operation,
            String reason,
            Map<String, Object> arguments) {

        return new EpubFileCheckFixAction(
                source.getIssueId(),
                source.getCategory(),
                fixType,
                toolName,
                operation,
                reason,
                arguments);
    }

    private ProjectState inspectProject(Path projectRoot) {

        Path packagePath = resolvePackagePath(projectRoot);
        Path contentRoot = packagePath.getParent();

        if (contentRoot == null) throw new IllegalStateException("EPUB content root could not be resolved: " + packagePath);

        Document packageDocument = EpubXmlUtil.readDocument(packagePath);
        Path navigationPath = resolveNavigationPath(contentRoot, packageDocument);
        Document navigationDocument = Files.isRegularFile(navigationPath) ? EpubXmlUtil.readDocument(navigationPath) : null;

        return new ProjectState(projectRoot, contentRoot, packagePath, packageDocument, navigationPath, navigationDocument);
    }

    private Path resolvePackagePath(Path projectRoot) {

        Path packagePath = projectRoot.resolve(CONTENT_DIRECTORY).resolve(PACKAGE_FILE_NAME).normalize();

        if (Files.isRegularFile(packagePath)) return packagePath;

        Path directPackagePath = projectRoot.resolve(PACKAGE_FILE_NAME).normalize();

        if (Files.isRegularFile(directPackagePath)) return directPackagePath;

        throw new IllegalStateException("EPUB package document does not exist: " + packagePath);
    }

    private Path resolveNavigationPath(Path contentRoot, Document packageDocument) {

        Element manifest = findElement(packageDocument, "manifest");

        if (manifest != null) {

            NodeList items = manifest.getElementsByTagNameNS("*", "item");

            for (int i = 0; i < items.getLength(); i++) {

                if (!(items.item(i) instanceof Element item)) continue;

                String properties = item.getAttribute("properties");

                if (!containsToken(properties, "nav")) continue;

                String href = normalizePath(item.getAttribute("href"));

                if (href != null) return contentRoot.resolve(href).normalize();
            }
        }

        return contentRoot.resolve("Text").resolve(NAV_FILE_NAME).normalize();
    }

    private ManifestReference findManifestReference(Document document, String href) {

        if (document == null || href == null) return null;

        Element manifest = findElement(document, "manifest");

        if (manifest == null) return null;

        String normalizedHref = normalizeHref(href);
        NodeList items = manifest.getElementsByTagNameNS("*", "item");

        for (int i = 0; i < items.getLength(); i++) {

            if (!(items.item(i) instanceof Element item)) continue;

            String itemHref = normalizeHref(item.getAttribute("href"));

            if (!normalizedHref.equals(itemHref)) continue;

            String id = trimToNull(item.getAttribute("id"));

            if (id != null) return new ManifestReference(id, itemHref);
        }

        return null;
    }

    private boolean containsSpineReference(Document document, String idref) {

        if (document == null || idref == null) return false;

        Element spine = findElement(document, "spine");

        if (spine == null) return false;

        NodeList items = spine.getElementsByTagNameNS("*", "itemref");

        for (int i = 0; i < items.getLength(); i++) {

            if (!(items.item(i) instanceof Element item)) continue;

            if (idref.equals(trimToNull(item.getAttribute("idref")))) return true;
        }

        return false;
    }

    private boolean containsNavigationReference(Document document, String packageHref, String file) {

        if (document == null) return false;

        String expected = navigationHref(packageHref);
        String expectedFileName = fileName(file);

        NodeList links = document.getElementsByTagNameNS("*", "a");

        for (int i = 0; i < links.getLength(); i++) {

            if (!(links.item(i) instanceof Element link)) continue;

            String href = normalizeHref(removeFragment(link.getAttribute("href")));

            if (href == null) continue;
            if (expected != null && expected.equals(href)) return true;
            if (expectedFileName != null && expectedFileName.equalsIgnoreCase(fileName(href))) return true;
        }

        return false;
    }

    private Path resolveTargetFile(Path projectRoot, Path contentRoot, String file) {

        String normalized = normalizePath(removeFragment(file));

        if (normalized == null) return projectRoot;

        if (normalized.startsWith(CONTENT_DIRECTORY + "/")) return projectRoot.resolve(normalized).normalize();

        Path contentTarget = contentRoot.resolve(normalized).normalize();

        if (Files.exists(contentTarget)) return contentTarget;

        if (!normalized.contains("/")) {

            Path textTarget = contentRoot.resolve("Text").resolve(normalized).normalize();

            if (Files.exists(textTarget)) return textTarget;
        }

        return contentTarget;
    }

    private String resolvePackageHref(Path contentRoot, String file) {

        String normalized = normalizePath(removeFragment(file));

        if (normalized == null) return null;

        if (normalized.startsWith(CONTENT_DIRECTORY + "/")) normalized = normalized.substring((CONTENT_DIRECTORY + "/").length());

        if (normalized.startsWith("./")) normalized = normalized.substring(2);

        if (!normalized.contains("/") && isXhtml(normalized)) return "Text/" + normalized;

        return normalizeHref(normalized);
    }

    private String navigationHref(String packageHref) {

        String normalized = normalizeHref(packageHref);

        if (normalized == null) return null;
        if (normalized.startsWith("Text/")) return normalized.substring("Text/".length());

        return normalized;
    }

    private boolean requiresSpine(String file) {

        String normalized = normalizePath(file);

        if (normalized == null) return false;
        if (!isXhtml(normalized)) return false;
        if (NAV_FILE_NAME.equalsIgnoreCase(fileName(normalized))) return false;

        return true;
    }

    private boolean requiresNavigation(String file) {

        String normalized = normalizePath(file);

        if (normalized == null) return false;
        if (!isXhtml(normalized)) return false;
        if (NAV_FILE_NAME.equalsIgnoreCase(fileName(normalized))) return false;

        return true;
    }

    private String resolveResourceId(String file) {

        String name = fileName(file);

        if (name == null) return "resource";

        int dot = name.lastIndexOf('.');

        String id = dot > 0 ? name.substring(0, dot) : name;

        id = id.replaceAll("[^A-Za-z0-9_.-]", "_");

        return id.isBlank() ? "resource" : id;
    }

    private String resolveMediaType(String file) {

        String normalized = normalizePath(file);

        if (normalized == null) return null;

        String lower = normalized.toLowerCase(java.util.Locale.ROOT);

        if (lower.endsWith(".xhtml") || lower.endsWith(".html")) return "application/xhtml+xml";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";

        return null;
    }

    private Element findElement(Document document, String localName) {

        if (document == null || localName == null) return null;

        NodeList elements = document.getElementsByTagNameNS("*", localName);

        if (elements.getLength() == 0) return null;

        return elements.item(0) instanceof Element element ? element : null;
    }

    private String readStringArgument(EpubFileCheckFixAction action, String name) {

        if (action == null || action.getArguments() == null || name == null) return null;

        Object value = action.getArguments().get(name);

        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private boolean containsToken(String value, String token) {

        String normalized = trimToNull(value);

        if (normalized == null || token == null) return false;

        for (String current : normalized.split("\\s+")) {

            if (token.equals(current)) return true;
        }

        return false;
    }

    private String normalizeHref(String value) {

        String normalized = normalizePath(removeFragment(value));

        if (normalized == null) return null;

        while (normalized.startsWith("./")) normalized = normalized.substring(2);

        return normalized;
    }

    private String normalizePath(String value) {

        String normalized = trimToNull(value);

        if (normalized == null) return null;

        return normalized.replace('\\', '/');
    }

    private String removeFragment(String value) {

        String normalized = trimToNull(value);

        if (normalized == null) return null;

        int index = normalized.indexOf('#');

        return index < 0 ? normalized : normalized.substring(0, index);
    }

    private String fileName(String value) {

        String normalized = normalizePath(removeFragment(value));

        if (normalized == null) return null;

        int slash = normalized.lastIndexOf('/');

        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private boolean isXhtml(String file) {

        String normalized = normalizePath(file);

        if (normalized == null) return false;

        String lower = normalized.toLowerCase(java.util.Locale.ROOT);

        return lower.endsWith(".xhtml") || lower.endsWith(".html");
    }

    private Path requireProjectRoot(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path normalized = projectRoot.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) throw new IllegalStateException("EPUB project root does not exist: " + normalized);
        if (!Files.isDirectory(normalized)) throw new IllegalStateException("EPUB project root is not a directory: " + normalized);

        return normalized;
    }

    private static String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ManifestReference(String id, String href) {}

    private record ProjectState(
            Path projectRoot,
            Path contentRoot,
            Path packagePath,
            Document packageDocument,
            Path navigationPath,
            Document navigationDocument) {}
}