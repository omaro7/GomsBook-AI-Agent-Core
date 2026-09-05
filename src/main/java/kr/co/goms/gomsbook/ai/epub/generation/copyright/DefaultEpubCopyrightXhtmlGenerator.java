/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.copyright;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DefaultEpubCopyrightXhtmlGenerator implements EpubCopyrightXhtmlGenerator {

    private static final String DEFAULT_FILE_NAME = "copyright.xhtml";
    private static final String DEFAULT_STYLESHEET_HREF = "../Styles/style1.css";

    @Override
    public Path generate(EpubCopyrightPage page, Path outputDirectory) {

        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (outputDirectory == null) throw new IllegalArgumentException("outputDirectory must not be null.");

        validate(page);

        try {

            Files.createDirectories(outputDirectory);

            Path outputPath = outputDirectory.resolve(page.getFileName()).toAbsolutePath().normalize();
            String xhtml = createXhtml(page);

            Files.writeString(outputPath, xhtml, StandardCharsets.UTF_8);

            return outputPath;

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to generate copyright XHTML.", exception);
        }
    }

    private String createXhtml(EpubCopyrightPage page) {

        String stylesheetHref = trimToNull(page.getStylesheetHref());
        if (stylesheetHref == null) stylesheetHref = DEFAULT_STYLESHEET_HREF;

        StringBuilder builder = new StringBuilder();
        int pIndex = 1;

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ko\" lang=\"ko\">\n");
        builder.append("<head>\n");
        builder.append("    <meta charset=\"UTF-8\" />\n");
        builder.append("    <title>판권</title>\n");
        builder.append("    <link href=\"").append(escapeAttribute(stylesheetHref)).append("\" type=\"text/css\" rel=\"stylesheet\" />\n");
        builder.append("</head>\n");
        builder.append("<body epub:type=\"backmatter\">\n");
        builder.append("    <section epub:type=\"copyright-page\" aria-labelledby=\"copyright-title\">\n");
        builder.append("        <h1 id=\"copyright-title\">판권</h1>\n");

        pIndex = appendTitleParagraph(builder, pIndex, page.getTitle());
        pIndex = appendLabeledParagraph(builder, pIndex, "발행일", page.getPublicationDate());
        pIndex = appendLabeledParagraph(builder, pIndex, "지은이", page.getAuthor());
        pIndex = appendLabeledParagraph(builder, pIndex, "펴낸이", page.getPublisherRepresentative());
        pIndex = appendLabeledParagraph(builder, pIndex, "펴낸곳", page.getPublisher());
        pIndex = appendLabeledParagraph(builder, pIndex, "주소", page.getAddress());
        pIndex = appendLabeledParagraph(builder, pIndex, "이메일", page.getEmail());
        pIndex = appendWebsiteParagraph(builder, pIndex, page.getWebsite());
        pIndex = appendLabeledParagraph(builder, pIndex, "출판등록", page.getPublishingRegistration());
        pIndex = appendLabeledParagraph(builder, pIndex, "ISBN", page.getIsbn());
        pIndex = appendLabeledParagraph(builder, pIndex, "정가", page.getPrice());
        pIndex = appendTextParagraph(builder, pIndex, page.getSupportText());
        appendCopyrightParagraph(builder, pIndex, createCopyrightText(page));

        builder.append("    </section>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");

        return builder.toString();
    }

    private int appendTitleParagraph(StringBuilder builder, int index, String title) {

        if (isBlank(title)) return index;

        builder.append("        <p id=\"copyright_p_")
                .append(index)
                .append("\"><span class=\"label\">")
                .append(escapeHtml(title))
                .append("</span></p>\n");

        return index + 1;
    }

    private int appendLabeledParagraph(
            StringBuilder builder,
            int index,
            String label,
            String value) {

        if (isBlank(value)) return index;

        builder.append("        <p id=\"copyright_p_")
                .append(index)
                .append("\"><span class=\"label\">")
                .append(escapeHtml(label))
                .append("</span> ")
                .append(escapeHtml(value.trim()))
                .append("</p>\n");

        return index + 1;
    }

    private int appendWebsiteParagraph(
            StringBuilder builder,
            int index,
            String website) {

        if (isBlank(website)) return index;

        String value = website.trim();
        String escapedText = escapeHtml(value);
        String escapedHref = escapeAttribute(normalizeUrl(value));

        builder.append("        <p id=\"copyright_p_")
                .append(index)
                .append("\"><span class=\"label\">웹사이트</span> <a href=\"")
                .append(escapedHref)
                .append("\">")
                .append(escapedText)
                .append("</a></p>\n");

        return index + 1;
    }

    private int appendTextParagraph(
            StringBuilder builder,
            int index,
            String value) {

        if (isBlank(value)) return index;

        builder.append("        <p id=\"copyright_p_")
                .append(index)
                .append("\">")
                .append(escapeHtml(value.trim()))
                .append("</p>\n");

        return index + 1;
    }

    private int appendCopyrightParagraph(
            StringBuilder builder,
            int index,
            String value) {

        if (isBlank(value)) return index;

        builder.append("        <p id=\"copyright_p_")
                .append(index)
                .append("\" class=\"copyright\">")
                .append(escapeHtml(value))
                .append("</p>\n");

        return index + 1;
    }

    private String createCopyrightText(EpubCopyrightPage page) {

        String holder = trimToNull(page.getCopyrightHolder());
        String year = trimToNull(page.getCopyrightYear());
        String text = trimToNull(page.getCopyrightText());

        StringBuilder builder = new StringBuilder();

        if (year != null) builder.append("© ").append(year);
        if (holder != null) appendWithSpace(builder, holder);
        if (text != null) appendWithSpace(builder, text);

        return builder.toString();
    }

    private void appendWithSpace(StringBuilder builder, String value) {

        if (builder.length() > 0) builder.append(" ");

        builder.append(value);
    }

    private void validate(EpubCopyrightPage page) {

        if (isBlank(page.getFileName())) page.setFileName(DEFAULT_FILE_NAME);

        String fileName = page.getFileName().trim();

        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("Copyright page fileName must be an XHTML file: " + fileName);

        Path fileNamePath = Path.of(fileName).normalize();

        if (fileNamePath.isAbsolute() || fileNamePath.getNameCount() != 1) throw new IllegalArgumentException("Copyright page fileName must not contain a path: " + fileName);
    }

    private String normalizeUrl(String value) {

        if (isBlank(value)) return value;

        String normalized = value.trim();

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return normalized;

        return "https://" + normalized;
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();
    }

    private String escapeHtml(String value) {

        if (value == null) return "";

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeAttribute(String value) {

        if (value == null) return "";

        return escapeHtml(value)
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}