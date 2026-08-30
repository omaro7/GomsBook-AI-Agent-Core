/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadata;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadataItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubPageProgressionDirection;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubVersion;


public class EpubArchivePackageReader {


    private static final String CONTAINER_ENTRY = "META-INF/container.xml";

    private static final String PACKAGE_MEDIA_TYPE = "application/oebps-package+xml";

    private static final String DC_NAMESPACE = "http://purl.org/dc/elements/1.1/";

    private static final String XML_NAMESPACE = XMLConstants.XML_NS_URI;

    private static final Pattern PREFIX_PATTERN = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_.-]*):\\s*([^\\s]+)");


    private final EpubArchiveReader archiveReader;


    public EpubArchivePackageReader() {

        this(new EpubArchiveReader());
    }


    public EpubArchivePackageReader(
            EpubArchiveReader archiveReader) {

        if (archiveReader == null) {

            throw new IllegalArgumentException(
                    "archiveReader must not be null.");
        }

        this.archiveReader = archiveReader;
    }


    public EpubPackage read(
            Path epubFile) {

        String packagePath = findPackageDocumentPath(epubFile);

        Document document = readPackageDocument(epubFile, packagePath);

        return readPackage(document, packagePath);
    }


    public String findPackageDocumentPath(
            Path epubFile) {

        String containerXml = archiveReader.readText(epubFile, CONTAINER_ENTRY);

        Document document = parseXml(containerXml);

        NodeList rootfileNodes = document.getElementsByTagNameNS("*", "rootfile");

        if (rootfileNodes.getLength() == 0) {

            throw new IllegalStateException(
                    "EPUB package rootfile was not found.");
        }

        Element fallbackRootfile = null;

        for (int index = 0; index < rootfileNodes.getLength(); index++) {

            Element rootfileElement = asElement(rootfileNodes.item(index));

            if (rootfileElement == null) {

                continue;
            }

            if (fallbackRootfile == null) {

                fallbackRootfile = rootfileElement;
            }

            String mediaType = trimToNull(rootfileElement.getAttribute("media-type"));

            if (!PACKAGE_MEDIA_TYPE.equals(mediaType)) {

                continue;
            }

            String fullPath = trimToNull(rootfileElement.getAttribute("full-path"));

            if (fullPath != null) {

                return normalizeEntryPath(fullPath);
            }
        }

        if (fallbackRootfile == null) {

            throw new IllegalStateException(
                    "EPUB package rootfile was not found.");
        }

        String fullPath = trimToNull(fallbackRootfile.getAttribute("full-path"));

        if (fullPath == null) {

            throw new IllegalStateException(
                    "EPUB package rootfile path is empty.");
        }

        return normalizeEntryPath(fullPath);
    }


    public Document readPackageDocument(
            Path epubFile) {

        String packagePath = findPackageDocumentPath(epubFile);

        return readPackageDocument(epubFile, packagePath);
    }


    public Document readPackageDocument(
            Path epubFile,
            String packagePath) {

        String normalizedPackagePath = normalizeEntryPath(packagePath);

        if (normalizedPackagePath == null) {

            throw new IllegalArgumentException(
                    "packagePath must not be empty.");
        }

        String opfXml = archiveReader.readText(epubFile, normalizedPackagePath);

        return parseXml(opfXml);
    }


    private EpubPackage readPackage(
            Document document,
            String packagePath) {

        Element packageElement = document.getDocumentElement();

        if (packageElement == null) {

            throw new IllegalStateException(
                    "EPUB package element was not found.");
        }

        String versionValue = trimToNull(packageElement.getAttribute("version"));

        String uniqueIdentifierId = trimToNull(packageElement.getAttribute("unique-identifier"));

        String language = readLanguage(packageElement);

        String direction = trimToNull(packageElement.getAttribute("dir"));

        String prefixValue = trimToNull(packageElement.getAttribute("prefix"));

        Element metadataElement = findDirectChild(packageElement, "metadata");

        Element manifestElement = findDirectChild(packageElement, "manifest");

        Element spineElement = findDirectChild(packageElement, "spine");

        if (metadataElement == null) {

            throw new IllegalStateException(
                    "EPUB metadata element was not found.");
        }

        if (manifestElement == null) {

            throw new IllegalStateException(
                    "EPUB manifest element was not found.");
        }

        if (spineElement == null) {

            throw new IllegalStateException(
                    "EPUB spine element was not found.");
        }

        EpubMetadata metadata = readMetadata(metadataElement, uniqueIdentifierId);

        EpubManifest manifest = readManifest(manifestElement);

        EpubSpine spine = readSpine(spineElement);

        EpubPackage.Builder builder = EpubPackage.builder();

        EpubVersion version = resolveVersion(versionValue);

        if (version != null) {

            builder.version(version);
        }

        builder.metadata(metadata);

        builder.manifest(manifest);

        builder.spine(spine);

        builder.packageDocumentPath(packagePath);

        builder.validateOnBuild(false);

        if (language != null) {

            builder.language(language);
        }

        if (direction != null) {

            builder.direction(direction);
        }

        Map<String, String> prefixes = parsePrefixes(prefixValue);

        if (!prefixes.isEmpty()) {

            builder.prefixes(prefixes);
        }

        return builder.build();
    }


    private EpubMetadata readMetadata(
            Element metadataElement,
            String uniqueIdentifierId) {

        EpubMetadata.Builder builder = EpubMetadata.builder();

        builder.validateOnBuild(false);

        NodeList children = metadataElement.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Element element = asElement(children.item(index));

            if (element == null) {

                continue;
            }

            EpubMetadataItem entry = readMetadataEntry(element);

            if (entry != null) {

                builder.entry(entry);
            }
        }

        EpubMetadata metadata = builder.build();

        if (uniqueIdentifierId == null) {

            return metadata;
        }

        Optional<EpubMetadataItem> identifier = metadata.findById(uniqueIdentifierId);

        if (identifier.isEmpty()) {

            return metadata;
        }

        if (!identifier.get().isIdentifier()) {

            return metadata;
        }

        metadata.setUniqueIdentifierId(uniqueIdentifierId);

        return metadata;
    }


    private EpubMetadataItem readMetadataEntry(
            Element element) {

        if (isDublinCoreElement(element)) {

            return readDublinCoreEntry(element);
        }

        if (isElement(element, "meta")) {

            return readMetaEntry(element);
        }

        return null;
    }


    private EpubMetadataItem readDublinCoreEntry(
            Element element) {

        String localName = getLocalName(element);

        String value = trimToNull(element.getTextContent());

        if (value == null) {

            return null;
        }

        EpubMetadataItem.Builder builder = EpubMetadataItem.dc(
                "dc:" + localName,
                value);

        applyMetadataAttributes(element, builder);

        return builder.build();
    }


    private EpubMetadataItem readMetaEntry(
            Element element) {

        String property = trimToNull(element.getAttribute("property"));

        if (property == null) {

            return null;
        }

        String value = trimToNull(element.getTextContent());

        if (value == null) {

            return null;
        }

        EpubMetadataItem.Builder builder = EpubMetadataItem.meta(property, value);

        applyMetadataAttributes(element, builder);

        return builder.build();
    }


    private void applyMetadataAttributes(
            Element element,
            EpubMetadataItem.Builder builder) {

        String id = trimToNull(element.getAttribute("id"));

        String refines = trimToNull(element.getAttribute("refines"));

        String scheme = trimToNull(element.getAttribute("scheme"));

        String language = readLanguage(element);

        String direction = trimToNull(element.getAttribute("dir"));

        if (id != null) {

            builder.id(id);
        }

        if (refines != null) {

            builder.refines(refines);
        }

        if (scheme != null) {

            builder.scheme(scheme);
        }

        if (language != null) {

            builder.language(language);
        }

        if (direction != null) {

            builder.direction(direction);
        }

        NamedNodeMap attributes = element.getAttributes();

        for (int index = 0; index < attributes.getLength(); index++) {

            Node attribute = attributes.item(index);

            String name = attribute.getNodeName();

            if (isReservedMetadataAttribute(name)) {

                continue;
            }

            if (name.startsWith("xmlns")) {

                continue;
            }

            String value = trimToNull(attribute.getNodeValue());

            if (value != null) {

                builder.attribute(name, value);
            }
        }
    }


    private boolean isReservedMetadataAttribute(
            String name) {

        if ("id".equals(name)) {

            return true;
        }

        if ("property".equals(name)) {

            return true;
        }

        if ("refines".equals(name)) {

            return true;
        }

        if ("scheme".equals(name)) {

            return true;
        }

        if ("xml:lang".equals(name)) {

            return true;
        }

        if ("lang".equals(name)) {

            return true;
        }

        return "dir".equals(name);
    }


    private EpubManifest readManifest(
            Element manifestElement) {

        EpubManifest.Builder builder = EpubManifest.builder();

        builder.validateOnBuild(false);

        NodeList children = manifestElement.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Element element = asElement(children.item(index));

            if (element == null) {

                continue;
            }

            if (!isElement(element, "item")) {

                continue;
            }

            EpubManifestItem resource = readResource(element);

            builder.resource(resource);
        }

        return builder.build();
    }


    private EpubManifestItem readResource(
            Element element) {

        String id = requireAttribute(element, "id");

        String href = requireAttribute(element, "href");

        String mediaType = requireAttribute(element, "media-type");

        EpubManifestItem.Builder builder = EpubManifestItem.builder(id, href);

        builder.mediaType(mediaType);

        String properties = trimToNull(element.getAttribute("properties"));

        String fallback = trimToNull(element.getAttribute("fallback"));

        String mediaOverlay = trimToNull(element.getAttribute("media-overlay"));

        if (properties != null) {

            builder.properties(properties);
        }

        if (fallback != null) {

            builder.fallbackId(fallback);
        }

        if (mediaOverlay != null) {

            builder.mediaOverlayId(mediaOverlay);
        }

        return builder.build();
    }


    private EpubSpine readSpine(
            Element spineElement) {

        EpubSpine.Builder builder = EpubSpine.builder();

        builder.validateOnBuild(false);

        String toc = trimToNull(spineElement.getAttribute("toc"));

        String progression = trimToNull(
                spineElement.getAttribute("page-progression-direction"));

        if (toc != null) {

            builder.tocId(toc);
        }

        EpubPageProgressionDirection direction = resolvePageProgressionDirection(progression);

        if (direction != null) {

            builder.pageProgressionDirection(direction);
        }

        NodeList children = spineElement.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Element element = asElement(children.item(index));

            if (element == null) {

                continue;
            }

            if (!isElement(element, "itemref")) {

                continue;
            }

            builder.item(readSpineItem(element));
        }

        return builder.build();
    }


    private EpubSpineItem readSpineItem(
            Element element) {

        String idref = requireAttribute(element, "idref");

        EpubSpineItem.Builder builder = EpubSpineItem.builder(idref);

        String id = trimToNull(element.getAttribute("id"));

        String linear = trimToNull(element.getAttribute("linear"));

        String properties = trimToNull(element.getAttribute("properties"));

        if (id != null) {

            builder.id(id);
        }

        if ("no".equalsIgnoreCase(linear)) {

            builder.linear(false);
        }

        if (properties != null) {

            builder.properties(properties);
        }

        return builder.build();
    }


    private String readLanguage(
            Element element) {

        String language = trimToNull(
                element.getAttributeNS(XML_NAMESPACE, "lang"));

        if (language != null) {

            return language;
        }

        return trimToNull(element.getAttribute("xml:lang"));
    }


    private boolean isDublinCoreElement(
            Element element) {

        String namespace = element.getNamespaceURI();

        if (DC_NAMESPACE.equals(namespace)) {

            return true;
        }

        String prefix = element.getPrefix();

        return "dc".equalsIgnoreCase(prefix);
    }


    private Element findDirectChild(
            Element parent,
            String localName) {

        NodeList children = parent.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Element element = asElement(children.item(index));

            if (element == null) {

                continue;
            }

            if (isElement(element, localName)) {

                return element;
            }
        }

        return null;
    }


    private boolean isElement(
            Element element,
            String localName) {

        if (element == null || localName == null) {

            return false;
        }

        return localName.equalsIgnoreCase(getLocalName(element));
    }


    private String getLocalName(
            Element element) {

        String localName = element.getLocalName();

        if (localName != null) {

            return localName;
        }

        String tagName = element.getTagName();

        int index = tagName.indexOf(':');

        if (index >= 0) {

            return tagName.substring(index + 1);
        }

        return tagName;
    }


    private Element asElement(
            Node node) {

        if (node == null) {

            return null;
        }

        if (node.getNodeType() != Node.ELEMENT_NODE) {

            return null;
        }

        return (Element) node;
    }


    private String requireAttribute(
            Element element,
            String name) {

        String value = trimToNull(element.getAttribute(name));

        if (value == null) {

            throw new IllegalStateException(
                    "Required EPUB attribute is missing: "
                            + getLocalName(element)
                            + "@"
                            + name);
        }

        return value;
    }


    private EpubVersion resolveVersion(
            String value) {

        if (value == null) {

            return EpubVersion.defaultVersion();
        }

        EpubVersion version = invokeParser(
                EpubVersion.class,
                "fromPackageVersion",
                value);

        if (version != null) {

            return version;
        }

        version = invokeParser(
                EpubVersion.class,
                "from",
                value);

        if (version != null) {

            return version;
        }

        return EpubVersion.defaultVersion();
    }


    private EpubPageProgressionDirection resolvePageProgressionDirection(
            String value) {

        if (value == null) {

            return null;
        }

        EpubPageProgressionDirection direction = invokeParser(
                EpubPageProgressionDirection.class,
                "from",
                value);

        if (direction != null) {

            return direction;
        }

        direction = invokeParser(
                EpubPageProgressionDirection.class,
                "require",
                value);

        return direction;
    }


    @SuppressWarnings("unchecked")
    private <T> T invokeParser(
            Class<T> type,
            String methodName,
            String value) {

        try {

            Method method = type.getMethod(methodName, String.class);

            if (!Modifier.isStatic(method.getModifiers())) {

                return null;
            }

            Object result = method.invoke(null, value);

            if (result == null) {

                return null;
            }

            if (type.isInstance(result)) {

                return type.cast(result);
            }

            if (result instanceof Optional<?> optional) {

                if (optional.isEmpty()) {

                    return null;
                }

                Object resolved = optional.get();

                if (type.isInstance(resolved)) {

                    return (T) resolved;
                }
            }

            return null;

        } catch (ReflectiveOperationException exception) {

            return null;

        } catch (RuntimeException exception) {

            return null;
        }
    }


    private Map<String, String> parsePrefixes(
            String value) {

        Map<String, String> result = new LinkedHashMap<>();

        if (value == null) {

            return result;
        }

        Matcher matcher = PREFIX_PATTERN.matcher(value);

        while (matcher.find()) {

            String prefix = matcher.group(1);

            String uri = matcher.group(2);

            result.put(prefix, uri);
        }

        return result;
    }


    private Document parseXml(
            String xml) {

        if (xml == null || xml.isBlank()) {

            throw new IllegalArgumentException(
                    "xml must not be empty.");
        }

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            setFeature(
                    factory,
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);

            setFeature(
                    factory,
                    "http://xml.org/sax/features/external-general-entities",
                    false);

            setFeature(
                    factory,
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);

            setFeature(
                    factory,
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);

            factory.setXIncludeAware(false);

            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();

            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {

                return builder.parse(inputStream);
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to parse EPUB package XML.",
                    exception);
        }
    }


    private void setFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean value) {

        try {

            factory.setFeature(feature, value);

        } catch (Exception exception) {

            /*
             * XML parser implementation에 따라 지원하지 않을 수 있습니다.
             */
        }
    }


    private String normalizeEntryPath(
            String value) {

        if (value == null) {

            return null;
        }

        String normalized = value.trim().replace('\\', '/');

        while (normalized.startsWith("/")) {

            normalized = normalized.substring(1);
        }

        while (normalized.startsWith("./")) {

            normalized = normalized.substring(2);
        }

        return normalized.isEmpty() ? null : normalized;
    }


    private String trimToNull(
            String value) {

        if (value == null) {

            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}