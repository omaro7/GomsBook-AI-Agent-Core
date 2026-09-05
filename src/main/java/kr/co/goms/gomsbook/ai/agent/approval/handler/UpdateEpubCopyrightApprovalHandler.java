/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

/**
 * 승인된 EPUB 판권 페이지 수정 작업을 실행하는 Handler.
 */
public final class UpdateEpubCopyrightApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;

    public UpdateEpubCopyrightApprovalHandler(CurrentProjectProvider currentProjectProvider) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null");
    }

    @Override
    public void execute(AgentApproval approval) {

        Objects.requireNonNull(approval, "approval must not be null");

        EpubProjectContext project = requireCurrentProject();

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();

        if (!Files.exists(textDirectory)) {

            throw new IllegalStateException("EPUB Text directory does not exist: " + textDirectory);
        }

        if (!Files.isDirectory(textDirectory)) {

            throw new IllegalStateException("EPUB Text path is not a directory: " + textDirectory);
        }

        String fileName = requireText( approval.getFileName(), "approval.fileName");

        validateFileName(fileName);

        String content = requireText( approval.getContent(), "approval.content");

        Path copyrightFile = textDirectory.resolve(fileName).toAbsolutePath().normalize();

        if (!copyrightFile.startsWith(textDirectory)) {

            throw new IllegalArgumentException("Copyright XHTML path escapes Text directory: " + fileName);
        }

        if (!Files.exists(copyrightFile)) {

            throw new IllegalStateException("Copyright XHTML does not exist: " + copyrightFile);
        }

        if (!Files.isRegularFile(copyrightFile)) {

            throw new IllegalStateException("Copyright XHTML is not a file: " + copyrightFile);
        }

        try {

            Files.writeString(
                    copyrightFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to update copyright XHTML: " + copyrightFile, exception);
        }
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) {

            throw new IllegalStateException("Current EPUB project is not available.");
        }

        if (project.getTextDirectory() == null) {

            throw new IllegalStateException("Current EPUB Text directory is not available.");
        }

        return project;
    }

    private void validateFileName(String fileName) {

        if (!fileName.toLowerCase().endsWith(".xhtml")) {

            throw new IllegalArgumentException("Copyright file must be XHTML: " + fileName);
        }

        Path path = Path.of(fileName).normalize();

        if (path.isAbsolute() || path.getNameCount() != 1) {

            throw new IllegalArgumentException("Invalid copyright XHTML fileName: " + fileName);
        }
    }

    private String requireText(String value, String name) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(name  + " must not be blank.");
        }

        return value.trim();
    }
}