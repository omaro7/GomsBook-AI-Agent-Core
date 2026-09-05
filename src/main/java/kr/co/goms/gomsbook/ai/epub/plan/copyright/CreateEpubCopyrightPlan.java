/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.plan.copyright;

import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightPage;

public class CreateEpubCopyrightPlan {

    private final boolean enabled;
    private final EpubCopyrightPage page;

    public CreateEpubCopyrightPlan(boolean enabled, EpubCopyrightPage page) {
        this.enabled = enabled;
        this.page = page;
    }

    public boolean isEnabled() { return enabled; }
    public EpubCopyrightPage getPage() { return page; }
    public boolean hasPage() { return page != null; }
}