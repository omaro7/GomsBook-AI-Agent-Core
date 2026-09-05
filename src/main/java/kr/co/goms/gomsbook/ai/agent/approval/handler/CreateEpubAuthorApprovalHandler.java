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
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorPage;
import kr.co.goms.gomsbook.ai.epub.generation.author.EpubAuthorService;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.agent.approval.payload.EpubAuthorApprovalPayload;

public final class CreateEpubAuthorApprovalHandler implements AgentApprovalHandler {

    private static final String DEFAULT_FILE_NAME = "author.xhtml";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubAuthorService authorService;
    private final Gson gson;

    public CreateEpubAuthorApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubAuthorService authorService) {

        this(currentProjectProvider, authorService, new Gson());
    }

    public CreateEpubAuthorApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubAuthorService authorService,
            Gson gson) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.authorService = Objects.requireNonNull(authorService, "authorService must not be null");
        this.gson = Objects.requireNonNull(gson, "gson must not be null");
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);
        validateApproval(approval);

        EpubAuthorPage page = parsePage(approval);

        validateAuthorNotExists(project, page);

        authorService.generate(
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

        if (!fileName.toLowerCase().endsWith(".xhtml")) {
            throw new IllegalStateException("Approval file must be an XHTML file.");
        }

        Path fileNamePath = Path.of(fileName).normalize();

        if (fileNamePath.isAbsolute() || fileNamePath.getNameCount() != 1) {
            throw new IllegalStateException("Invalid author XHTML fileName: " + fileName);
        }
    }

    private EpubAuthorPage parsePage(AgentApproval approval) {

        EpubAuthorApprovalPayload payload;

        try {

            payload = gson.fromJson(
                    approval.getContent(),
                    EpubAuthorApprovalPayload.class);

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                    "Failed to parse EPUB author approval content.",
                    exception);
        }

        if (payload == null) throw new IllegalStateException("EPUB author approval content is empty.");

        EpubAuthorPage page = new EpubAuthorPage();

        page.setFileName(defaultIfBlank(payload.getFileName(), approval.getFileName()));
        page.setAuthorName(trimToNull(payload.getAuthorName()));
        page.setIntroduction(trimToNull(payload.getIntroduction()));
        page.setProfile(trimToNull(payload.getProfile()));
        page.setCareers(payload.getCareers());
        page.setImageFileName(trimToNull(payload.getImageFileName()));
        page.setImageAlt(trimToNull(payload.getImageAlt()));

        validatePage(page);

        return page;
    }

    private void validatePage(EpubAuthorPage page) {

        if (isBlank(page.getFileName())) page.setFileName(DEFAULT_FILE_NAME);
        if (isBlank(page.getAuthorName())) throw new IllegalStateException("Author name is not available.");
    }

    private void validateAuthorNotExists(
            EpubProjectContext project,
            EpubAuthorPage page) {

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(page.getFileName()).normalize();

        if (!targetFile.startsWith(textDirectory)) {
            throw new IllegalStateException("Author XHTML must be inside the EPUB Text directory.");
        }

        if (Files.exists(targetFile)) {
            throw new IllegalStateException("Author XHTML already exists: " + targetFile);
        }
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (!isBlank(project.getProjectName())) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String defaultIfBlank(String value, String defaultValue) {

        String normalized = trimToNull(value);

        return normalized != null ? normalized : defaultValue;
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