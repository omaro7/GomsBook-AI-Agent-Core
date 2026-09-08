/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import java.util.Collections;
import java.util.List;


public final class UpdateEpubManifestApprovalPayload {

    private String operation;
    private String id;
    private String href;
    private String mediaType;
    private List<String> properties;


    public UpdateEpubManifestApprovalPayload() {
    }


    public UpdateEpubManifestApprovalPayload(String operation, String id, String href, String mediaType, List<String> properties) {

        this.operation = operation;
        this.id = id;
        this.href = href;
        this.mediaType = mediaType;
        this.properties = properties == null ? List.of() : List.copyOf(properties);
    }


    public String getOperation() {

        return operation;
    }


    public String getId() {

        return id;
    }


    public String getHref() {

        return href;
    }


    public String getMediaType() {

        return mediaType;
    }


    public List<String> getProperties() {

        return properties == null ? Collections.emptyList() : Collections.unmodifiableList(properties);
    }
}