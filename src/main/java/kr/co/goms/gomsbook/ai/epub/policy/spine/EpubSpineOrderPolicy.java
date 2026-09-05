/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.policy.spine;

public interface EpubSpineOrderPolicy {

    int getOrder(String href);

    boolean isOrderedBefore(String firstHref, String secondHref);
}