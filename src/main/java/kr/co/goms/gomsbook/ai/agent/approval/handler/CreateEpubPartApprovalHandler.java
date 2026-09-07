/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.file.Path;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.CreateEpubPartApprovalPayload;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartPage;
import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class CreateEpubPartApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubPartService partService;
    private final Gson gson;

    public CreateEpubPartApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubPartService partService) {

        this(currentProjectProvider, partService, new Gson());
    }

    public CreateEpubPartApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubPartService partService,
            Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (partService == null) throw new IllegalArgumentException("partService must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.partService = partService;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validateApproval(approval);

        CreateEpubPartApprovalPayload payload = parsePayload(approval);

        validatePayload(payload);

        EpubPartPage page = createPage(payload);

        partService.generate(
                page,
                project.getPackageDocument(),
                project.getNavigationFile(),
                project.getTextDirectory());
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");
        if (project.getNavigationFile() == null) throw new IllegalStateException("Current EPUB navigation file is not available.");

        return project;
    }

    private void validateProject(
            AgentApproval approval,
            EpubProjectContext project) {

        String approvalProjectId = trimToNull(approval.getProjectId());

        if (approvalProjectId == null) return;

        String currentProjectId = resolveProjectId(project);

        if (!approvalProjectId.equals(currentProjectId)) {
            throw new IllegalStateException(
                    "Approval project mismatch. approvalProjectId="
                            + approvalProjectId
                            + ", currentProjectId="
                            + currentProjectId);
        }
    }

    private void validateApproval(AgentApproval approval) {

        if (isBlank(approval.getFileName())) throw new IllegalStateException("Approval fileName is not available.");
        if (isBlank(approval.getContent())) throw new IllegalStateException("Approval content is not available.");

        String fileName = approval.getFileName().trim();

        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalStateException("Approval file must be an XHTML file.");

        Path fileNamePath = Path.of(fileName).normalize();

        if (fileNamePath.isAbsolute() || fileNamePath.getNameCount() != 1) {
            throw new IllegalStateException("Invalid part XHTML fileName: " + fileName);
        }
    }

    private CreateEpubPartApprovalPayload parsePayload(AgentApproval approval) {

        try {

            CreateEpubPartApprovalPayload payload = gson.fromJson(approval.getContent(), CreateEpubPartApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB part approval content is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB part approval content.", exception);
        }
    }

    private void validatePayload(CreateEpubPartApprovalPayload payload) {

        if (payload == null) throw new IllegalArgumentException("payload must not be null.");
        if (isBlank(payload.getFileName())) throw new IllegalStateException("Part fileName is not available.");
        if (payload.getPartNumber() <= 0) throw new IllegalStateException("Part number must be greater than 0.");
        if (isBlank(payload.getTitle())) throw new IllegalStateException("Part title is not available.");
        if (isBlank(payload.getXhtml())) throw new IllegalStateException("Part XHTML content is not available.");

        String expectedFileName = String.format("part%02d.xhtml", payload.getPartNumber());

        if (!expectedFileName.equals(payload.getFileName().trim())) {
            throw new IllegalStateException(
                    "Part fileName does not match partNumber. expected="
                            + expectedFileName
                            + ", actual="
                            + payload.getFileName());
        }
    }

    private EpubPartPage createPage(CreateEpubPartApprovalPayload payload) {

        return new EpubPartPage(
                payload.getPartNumber(),
                payload.getFileName(),
                payload.getTitle(),
                payload.getXhtml());
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (!isBlank(project.getProjectName())) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();
    }
}