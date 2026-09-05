/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.copyright;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightPage;

public final class EpubCopyrightReader {

    public EpubCopyrightPage read(Path copyrightFile) {

        if (copyrightFile == null) throw new IllegalArgumentException("copyrightFile must not be null.");
        if (!Files.exists(copyrightFile)) throw new IllegalStateException("Copyright XHTML not found: " + copyrightFile);

        try {

            String xhtml = Files.readString(copyrightFile, StandardCharsets.UTF_8);
            Document document = Jsoup.parse(xhtml, "", Parser.xmlParser());

            EpubCopyrightPage page = new EpubCopyrightPage();

            page.setFileName(copyrightFile.getFileName().toString());
            page.setTitle(findTitle(document));
            page.setPublicationDate(findValue(document, "발행일"));
            page.setAuthor(findValue(document, "지은이", "저자"));
            page.setPublisherRepresentative(findValue(document, "펴낸이"));
            page.setPublisher(findValue(document, "펴낸곳", "발행처"));
            page.setAddress(findValue(document, "주소"));
            page.setEmail(findValue(document, "이메일"));
            page.setWebsite(findValue(document, "웹사이트"));
            page.setPublishingRegistration(findValue(document, "출판등록"));
            page.setIsbn(findValue(document, "ISBN"));
            page.setPrice(findValue(document, "정가"));
            page.setSupportText(findSupportText(document));
            page.setCopyrightText(findCopyrightText(document));

            return page;

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to read copyright XHTML: " + copyrightFile, exception);
        }
    }

    private String findTitle(Document document) {

        Element section = document.selectFirst("section");
        if (section == null) return "";

        Element paragraph = section.selectFirst("p");
        if (paragraph == null) return "";

        Element label = paragraph.selectFirst("span.label");
        if (label == null) return paragraph.text().trim();

        return label.text().trim();
    }

    private String findValue(Document document, String... labels) {

        for (Element paragraph : document.select("p")) {

            Element labelElement = paragraph.selectFirst("span.label, strong");
            if (labelElement == null) continue;
            if (!matches(labelElement.text(), labels)) continue;

            Element copy = paragraph.clone();
            Element copyLabel = copy.selectFirst("span.label, strong");

            if (copyLabel != null) copyLabel.remove();

            return copy.text().trim();
        }

        return "";
    }

    private boolean matches(String value, String... labels) {

        if (value == null) return false;

        String normalized = value.trim();

        for (String label : labels) {
            if (normalized.equals(label)) return true;
        }

        return false;
    }

    private String findSupportText(Document document) {

        for (Element paragraph : document.select("p")) {

            if (paragraph.hasClass("copyright")) continue;
            if (paragraph.selectFirst("span.label, strong") != null) continue;

            String value = paragraph.text().trim();

            if (!value.isBlank()) return value;
        }

        return "";
    }

    private String findCopyrightText(Document document) {

        Element element = document.selectFirst("p.copyright, .copyright-text");

        return element == null ? "" : element.text().trim();
    }
}