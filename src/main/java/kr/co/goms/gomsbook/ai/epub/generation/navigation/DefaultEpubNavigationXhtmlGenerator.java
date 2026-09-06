/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.navigation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

public final class DefaultEpubNavigationXhtmlGenerator implements EpubNavigationXhtmlGenerator {

    private static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";
    private static final String EPUB_NAMESPACE = "http://www.idpf.org/2007/ops";
    private static final String DEFAULT_LANGUAGE = "ko";
    private static final String TOC_TITLE_ID = "toc-title";
    private static final String LANDMARKS_TITLE_ID = "landmarks-title";
    private static final String PAGE_LIST_TITLE_ID = "page-list-title";

    @Override
    public Path generate(EpubNavigation navigation, Path navigationPath) {
        if (navigation == null) throw new IllegalArgumentException("navigation must not be null.");
        if (navigationPath == null) throw new IllegalArgumentException("navigationPath must not be null.");

        Document document = createNavigationDocument(navigation);

        EpubXmlUtil.writeDocument(navigationPath, document);

        if (!Files.isRegularFile(navigationPath)) throw new IllegalStateException("EPUB navigation XHTML was not created: " + navigationPath);

        return navigationPath;
    }

    @Override
    public String render(EpubNavigation navigation) {
        if (navigation == null) throw new IllegalArgumentException("navigation must not be null.");

        try {
            return EpubXmlUtil.xmlDocumentToString(createNavigationDocument(navigation));

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render EPUB navigation XHTML.", exception);
        }
    }

    private Document createNavigationDocument(EpubNavigation navigation) {
        navigation.validate();

        Document document = createDocument();
        Element html = createHtml(document, navigation);
        Element head = createHead(document, navigation);
        Element body = createBody(document, navigation);

        document.appendChild(html);
        html.appendChild(head);
        html.appendChild(body);

        appendToc(document, body, navigation);

        if (navigation.hasLandmarks()) appendLandmarks(document, body, navigation);
        if (navigation.hasPageList()) appendPageList(document, body, navigation);

        return document;
    }

    private Document createDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder().newDocument();

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create EPUB navigation XML document.", exception);
        }
    }

    private Element createHtml(Document document, EpubNavigation navigation) {
        Element html = document.createElementNS(XHTML_NAMESPACE, "html");

        html.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:epub", EPUB_NAMESPACE);

        String language = navigation.getLanguage().orElse(DEFAULT_LANGUAGE);

        applyLanguage(html, language);
        applyDirection(html, navigation.getDirection());

        return html;
    }

    private Element createHead(Document document, EpubNavigation navigation) {
        Element head = document.createElementNS(XHTML_NAMESPACE, "head");
        Element meta = document.createElementNS(XHTML_NAMESPACE, "meta");
        Element title = document.createElementNS(XHTML_NAMESPACE, "title");

        meta.setAttribute("charset", "UTF-8");
        title.setTextContent(navigation.getTitle());

        head.appendChild(meta);
        head.appendChild(title);

        return head;
    }

	private Element createBody(Document document, EpubNavigation navigation) {
	    Element body = document.createElementNS(XHTML_NAMESPACE, "body");
	
	    applyDirection(body, navigation.getDirection());
	
	    return body;
	}

    private void appendToc(Document document, Element body, EpubNavigation navigation) {
        Element nav = createNavigationElement(document, "toc", "doc-toc", TOC_TITLE_ID);
        Element heading = createHeading(document, "h1", TOC_TITLE_ID, navigation.getTocTitle());
        Element list = document.createElementNS(XHTML_NAMESPACE, "ol");

        appendItems(document, list, navigation.getTocItems());

        nav.appendChild(heading);
        nav.appendChild(list);
        body.appendChild(nav);
    }

    private void appendLandmarks(Document document, Element body, EpubNavigation navigation) {
        Element nav = createNavigationElement(document, "landmarks", null, LANDMARKS_TITLE_ID);
        Element heading = createHeading(document, "h2", LANDMARKS_TITLE_ID, navigation.getLandmarksTitle());
        Element list = document.createElementNS(XHTML_NAMESPACE, "ol");

        appendItems(document, list, navigation.getLandmarkItems());

        nav.appendChild(heading);
        nav.appendChild(list);
        body.appendChild(nav);
    }

    private void appendPageList(Document document, Element body, EpubNavigation navigation) {
        Element nav = createNavigationElement(document, "page-list", "doc-pagelist", PAGE_LIST_TITLE_ID);
        Element heading = createHeading(document, "h2", PAGE_LIST_TITLE_ID, navigation.getPageListTitle());
        Element list = document.createElementNS(XHTML_NAMESPACE, "ol");

        appendItems(document, list, navigation.getPageListItems());

        nav.appendChild(heading);
        nav.appendChild(list);
        body.appendChild(nav);
    }

    private Element createNavigationElement(Document document, String epubType, String role, String labelledBy) {
        Element nav = document.createElementNS(XHTML_NAMESPACE, "nav");

        nav.setAttributeNS(EPUB_NAMESPACE, "epub:type", epubType);
        nav.setAttribute("aria-labelledby", labelledBy);

        if (role != null && !role.isBlank()) nav.setAttribute("role", role);

        return nav;
    }

    private Element createHeading(Document document, String name, String id, String text) {
        Element heading = document.createElementNS(XHTML_NAMESPACE, name);

        heading.setAttribute("id", id);
        heading.setTextContent(text);

        return heading;
    }

    private void appendItems(Document document, Element list, List<EpubNavigationItem> items) {
        for (EpubNavigationItem item : items) {
            if (!item.isIncluded()) continue;

            Element li = createListItem(document, item);

            list.appendChild(li);

            if (!item.hasChildren()) continue;

            Element childList = document.createElementNS(XHTML_NAMESPACE, "ol");

            appendItems(document, childList, item.getChildren());

            if (childList.hasChildNodes()) li.appendChild(childList);
        }
    }

    private Element createListItem(Document document, EpubNavigationItem item) {
        Element li = document.createElementNS(XHTML_NAMESPACE, "li");
        Element anchor = document.createElementNS(XHTML_NAMESPACE, "a");

        item.getId().ifPresent(value -> li.setAttribute("id", value));

        anchor.setAttribute("href", item.getHref());
        anchor.setTextContent(item.getLabel());

        item.getEpubType().ifPresent(value -> anchor.setAttributeNS(EPUB_NAMESPACE, "epub:type", value));
        item.getLanguage().ifPresent(value -> applyLanguage(anchor, value));
        item.getDirection().ifPresent(value -> applyDirection(anchor, value));

        li.appendChild(anchor);

        return li;
    }

    private void applyLanguage(Element element, String language) {
        if (language == null || language.isBlank()) return;

        element.setAttribute("lang", language);
        element.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", language);
    }

    private void applyDirection(Element element, EpubNavigationItem.TextDirection direction) {
        if (direction == null || direction == EpubNavigationItem.TextDirection.AUTO) return;

        element.setAttribute("dir", direction.toString());
    }
}