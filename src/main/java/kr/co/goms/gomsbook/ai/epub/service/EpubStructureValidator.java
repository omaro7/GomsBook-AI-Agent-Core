/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubStructureValidationIssue;
import kr.co.goms.gomsbook.ai.epub.model.EpubStructureValidationResult;
import kr.co.goms.gomsbook.ai.epub.policy.spine.EpubSpineOrderPolicy;


public class EpubStructureValidator {


    private final EpubArchiveReader archiveReader;

    private final EpubArchivePackageReader packageReader;

    private final EpubSpineOrderPolicy spineOrderPolicy;


    public EpubStructureValidator(EpubSpineOrderPolicy spineOrderPolicy) {
        this(new EpubArchiveReader(), spineOrderPolicy);
    }


    public EpubStructureValidator(
            EpubArchiveReader archiveReader,
            EpubSpineOrderPolicy spineOrderPolicy) {

        this(
                archiveReader,
                new EpubArchivePackageReader(archiveReader),
                spineOrderPolicy);
    }


    public EpubStructureValidator(
            EpubArchiveReader archiveReader,
            EpubArchivePackageReader packageReader,
            EpubSpineOrderPolicy spineOrderPolicy) {

        if (archiveReader == null) throw new IllegalArgumentException("archiveReader must not be null.");
        if (packageReader == null) throw new IllegalArgumentException("packageReader must not be null.");
        if (spineOrderPolicy == null) throw new IllegalArgumentException("spineOrderPolicy must not be null.");

        this.archiveReader = archiveReader;
        this.packageReader = packageReader;
        this.spineOrderPolicy = spineOrderPolicy;
    }


    public EpubStructureValidationResult validate(Path epubFile) {

        if (epubFile == null) throw new IllegalArgumentException("epubFile must not be null.");

        EpubStructureValidationResult result = new EpubStructureValidationResult();

        String packagePath;

        try {

            packagePath = packageReader.findPackageDocumentPath(epubFile);

        } catch (RuntimeException exception) {

            addError(
                    result,
                    "PACKAGE_PATH_NOT_FOUND",
                    safeMessage(exception),
                    null);

            return result;
        }

        result.setPackagePath(packagePath);

        if (!archiveReader.exists(epubFile, packagePath)) {

            addError(
                    result,
                    "PACKAGE_DOCUMENT_MISSING",
                    "EPUB Package Document does not exist.",
                    packagePath);

            return result;
        }

        Document document;

        try {

            document = packageReader.readPackageDocument(epubFile, packagePath);

        } catch (RuntimeException exception) {

            addError(
                    result,
                    "PACKAGE_DOCUMENT_INVALID",
                    safeMessage(exception),
                    packagePath);

            return result;
        }

        Element packageElement = document.getDocumentElement();

        if (packageElement == null) {

            addError(
                    result,
                    "PACKAGE_ELEMENT_MISSING",
                    "EPUB package element was not found.",
                    packagePath);

            return result;
        }

        Element manifestElement = findDirectChild(packageElement, "manifest");
        Element spineElement = findDirectChild(packageElement, "spine");

        if (manifestElement == null) {

            addError(
                    result,
                    "MANIFEST_MISSING",
                    "EPUB manifest element was not found.",
                    packagePath);
        }

        if (spineElement == null) {

            addError(
                    result,
                    "SPINE_MISSING",
                    "EPUB spine element was not found.",
                    packagePath);
        }

        Map<String, ManifestEntry> manifestEntries = new LinkedHashMap<>();

        if (manifestElement != null) {

            inspectManifest(
                    epubFile,
                    packagePath,
                    manifestElement,
                    manifestEntries,
                    result);
        }

        if (spineElement != null) {

            inspectSpine(
                    spineElement,
                    manifestEntries,
                    result);
        }

        inspectNavigation(
                manifestEntries,
                result);

        return result;
    }


    private void inspectManifest(
            Path epubFile,
            String packagePath,
            Element manifestElement,
            Map<String, ManifestEntry> manifestEntries,
            EpubStructureValidationResult result) {

        NodeList children = manifestElement.getChildNodes();

        Set<String> ids = new HashSet<>();
        Set<String> hrefs = new HashSet<>();

        int itemCount = 0;

        for (int index = 0; index < children.getLength(); index++) {

            Element itemElement = asElement(children.item(index));

            if (itemElement == null) continue;
            if (!isElement(itemElement, "item")) continue;

            itemCount++;

            String id = trimToNull(itemElement.getAttribute("id"));
            String href = trimToNull(itemElement.getAttribute("href"));
            String mediaType = trimToNull(itemElement.getAttribute("media-type"));
            String properties = trimToNull(itemElement.getAttribute("properties"));

            if (id == null) {

                addError(
                        result,
                        "MANIFEST_ID_MISSING",
                        "Manifest item id is missing.",
                        href);

            } else if (!ids.add(id)) {

                addError(
                        result,
                        "MANIFEST_ID_DUPLICATED",
                        "Duplicate EPUB manifest resource id: " + id,
                        id);
            }

            if (href == null) {

                addError(
                        result,
                        "MANIFEST_HREF_MISSING",
                        "Manifest item href is missing.",
                        id);

            } else {

                String normalizedHref = normalizeHref(href);

                if (!hrefs.add(normalizedHref)) {

                    addError(
                            result,
                            "MANIFEST_HREF_DUPLICATED",
                            "Duplicate EPUB manifest resource href: " + href,
                            href);
                }

                String archivePath = resolveArchivePath(packagePath, href);

                if (!isRemoteHref(href) && !archiveReader.exists(epubFile, archivePath)) {

                    addError(
                            result,
                            "MANIFEST_RESOURCE_MISSING",
                            "Manifest resource does not exist in EPUB archive: " + href,
                            href);
                }
            }

            if (mediaType == null) {

                addError(
                        result,
                        "MANIFEST_MEDIA_TYPE_MISSING",
                        "Manifest item media-type is missing.",
                        id);
            }

            if (id != null) {

                ManifestEntry entry = new ManifestEntry();

                entry.id = id;
                entry.href = href;
                entry.mediaType = mediaType;
                entry.properties = properties;

                manifestEntries.putIfAbsent(id, entry);
            }
        }

        result.setManifestItemCount(itemCount);
    }


    private void inspectSpine(
            Element spineElement,
            Map<String, ManifestEntry> manifestEntries,
            EpubStructureValidationResult result) {

        NodeList children = spineElement.getChildNodes();

        Set<String> idrefs = new HashSet<>();

        int spineItemCount = 0;
        int previousOrder = Integer.MIN_VALUE;
        String previousHref = null;

        for (int index = 0; index < children.getLength(); index++) {

            Element itemrefElement = asElement(children.item(index));

            if (itemrefElement == null) continue;
            if (!isElement(itemrefElement, "itemref")) continue;

            spineItemCount++;

            String idref = trimToNull(itemrefElement.getAttribute("idref"));

            if (idref == null) {

                addError(
                        result,
                        "SPINE_IDREF_MISSING",
                        "Spine itemref idref is missing.",
                        "index=" + index);

                continue;
            }

            if (!idrefs.add(idref)) {

                addError(
                        result,
                        "SPINE_IDREF_DUPLICATED",
                        "Duplicate EPUB spine idref: " + idref,
                        idref);
            }

            ManifestEntry manifestEntry = manifestEntries.get(idref);

            if (manifestEntry == null) {

                addError(
                        result,
                        "SPINE_MANIFEST_REFERENCE_MISSING",
                        "Spine idref does not exist in manifest: " + idref,
                        idref);

                continue;
            }

            validateSpineOrder(
                    manifestEntry,
                    previousOrder,
                    previousHref,
                    result);

            previousOrder = spineOrderPolicy.getOrder(manifestEntry.href);
            previousHref = manifestEntry.href;

            if (!isDirectSpineMediaType(manifestEntry.mediaType)) {

                addError(
                        result,
                        "SPINE_RESOURCE_NOT_DOCUMENT",
                        "Spine resource is not XHTML or SVG: " + idref,
                        manifestEntry.href);
            }

            if (hasProperty(manifestEntry.properties, "nav")) {

                addWarning(
                        result,
                        "SPINE_NAV_REFERENCE",
                        "Navigation Document is referenced by spine: " + idref,
                        manifestEntry.href);
            }
        }

        result.setSpineItemCount(spineItemCount);

        if (spineItemCount == 0) {

            addError(
                    result,
                    "SPINE_EMPTY",
                    "EPUB spine does not contain itemref elements.",
                    null);
        }
    }


    private void validateSpineOrder(
            ManifestEntry manifestEntry,
            int previousOrder,
            String previousHref,
            EpubStructureValidationResult result) {

        if (manifestEntry == null || manifestEntry.href == null) return;

        int currentOrder = spineOrderPolicy.getOrder(manifestEntry.href);

        if (currentOrder >= previousOrder) return;

        addError(
                result,
                "SPINE_ORDER_INVALID",
                "EPUB spine reading order is invalid: "
                        + manifestEntry.href
                        + " must not appear after "
                        + previousHref
                        + ".",
                manifestEntry.href);
    }


    private void inspectNavigation(
            Map<String, ManifestEntry> manifestEntries,
            EpubStructureValidationResult result) {

        ManifestEntry navigationEntry = null;

        int navigationCount = 0;

        for (ManifestEntry entry : manifestEntries.values()) {

            if (!hasProperty(entry.properties, "nav")) continue;

            navigationCount++;

            if (navigationEntry == null) navigationEntry = entry;
        }

        if (navigationCount == 0) {

            addError(
                    result,
                    "NAV_ITEM_MISSING",
                    "Manifest item with properties=\"nav\" was not found.",
                    null);

            return;
        }

        if (navigationCount > 1) {

            addError(
                    result,
                    "NAV_ITEM_DUPLICATED",
                    "Multiple Navigation Documents are declared in manifest.",
                    null);
        }

        if (navigationEntry == null) return;

        result.setNavId(navigationEntry.id);
        result.setNavHref(navigationEntry.href);

        if (!"application/xhtml+xml".equalsIgnoreCase(navigationEntry.mediaType)) {

            addError(
                    result,
                    "NAV_MEDIA_TYPE_INVALID",
                    "Navigation Document media-type must be application/xhtml+xml.",
                    navigationEntry.mediaType);
        }
    }


    private boolean isDirectSpineMediaType(String mediaType) {

        if (mediaType == null) return false;
        if ("application/xhtml+xml".equalsIgnoreCase(mediaType)) return true;

        return "image/svg+xml".equalsIgnoreCase(mediaType);
    }


    private boolean hasProperty(String properties, String expected) {

        if (properties == null || expected == null) return false;

        String[] values = properties.trim().split("\\s+");

        for (String value : values) {
            if (expected.equalsIgnoreCase(value)) return true;
        }

        return false;
    }


    private String resolveArchivePath(String packagePath, String href) {

        if (href == null) return null;

        String normalizedHref = normalizeHref(href);

        int slashIndex = packagePath.lastIndexOf('/');

        if (slashIndex < 0) return normalizedHref;

        String packageDirectory = packagePath.substring(0, slashIndex + 1);

        Path resolved = Path.of(packageDirectory).resolve(normalizedHref).normalize();

        return resolved.toString().replace('\\', '/');
    }


    private String normalizeHref(String href) {

        String normalized = href.trim().replace('\\', '/');

        while (normalized.startsWith("./")) normalized = normalized.substring(2);

        return normalized;
    }


    private boolean isRemoteHref(String href) {

        if (href == null) return false;

        String normalized = href.trim().toLowerCase();

        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }


    private Element findDirectChild(Element parent, String localName) {

        NodeList children = parent.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Element element = asElement(children.item(index));

            if (element == null) continue;
            if (isElement(element, localName)) return element;
        }

        return null;
    }


    private boolean isElement(Element element, String localName) {

        if (element == null || localName == null) return false;

        String elementLocalName = element.getLocalName();

        if (elementLocalName != null) return localName.equalsIgnoreCase(elementLocalName);

        String tagName = element.getTagName();

        int colonIndex = tagName.indexOf(':');

        if (colonIndex >= 0) tagName = tagName.substring(colonIndex + 1);

        return localName.equalsIgnoreCase(tagName);
    }


    private Element asElement(Node node) {

        if (node == null) return null;
        if (node.getNodeType() != Node.ELEMENT_NODE) return null;

        return (Element) node;
    }


    private void addError(
            EpubStructureValidationResult result,
            String code,
            String message,
            String target) {

        result.addIssue(
                new EpubStructureValidationIssue(
                        code,
                        "ERROR",
                        message,
                        target));
    }


    private void addWarning(
            EpubStructureValidationResult result,
            String code,
            String message,
            String target) {

        result.addIssue(
                new EpubStructureValidationIssue(
                        code,
                        "WARNING",
                        message,
                        target));
    }


    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }


    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "Unknown EPUB structure validation error.";

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();

        return message.trim();
    }


    private static final class ManifestEntry {

        private String id;
        private String href;
        private String mediaType;
        private String properties;
    }
}