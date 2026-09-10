/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.accessibility.validation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubProjectValidationIssue;
import kr.co.goms.gomsbook.ai.epub.model.EpubProjectValidationResult;
import kr.co.goms.gomsbook.ai.epub.validation.EpubProjectValidator;

/**
 * EPUB 파일 생성 전 현재 EPUB 프로젝트의 구조와 정합성을 검증합니다.
 */
public final class DefaultEpubProjectValidator implements EpubProjectValidator {

    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final String EPUB_NAMESPACE = "http://www.idpf.org/2007/ops";
    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    @Override
    public EpubProjectValidationResult validate(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        EpubProjectValidationResult.Builder result = EpubProjectValidationResult.builder().projectRoot(normalizedProjectRoot);

        if (!validateProjectRoot(normalizedProjectRoot, result)) return result.build();

        Path containerPath = normalizedProjectRoot.resolve(CONTAINER_PATH).normalize();

        if (!validateContainerFile(containerPath, result)) return result.build();

        Document containerDocument = parseXml(containerPath, "EPUB_PROJECT_CONTAINER_XML_INVALID", result);

        if (containerDocument == null) return result.build();

        String packageRelativePath = resolvePackageRelativePath(containerDocument, result);

        if (packageRelativePath == null) return result.build();

        Path packagePath = resolveProjectPath(normalizedProjectRoot, normalizedProjectRoot, packageRelativePath);

        if (packagePath == null) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_PATH_INVALID",
                    "Package document path escapes the EPUB project root: " + packageRelativePath));

            return result.build();
        }

        result.packagePath(packagePath);

        if (!validatePackageFile(packagePath, result)) return result.build();

        Document packageDocument = parseXml(packagePath, "EPUB_PROJECT_PACKAGE_XML_INVALID", result);

        if (packageDocument == null) return result.build();

        validatePackageElement(packageDocument, result);
        validateMetadata(packageDocument, result);

        Path packageDirectory = packagePath.getParent();

        if (packageDirectory == null) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_DIRECTORY_MISSING",
                    "Package document parent directory is not available: " + packagePath));

            return result.build();
        }

        ManifestValidationContext manifestContext = validateManifest(
                normalizedProjectRoot,
                packageDirectory,
                packageDocument,
                result);

        validateSpine(packageDocument, manifestContext, result);
        validateNavigation(normalizedProjectRoot, packageDirectory, manifestContext, result);
        validateXhtmlResources(normalizedProjectRoot, packageDirectory, manifestContext, result);

        return result.build();
    }

    private boolean validateProjectRoot(Path projectRoot, EpubProjectValidationResult.Builder result) {

        if (!Files.exists(projectRoot)) {

            result.issue(error(
                    "EPUB_PROJECT_ROOT_NOT_FOUND",
                    "EPUB project root does not exist: " + projectRoot));

            return false;
        }

        if (!Files.isDirectory(projectRoot)) {

            result.issue(error(
                    "EPUB_PROJECT_ROOT_NOT_DIRECTORY",
                    "EPUB project root is not a directory: " + projectRoot));

            return false;
        }

        if (!Files.isReadable(projectRoot)) {

            result.issue(error(
                    "EPUB_PROJECT_ROOT_NOT_READABLE",
                    "EPUB project root is not readable: " + projectRoot));

            return false;
        }

        return true;
    }

    private boolean validateContainerFile(Path containerPath, EpubProjectValidationResult.Builder result) {

        if (!Files.exists(containerPath)) {

            result.issue(error(
                    "EPUB_PROJECT_CONTAINER_MISSING",
                    "EPUB container.xml does not exist: " + containerPath));

            return false;
        }

        if (!Files.isRegularFile(containerPath)) {

            result.issue(error(
                    "EPUB_PROJECT_CONTAINER_NOT_FILE",
                    "EPUB container.xml is not a regular file: " + containerPath));

            return false;
        }

        if (!Files.isReadable(containerPath)) {

            result.issue(error(
                    "EPUB_PROJECT_CONTAINER_NOT_READABLE",
                    "EPUB container.xml is not readable: " + containerPath));

            return false;
        }

        return true;
    }

    private String resolvePackageRelativePath(
            Document containerDocument,
            EpubProjectValidationResult.Builder result) {

        NodeList rootfiles = elements(containerDocument, "rootfile");

        if (rootfiles.getLength() == 0) {

            result.issue(error(
                    "EPUB_PROJECT_ROOTFILE_MISSING",
                    "container.xml does not contain a rootfile element."));

            return null;
        }

        Element rootfile = (Element) rootfiles.item(0);
        String fullPath = attribute(rootfile, "full-path");

        if (fullPath == null) {

            result.issue(error(
                    "EPUB_PROJECT_ROOTFILE_PATH_MISSING",
                    "container.xml rootfile does not contain full-path."));

            return null;
        }

        String mediaType = attribute(rootfile, "media-type");

        if (mediaType != null && !"application/oebps-package+xml".equalsIgnoreCase(mediaType)) {

            result.issue(warning(
                    "EPUB_PROJECT_ROOTFILE_MEDIA_TYPE_UNEXPECTED",
                    "Unexpected rootfile media-type: " + mediaType));
        }

        return fullPath;
    }

    private boolean validatePackageFile(Path packagePath, EpubProjectValidationResult.Builder result) {

        if (!Files.exists(packagePath)) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_MISSING",
                    "Package document does not exist: " + packagePath));

            return false;
        }

        if (!Files.isRegularFile(packagePath)) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_NOT_FILE",
                    "Package document is not a regular file: " + packagePath));

            return false;
        }

        if (!Files.isReadable(packagePath)) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_NOT_READABLE",
                    "Package document is not readable: " + packagePath));

            return false;
        }

        return true;
    }

    private void validatePackageElement(
            Document packageDocument,
            EpubProjectValidationResult.Builder result) {

        Element packageElement = packageDocument.getDocumentElement();

        if (packageElement == null || !"package".equals(localName(packageElement))) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_ELEMENT_INVALID",
                    "Package document root element must be <package>."));

            return;
        }

        String version = attribute(packageElement, "version");

        if (version == null) {

            result.issue(error(
                    "EPUB_PROJECT_PACKAGE_VERSION_MISSING",
                    "Package document version is missing."));

        } else if (!version.startsWith("3")) {

            result.issue(warning(
                    "EPUB_PROJECT_PACKAGE_VERSION_NOT_EPUB3",
                    "Package document version is not EPUB 3.x: " + version));
        }

        String uniqueIdentifier = attribute(packageElement, "unique-identifier");

        if (uniqueIdentifier == null) {

            result.issue(error(
                    "EPUB_PROJECT_UNIQUE_IDENTIFIER_MISSING",
                    "Package document unique-identifier is missing."));
        }
    }

    private void validateMetadata(
            Document packageDocument,
            EpubProjectValidationResult.Builder result) {

        Element metadata = firstElement(packageDocument, "metadata");

        if (metadata == null) {

            result.issue(error(
                    "EPUB_PROJECT_METADATA_MISSING",
                    "Package document metadata element is missing."));

            return;
        }

        Element identifier = firstElement(metadata, "identifier");
        Element title = firstElement(metadata, "title");
        Element language = firstElement(metadata, "language");

        if (identifier == null || text(identifier) == null) {

            result.issue(error(
                    "EPUB_PROJECT_IDENTIFIER_MISSING",
                    "EPUB metadata dc:identifier is missing."));
        }

        if (title == null || text(title) == null) {

            result.issue(error(
                    "EPUB_PROJECT_TITLE_MISSING",
                    "EPUB metadata dc:title is missing."));
        }

        if (language == null || text(language) == null) {

            result.issue(error(
                    "EPUB_PROJECT_LANGUAGE_MISSING",
                    "EPUB metadata dc:language is missing."));
        }

        validateUniqueIdentifierReference(packageDocument, identifier, result);
        validateModifiedMetadata(metadata, result);
    }

    private void validateUniqueIdentifierReference(
            Document packageDocument,
            Element firstIdentifier,
            EpubProjectValidationResult.Builder result) {

        Element packageElement = packageDocument.getDocumentElement();

        if (packageElement == null) return;

        String uniqueIdentifier = attribute(packageElement, "unique-identifier");

        if (uniqueIdentifier == null) return;

        NodeList identifiers = elements(packageDocument, "identifier");

        for (int index = 0; index < identifiers.getLength(); index++) {

            Element identifier = (Element) identifiers.item(index);
            String id = attribute(identifier, "id");

            if (uniqueIdentifier.equals(id)) return;
        }

        String fallback = firstIdentifier == null
                ? ""
                : " First dc:identifier exists but does not use id=\"" + uniqueIdentifier + "\".";

        result.issue(error(
                "EPUB_PROJECT_UNIQUE_IDENTIFIER_REFERENCE_INVALID",
                "Package unique-identifier does not reference an existing dc:identifier: "
                        + uniqueIdentifier
                        + "."
                        + fallback));
    }

    private void validateModifiedMetadata(
            Element metadata,
            EpubProjectValidationResult.Builder result) {

        NodeList metaElements = elements(metadata, "meta");

        for (int index = 0; index < metaElements.getLength(); index++) {

            Element meta = (Element) metaElements.item(index);
            String property = attribute(meta, "property");

            if (!"dcterms:modified".equals(property)) continue;
            if (text(meta) != null) return;
        }

        result.issue(error(
                "EPUB_PROJECT_MODIFIED_MISSING",
                "EPUB metadata dcterms:modified is missing."));
    }

    private ManifestValidationContext validateManifest(
            Path projectRoot,
            Path packageDirectory,
            Document packageDocument,
            EpubProjectValidationResult.Builder result) {

        ManifestValidationContext context = new ManifestValidationContext();
        Element manifest = firstElement(packageDocument, "manifest");

        if (manifest == null) {

            result.issue(error(
                    "EPUB_PROJECT_MANIFEST_MISSING",
                    "Package document manifest element is missing."));

            return context;
        }

        Set<String> ids = new HashSet<>();
        Set<String> hrefs = new HashSet<>();

        for (Element itemElement : childElements(manifest, "item")) {

            String id = attribute(itemElement, "id");
            String href = attribute(itemElement, "href");
            String mediaType = attribute(itemElement, "media-type");
            String properties = attribute(itemElement, "properties");

            boolean validForModel = true;

            if (id == null) {

                result.issue(error(
                        "EPUB_PROJECT_MANIFEST_ID_MISSING",
                        "Manifest item id is missing: href=" + safe(href)));

                validForModel = false;
            }

            if (href == null) {

                result.issue(error(
                        "EPUB_PROJECT_MANIFEST_HREF_MISSING",
                        "Manifest item href is missing: id=" + safe(id)));

                validForModel = false;
            }

            if (mediaType == null) {

                result.issue(error(
                        "EPUB_PROJECT_MANIFEST_MEDIA_TYPE_MISSING",
                        "Manifest item media-type is missing: id="
                                + safe(id)
                                + ", href="
                                + safe(href)));

                validForModel = false;
            }

            if (id != null && !ids.add(id)) {

                result.issue(error(
                        "EPUB_PROJECT_MANIFEST_ID_DUPLICATE",
                        "Duplicate manifest id: " + id));
            }

            String normalizedHref = normalizeHref(href);

            if (normalizedHref != null && !hrefs.add(normalizedHref)) {

                result.issue(error(
                        "EPUB_PROJECT_MANIFEST_HREF_DUPLICATE",
                        "Duplicate manifest href: " + href));
            }

            if (!validForModel) continue;

            EpubManifestItem manifestItem;

            try {

                manifestItem = EpubManifestItem.builder()
                        .id(id)
                        .href(href)
                        .mediaType(mediaType)
                        .properties(properties)
                        .build();

            } catch (IllegalArgumentException exception) {

                result.issue(error(
                        "EPUB_PROJECT_MANIFEST_ITEM_INVALID",
                        "Invalid manifest item: id="
                                + id
                                + ", href="
                                + href
                                + " / "
                                + safeMessage(exception)));

                continue;
            }

            context.items.add(manifestItem);

            if (!context.itemsById.containsKey(manifestItem.getId())) {
                context.itemsById.put(manifestItem.getId(), manifestItem);
            }

            if (manifestItem.hasProperty("nav")) {
                context.navItems.add(manifestItem);
            }

            validateManifestResource(
                    projectRoot,
                    packageDirectory,
                    manifestItem,
                    result);

            validateNavProperty(
                    manifestItem,
                    result);
        }

        if (context.navItems.isEmpty()) {

            result.issue(error(
                    "EPUB_PROJECT_NAV_MANIFEST_ITEM_MISSING",
                    "Manifest does not contain an item with properties=\"nav\"."));

        } else if (context.navItems.size() > 1) {

            result.issue(error(
                    "EPUB_PROJECT_NAV_MANIFEST_ITEM_DUPLICATE",
                    "Manifest contains more than one item with properties=\"nav\"."));
        }

        return context;
    }

    private void validateManifestResource(
            Path projectRoot,
            Path packageDirectory,
            EpubManifestItem item,
            EpubProjectValidationResult.Builder result) {

        if (item.isRemote()) return;
        if (isExternalReference(item.getHref())) return;

        Path resourcePath = resolveProjectPath(
                projectRoot,
                packageDirectory,
                item.getHref());

        if (resourcePath == null) {

            result.issue(error(
                    "EPUB_PROJECT_MANIFEST_RESOURCE_PATH_INVALID",
                    "Manifest resource escapes the EPUB project root: " + item.getHref()));

            return;
        }

        if (!Files.exists(resourcePath)) {

            result.issue(error(
                    "EPUB_PROJECT_MANIFEST_RESOURCE_MISSING",
                    "Manifest resource does not exist: " + item.getHref()));

            return;
        }

        if (!Files.isRegularFile(resourcePath)) {

            result.issue(error(
                    "EPUB_PROJECT_MANIFEST_RESOURCE_NOT_FILE",
                    "Manifest resource is not a regular file: " + item.getHref()));
        }
    }

    private void validateNavProperty(
            EpubManifestItem item,
            EpubProjectValidationResult.Builder result) {

        if (!"nav.xhtml".equalsIgnoreCase(item.getFileName())) return;
        if (item.hasProperty("nav")) return;

        result.issue(error(
                "EPUB_PROJECT_NAV_PROPERTY_MISSING",
                "Manifest item for nav.xhtml must contain properties=\"nav\": id="
                        + item.getId()
                        + ", href="
                        + item.getHref()));
    }

    private void validateSpine(
            Document packageDocument,
            ManifestValidationContext manifestContext,
            EpubProjectValidationResult.Builder result) {

        Element spine = firstElement(packageDocument, "spine");

        if (spine == null) {

            result.issue(error(
                    "EPUB_PROJECT_SPINE_MISSING",
                    "Package document spine element is missing."));

            return;
        }

        List<Element> itemrefs = childElements(spine, "itemref");

        if (itemrefs.isEmpty()) {

            result.issue(warning(
                    "EPUB_PROJECT_SPINE_EMPTY",
                    "Package document spine contains no itemref elements."));

            return;
        }

        for (Element itemref : itemrefs) {

            String idref = attribute(itemref, "idref");

            if (idref == null) {

                result.issue(error(
                        "EPUB_PROJECT_SPINE_IDREF_MISSING",
                        "Spine itemref does not contain idref."));

                continue;
            }

            if (!manifestContext.itemsById.containsKey(idref)) {

                result.issue(error(
                        "EPUB_PROJECT_SPINE_MANIFEST_REFERENCE_MISSING",
                        "Spine idref does not reference a manifest item: " + idref));
            }
        }
    }

    private void validateNavigation(
            Path projectRoot,
            Path packageDirectory,
            ManifestValidationContext manifestContext,
            EpubProjectValidationResult.Builder result) {

        if (manifestContext.navItems.isEmpty()) return;

        EpubManifestItem navItem = manifestContext.navItems.get(0);

        Path navigationPath = resolveProjectPath(
                projectRoot,
                packageDirectory,
                navItem.getHref());

        if (navigationPath == null || !Files.isRegularFile(navigationPath)) return;

        Document navDocument = parseXml(
                navigationPath,
                "EPUB_PROJECT_NAV_XML_INVALID",
                result);

        if (navDocument == null) return;

        Element tocNav = findTocNav(navDocument);

        if (tocNav == null) {

            result.issue(error(
                    "EPUB_PROJECT_NAV_TOC_MISSING",
                    "Navigation document does not contain nav epub:type=\"toc\"."));

            return;
        }

        Path navigationDirectory = navigationPath.getParent();

        if (navigationDirectory == null) return;

        NodeList anchors = elements(tocNav, "a");

        for (int index = 0; index < anchors.getLength(); index++) {

            Element anchor = (Element) anchors.item(index);
            String href = attribute(anchor, "href");

            if (href == null) {

                result.issue(error(
                        "EPUB_PROJECT_NAV_HREF_MISSING",
                        "Navigation TOC anchor does not contain href."));

                continue;
            }

            validateNavigationHref(
                    projectRoot,
                    navigationDirectory,
                    href,
                    result);
        }
    }

    private Element findTocNav(Document document) {

        NodeList navElements = elements(document, "nav");

        for (int index = 0; index < navElements.getLength(); index++) {

            Element nav = (Element) navElements.item(index);
            String epubType = nav.getAttributeNS(EPUB_NAMESPACE, "type");

            if (epubType == null || epubType.isBlank()) epubType = nav.getAttribute("epub:type");
            if (containsToken(epubType, "toc")) return nav;
        }

        return null;
    }

    private void validateNavigationHref(
            Path projectRoot,
            Path navigationDirectory,
            String href,
            EpubProjectValidationResult.Builder result) {

        if (href.isBlank()) {

            result.issue(error(
                    "EPUB_PROJECT_NAV_HREF_EMPTY",
                    "Navigation TOC href must not be empty."));

            return;
        }

        if (href.startsWith("#")) return;
        if (isExternalReference(href)) return;

        Path target = resolveProjectPath(
                projectRoot,
                navigationDirectory,
                href);

        if (target == null) {

            result.issue(error(
                    "EPUB_PROJECT_NAV_TARGET_PATH_INVALID",
                    "Navigation href escapes the EPUB project root: " + href));

            return;
        }

        if (!Files.isRegularFile(target)) {

            result.issue(error(
                    "EPUB_PROJECT_NAV_TARGET_MISSING",
                    "Navigation target does not exist: " + href));
        }
    }

    private void validateXhtmlResources(
            Path projectRoot,
            Path packageDirectory,
            ManifestValidationContext manifestContext,
            EpubProjectValidationResult.Builder result) {

        for (EpubManifestItem item : manifestContext.items) {

            if (!XHTML_MEDIA_TYPE.equalsIgnoreCase(item.getMediaType())) continue;
            if (item.isRemote()) continue;

            Path documentPath = resolveProjectPath(
                    projectRoot,
                    packageDirectory,
                    item.getHref());

            if (documentPath == null || !Files.isRegularFile(documentPath)) continue;

            Document document = parseXml(
                    documentPath,
                    "EPUB_PROJECT_XHTML_XML_INVALID",
                    result);

            if (document == null) continue;

            validateElementReferences(projectRoot, documentPath, document, "img", "src", result);
            validateElementReferences(projectRoot, documentPath, document, "script", "src", result);
            validateElementReferences(projectRoot, documentPath, document, "link", "href", result);
        }
    }

    private void validateElementReferences(
            Path projectRoot,
            Path documentPath,
            Document document,
            String elementName,
            String attributeName,
            EpubProjectValidationResult.Builder result) {

        NodeList nodes = elements(document, elementName);
        Path documentDirectory = documentPath.getParent();

        if (documentDirectory == null) return;

        for (int index = 0; index < nodes.getLength(); index++) {

            Element element = (Element) nodes.item(index);
            String reference = attribute(element, attributeName);

            if (reference == null) continue;
            if (reference.startsWith("#")) continue;
            if (isExternalReference(reference)) continue;

            Path target = resolveProjectPath(
                    projectRoot,
                    documentDirectory,
                    reference);

            if (target == null) {

                result.issue(error(
                        "EPUB_PROJECT_XHTML_RESOURCE_PATH_INVALID",
                        "XHTML resource reference escapes the EPUB project root: "
                                + relative(projectRoot, documentPath)
                                + " -> "
                                + reference));

                continue;
            }

            if (!Files.isRegularFile(target)) {

                result.issue(error(
                        "EPUB_PROJECT_XHTML_RESOURCE_MISSING",
                        "XHTML resource does not exist: "
                                + relative(projectRoot, documentPath)
                                + " -> "
                                + reference));
            }
        }
    }

    private Document parseXml(
            Path path,
            String errorCode,
            EpubProjectValidationResult.Builder result) {

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

            result.issue(error(
                    errorCode,
                    "Failed to read XML document: "
                            + path
                            + " / "
                            + safeMessage(exception)));

            return null;
        }
    }

    private Path resolveProjectPath(
            Path projectRoot,
            Path baseDirectory,
            String reference) {

        String normalizedReference = removeQueryAndFragment(reference);

        if (normalizedReference == null) return null;
        if (isExternalReference(normalizedReference)) return null;

        try {

            Path path = baseDirectory
                    .resolve(normalizedReference.replace('/', java.io.File.separatorChar))
                    .normalize();

            if (!path.startsWith(projectRoot)) return null;

            return path;

        } catch (RuntimeException exception) {
            return null;
        }
    }

    private NodeList elements(Document document, String localName) {

        NodeList nodes = document.getElementsByTagNameNS("*", localName);

        if (nodes.getLength() == 0) nodes = document.getElementsByTagName(localName);

        return nodes;
    }

    private NodeList elements(Element element, String localName) {

        NodeList nodes = element.getElementsByTagNameNS("*", localName);

        if (nodes.getLength() == 0) nodes = element.getElementsByTagName(localName);

        return nodes;
    }

    private Element firstElement(Document document, String localName) {

        NodeList nodes = elements(document, localName);

        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private Element firstElement(Element parent, String localName) {

        NodeList nodes = elements(parent, localName);

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

    private String localName(Element element) {

        if (element == null) return "";

        String localName = element.getLocalName();

        if (localName != null && !localName.isBlank()) return localName.toLowerCase(Locale.ROOT);

        String nodeName = element.getNodeName();
        int separatorIndex = nodeName.indexOf(':');

        if (separatorIndex >= 0) nodeName = nodeName.substring(separatorIndex + 1);

        return nodeName.toLowerCase(Locale.ROOT);
    }

    private String attribute(Element element, String name) {

        if (element == null || name == null) return null;

        String value = element.getAttribute(name);

        return value == null || value.isBlank() ? null : value.trim();
    }

    private String text(Element element) {

        if (element == null) return null;

        String value = element.getTextContent();

        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean containsToken(String values, String target) {

        if (values == null || target == null) return false;

        for (String token : values.trim().split("\\s+")) {
            if (target.equalsIgnoreCase(token)) return true;
        }

        return false;
    }

    private boolean isExternalReference(String reference) {

        if (reference == null || reference.isBlank()) return false;

        String value = reference.trim().toLowerCase(Locale.ROOT);

        return value.startsWith("http:")
                || value.startsWith("https:")
                || value.startsWith("mailto:")
                || value.startsWith("tel:")
                || value.startsWith("data:")
                || value.startsWith("urn:");
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

    private String normalizeHref(String href) {

        String value = removeQueryAndFragment(href);

        return value == null ? null : value.replace('\\', '/');
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

        if (throwable == null) return "Unknown validation error.";

        String message = throwable.getMessage();

        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message.trim();
    }

    private EpubProjectValidationIssue error(String code, String message) {
        return EpubProjectValidationIssue.error(code, message);
    }

    private EpubProjectValidationIssue warning(String code, String message) {
        return EpubProjectValidationIssue.warning(code, message);
    }

    private static final class ManifestValidationContext {

        private final List<EpubManifestItem> items = new ArrayList<>();
        private final Map<String, EpubManifestItem> itemsById = new HashMap<>();
        private final List<EpubManifestItem> navItems = new ArrayList<>();

        private ManifestValidationContext() {
        }
    }
}