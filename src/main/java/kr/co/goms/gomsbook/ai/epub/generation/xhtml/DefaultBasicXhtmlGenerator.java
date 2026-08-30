package kr.co.goms.gomsbook.ai.epub.generation.xhtml;

public final class DefaultBasicXhtmlGenerator implements BasicXhtmlGenerator {

    private static final String DEFAULT_TITLE = "제목";

    @Override
    public String generate( String title) {

        String normalizedTitle = normalizeTitle(title);

        return """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml"
                      xmlns:epub="http://www.idpf.org/2007/ops"
                      lang="ko"
                      xml:lang="ko">
                <head>
                    <title>%s</title>
                    <link rel="stylesheet"
                          type="text/css"
                          href="../Styles/style1.css" />
                </head>
                <body>
                    <section epub:type="chapter"
                             role="doc-chapter">
                        <h1>%s</h1>
                        <p></p>
                    </section>
                </body>
                </html>
                """.formatted(
                        escapeXml(normalizedTitle),
                        escapeXml(normalizedTitle)
                );
    }

    private String normalizeTitle( String title) {

        if (title == null || title.isBlank()) {

            return DEFAULT_TITLE;
        }

        return title.trim();
    }

    private String escapeXml( String value) {

        if (value == null) {

            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}