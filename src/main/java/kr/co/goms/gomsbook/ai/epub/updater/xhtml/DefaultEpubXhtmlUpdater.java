/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.updater.xhtml;

import java.io.StringReader;
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
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

public class DefaultEpubXhtmlUpdater implements EpubXhtmlUpdater {

    @Override
    public void setAttribute(String fileName, String elementName, String matchAttributeName, String matchAttributeValue, String attributeName, String attributeValue, Path textDirectory) {

        validate(fileName, elementName, matchAttributeName, matchAttributeValue, attributeName, attributeValue, textDirectory);

        Path filePath = resolveFile(fileName, textDirectory);

        try {

            String source = Files.readString(filePath, StandardCharsets.UTF_8);

            boolean hasDoctype = EpubXmlUtil.hasHtmlDoctype(source);

            Document document = parse(source);

            Element targetElement = EpubXmlUtil.findTargetElement(document, elementName, matchAttributeName, matchAttributeValue);

            EpubXmlUtil.setAttribute(targetElement, attributeName, attributeValue);

            String updatedSource = serialize(document);

            if (hasDoctype) {

                updatedSource = "<!DOCTYPE html>\n" + updatedSource;
            }

            Files.writeString(filePath, updatedSource, StandardCharsets.UTF_8);

        } catch (IllegalArgumentException exception) {

            throw exception;

        } catch (IllegalStateException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to update EPUB XHTML file: " + fileName, exception);
        }
    }

    private void validate(String fileName, String elementName, String matchAttributeName, String matchAttributeValue, String attributeName, String attributeValue, Path textDirectory) {

        if (fileName == null || fileName.isBlank()) {

            throw new IllegalArgumentException("fileName must not be blank.");
        }

        if (!fileName.toLowerCase().endsWith(".xhtml")) {

            throw new IllegalArgumentException("Only XHTML files can be updated.");
        }

        if (elementName == null || elementName.isBlank()) {

            throw new IllegalArgumentException("elementName must not be blank.");
        }

        if (matchAttributeName == null || matchAttributeName.isBlank()) {

            throw new IllegalArgumentException("matchAttributeName must not be blank.");
        }

        if (matchAttributeValue == null || matchAttributeValue.isBlank()) {

            throw new IllegalArgumentException("matchAttributeValue must not be blank.");
        }

        if (attributeName == null || attributeName.isBlank()) {

            throw new IllegalArgumentException("attributeName must not be blank.");
        }

        if (attributeValue == null) {

            throw new IllegalArgumentException("attributeValue must not be null.");
        }

        if (textDirectory == null) {

            throw new IllegalArgumentException("textDirectory must not be null.");
        }
    }

    private Path resolveFile(String fileName, Path textDirectory) {

        Path normalizedTextDirectory = textDirectory.toAbsolutePath().normalize();

        Path filePath = normalizedTextDirectory.resolve(fileName).normalize();

        if (!filePath.startsWith(normalizedTextDirectory)) {

            throw new IllegalArgumentException("Invalid EPUB XHTML file path: " + fileName);
        }

        if (!Files.exists(filePath)) {

            throw new IllegalStateException("EPUB XHTML file not found: " + filePath);
        }

        if (!Files.isRegularFile(filePath)) {

            throw new IllegalStateException("EPUB XHTML path is not a regular file: " + filePath);
        }

        return filePath;
    }

    private Document parse(String source) throws Exception {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);

        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);

        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        factory.setXIncludeAware(false);

        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        String xhtml = EpubXmlUtil.removeDoctype(source);

        InputSource inputSource = new InputSource(new StringReader(xhtml));

        return builder.parse(inputSource);
    }

    private String serialize(Document document) throws Exception {

        TransformerFactory factory = TransformerFactory.newInstance();

        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");

        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = factory.newTransformer();

        transformer.setOutputProperty(OutputKeys.METHOD, "xml");

        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());

        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();

        transformer.transform(new DOMSource(document), new StreamResult(writer));

        return writer.toString();
    }
}