/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.author;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorPage;

public class EpubAuthorReader {

    public EpubAuthorPage read(Path authorPath) {

        if (authorPath == null) throw new IllegalArgumentException("authorPath must not be null.");
        if (!Files.exists(authorPath)) throw new IllegalStateException("Author XHTML file does not exist: " + authorPath);
        if (!Files.isRegularFile(authorPath)) throw new IllegalStateException("Author XHTML path is not a file: " + authorPath);

        Document document = parseXml(authorPath);

        EpubAuthorPage page = new EpubAuthorPage();

        page.setFileName(authorPath.getFileName().toString());
        page.setAuthorName(readTextByClass(document, "author-name"));
        page.setIntroduction(readTextByClass(document, "introduction"));
        page.setProfile(readTextByClass(document, "profile"));
        page.setCareers(readCareers(document));
        page.setImageFileName(readImageFileName(document));
        page.setImageAlt(readImageAlt(document));

        return page;
    }

    private String readTextByClass(Document document, String className) {

        Element element = findElementByClass(document, className);

        if (element == null) return null;

        return normalizeText(element.getTextContent());
    }

    private List<String> readCareers(Document document) {

        List<String> careers = new ArrayList<>();
        Element careerList = findElementByClass(document, "author-careers");

        if (careerList == null) return careers;

        NodeList items = careerList.getElementsByTagNameNS("*", "li");

        for (int index = 0; index < items.getLength(); index++) {

            Node node = items.item(index);

            if (!(node instanceof Element)) continue;

            String text = normalizeText(node.getTextContent());

            if (text != null) careers.add(text);
        }

        return careers;
    }

    private String readImageFileName(Document document) {

        Element image = findAuthorImage(document);

        if (image == null) return null;

        String src = trimToNull(image.getAttribute("src"));

        if (src == null) return null;

        String normalized = src.replace('\\', '/');

        int fragmentIndex = normalized.indexOf('#');

        if (fragmentIndex >= 0) normalized = normalized.substring(0, fragmentIndex);

        int queryIndex = normalized.indexOf('?');

        if (queryIndex >= 0) normalized = normalized.substring(0, queryIndex);

        int slashIndex = normalized.lastIndexOf('/');

        if (slashIndex >= 0) normalized = normalized.substring(slashIndex + 1);

        return trimToNull(normalized);
    }

    private String readImageAlt(Document document) {

        Element image = findAuthorImage(document);

        if (image == null) return null;

        return trimToNull(image.getAttribute("alt"));
    }

    private Element findAuthorImage(Document document) {

        Element figure = findElementByClass(document, "author-image");

        if (figure == null) return null;

        NodeList images = figure.getElementsByTagNameNS("*", "img");

        if (images.getLength() == 0) return null;

        Node node = images.item(0);

        return node instanceof Element ? (Element) node : null;
    }

    private Element findElementByClass(Document document, String className) {

        NodeList elements = document.getElementsByTagNameNS("*", "*");

        for (int index = 0; index < elements.getLength(); index++) {

            Node node = elements.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (hasClass(element, className)) return element;
        }

        return null;
    }

    private boolean hasClass(Element element, String className) {

        if (element == null || className == null || className.isBlank()) return false;

        String value = trimToNull(element.getAttribute("class"));

        if (value == null) return false;

        String[] classes = value.split("\\s+");

        for (String candidate : classes) {

            if (className.equals(candidate)) return true;
        }

        return false;
    }

    private Document parseXml(Path path) {

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            /*
             * EPUB XHTML의 <!DOCTYPE html>은 허용한다.
             * 외부 Entity 및 외부 DTD 접근만 차단한다.
             */
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            try {

                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            } catch (IllegalArgumentException ignore) {
            }

            DocumentBuilder builder = factory.newDocumentBuilder();

            try (InputStream inputStream = Files.newInputStream(path)) {

                return builder.parse(inputStream);
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB author XHTML: "
                            + path,
                    exception);
        }
    }
    
    private String normalizeText(String value) {

        if (value == null) return null;

        String normalized = value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}