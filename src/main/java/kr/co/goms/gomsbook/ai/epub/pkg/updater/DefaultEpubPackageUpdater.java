/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.pkg.updater;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadataItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.policy.spine.EpubSpineOrderPolicy;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

/**
 * EPUB package document(content.opf)의 metadata / manifest / spine을 갱신합니다.
 */
public class DefaultEpubPackageUpdater implements EpubPackageUpdater {

    private static final String OPF_NAMESPACE = "http://www.idpf.org/2007/opf";
    private static final String DC_NAMESPACE = "http://purl.org/dc/elements/1.1/";

    private final EpubSpineOrderPolicy spineOrderPolicy;

    public DefaultEpubPackageUpdater(EpubSpineOrderPolicy spineOrderPolicy) {

        if (spineOrderPolicy == null) throw new IllegalArgumentException("spineOrderPolicy must not be null.");

        this.spineOrderPolicy = spineOrderPolicy;
    }

    @Override
    public void addManifestItem(Path packagePath, EpubManifestItem resource) {

        validatePackagePath(packagePath);
        validateManifestItem(resource);

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");

        String resourceId = resource.getId();
        String resourceHref = normalizeHref(resource.getHref());

        if (findManifestItemById(manifest, resourceId) != null) throw new IllegalStateException("Manifest item id already exists: " + resourceId);
        if (findManifestItemByHref(manifest, resourceHref) != null) throw new IllegalStateException("Manifest item href already exists: " + resourceHref);

        Element item = document.createElementNS(OPF_NAMESPACE, "item");

        item.setAttribute("id", resourceId);
        item.setAttribute("href", resourceHref);
        item.setAttribute("media-type", resource.getMediaType());

        writeProperties(item, resource);
        writeFallback(item, resource);
        writeMediaOverlay(item, resource);

        manifest.appendChild(item);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public void addOrUpdateManifestItem(Path packagePath, EpubManifestItem resource) {

        validatePackagePath(packagePath);
        validateManifestItem(resource);

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        Map<String, String> idMappings = new LinkedHashMap<>();

        updateManifestItem(document, manifest, resource, idMappings);
        updateSpineReferences(spine, idMappings);

        removeDuplicateManifestItems(manifest);
        removeDuplicateSpineItems(spine);

        sortSpineItems(manifest, spine);
        validateAllSpineReferences(manifest, spine);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public void removeManifestItem(Path packagePath, String resourceId) {

        validatePackagePath(packagePath);

        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId must not be empty.");

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        String normalizedId = resourceId.trim();
        Element existing = findManifestItemById(manifest, normalizedId);

        if (existing == null) return;

        manifest.removeChild(existing);

        removeSpineItemsByIdref(spine, normalizedId);

        validateAllSpineReferences(manifest, spine);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public void removeByHrefIfExists(Path packagePath, String href) {

        validatePackagePath(packagePath);

        if (href == null || href.isBlank()) throw new IllegalArgumentException("href must not be empty.");

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        String normalizedHref = normalizeHref(href);
        Element manifestItem = findManifestItemByHref(manifest, normalizedHref);
        String resourceId = null;
        boolean changed = false;

        if (manifestItem != null) {

            resourceId = manifestItem.getAttribute("id");

            manifest.removeChild(manifestItem);

            changed = true;
        }

        if (resourceId == null || resourceId.isBlank()) resourceId = resolveResourceIdFromHref(normalizedHref);
        if (resourceId != null && !resourceId.isBlank()) changed = removeSpineItemsByIdrefIfExists(spine, resourceId) || changed;
        if (!changed) return;

        validateAllSpineReferences(manifest, spine);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public void addMetadata(Path packagePath, EpubMetadataItem item) {

        validatePackagePath(packagePath);
        validateMetadataItem(item);

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element metadata = requireElement(document, "metadata");

        List<Element> matches = findMetadataItems(
                metadata,
                item.getElementName(),
                item.getValue(),
                item.getId().orElse(null),
                item.getProperty().orElse(null),
                item.getRefines().orElse(null),
                item.getScheme().orElse(null));

        if (!matches.isEmpty()) return;

        Element element = createMetadataElement(document, item);

        writeMetadataItem(element, item);

        metadata.appendChild(element);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public boolean updateMetadata(
            Path packagePath,
            String name,
            String targetValue,
            String id,
            String property,
            String refines,
            String scheme,
            EpubMetadataItem replacement) {

        validatePackagePath(packagePath);
        validateMetadataItem(replacement);

        String normalizedName = EpubXmlUtil.trimToNull(name);

        if (normalizedName == null) throw new IllegalArgumentException("name must not be empty.");

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element metadata = requireElement(document, "metadata");

        List<Element> matches = findMetadataItems(
                metadata,
                normalizedName,
                EpubXmlUtil.trimToNull(targetValue),
                EpubXmlUtil.trimToNull(id),
                EpubXmlUtil.trimToNull(property),
                EpubXmlUtil.normalizeRefines(refines),
                EpubXmlUtil.trimToNull(scheme));

        if (matches.isEmpty()) return false;
        if (matches.size() > 1) throw new IllegalStateException("Metadata selector is ambiguous. matched=" + matches.size() + ", name=" + normalizedName + ", property=" + property + ", targetValue=" + targetValue);

        Element existing = matches.get(0);

        replaceMetadataElement(document, metadata, existing, replacement);

        EpubXmlUtil.writeDocument(packagePath, document);

        return true;
    }

    @Override
    public boolean removeMetadataIfExists(
            Path packagePath,
            String name,
            String targetValue,
            String id,
            String property,
            String refines,
            String scheme) {

        validatePackagePath(packagePath);

        String normalizedName = EpubXmlUtil.trimToNull(name);

        if (normalizedName == null) throw new IllegalArgumentException("name must not be empty.");

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element metadata = requireElement(document, "metadata");

        List<Element> matches = findMetadataItems(
                metadata,
                normalizedName,
                EpubXmlUtil.trimToNull(targetValue),
                EpubXmlUtil.trimToNull(id),
                EpubXmlUtil.trimToNull(property),
                EpubXmlUtil.normalizeRefines(refines),
                EpubXmlUtil.trimToNull(scheme));

        if (matches.isEmpty()) return false;
        if (matches.size() > 1) throw new IllegalStateException("Metadata selector is ambiguous. matched=" + matches.size() + ", name=" + normalizedName + ", property=" + property + ", targetValue=" + targetValue);

        metadata.removeChild(matches.get(0));

        EpubXmlUtil.writeDocument(packagePath, document);

        return true;
    }

    @Override
    public void addOrUpdateSpineItem(Path packagePath, EpubSpineItem item) {

        validatePackagePath(packagePath);

        if (item == null) throw new IllegalArgumentException("item must not be null.");

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        validateSpineReference(manifest, item.getIdref());

        updateSpineItem(document, spine, item);

        removeDuplicateSpineItems(spine);
        sortSpineItems(manifest, spine);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public void removeSpineItem(Path packagePath, String idref) {

        validatePackagePath(packagePath);

        if (idref == null || idref.isBlank()) throw new IllegalArgumentException("idref must not be empty.");

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element spine = requireElement(document, "spine");

        removeSpineItemsByIdref(spine, idref.trim());

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public void addSpineItemref(Path packagePath, String idref, int targetIndex) {

        validatePackagePath(packagePath);

        String normalizedIdref = normalizeIdref(idref);

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");
        Element spine = requireElement(document, "spine");

        validateSpineReference(manifest, normalizedIdref);

        if (findSpineItemByIdref(spine, normalizedIdref) != null) throw new IllegalStateException("Spine itemref already exists: " + normalizedIdref);

        List<Element> items = findSpineItemrefs(spine);

        validateInsertIndex(targetIndex, items.size());

        Element itemref = document.createElementNS(OPF_NAMESPACE, "itemref");

        itemref.setAttribute("idref", normalizedIdref);

        insertSpineItemref(spine, itemref, items, targetIndex);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public boolean removeSpineItemrefIfExists(Path packagePath, String idref) {

        validatePackagePath(packagePath);

        String normalizedIdref = normalizeIdref(idref);

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element spine = requireElement(document, "spine");
        Element itemref = findSpineItemByIdref(spine, normalizedIdref);

        if (itemref == null) return false;

        spine.removeChild(itemref);

        EpubXmlUtil.writeDocument(packagePath, document);

        return true;
    }

    @Override
    public void moveSpineItemref(Path packagePath, String idref, int targetIndex) {

        validatePackagePath(packagePath);

        String normalizedIdref = normalizeIdref(idref);

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element spine = requireElement(document, "spine");
        List<Element> items = findSpineItemrefs(spine);
        Element itemref = findSpineItemByIdref(spine, normalizedIdref);

        if (itemref == null) throw new IllegalStateException("Spine itemref does not exist: " + normalizedIdref);

        validateMoveIndex(targetIndex, items.size());

        int currentIndex = items.indexOf(itemref);

        if (currentIndex == targetIndex) return;

        spine.removeChild(itemref);

        List<Element> remainingItems = findSpineItemrefs(spine);

        insertSpineItemref(spine, itemref, remainingItems, targetIndex);

        EpubXmlUtil.writeDocument(packagePath, document);
    }

    @Override
    public boolean containsManifestItem(Path packagePath, String resourceId) {

        validatePackagePath(packagePath);

        if (resourceId == null || resourceId.isBlank()) return false;

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element manifest = requireElement(document, "manifest");

        return findManifestItemById(manifest, resourceId.trim()) != null;
    }

    @Override
    public boolean containsSpineItem(Path packagePath, String idref) {

        validatePackagePath(packagePath);

        if (idref == null || idref.isBlank()) return false;

        Document document = EpubXmlUtil.readDocument(packagePath);
        Element spine = requireElement(document, "spine");

        return findSpineItemByIdref(spine, idref.trim()) != null;
    }

    @Override
    public void update(Path packagePath, List<EpubManifestItem> resources, List<EpubSpineItem> spineItems) {

        validatePackagePath(packagePath);

        if ((resources == null || resources.isEmpty()) && (spineItems == null || spineItems.isEmpty())) return;

        Document document = EpubXmlUtil.readDocument(packagePath);
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

        EpubXmlUtil.writeDocument(packagePath, document);
    }

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

    private List<EpubSpineItem> normalizeSpineItems(List<EpubSpineItem> spineItems) {

        if (spineItems == null || spineItems.isEmpty()) return List.of();

        Map<String, EpubSpineItem> byIdref = new LinkedHashMap<>();

        for (EpubSpineItem item : spineItems) {

            if (item == null) continue;

            byIdref.put(item.getIdref(), item);
        }

        return List.copyOf(byIdref.values());
    }

    private void updateManifestItems(Document document, Element manifest, List<EpubManifestItem> resources, Map<String, String> idMappings) {

        if (resources == null || resources.isEmpty()) return;

        for (EpubManifestItem resource : resources) updateManifestItem(document, manifest, resource, idMappings);
    }

    private void updateSpineItems(Document document, Element spine, List<EpubSpineItem> spineItems) {

        if (spineItems == null || spineItems.isEmpty()) return;

        for (EpubSpineItem item : spineItems) updateSpineItem(document, spine, item);
    }

    private void updateManifestItem(Document document, Element manifest, EpubManifestItem resource, Map<String, String> idMappings) {

        Element byId = findManifestItemById(manifest, resource.getId());
        Element byHref = findManifestItemByHref(manifest, resource.getHref());

        if (byHref != null && byHref != byId) {

            String oldId = byHref.getAttribute("id");

            if (oldId != null && !oldId.isBlank() && !oldId.equals(resource.getId())) idMappings.put(oldId, resource.getId());

            manifest.removeChild(byHref);
        }

        Element existing = byId;

        if (existing == null) {

            existing = document.createElementNS(OPF_NAMESPACE, "item");

            manifest.appendChild(existing);
        }

        existing.setAttribute("id", resource.getId());
        existing.setAttribute("href", normalizeHref(resource.getHref()));
        existing.setAttribute("media-type", resource.getMediaType());

        writeProperties(existing, resource);
        writeFallback(existing, resource);
        writeMediaOverlay(existing, resource);
    }

    private Element createMetadataElement(Document document, EpubMetadataItem item) {

        if (item.isDublinCore()) return document.createElementNS(DC_NAMESPACE, item.getElementName());
        if (item.isMetaProperty()) return document.createElementNS(OPF_NAMESPACE, "meta");

        throw new IllegalArgumentException("Unsupported EPUB metadata item: " + item.getElementName());
    }

    private void replaceMetadataElement(
            Document document,
            Element metadata,
            Element existing,
            EpubMetadataItem replacement) {

        Element replacementElement = createMetadataElement(document, replacement);

        writeMetadataItem(replacementElement, replacement);

        metadata.replaceChild(replacementElement, existing);
    }

    private List<Element> findMetadataItems(
            Element metadata,
            String name,
            String targetValue,
            String id,
            String property,
            String refines,
            String scheme) {

        String normalizedName = EpubXmlUtil.trimToNull(name);
        String normalizedTargetValue = EpubXmlUtil.trimToNull(targetValue);
        String normalizedId = EpubXmlUtil.trimToNull(id);
        String normalizedProperty = EpubXmlUtil.trimToNull(property);
        String normalizedRefines = EpubXmlUtil.normalizeRefines(refines);
        String normalizedScheme = EpubXmlUtil.trimToNull(scheme);

        List<Element> matches = new ArrayList<>();
        NodeList children = metadata.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!matchesMetadataName(element, normalizedName)) continue;
            if (normalizedTargetValue != null && !normalizedTargetValue.equals(EpubXmlUtil.trimToNull(element.getTextContent()))) continue;
            if (normalizedId != null && !normalizedId.equals(EpubXmlUtil.trimToNull(element.getAttribute("id")))) continue;
            if (normalizedProperty != null && !normalizedProperty.equals(EpubXmlUtil.trimToNull(element.getAttribute("property")))) continue;
            if (normalizedRefines != null && !normalizedRefines.equals(EpubXmlUtil.normalizeRefines(element.getAttribute("refines")))) continue;
            if (normalizedScheme != null && !normalizedScheme.equals(EpubXmlUtil.trimToNull(element.getAttribute("scheme")))) continue;

            matches.add(element);
        }

        return matches;
    }

    private boolean matchesMetadataName(Element element, String name) {

        if (element == null || name == null) return false;

        if ("meta".equals(name)) return isElement(element, "meta");
        if (!name.startsWith("dc:")) return false;

        String localName = name.substring(3);

        if (!localName.equals(element.getLocalName()) && !name.equals(element.getNodeName())) return false;

        String namespace = element.getNamespaceURI();

        return namespace == null || namespace.isBlank() || DC_NAMESPACE.equals(namespace);
    }

    private void writeMetadataItem(Element element, EpubMetadataItem item) {

        element.setTextContent(item.getValue());

        for (Map.Entry<String, String> attribute : item.toXmlAttributes().entrySet()) {

            String name = attribute.getKey();
            String value = attribute.getValue();

            if ("xml:lang".equals(name)) {

                element.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", value);

                continue;
            }

            element.setAttribute(name, value);
        }
    }

    private void validateMetadataItem(EpubMetadataItem item) {

        if (item == null) throw new IllegalArgumentException("item must not be null.");
        if (item.getElementName() == null || item.getElementName().isBlank()) throw new IllegalArgumentException("metadata elementName must not be empty.");
        if (item.getValue() == null || item.getValue().isBlank()) throw new IllegalArgumentException("metadata value must not be empty.");
        if (!item.isDublinCore() && !item.isMetaProperty()) throw new IllegalArgumentException("Unsupported metadata item: " + item.getElementName());
    }

    private void updateSpineItem(Document document, Element spine, EpubSpineItem item) {

        Element existing = findSpineItemByIdref(spine, item.getIdref());

        if (existing == null) {

            existing = document.createElementNS(OPF_NAMESPACE, "itemref");

            spine.appendChild(existing);
        }

        existing.setAttribute("idref", item.getIdref());

        if (item.getId().isPresent()) existing.setAttribute("id", item.getId().get());
        else existing.removeAttribute("id");

        if (item.shouldWriteLinearAttribute()) existing.setAttribute("linear", "no");
        else existing.removeAttribute("linear");

        if (item.shouldWriteProperties()) existing.setAttribute("properties", item.getPropertiesValue());
        else existing.removeAttribute("properties");
    }

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

    private boolean removeSpineItemsByIdrefIfExists(Element spine, String idref) {

        boolean removed = false;
        NodeList children = spine.getChildNodes();

        for (int index = children.getLength() - 1; index >= 0; index--) {

            Node node = children.item(index);

            if (!(node instanceof Element)) continue;

            Element element = (Element) node;

            if (!isElement(element, "itemref")) continue;
            if (!idref.equals(element.getAttribute("idref"))) continue;

            spine.removeChild(element);

            removed = true;
        }

        return removed;
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

    private void validateManifestItem(EpubManifestItem resource) {

        if (resource == null) throw new IllegalArgumentException("resource must not be null.");
        if (resource.getId() == null || resource.getId().isBlank()) throw new IllegalArgumentException("resource id must not be empty.");
        if (resource.getHref() == null || resource.getHref().isBlank()) throw new IllegalArgumentException("resource href must not be empty.");
        if (resource.getMediaType() == null || resource.getMediaType().isBlank()) throw new IllegalArgumentException("resource mediaType must not be empty.");
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

    private List<Element> findSpineItemrefs(Element spine) {

        List<Element> items = new ArrayList<>();
        NodeList children = spine.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {

            Node node = children.item(index);

            if (!(node instanceof Element element)) continue;
            if (!isElement(element, "itemref")) continue;

            items.add(element);
        }

        return items;
    }

    private void insertSpineItemref(Element spine, Element itemref, List<Element> items, int targetIndex) {

        if (targetIndex >= items.size()) {

            spine.appendChild(itemref);

            return;
        }

        Element referenceItem = items.get(targetIndex);

        spine.insertBefore(itemref, referenceItem);
    }

    private void writeProperties(Element element, EpubManifestItem resource) {

        if (resource.getProperties() == null || resource.getProperties().isEmpty()) {

            element.removeAttribute("properties");

            return;
        }

        element.setAttribute("properties", String.join(" ", resource.getProperties()));
    }

    private void writeFallback(Element element, EpubManifestItem resource) {

        if (resource.getFallbackId().isPresent()) element.setAttribute("fallback", resource.getFallbackId().get());
        else element.removeAttribute("fallback");
    }

    private void writeMediaOverlay(Element element, EpubManifestItem resource) {

        if (resource.getMediaOverlayId().isPresent()) element.setAttribute("media-overlay", resource.getMediaOverlayId().get());
        else element.removeAttribute("media-overlay");
    }

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

    private String resolveResourceIdFromHref(String href) {

        if (href == null || href.isBlank()) return null;

        String normalizedHref = normalizeHref(href);
        int slashIndex = normalizedHref.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedHref.substring(slashIndex + 1) : normalizedHref;
        int extensionIndex = fileName.lastIndexOf('.');

        if (extensionIndex > 0) fileName = fileName.substring(0, extensionIndex);

        return fileName.isBlank() ? null : fileName;
    }

    private String normalizeHref(String href) {

        if (href == null) return "";

        return href.trim().replace('\\', '/');
    }

    private String normalizeIdref(String idref) {

        if (idref == null || idref.isBlank()) throw new IllegalArgumentException("idref must not be empty.");

        return idref.trim();
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

    private void validatePackagePath(Path packagePath) {

        if (packagePath == null) throw new IllegalArgumentException("packagePath must not be null.");
        if (!Files.exists(packagePath)) throw new IllegalStateException("EPUB package does not exist: " + packagePath);
        if (!Files.isRegularFile(packagePath)) throw new IllegalStateException("EPUB package is not a file: " + packagePath);
    }

    private void validateInsertIndex(int targetIndex, int itemCount) {

        if (targetIndex < 0 || targetIndex > itemCount) throw new IllegalArgumentException("targetIndex must be between 0 and " + itemCount + ": " + targetIndex);
    }

    private void validateMoveIndex(int targetIndex, int itemCount) {

        if (itemCount <= 0) throw new IllegalStateException("EPUB spine does not contain any itemref.");
        if (targetIndex < 0 || targetIndex >= itemCount) throw new IllegalArgumentException("targetIndex must be between 0 and " + (itemCount - 1) + ": " + targetIndex);
    }
}