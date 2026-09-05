/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.epub.copyright.EpubCopyrightLocator;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

/**
 * 승인된 EPUB 판권 페이지 생성 작업을 실행하는 Handler.
 */
public final class CreateEpubCopyrightApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubCopyrightLocator copyrightLocator;

    public CreateEpubCopyrightApprovalHandler(CurrentProjectProvider currentProjectProvider) {
        this(currentProjectProvider, new EpubCopyrightLocator());
    }

    public CreateEpubCopyrightApprovalHandler(
            CurrentProjectProvider currentProjectProvider,
            EpubCopyrightLocator copyrightLocator) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
        this.copyrightLocator = Objects.requireNonNull(copyrightLocator, "copyrightLocator must not be null");
    }

    @Override
    public void execute(AgentApproval approval) {

        Objects.requireNonNull(approval, "approval must not be null");

        EpubProjectContext project = requireCurrentProject();

        validateProject(approval, project);

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();

        if (!Files.exists(textDirectory)) throw new IllegalStateException("EPUB Text directory does not exist: " + textDirectory);
        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("EPUB Text path is not a directory: " + textDirectory);

        validateCopyrightNotExists(textDirectory);

        String fileName = requireText(approval.getFileName(), "approval.fileName");
        String content = requireText(approval.getContent(), "approval.content");

        validateFileName(fileName);

        Path copyrightFile = textDirectory.resolve(fileName).toAbsolutePath().normalize();

        if (!copyrightFile.startsWith(textDirectory)) throw new IllegalArgumentException("Copyright XHTML path escapes Text directory: " + fileName);
        if (Files.exists(copyrightFile)) throw new IllegalStateException("Copyright XHTML already exists: " + copyrightFile);

        try {

            Files.writeString(
                    copyrightFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to create copyright XHTML: " + copyrightFile, exception);
        }
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");

        return project;
    }

    private void validateProject(
            AgentApproval approval,
            EpubProjectContext project) {

        String approvalProjectId = trimToNull(approval.getProjectId());

        if (approvalProjectId == null) return;

        String currentProjectId = resolveProjectId(project);

        if (!approvalProjectId.equals(currentProjectId)) {
            throw new IllegalStateException("Approval project mismatch. approvalProjectId=" + approvalProjectId + ", currentProjectId=" + currentProjectId);
        }
    }

    private void validateCopyrightNotExists(Path textDirectory) {

        copyrightLocator.find(textDirectory).ifPresent(path -> {
            throw new IllegalStateException("Copyright page already exists: " + path);
        });
    }

    private void validateFileName(String fileName) {

        if (!fileName.toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("Copyright file must be XHTML: " + fileName);

        Path path = Path.of(fileName).normalize();

        if (path.isAbsolute() || path.getNameCount() != 1) throw new IllegalArgumentException("Invalid copyright XHTML fileName: " + fileName);
    }

    private String resolveProjectId(EpubProjectContext project) {

        if (project.getProjectName() != null && !project.getProjectName().isBlank()) return project.getProjectName().trim();

        return project.getProjectRoot().toAbsolutePath().normalize().toString();
    }

    private String requireText(String value, String name) {

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }

    private String trimToNull(String value) {

        if (value == null) return null;

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}