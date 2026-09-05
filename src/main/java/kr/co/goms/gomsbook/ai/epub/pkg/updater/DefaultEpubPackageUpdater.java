/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.pkg.updater;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.policy.spine.EpubSpineOrderPolicy;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * EPUB package document(content.opf)의 manifest / spine을 갱신합니다.
 */
public class DefaultEpubPackageUpdater implements EpubPackageUpdater {

    private static final String OPF_NAMESPACE = "http://www.idpf.org/2007/opf";

    private final EpubSpineOrderPolicy spineOrderPolicy;

    public DefaultEpubPackageUpdater(EpubSpineOrderPolicy spineOrderPolicy) {

        if (spineOrderPolicy == null) throw new IllegalArgumentException("spineOrderPolicy must not be null.");

        this.spineOrderPolicy = spineOrderPolicy;
    }
    

    @Override
    public void addOrUpdateManifestItem(Path packagePath, EpubManifestItem resource) {

        validatePackagePath(packagePath);

        if (resource == null) throw new IllegalArgumentException("resource must not be null.");

        Document document = readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        Map<String, String> idMappings = new LinkedHashMap<>();

        updateManifestItem(document, manifest, resource, idMappings);
        updateSpineReferences(spine, idMappings);

        removeDuplicateManifestItems(manifest);
        removeDuplicateSpineItems(spine);

        sortSpineItems(manifest, spine);
        
        validateAllSpineReferences(manifest, spine);

        writeDocument(packagePath, document);
    }


    @Override
    public void removeManifestItem(Path packagePath, String resourceId) {

        validatePackagePath(packagePath);

        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId must not be empty.");

        Document document = readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        String normalizedId = resourceId.trim();

        Element existing = findManifestItemById(manifest, normalizedId);

        if (existing == null) return;

        manifest.removeChild(existing);

        removeSpineItemsByIdref(spine, normalizedId);

        validateAllSpineReferences(manifest, spine);

        writeDocument(packagePath, document);
    }


    @Override
    public void addOrUpdateSpineItem(Path packagePath, EpubSpineItem item) {

        validatePackagePath(packagePath);

        if (item == null) throw new IllegalArgumentException("item must not be null.");

        Document document = readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        validateSpineReference(manifest, item.getIdref());

        updateSpineItem(document, spine, item);

        removeDuplicateSpineItems(spine);
        sortSpineItems(manifest, spine);

        writeDocument(packagePath, document);
    }


    @Override
    public void removeSpineItem(Path packagePath, String idref) {

        validatePackagePath(packagePath);

        if (idref == null || idref.isBlank()) throw new IllegalArgumentException("idref must not be empty.");

        Document document = readDocument(packagePath);
        Element spine = requireElement(document, "spine");

        removeSpineItemsByIdref(spine, idref.trim());

        writeDocument(packagePath, document);
    }


    @Override
    public boolean containsManifestItem(Path packagePath, String resourceId) {

        validatePackagePath(packagePath);

        if (resourceId == null || resourceId.isBlank()) return false;

        Document document = readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");

        return findManifestItemById(manifest, resourceId.trim()) != null;
    }


    @Override
    public boolean containsSpineItem(Path packagePath, String idref) {

        validatePackagePath(packagePath);

        if (idref == null || idref.isBlank()) return false;

        Document document = readDocument(packagePath);
        Element spine = requireElement(document, "spine");

        return findSpineItemByIdref(spine, idref.trim()) != null;
    }


    /**
     * manifest / spine을 한 번에 갱신합니다.
     *
     * content.opf를 1회 읽고 1회 저장합니다.
     */
    @Override
    public void update(Path packagePath, List<EpubManifestItem> resources, List<EpubSpineItem> spineItems) {

        validatePackagePath(packagePath);

        if ((resources == null || resources.isEmpty()) && (spineItems == null || spineItems.isEmpty())) return;

        Document document = readDocument(packagePath);

        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        List<EpubManifestItem> normalizedResources = normalizeResources(resources);
        List<EpubSpineItem> normalizedSpineItems = normalizeSpineItems(spineItems);

        Map<String, String> idMappings = new LinkedHashMap<>();

        updateManifestItems(document, manifest, normalizedResources, idMappings);

        updateSpineReferences(spine, idMappings);

        validateSpineReferences(manifest, normalizedSpineItems);

        updateSpineItems(document, spine, normalizedSpineItems);

        removeDuplicateManifestItems(manifest);
        removeDuplicateSpineItems(spine);

        sortSpineItems(manifest, spine);

        validateAllSpineReferences(manifest, spine);

        writeDocument(packagePath, document);
    }


    /**
     * Batch 내부 manifest 중복을 정리합니다.
     *
     * id / href 중복은 마지막 Resource를 사용합니다.
     */
    private List<EpubManifestItem> normalizeResources(List<EpubManifestItem> resources) {

        if (resources == null || resources.isEmpty()) return List.of();

        Map<String, EpubManifestItem> byId = new LinkedHashMap<>();
        Map<String, String> hrefToId = new LinkedHashMap<>();

        for (EpubManifestItem resource : resources) {

            if (resource == null) continue;

            String id = resource.getId();
            String href = normalizeHref(resource.getHref());

            EpubManifestItem previousById = byId.get(id);

            if (previousById != null) hrefToId.remove(normalizeHref(previousById.getHref()));

            String previousIdByHref = hrefToId.get(href);

            if (previousIdByHref != null && !previousIdByHref.equals(id)) byId.remove(previousIdByHref);

            byId.put(id, resource);
            hrefToId.put(href, id);
        }

        return List.copyOf(byId.values());
    }


    /**
     * Batch 내부 spine idref 중복은 마지막 값을 사용합니다.
     */
    private List<EpubSpineItem> normalizeSpineItems(List<EpubSpineItem> spineItems) {

        if (spineItems == null || spineItems.isEmpty()) return List.of();

        Map<String, EpubSpineItem> byIdref = new LinkedHashMap<>();

        for (EpubSpineItem item : spineItems) {

            if (item == null) continue;

            byIdref.put(item.getIdref(), item);
        }

        return List.copyOf(byIdref.values());
    }


    private void updateManifestItems(
            Document document,
            Element manifest,
            List<EpubManifestItem> resources,
            Map<String, String> idMappings) {

        if (resources == null || resources.isEmpty()) return;

        for (EpubManifestItem resource : resources) updateManifestItem(document, manifest, resource, idMappings);
    }


    private void updateSpineItems(Document document, Element spine, List<EpubSpineItem> spineItems) {

        if (spineItems == null || spineItems.isEmpty()) return;

        for (EpubSpineItem item : spineItems) updateSpineItem(document, spine, item);
    }


    /**
     * manifest Resource를 add-or-update 합니다.
     *
     * 동일 href에 다른 id가 있으면 old id -> new id 매핑을 기록합니다.
     */
    private void updateManifestItem(
            Document document,
            Element manifest,
            EpubManifestItem resource,
            Map<String, String> idMappings) {

        Element byId = findManifestItemById(manifest, resource.getId());
        Element byHref = findManifestItemByHref(manifest, resource.getHref());

        if (byHref != null && byHref != byId) {

            String oldId = byHref.getAttribute("id");

            if (oldId != null && !oldId.isBlank() && !oldId.equals(resource.getId())) {
                idMappings.put(oldId, resource.getId());
            }

            manifest.removeChild(byHref);
        }

        Element existing = byId;

        if (existing == null) {

            existing = document.createElementNS(OPF_NAMESPACE, "item");
            manifest.appendChild(existing);
        }

        existing.setAttribute("id", resource.getId());
        existing.setAttribute("href", resource.getHref());
        existing.setAttribute("media-type", resource.getMediaType());

        writeProperties(existing, resource);
        writeFallback(existing, resource);
        writeMediaOverlay(existing, resource);
    }


    private void updateSpineItem(Document document, Element spine, EpubSpineItem item) {

        Element existing = findSpineItemByIdref(spine, item.getIdref());

        if (existing == null) {

            existing = document.createElementNS(OPF_NAMESPACE, "itemref");
            spine.appendChild(existing);
        }

        existing.setAttribute("idref", item.getIdref());

        if (item.getId().isPresent()) {
            existing.setAttribute("id", item.getId().get());
        } else {
            existing.removeAttribute("id");
        }

        if (item.shouldWriteLinearAttribute()) {
            existing.setAttribute("linear", "no");
        } else {
            existing.removeAttribute("linear");
        }

        if (item.shouldWriteProperties()) {
            existing.setAttribute("properties", item.getPropertiesValue());
        } else {
            existing.removeAttribute("properties");
        }
    }


    /**
     * manifest id가 변경되었을 경우 기존 spine idref도 변경합니다.
     *
     * 예:
     * quiz-old -> quiz
     */
    private void updateSpineReferences(Element spine, Map<String, String> idMappings) {

        if (idMappings == null || idMappings.isEmpty()) return;

        NodeList children = spine.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;

            String idref = element.getAttribute("idref");
            String mappedId = resolveMappedId(idref, idMappings);

            if (!idref.equals(mappedId)) element.setAttribute("idref", mappedId);
        }
    }


    /**
     * 연속 ID 변경도 처리합니다.
     *
     * old -> new
     * new -> final
     *
     * 결과:
     * old -> final
     */
    private String resolveMappedId(String id, Map<String, String> idMappings) {

        if (id == null || idMappings == null || idMappings.isEmpty()) return id;

        String current = id;

        int guard = 0;

        while (idMappings.containsKey(current) && guard < 100) {

            String next = idMappings.get(current);

            if (next == null || next.equals(current)) break;

            current = next;
            guard++;
        }

        return current;
    }


    /**
     * manifest의 ID/HREF 중복을 제거합니다.
     *
     * 뒤쪽 항목을 우선 유지합니다.
     */
    private void removeDuplicateManifestItems(Element manifest) {

        Map<String, Boolean> ids = new LinkedHashMap<>();
        Map<String, Boolean> hrefs = new LinkedHashMap<>();

        NodeList children = manifest.getChildNodes();

        for (int index = children.getLength() - 1; index >= 0; index--) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "item")) continue;

            String id = element.getAttribute("id");
            String href = normalizeHref(element.getAttribute("href"));

            if (ids.containsKey(id) || hrefs.containsKey(href)) {

                manifest.removeChild(element);

                continue;
            }

            ids.put(id, Boolean.TRUE);
            hrefs.put(href, Boolean.TRUE);
        }
    }


    /**
     * spine의 idref 중복을 제거합니다.
     *
     * 뒤쪽 항목을 우선 유지합니다.
     */
    private void removeDuplicateSpineItems(Element spine) {

        Map<String, Boolean> idrefs = new LinkedHashMap<>();

        NodeList children = spine.getChildNodes();

        for (int index = children.getLength() - 1; index >= 0; index--) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;

            String idref = element.getAttribute("idref");

            if (idrefs.containsKey(idref)) {

                spine.removeChild(element);

                continue;
            }

            idrefs.put(idref, Boolean.TRUE);
        }
    }


    /**
     * 특정 manifest 항목 삭제 시 해당 spine itemref도 제거합니다.
     */
    private void removeSpineItemsByIdref(Element spine, String idref) {

        NodeList children = spine.getChildNodes();

        for (int index = children.getLength() - 1; index >= 0; index--) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;

            if (idref.equals(element.getAttribute("idref"))) spine.removeChild(element);
        }
    }


    private void validateSpineReferences(Element manifest, List<EpubSpineItem> spineItems) {

        if (spineItems == null || spineItems.isEmpty()) return;

        for (EpubSpineItem item : spineItems) validateSpineReference(manifest, item.getIdref());
    }


    private void validateSpineReference(Element manifest, String idref) {

        if (idref == null || idref.isBlank()) throw new IllegalStateException("Spine idref must not be empty.");

        if (findManifestItemById(manifest, idref) != null) return;

        throw new IllegalStateException("Spine idref does not exist in manifest: " + idref);
    }


    /**
     * 최종 OPF 전체 spine -> manifest 정합성을 검증합니다.
     */
    private void validateAllSpineReferences(Element manifest, Element spine) {

        NodeList children = spine.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;

            String idref = element.getAttribute("idref");

            validateSpineReference(manifest, idref);
        }
    }


    private Element findManifestItemById(Element manifest, String resourceId) {

        NodeList children = manifest.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "item")) continue;
            if (resourceId.equals(element.getAttribute("id"))) return element;
        }

        return null;
    }


    private Element findManifestItemByHref(Element manifest, String href) {

        String normalizedHref = normalizeHref(href);

        NodeList children = manifest.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "item")) continue;

            String currentHref = normalizeHref(element.getAttribute("href"));

            if (normalizedHref.equals(currentHref)) return element;
        }

        return null;
    }


    private Element findSpineItemByIdref(Element spine, String idref) {

        NodeList children = spine.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;
            if (idref.equals(element.getAttribute("idref"))) return element;
        }

        return null;
    }


    private void writeProperties(Element element, EpubManifestItem resource) {

        if (resource.getProperties() == null || resource.getProperties().isEmpty()) {

            element.removeAttribute("properties");

            return;
        }

        element.setAttribute("properties", String.join(" ", resource.getProperties()));
    }


    private void writeFallback(Element element, EpubManifestItem resource) {

        if (resource.getFallbackId().isPresent()) {
            element.setAttribute("fallback", resource.getFallbackId().get());
        } else {
            element.removeAttribute("fallback");
        }
    }


    private void writeMediaOverlay(Element element, EpubManifestItem resource) {

        if (resource.getMediaOverlayId().isPresent()) {
            element.setAttribute("media-overlay", resource.getMediaOverlayId().get());
        } else {
            element.removeAttribute("media-overlay");
        }
    }


    private String normalizeHref(String href) {

        if (href == null) return "";

        return href.trim().replace('\\', '/');
    }


    private boolean isElement(Element element, String localName) {

        if (element == null) return false;
        if (localName.equals(element.getLocalName())) return true;

        return localName.equals(element.getNodeName());
    }


    private Element requireElement(Document document, String localName) {

        NodeList nodes = document.getElementsByTagNameNS("*", localName);

        if (nodes.getLength() == 0) throw new IllegalStateException("EPUB package element was not found: " + localName);

        return (Element) nodes.item(0);
    }


    private Document readDocument(Path packagePath) {

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
            } catch (IllegalArgumentException exception) {
            }

            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException exception) {
            }

            DocumentBuilder builder = factory.newDocumentBuilder();

            try (InputStream inputStream = Files.newInputStream(packagePath)) {
                return builder.parse(inputStream);
            }

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to read EPUB package: " + packagePath, exception);
        }
    }


    private void writeDocument(Path packagePath, Document document) {

        try {

            TransformerFactory factory = TransformerFactory.newInstance();

            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            } catch (IllegalArgumentException exception) {
            }

            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            } catch (IllegalArgumentException exception) {
            }

            Transformer transformer = factory.newTransformer();

            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            try {
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            } catch (IllegalArgumentException exception) {
            }

            try (OutputStream outputStream = Files.newOutputStream(packagePath)) {
                transformer.transform(new DOMSource(document), new StreamResult(outputStream));
            }

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to write EPUB package: " + packagePath, exception);
        }
    }


    private void validatePackagePath(Path packagePath) {

        if (packagePath == null) throw new IllegalArgumentException("packagePath must not be null.");
        if (!Files.exists(packagePath)) throw new IllegalStateException("EPUB package does not exist: " + packagePath);
        if (!Files.isRegularFile(packagePath)) throw new IllegalStateException("EPUB package is not a file: " + packagePath);
    }
    
    /**
     * EPUB reading order 정책에 따라 spine itemref를 정렬합니다.
     *
     * 실제 정렬 우선순위는 EpubSpineOrderPolicy가 결정합니다.
     */
    private void sortSpineItems(Element manifest, Element spine) {

        List<Element> items = new ArrayList<>();
        NodeList children = spine.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;

            items.add(element);
        }

        items.sort(Comparator.comparingInt(item -> getSpineOrder(manifest, item)));

        for (Element item : items) spine.appendChild(item);
    }


    private int getSpineOrder(Element manifest, Element spineItem) {

        String idref = spineItem.getAttribute("idref");
        Element manifestItem = findManifestItemById(manifest, idref);

        if (manifestItem == null) return Integer.MAX_VALUE;

        String href = manifestItem.getAttribute("href");

        return spineOrderPolicy.getOrder(href);
    }
}