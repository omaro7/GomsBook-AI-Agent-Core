/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.navigation.updater;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

/**
 * EPUB nav.xhtml의 TOC 항목을 갱신하는 기본 구현체입니다.
 */
public class DefaultEpubNavigationUpdater implements EpubNavigationUpdater {

    private static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";
    private static final String EPUB_NAMESPACE = "http://www.idpf.org/2007/ops";

    @Override
    public void addOrUpdateItem(Path navigationPath, EpubNavigationUpdateItem updateItem) {
        validateNavigationPath(navigationPath);

        if (updateItem == null) throw new IllegalArgumentException("updateItem must not be null.");

        Document document = EpubXmlUtil.readDocument(navigationPath);
        Element toc = requireTocNavigation(document);
        Element list = requireNavigationList(document, toc);

        applyUpdate(document, list, updateItem);
        removeDuplicateItems(list);

        EpubXmlUtil.writeDocument(navigationPath, document);
    }

    @Override
    public void removeItem(Path navigationPath, String href) {
        validateNavigationPath(navigationPath);

        if (href == null || href.isBlank()) throw new IllegalArgumentException("href must not be empty.");

        Document document = EpubXmlUtil.readDocument(navigationPath);
        Element toc = requireTocNavigation(document);
        Element list = requireNavigationList(document, toc);

        removeItemsByHref(list, href);

        EpubXmlUtil.writeDocument(navigationPath, document);
    }

    @Override
    public boolean containsItem(Path navigationPath, String href) {
        validateNavigationPath(navigationPath);

        if (href == null || href.isBlank()) return false;

        Document document = EpubXmlUtil.readDocument(navigationPath);
        Element toc = requireTocNavigation(document);
        Element list = requireNavigationList(document, toc);

        return findItemByHref(list, href) != null;
    }

    @Override
    public void update(Path navigationPath, List<EpubNavigationUpdateItem> items) {
        validateNavigationPath(navigationPath);

        if (items == null || items.isEmpty()) return;

        Document document = EpubXmlUtil.readDocument(navigationPath);
        Element toc = requireTocNavigation(document);
        Element list = requireNavigationList(document, toc);

        List<EpubNavigationUpdateItem> normalizedItems = normalizeItems(items);

        for (EpubNavigationUpdateItem item : normalizedItems) applyUpdate(document, list, item);

        removeDuplicateItems(list);

        EpubXmlUtil.writeDocument(navigationPath, document);
    }

    /**
     * batch 내부 href 중복은 마지막 값을 사용합니다.
     */
    private List<EpubNavigationUpdateItem> normalizeItems(List<EpubNavigationUpdateItem> items) {
        Map<String, EpubNavigationUpdateItem> byHref = new LinkedHashMap<>();

        for (EpubNavigationUpdateItem item : items) {
            if (item == null) continue;
            if (item.getItem() == null) continue;

            String href = normalizeHref(item.getItem().getHref());

            if (href.isEmpty()) continue;

            byHref.put(href, item);
        }

        return List.copyOf(byHref.values());
    }

    private void applyUpdate(Document document, Element list, EpubNavigationUpdateItem updateItem) {
        if (updateItem == null) throw new IllegalArgumentException("updateItem must not be null.");

        EpubNavigationItem item = updateItem.getItem();

        if (item == null) throw new IllegalArgumentException("Navigation item must not be null.");

        validateRelativePosition(updateItem);

        Element existingByHref = findItemByHref(list, item.getHref());
        Element existingById = item.getId().map(id -> findItemById(list, id)).orElse(null);

        if (existingById != null && existingById != existingByHref) removeItemElement(list, existingById);

        Element target = existingByHref;

        if (target == null) target = createNavigationItem(document, item);

        updateNavigationItem(target, item);

        /*
         * 기존 항목도 position 규칙을 적용할 수 있도록
         * 현재 위치에서 제거한 후 다시 삽입합니다.
         */
        if (target.getParentNode() == list) list.removeChild(target);

        insertItem(list, target, updateItem);
    }

    /**
     * BEFORE / AFTER 위치 변경에서 자기 자신을 기준 항목으로
     * 지정하는 잘못된 요청을 방지합니다.
     */
    private void validateRelativePosition(EpubNavigationUpdateItem updateItem) {
        EpubNavigationInsertPosition position = updateItem.getPosition();

        if (position != EpubNavigationInsertPosition.BEFORE && position != EpubNavigationInsertPosition.AFTER) return;

        EpubNavigationItem item = updateItem.getItem();
        String href = normalizeHref(item.getHref());
        String referenceHref = normalizeHref(updateItem.getReferenceHref());

        if (href.equals(referenceHref)) {
            throw new IllegalStateException("Navigation item cannot be positioned relative to itself: " + item.getHref());
        }
    }

    private Element createNavigationItem(Document document, EpubNavigationItem item) {
        Element li = document.createElementNS(XHTML_NAMESPACE, "li");
        Element anchor = document.createElementNS(XHTML_NAMESPACE, "a");

        li.appendChild(anchor);

        updateNavigationItem(li, item);

        return li;
    }

    private void updateNavigationItem(Element listItem, EpubNavigationItem item) {
        String itemId = item.getId().orElse(null);

        if (itemId != null) listItem.setAttribute("id", itemId);
        else listItem.removeAttribute("id");

        Element anchor = findDirectAnchor(listItem);

        if (anchor == null) {
            anchor = listItem.getOwnerDocument().createElementNS(XHTML_NAMESPACE, "a");
            listItem.insertBefore(anchor, listItem.getFirstChild());
        }

        anchor.setAttribute("href", item.getHref());
        anchor.setTextContent(item.getLabel());
    }

    private void insertItem(Element list, Element item, EpubNavigationUpdateItem updateItem) {
        EpubNavigationInsertPosition position = updateItem.getPosition();

        if (position == EpubNavigationInsertPosition.FIRST) {
            Node first = findFirstListItem(list);

            if (first != null) list.insertBefore(item, first);
            else list.appendChild(item);

            return;
        }

        if (position == EpubNavigationInsertPosition.BEFORE) {
            Element reference = requireReferenceItem(list, updateItem);

            list.insertBefore(item, reference);

            return;
        }

        if (position == EpubNavigationInsertPosition.AFTER) {
            Element reference = requireReferenceItem(list, updateItem);

            insertAfter(list, item, reference);

            return;
        }

        /*
         * LAST 또는 기본 위치는 마지막에 삽입합니다.
         */
        list.appendChild(item);
    }

    private Element requireReferenceItem(Element list, EpubNavigationUpdateItem updateItem) {
        String referenceHref = updateItem.getReferenceHref();

        if (referenceHref == null || referenceHref.isBlank()) {
            throw new IllegalStateException("Navigation reference href must not be empty for position: " + updateItem.getPosition());
        }

        Element reference = findItemByHref(list, referenceHref);

        if (reference == null) {
            throw new IllegalStateException("Navigation reference item does not exist: " + referenceHref);
        }

        return reference;
    }

    private void insertAfter(Element parent, Element newItem, Element reference) {
        Node next = reference.getNextSibling();

        if (next != null) parent.insertBefore(newItem, next);
        else parent.appendChild(newItem);
    }

    private Node findFirstListItem(Element list) {
        NodeList children = list.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (isElement(element, "li")) return element;
        }

        return null;
    }

    private Element findItemByHref(Element list, String href) {
        if (href == null || href.isBlank()) return null;

        String normalizedHref = normalizeHref(href);
        NodeList children = list.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "li")) continue;

            Element anchor = findDirectAnchor(element);

            if (anchor == null) continue;

            if (normalizedHref.equals(normalizeHref(anchor.getAttribute("href")))) return element;
        }

        return null;
    }

    private Element findItemById(Element list, String id) {
        if (id == null || id.isBlank()) return null;

        NodeList children = list.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "li")) continue;
            if (id.equals(element.getAttribute("id"))) return element;
        }

        return null;
    }

    private Element findDirectAnchor(Element listItem) {
        NodeList children = listItem.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (isElement(element, "a")) return element;
        }

        return null;
    }

    private void removeItemsByHref(Element list, String href) {
        String normalizedHref = normalizeHref(href);
        NodeList children = list.getChildNodes();

        for (int index = children.getLength() - 1; index >= 0; index--) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "li")) continue;

            Element anchor = findDirectAnchor(element);

            if (anchor == null) continue;

            if (normalizedHref.equals(normalizeHref(anchor.getAttribute("href")))) list.removeChild(element);
        }
    }

    private void removeItemElement(Element list, Element item) {
        if (item == null) return;
        if (item.getParentNode() != list) return;

        list.removeChild(item);
    }

    /**
     * href / id 중복을 제거합니다.
     *
     * 뒤쪽 항목을 우선 유지합니다.
     */
    private void removeDuplicateItems(Element list) {
        Map<String, Boolean> hrefs = new LinkedHashMap<>();
        Map<String, Boolean> ids = new LinkedHashMap<>();
        NodeList children = list.getChildNodes();

        for (int index = children.getLength() - 1; index >= 0; index--) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "li")) continue;

            Element anchor = findDirectAnchor(element);

            if (anchor == null) continue;

            String href = normalizeHref(anchor.getAttribute("href"));
            String id = element.getAttribute("id");

            boolean hrefDuplicated = !href.isEmpty() && hrefs.containsKey(href);
            boolean idDuplicated = !id.isBlank() && ids.containsKey(id);

            if (hrefDuplicated || idDuplicated) {
                list.removeChild(element);
                continue;
            }

            if (!href.isEmpty()) hrefs.put(href, Boolean.TRUE);
            if (!id.isBlank()) ids.put(id, Boolean.TRUE);
        }
    }

    /**
     * epub:type="toc"인 nav 요소를 찾습니다.
     */
    private Element requireTocNavigation(Document document) {
        NodeList nodes = document.getElementsByTagNameNS("*", "nav");

        for (int index = 0; index < nodes.getLength(); index++) {
            Element nav = (Element) nodes.item(index);
            String epubType = nav.getAttributeNS(EPUB_NAMESPACE, "type");

            if (epubType == null || epubType.isBlank()) epubType = nav.getAttribute("epub:type");

            if ("toc".equals(epubType)) return nav;
        }

        throw new IllegalStateException("EPUB TOC navigation was not found.");
    }

    /**
     * TOC nav의 최상위 ol을 반환합니다.
     */
    private Element requireNavigationList(Document document, Element toc) {
        NodeList children = toc.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (isElement(element, "ol")) return element;
        }

        Element list = document.createElementNS(XHTML_NAMESPACE, "ol");

        toc.appendChild(list);

        return list;
    }

    private boolean isElement(Element element, String localName) {
        if (element == null) return false;
        if (localName.equals(element.getLocalName())) return true;

        return localName.equals(element.getNodeName());
    }

    private String normalizeHref(String href) {
        if (href == null) return "";

        return href.trim().replace('\\', '/');
    }

    private void validateNavigationPath(Path navigationPath) {
        if (navigationPath == null) throw new IllegalArgumentException("navigationPath must not be null.");
        if (!Files.exists(navigationPath)) throw new IllegalStateException("EPUB navigation does not exist: " + navigationPath);
        if (!Files.isRegularFile(navigationPath)) throw new IllegalStateException("EPUB navigation is not a file: " + navigationPath);
    }
}