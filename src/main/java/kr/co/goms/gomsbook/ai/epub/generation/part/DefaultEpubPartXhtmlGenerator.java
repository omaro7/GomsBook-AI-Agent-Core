/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.part;

public class DefaultEpubPartXhtmlGenerator implements EpubPartXhtmlGenerator {

    @Override
    public String generate(int partNumber, String title, String stylesheetHref) {

        validate(partNumber, title, stylesheetHref);

        String partId = String.format("part%02d_title", partNumber);
        String displayTitle = partNumber + "부 " + title.trim();

        StringBuilder builder = new StringBuilder();

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ko\" xml:lang=\"ko\">\n");
        builder.append("<head>\n");
        builder.append("    <meta charset=\"UTF-8\"/>\n");
        builder.append("    <title>").append(escapeText(displayTitle)).append("</title>\n");
        builder.append("    <link href=\"").append(escapeAttribute(stylesheetHref)).append("\" type=\"text/css\" rel=\"stylesheet\" />\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("    <section epub:type=\"part\" role=\"doc-part\" aria-labelledby=\"").append(partId).append("\">\n");
        builder.append("        <h1 id=\"").append(partId).append("\">").append(escapeText(displayTitle)).append("</h1>\n");
        builder.append("    </section>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");

        return builder.toString();
    }

    private void validate(int partNumber, String title, String stylesheetHref) {

        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber must be greater than 0.");
        }

        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title must not be empty.");
        }

        if (stylesheetHref == null || stylesheetHref.trim().isEmpty()) {
            throw new IllegalArgumentException("stylesheetHref must not be empty.");
        }
    }

    private String escapeText(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeAttribute(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}