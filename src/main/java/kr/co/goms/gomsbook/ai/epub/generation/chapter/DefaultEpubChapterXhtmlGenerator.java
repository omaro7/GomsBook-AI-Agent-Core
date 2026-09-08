/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.chapter;

public class DefaultEpubChapterXhtmlGenerator implements EpubChapterXhtmlGenerator {

    @Override
    public String generate(int partNumber, int chapterNumber, String title, String content, String stylesheetHref) {

        validate(partNumber, chapterNumber, title, content, stylesheetHref);

        String chapterId = String.format("chapter%02d_%02d_title", partNumber, chapterNumber);
        String displayTitle = chapterNumber + ". " + title.trim();

        StringBuilder builder = new StringBuilder();

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ko\" xml:lang=\"ko\">\n");
        builder.append("\n");
        builder.append("<head>\n");
        builder.append("    <meta charset=\"UTF-8\"/>\n");
        builder.append("    <title>").append(escapeText(displayTitle)).append("</title>\n");
        builder.append("    <link href=\"").append(escapeAttribute(stylesheetHref)).append("\" type=\"text/css\" rel=\"stylesheet\" />\n");
        builder.append("</head>\n");
        builder.append("\n");
        builder.append("<body epub:type=\"bodymatter\">\n");
        builder.append("\n");
        builder.append("    <section epub:type=\"chapter\" role=\"doc-chapter\" aria-labelledby=\"").append(chapterId).append("\">\n");
        builder.append("        <h1 id=\"").append(chapterId).append("\">").append(escapeText(displayTitle)).append("</h1>\n");

        appendContent(builder, content);

        builder.append("    </section>\n");
        builder.append("\n");
        builder.append("</body>\n");
        builder.append("</html>\n");

        return builder.toString();
    }

    private void appendContent(StringBuilder builder, String content) {

        String normalizedContent = content.trim();

        if (normalizedContent.isEmpty()) return;

        String[] lines = normalizedContent.split("\\R", -1);

        for (String line : lines) {
            if (line.isEmpty()) builder.append("\n");
            else builder.append("        ").append(line).append("\n");
        }
    }

    private void validate(int partNumber, int chapterNumber, String title, String content, String stylesheetHref) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (chapterNumber <= 0) throw new IllegalArgumentException("chapterNumber must be greater than 0.");
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("title must not be empty.");
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("content must not be empty.");
        if (stylesheetHref == null || stylesheetHref.trim().isEmpty()) throw new IllegalArgumentException("stylesheetHref must not be empty.");
    }

    private String escapeText(String value) {

        if (value == null) return "";

        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeAttribute(String value) {

        if (value == null) return "";

        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}