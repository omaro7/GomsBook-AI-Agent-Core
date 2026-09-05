/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.epub.plan.project;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 신규 EPUB 프로젝트 생성 Plan.
 *
 * <p>
 * 프로젝트 자체의 생성 정보와 승인 상태만 관리한다.
 * 책의 Part, Chapter, Author, Copyright, Quiz 등의
 * 콘텐츠 생성 계획은 epub.generation.plan 영역에서 관리한다.
 * </p>
 */
public final class CreateEpubProjectPlan {

    public static final String DEFAULT_LANGUAGE =
            "ko";

    public static final String DEFAULT_EPUB_VERSION =
            "3.0";

    private final String planId;

    private String projectName;

    private String folderName;

    private final String language;

    private final String epubVersion;

    private Status status;

    private final Instant createdAt;

    private Instant updatedAt;


    public CreateEpubProjectPlan(
            String projectName,
            String folderName) {

        this(
                UUID.randomUUID()
                        .toString(),
                projectName,
                folderName,
                DEFAULT_LANGUAGE,
                DEFAULT_EPUB_VERSION,
                Status.WAITING_PROJECT_APPROVAL,
                Instant.now(),
                Instant.now()
        );
    }


    public CreateEpubProjectPlan(
            String planId,
            String projectName,
            String folderName,
            String language,
            String epubVersion,
            Status status,
            Instant createdAt,
            Instant updatedAt) {

        this.planId =
                requireText(
                        planId,
                        "planId");

        this.projectName =
                requireText(
                        projectName,
                        "projectName");

        this.folderName =
                requireText(
                        folderName,
                        "folderName");

        this.language =
                requireText(
                        language,
                        "language");

        this.epubVersion =
                requireText(
                        epubVersion,
                        "epubVersion");

        this.status =
                Objects.requireNonNull(
                        status,
                        "status must not be null");

        this.createdAt =
                Objects.requireNonNull(
                        createdAt,
                        "createdAt must not be null");

        this.updatedAt =
                Objects.requireNonNull(
                        updatedAt,
                        "updatedAt must not be null");
    }


    public String getPlanId() {

        return planId;
    }


    public String getProjectName() {

        return projectName;
    }


    public String getFolderName() {

        return folderName;
    }


    public String getLanguage() {

        return language;
    }


    public String getEpubVersion() {

        return epubVersion;
    }


    public Status getStatus() {

        return status;
    }


    public Instant getCreatedAt() {

        return createdAt;
    }


    public Instant getUpdatedAt() {

        return updatedAt;
    }


    public void changeProjectName(
            String projectName) {

        ensureWaitingForApproval();

        this.projectName =
                requireText(
                        projectName,
                        "projectName");

        touch();
    }


    public void changeFolderName(
            String folderName) {

        ensureWaitingForApproval();

        this.folderName =
                requireText(
                        folderName,
                        "folderName");

        touch();
    }


    public void changeProject(
            String projectName,
            String folderName) {

        ensureWaitingForApproval();

        this.projectName =
                requireText(
                        projectName,
                        "projectName");

        this.folderName =
                requireText(
                        folderName,
                        "folderName");

        touch();
    }


    public void approve() {

        ensureStatus(
                Status.WAITING_PROJECT_APPROVAL);

        this.status =
                Status.PROJECT_APPROVED;

        touch();
    }


    public void markCreating() {

        ensureStatus(
                Status.PROJECT_APPROVED);

        this.status =
                Status.CREATING;

        touch();
    }


    public void markCreated() {

        ensureStatus(
                Status.CREATING);

        this.status =
                Status.CREATED;

        touch();
    }


    public void complete() {

        ensureStatus(
                Status.CREATED);

        this.status =
                Status.COMPLETED;

        touch();
    }


    public void cancel() {

        if (status == Status.COMPLETED) {

            throw new IllegalStateException(
                    "Completed project plan cannot be cancelled.");
        }

        if (status == Status.CANCELLED) {

            return;
        }

        this.status =
                Status.CANCELLED;

        touch();
    }


    public boolean isWaitingForApproval() {

        return status
                == Status.WAITING_PROJECT_APPROVAL;
    }


    public boolean isApproved() {

        return status
                == Status.PROJECT_APPROVED;
    }


    public boolean isCreating() {

        return status
                == Status.CREATING;
    }


    public boolean isCreated() {

        return status
                == Status.CREATED;
    }


    public boolean isCompleted() {

        return status
                == Status.COMPLETED;
    }


    public boolean isCancelled() {

        return status
                == Status.CANCELLED;
    }


    public boolean isEditable() {

        return status
                == Status.WAITING_PROJECT_APPROVAL;
    }


    private void ensureWaitingForApproval() {

        ensureStatus(
                Status.WAITING_PROJECT_APPROVAL);
    }


    private void ensureStatus(
            Status expectedStatus) {

        if (status != expectedStatus) {

            throw new IllegalStateException(
                    "Invalid EPUB project plan status. "
                            + "expected="
                            + expectedStatus
                            + ", actual="
                            + status);
        }
    }


    private void touch() {

        this.updatedAt =
                Instant.now();
    }


    private static String requireText(
            String value,
            String name) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    name
                            + " must not be blank.");
        }

        return value.trim();
    }


    public enum Status {

        WAITING_PROJECT_APPROVAL,

        PROJECT_APPROVED,

        CREATING,

        CREATED,

        COMPLETED,

        CANCELLED
    }
}