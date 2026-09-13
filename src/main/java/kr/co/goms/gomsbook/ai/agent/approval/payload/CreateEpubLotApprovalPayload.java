/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

public record CreateEpubLotApprovalPayload(String fileName) {

    public static CreateEpubLotApprovalPayload create() {
        return new CreateEpubLotApprovalPayload("lot.xhtml");
    }

}