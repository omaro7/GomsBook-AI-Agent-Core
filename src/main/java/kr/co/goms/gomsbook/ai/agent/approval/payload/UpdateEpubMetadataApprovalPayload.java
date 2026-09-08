/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

/**
 * operation   : ADD / UPDATE / REMOVE
 * name        : dc:title / dc:creator / dc:language / dc:publisher / meta
 * value       : ADD / UPDATE 시 적용할 metadata 값
 * targetValue : UPDATE / REMOVE 시 기존 metadata 식별 값
 * id          : metadata id 속성
 * property    : EPUB3 meta property
 * refines     : EPUB3 refinement 대상
 * scheme      : metadata scheme
 */
public class UpdateEpubMetadataApprovalPayload {

    private String operation;
    private String name;
    private String value;
    private String targetValue;
    private String id;
    private String property;
    private String refines;
    private String scheme;

    public UpdateEpubMetadataApprovalPayload() {
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getRefines() {
        return refines;
    }

    public void setRefines(String refines) {
        this.refines = refines;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }
}