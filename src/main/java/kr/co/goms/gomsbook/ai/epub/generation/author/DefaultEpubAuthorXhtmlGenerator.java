/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.generation.author;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.resource.EpubStylesheetResolver;

public class DefaultEpubAuthorXhtmlGenerator implements EpubAuthorXhtmlGenerator {

    private final EpubStylesheetResolver stylesheetResolver;


    public DefaultEpubAuthorXhtmlGenerator() {
        this(new EpubStylesheetResolver());
    }


    public DefaultEpubAuthorXhtmlGenerator(EpubStylesheetResolver stylesheetResolver) {
        if (stylesheetResolver == null) throw new IllegalArgumentException("stylesheetResolver must not be null.");
        this.stylesheetResolver = stylesheetResolver;
    }

    @Override
    public String render(EpubAuthorPage page, Path outputDirectory) {

        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (outputDirectory == null) throw new IllegalArgumentException("outputDirectory must not be null.");

        validate(page);

        Path outputPath = outputDirectory.resolve(page.getFileName()).toAbsolutePath().normalize();
        String stylesheetHref = stylesheetResolver.resolveHref(outputPath);

        return createXhtml(page, stylesheetHref);
    }
    

    @Override
    public Path generate(EpubAuthorPage page, Path outputDirectory) {

        validate(page);

        try {

            Files.createDirectories(outputDirectory);

            Path outputPath = outputDirectory.resolve(page.getFileName());
            String xhtml = render(page, outputDirectory);

            Files.writeString(outputPath, xhtml, StandardCharsets.UTF_8);

            return outputPath;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to generate EPUB author XHTML.",
                    exception);
        }
    }

    private String createXhtml(EpubAuthorPage page, String stylesheetHref) {

        StringBuilder builder = new StringBuilder();
        int pIndex = 1;

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ko\" xml:lang=\"ko\">\n");
        builder.append("<head>\n");
        builder.append("    <meta charset=\"UTF-8\" />\n");
        builder.append("    <title>작가소개</title>\n");
        builder.append("    <link rel=\"stylesheet\" type=\"text/css\" href=\"").append(escapeAttribute(stylesheetHref)).append("\" />\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("    <section epub:type=\"preface\" role=\"doc-preface\" aria-labelledby=\"author-title\">\n");
        builder.append("        <h1 id=\"author-title\">작가소개</h1>\n");

        pIndex = appendAuthorNameParagraph(builder, pIndex, page.getAuthorName());
        pIndex = appendTextParagraph(builder, pIndex, page.getIntroduction(), "introduction");
        pIndex = appendTextParagraph(builder, pIndex, page.getProfile(), "profile");

        if (page.getCareers() != null && !page.getCareers().isEmpty()) {
            appendCareers(builder, page);
        }

        appendAuthorImage(builder, page);

        builder.append("    </section>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");

        return builder.toString();
    }


    private int appendAuthorNameParagraph(
            StringBuilder builder,
            int index,
            String authorName) {

        if (isBlank(authorName)) return index;

        builder.append("        <p id=\"author_p_")
                .append(index)
                .append("\" class=\"author-name\">")
                .append(escapeHtml(authorName))
                .append("</p>\n");

        return index + 1;
    }


    private int appendTextParagraph(
            StringBuilder builder,
            int index,
            String value,
            String className) {

        if (isBlank(value)) return index;

        builder.append("        <p id=\"author_p_")
                .append(index)
                .append("\" class=\"")
                .append(escapeAttribute(className))
                .append("\">")
                .append(escapeHtml(value))
                .append("</p>\n");

        return index + 1;
    }


    private void appendCareers(
            StringBuilder builder,
            EpubAuthorPage page) {

        builder.append("        <ul class=\"author-careers\">\n");

        for (String career : page.getCareers()) {

            if (isBlank(career)) continue;

            builder.append("            <li>")
                    .append(escapeHtml(career))
                    .append("</li>\n");
        }

        builder.append("        </ul>\n");
    }


    private void appendAuthorImage(
            StringBuilder builder,
            EpubAuthorPage page) {

        if (isBlank(page.getImageFileName())) return;

        String imageHref = "../Images/" + page.getImageFileName().trim();

        builder.append("        <figure class=\"author-image\">\n");
        builder.append("            <img src=\"")
                .append(escapeAttribute(imageHref))
                .append("\" alt=\"")
                .append(escapeAttribute(value(page.getImageAlt())))
                .append("\" />\n");
        builder.append("        </figure>\n");
    }


    private void validate(EpubAuthorPage page) {

        if (isBlank(page.getFileName())) throw new IllegalArgumentException("fileName must not be blank.");

        String fileName = page.getFileName().trim().toLowerCase();

        if (!fileName.endsWith(".xhtml")) throw new IllegalArgumentException("Author page fileName must be an XHTML file: " + page.getFileName());

        Path fileNamePath = Path.of(page.getFileName()).normalize();

        if (fileNamePath.isAbsolute() || fileNamePath.getNameCount() != 1) throw new IllegalArgumentException("Author page fileName must not contain a path: " + page.getFileName());
    }


    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }


    private String value(String value) {
        return value == null ? "" : value.trim();
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