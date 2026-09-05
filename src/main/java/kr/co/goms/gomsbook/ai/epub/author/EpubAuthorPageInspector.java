package kr.co.goms.gomsbook.ai.epub.author;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public class EpubAuthorPageInspector {

    private static final String AUTHOR_FILE_NAME = "author.xhtml";
    private static final String AUTHOR_HREF = "Text/author.xhtml";

    public EpubAuthorPageState inspect(EpubProjectContext project) {

        if (project == null) {
            throw new IllegalArgumentException("project must not be null.");
        }

        Path projectRoot = project.getProjectRoot();

        if (projectRoot == null) {
            throw new IllegalStateException("EPUB project root is not available.");
        }

        Path oebpsPath = projectRoot.resolve("OEBPS");
        Path authorPath = oebpsPath.resolve("Text").resolve(AUTHOR_FILE_NAME);
        Path packagePath = oebpsPath.resolve("content.opf");
        Path navigationPath = oebpsPath.resolve("nav.xhtml");

        boolean fileExists = Files.exists(authorPath);
        boolean manifestRegistered = false;
        boolean spineRegistered = false;
        boolean navigationRegistered = false;

        if (Files.exists(packagePath)) {
            Document packageDocument = parseXml(packagePath);
            String manifestId = findAuthorManifestId(packageDocument);
            manifestRegistered = manifestId != null;
            spineRegistered = manifestId != null && isRegisteredInSpine(packageDocument, manifestId);
        }

        if (Files.exists(navigationPath)) {
            Document navigationDocument = parseXml(navigationPath);
            navigationRegistered = isRegisteredInNavigation(navigationDocument);
        }

        return new EpubAuthorPageState(fileExists, manifestRegistered, spineRegistered, navigationRegistered);
    }

    private String findAuthorManifestId(Document document) {

        NodeList items = document.getElementsByTagNameNS("*", "item");

        for (int index = 0; index < items.getLength(); index++) {

            Element item = (Element) items.item(index);
            String href = normalizePath(item.getAttribute("href"));

            if (isAuthorHref(href)) {
                return trimToNull(item.getAttribute("id"));
            }
        }

        return null;
    }

    private boolean isRegisteredInSpine(Document document, String manifestId) {

        NodeList itemRefs = document.getElementsByTagNameNS("*", "itemref");

        for (int index = 0; index < itemRefs.getLength(); index++) {

            Element itemRef = (Element) itemRefs.item(index);
            String idref = trimToNull(itemRef.getAttribute("idref"));

            if (manifestId.equals(idref)) {
                return true;
            }
        }

        return false;
    }

    private boolean isRegisteredInNavigation(Document document) {

        NodeList links = document.getElementsByTagNameNS("*", "a");

        for (int index = 0; index < links.getLength(); index++) {

            Node node = links.item(index);

            if (!(node instanceof Element)) {
                continue;
            }

            Element link = (Element) node;
            String href = normalizePath(link.getAttribute("href"));

            if (isAuthorHref(href)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAuthorHref(String href) {

        if (href == null) {
            return false;
        }

        String normalized = normalizePath(href);

        return AUTHOR_HREF.equals(normalized)
                || AUTHOR_FILE_NAME.equals(normalized)
                || normalized.endsWith("/" + AUTHOR_FILE_NAME);
    }

    private Document parseXml(Path path) {

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
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
            throw new IllegalStateException("Failed to inspect EPUB author page XML: " + path, exception);
        }
    }

    private String normalizePath(String value) {

        String normalized = trimToNull(value);

        if (normalized == null) {
            return null;
        }

        int fragmentIndex = normalized.indexOf('#');

        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }

        int queryIndex = normalized.indexOf('?');

        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized.replace('\\', '/');
    }

    private String trimToNull(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}