/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.reader.pkg;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;

public final class DefaultEpubManifestReader implements EpubManifestReader {

    @Override
    public List<EpubManifestItem> read(Path packagePath) {

        if (packagePath == null) throw new IllegalArgumentException("packagePath must not be null.");
        if (!Files.isRegularFile(packagePath)) throw new IllegalStateException("EPUB package document does not exist: " + packagePath);

        try {

            Document document = parse(packagePath);
            NodeList nodes = document.getElementsByTagNameNS("*", "item");
            List<EpubManifestItem> items = new ArrayList<>();

            for (int index = 0; index < nodes.getLength(); index++) {

                Element element = (Element) nodes.item(index);

                String id = normalize(element.getAttribute("id"));
                String href = normalize(element.getAttribute("href"));
                String mediaType = normalize(element.getAttribute("media-type"));
                String properties = normalize(element.getAttribute("properties"));

                if (id == null || href == null) continue;

                items.add(EpubManifestItem.builder(id, href).mediaType(mediaType).properties(properties).build());
            }

            return items;

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to read EPUB manifest: " + packagePath, exception);
        }
    }

    private Document parse(Path packagePath) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream inputStream = Files.newInputStream(packagePath)) {

            return builder.parse(inputStream);
        }
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) return null;

        return value.trim();
    }

}