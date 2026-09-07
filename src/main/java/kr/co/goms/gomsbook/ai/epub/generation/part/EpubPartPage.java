/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.part;

public final class EpubPartPage {

    private final int partNumber;
    private final String fileName;
    private final String title;
    private final String xhtml;

    public EpubPartPage(int partNumber, String fileName, String title, String xhtml) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank.");
        if (xhtml == null || xhtml.isBlank()) throw new IllegalArgumentException("xhtml must not be blank.");

        this.partNumber = partNumber;
        this.fileName = fileName.trim();
        this.title = title.trim();
        this.xhtml = xhtml;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getXhtml() {
        return xhtml;
    }

    public String getDisplayTitle() {
        return partNumber + "부 " + title;
    }

    public String getManifestId() {
        return String.format("part%02d", partNumber);
    }
}