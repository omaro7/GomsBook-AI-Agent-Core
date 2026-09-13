/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.lot;

import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubLotItem;

public final class DefaultEpubLotGenerator implements EpubLotGenerator {

    private static final String TITLE = "표 목록";

    @Override
    public String generate(List<EpubLotItem> items, String stylesheetHref) {

        StringBuilder builder = new StringBuilder();

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"ko\" xml:lang=\"ko\">\n");
        builder.append("<head>\n");
        builder.append("<meta charset=\"UTF-8\"/>\n");
        builder.append("<title>").append(escape(TITLE)).append("</title>\n");

        if (stylesheetHref != null && !stylesheetHref.isBlank()) builder.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(escape(stylesheetHref)).append("\"/>\n");

        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("<section aria-labelledby=\"lot-title\">\n");
        builder.append("<h1 id=\"lot-title\">").append(escape(TITLE)).append("</h1>\n");
        builder.append("<ol>\n");

        if (items != null) {

            for (EpubLotItem item : items) appendItem(builder, item);
        }

        builder.append("</ol>\n");
        builder.append("</section>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");

        return builder.toString();
    }

    private void appendItem(StringBuilder builder, EpubLotItem item) {

        if (item == null) return;
        if (item.href() == null || item.href().isBlank()) return;
        if (item.title() == null || item.title().isBlank()) return;

        builder.append("<li><a href=\"")
                .append(escape(item.href()))
                .append("\">")
                .append(escape(item.title()))
                .append("</a></li>\n");
    }

    private String escape(String value) {

        if (value == null) return "";

        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

}