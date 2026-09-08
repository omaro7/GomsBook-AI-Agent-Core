/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

/**
 * EPUB spine 수정 승인 Payload입니다.
 *
 * <p>기존 spine의 itemref에 대해 추가, 삭제, 이동만 처리합니다.</p>
 *
 * <p>현재 단계에서는 itemref의 idref 속성만 처리합니다.</p>
 */
public final class UpdateEpubSpineApprovalPayload {

    private String operation;
    private String idref;
    private Integer targetIndex;

    public UpdateEpubSpineApprovalPayload() {

    }

    public UpdateEpubSpineApprovalPayload(String operation, String idref, Integer targetIndex) {

        this.operation = trimToEmpty(operation);
        this.idref = trimToEmpty(idref);
        this.targetIndex = targetIndex;
    }

    public String getOperation() {

        return operation;
    }

    public void setOperation(String operation) {

        this.operation = trimToEmpty(operation);
    }

    public String getIdref() {

        return idref;
    }

    public void setIdref(String idref) {

        this.idref = trimToEmpty(idref);
    }

    public Integer getTargetIndex() {

        return targetIndex;
    }

    public void setTargetIndex(Integer targetIndex) {

        this.targetIndex = targetIndex;
    }

    private String trimToEmpty(String value) {

        if (value == null) return "";

        return value.trim();
    }
}