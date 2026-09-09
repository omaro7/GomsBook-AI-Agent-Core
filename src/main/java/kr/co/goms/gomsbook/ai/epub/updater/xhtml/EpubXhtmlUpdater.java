/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.updater.xhtml;

import java.nio.file.Path;

public interface EpubXhtmlUpdater {

    void setAttribute(String fileName, String elementName, String matchAttributeName, String matchAttributeValue, String attributeName, String attributeValue, Path textDirectory);
}