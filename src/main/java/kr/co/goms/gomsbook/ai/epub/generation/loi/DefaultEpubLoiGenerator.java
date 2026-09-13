/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.loi;

import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubLoiItem;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

public final class DefaultEpubLoiGenerator implements EpubLoiGenerator {

    @Override
    public String generate(List<EpubLoiItem> items, String stylesheetHref) {

        if (items == null) throw new IllegalArgumentException("items must not be null.");
        if (stylesheetHref == null || stylesheetHref.isBlank()) throw new IllegalArgumentException("stylesheetHref must not be blank.");

        StringBuilder builder = new StringBuilder();

        appendDocumentStart(builder, stylesheetHref);
        appendItems(builder, items);
        appendDocumentEnd(builder);

        return builder.toString();
    }

    private void appendDocumentStart(StringBuilder builder, String stylesheetHref) {

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ko\" lang=\"ko\">\n");
        builder.append("<head>\n");
        builder.append("    <meta charset=\"utf-8\" />\n");
        builder.append("    <title>이미지 목차</title>\n");
        builder.append("    <link href=\"").append(EpubXmlUtil.escapeAttribute(stylesheetHref)).append("\" type=\"text/css\" rel=\"stylesheet\" />\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("    <nav epub:type=\"loi\" id=\"loi\" aria-labelledby=\"loi-title\">\n");
        builder.append("        <h1 id=\"loi-title\">이미지 목차</h1>\n");
        builder.append("        <ol>\n");
    }

    private void appendItems(StringBuilder builder, List<EpubLoiItem> items) {

        for (EpubLoiItem item : items) {

            if (item == null) continue;

            String href = EpubXmlUtil.normalize(item.href());
            String alt = EpubXmlUtil.normalize(item.alt());

            if (href == null || alt == null) continue;

            builder.append("            <li><a href=\"")
                    .append(EpubXmlUtil.escapeAttribute(href))
                    .append("\">")
                    .append(EpubXmlUtil.escapeText(alt))
                    .append("</a></li>\n");
        }
    }

    private void appendDocumentEnd(StringBuilder builder) {

        builder.append("        </ol>\n");
        builder.append("    </nav>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");
    }
}