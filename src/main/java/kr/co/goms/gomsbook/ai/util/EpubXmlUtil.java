/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.util;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public final class EpubXmlUtil {

    private EpubXmlUtil() {
    }
    
    public static Document readDocument(Path path) {

        if (path == null) throw new IllegalArgumentException("path must not be null.");
        if (!Files.exists(path)) throw new IllegalStateException("XML document does not exist: " + path);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("XML document is not a file: " + path);

        try {

            DocumentBuilderFactory factory = createDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();

            try (InputStream inputStream = Files.newInputStream(path)) {
                return builder.parse(inputStream);
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to read XML document: " + path,
                    exception);
        }
    }
    
    public static void writeDocument(Path path, Document document) {

        if (path == null) throw new IllegalArgumentException("path must not be null.");
        if (document == null) throw new IllegalArgumentException("document must not be null.");

        try {

            String xml = xmlDocumentToString(document);

            Files.writeString(
                    path,
                    xml,
                    StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to write XML document: " + path,
                    exception);
        }
    }

    public static String xmlDocumentToString(Document document) throws Exception {

        if (document == null) throw new IllegalArgumentException("document must not be null.");

        removeWhitespaceNodes(document);

        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();

        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter writer = new StringWriter();

        transformer.transform(
                new DOMSource(document),
                new StreamResult(writer));

        return writer.toString();
    }

    private static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {

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

        return factory;
    }

    private static TransformerFactory createTransformerFactory() {

        TransformerFactory factory = TransformerFactory.newInstance();

        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException exception) {
        }

        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException exception) {
        }

        return factory;
    }
    
    private static void removeWhitespaceNodes(Node node) {

        Node child = node.getFirstChild();

        while (child != null) {

            Node next = child.getNextSibling();

            if (child.getNodeType() == Node.TEXT_NODE
                    && child.getTextContent().trim().isEmpty()) {

                node.removeChild(child);

            } else {

                removeWhitespaceNodes(child);
            }

            child = next;
        }
    }
}