/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.UpdateEpubPartApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.part.DefaultEpubPartXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartPage;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartService;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartXhtmlGenerator;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class UpdateEpubPartApprovalHandler
        implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;

    private final EpubPartService epubPartService;

    private final EpubPartXhtmlGenerator xhtmlGenerator;

    private final EpubStylesheetResolver stylesheetResolver;

    private final Gson gson;


    public UpdateEpubPartApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubPartService epubPartService) {

        this(
                currentProjectProvider,
                epubPartService,
                new DefaultEpubPartXhtmlGenerator(),
                new EpubStylesheetResolver(),
                new Gson());
    }


    public UpdateEpubPartApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubPartService epubPartService,
            EpubPartXhtmlGenerator xhtmlGenerator,
            EpubStylesheetResolver stylesheetResolver,
            Gson gson) {

        if (currentProjectProvider == null) {

            throw new IllegalArgumentException(
                    "currentProjectProvider must not be null.");
        }

        if (epubPartService == null) {

            throw new IllegalArgumentException(
                    "epubPartService must not be null.");
        }

        if (xhtmlGenerator == null) {

            throw new IllegalArgumentException(
                    "xhtmlGenerator must not be null.");
        }

        if (stylesheetResolver == null) {

            throw new IllegalArgumentException(
                    "stylesheetResolver must not be null.");
        }

        if (gson == null) {

            throw new IllegalArgumentException(
                    "gson must not be null.");
        }

        this.currentProjectProvider =
                currentProjectProvider;

        this.epubPartService =
                epubPartService;

        this.xhtmlGenerator =
                xhtmlGenerator;

        this.stylesheetResolver =
                stylesheetResolver;

        this.gson =
                gson;
    }


    @Override
    public void execute(
            AgentApproval approval) {

        if (approval == null) {

            throw new IllegalArgumentException(
                    "approval must not be null.");
        }

        EpubProjectContext project =
                requireCurrentProject();

        validateProject(
                approval,
                project);

        validateApproval(
                approval);

        UpdateEpubPartApprovalPayload payload =
                parsePayload(
                        approval);

        validatePayload(
                payload);

        Path targetFile =
                resolveTargetFile(
                        project,
                        payload.getFileName());

        validateTargetFile(
                targetFile);

        String stylesheetHref =
                stylesheetResolver.resolveHref(
                        targetFile);

        String xhtml =
                xhtmlGenerator.generate(
                        payload.getPartNumber(),
                        payload.getTitle(),
                        stylesheetHref);

        EpubPartPage page =
                new EpubPartPage(
                        payload.getPartNumber(),
                        payload.getFileName(),
                        payload.getTitle(),
                        xhtml);

        epubPartService.update(
                page,
                project.getNavigationFile(),
                project.getTextDirectory());
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project =
                currentProjectProvider
                        .getCurrentProject();

        if (project == null) {

            throw new IllegalStateException(
                    "Current EPUB project is not available.");
        }

        if (project.getProjectRoot() == null) {

            throw new IllegalStateException(
                    "Current EPUB project root is not available.");
        }

        if (project.getTextDirectory() == null) {

            throw new IllegalStateException(
                    "Current EPUB Text directory is not available.");
        }

        if (project.getNavigationFile() == null) {

            throw new IllegalStateException(
                    "Current EPUB navigation file is not available.");
        }

        if (!Files.isDirectory(
                project.getTextDirectory())) {

            throw new IllegalStateException(
                    "Current EPUB Text directory does not exist: "
                            + project.getTextDirectory());
        }

        if (!Files.isRegularFile(
                project.getNavigationFile())) {

            throw new IllegalStateException(
                    "Current EPUB navigation file does not exist: "
                            + project.getNavigationFile());
        }

        return project;
    }


    private void validateProject(
            AgentApproval approval,
            EpubProjectContext project) {

        String approvalProjectId =
                trimToNull(
                        approval.getProjectId());

        if (approvalProjectId == null) {

            return;
        }

        String currentProjectId =
                resolveProjectId(
                        project);

        if (!approvalProjectId.equals(
                currentProjectId)) {

            throw new IllegalStateException(
                    "Approval project mismatch. approvalProjectId="
                            + approvalProjectId
                            + ", currentProjectId="
                            + currentProjectId);
        }
    }


    private void validateApproval(
            AgentApproval approval) {

        if (isBlank(
                approval.getFileName())) {

            throw new IllegalStateException(
                    "Approval fileName is not available.");
        }

        if (isBlank(
                approval.getContent())) {

            throw new IllegalStateException(
                    "Approval content is not available.");
        }

        String fileName =
                approval.getFileName().trim();

        validateFileName(
                fileName);
    }


    private UpdateEpubPartApprovalPayload parsePayload(
            AgentApproval approval) {

        try {

            UpdateEpubPartApprovalPayload payload =
                    gson.fromJson(
                            approval.getContent(),
                            UpdateEpubPartApprovalPayload.class);

            if (payload == null) {

                throw new IllegalStateException(
                        "EPUB part approval content is empty.");
            }

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                    "Failed to parse EPUB part approval content.",
                    exception);
        }
    }


    private void validatePayload(
            UpdateEpubPartApprovalPayload payload) {

        if (payload.getPartNumber() <= 0) {

            throw new IllegalArgumentException(
                    "partNumber must be greater than 0.");
        }

        if (isBlank(
                payload.getFileName())) {

            throw new IllegalArgumentException(
                    "fileName must not be blank.");
        }

        if (isBlank(
                payload.getTitle())) {

            throw new IllegalArgumentException(
                    "title must not be blank.");
        }

        validateFileName(
                payload.getFileName());

        String approvalFileName =
                payload
                        .getFileName()
                        .trim();

        if (!approvalFileName.equals(
                payload.getFileName().trim())) {

            throw new IllegalStateException(
                    "Invalid EPUB part fileName.");
        }
    }


    private Path resolveTargetFile(
            EpubProjectContext project,
            String fileName) {

        Path textDirectory =
                project
                        .getTextDirectory()
                        .toAbsolutePath()
                        .normalize();

        Path targetFile =
                textDirectory
                        .resolve(
                                fileName.trim())
                        .toAbsolutePath()
                        .normalize();

        if (!targetFile.startsWith(
                textDirectory)) {

            throw new IllegalStateException(
                    "EPUB part XHTML must be inside the EPUB Text directory.");
        }

        return targetFile;
    }


    private void validateTargetFile(
            Path targetFile) {

        if (!Files.exists(
                targetFile)) {

            throw new IllegalStateException(
                    "EPUB part XHTML does not exist: "
                            + targetFile);
        }

        if (!Files.isRegularFile(
                targetFile)) {

            throw new IllegalStateException(
                    "EPUB part XHTML path is not a file: "
                            + targetFile);
        }
    }


    private void validateFileName(
            String fileName) {

        if (isBlank(
                fileName)) {

            throw new IllegalArgumentException(
                    "fileName must not be blank.");
        }

        String normalized =
                fileName.trim();

        if (normalized.contains("/")
                || normalized.contains("\\")) {

            throw new IllegalArgumentException(
                    "fileName must contain only the XHTML file name.");
        }

        if (!normalized
                .toLowerCase()
                .endsWith(
                        ".xhtml")) {

            throw new IllegalArgumentException(
                    "EPUB part file must use the .xhtml extension.");
        }
    }


    private String resolveProjectId(
            EpubProjectContext project) {

        if (!isBlank(
                project.getProjectName())) {

            return project
                    .getProjectName()
                    .trim();
        }

        return project
                .getProjectRoot()
                .toAbsolutePath()
                .normalize()
                .toString();
    }


    private String trimToNull(
            String value) {

        if (value == null) {

            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }


    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }
}