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
 * 기본 XHTML 생성 승인 Handler.
 */
public final class CreateBasicXhtmlApprovalHandler
        implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;


    public CreateBasicXhtmlApprovalHandler(
            CurrentProjectProvider currentProjectProvider) {

        this.currentProjectProvider =
                Objects.requireNonNull(
                        currentProjectProvider,
                        "currentProjectProvider must not be null");
    }


    @Override
    public void execute(
            AgentApproval approval) {

        Objects.requireNonNull(
                approval,
                "approval must not be null");


        EpubProjectContext project =
                requireCurrentProject();


        Path textDirectory =
                project.getTextDirectory()
                        .toAbsolutePath()
                        .normalize();


        if (!Files.exists(
                textDirectory)) {

            throw new IllegalStateException(
                    "EPUB Text directory does not exist: "
                            + textDirectory);
        }


        if (!Files.isDirectory(
                textDirectory)) {

            throw new IllegalStateException(
                    "EPUB Text path is not a directory: "
                            + textDirectory);
        }


        String fileName =
                requireText(
                        approval.getFileName(),
                        "approval.fileName");


        validateXhtmlFileName(
                fileName);


        String content =
                requireText(
                        approval.getContent(),
                        "approval.content");


        Path targetFile =
                textDirectory
                        .resolve(
                                fileName)
                        .toAbsolutePath()
                        .normalize();


        if (!targetFile.startsWith(
                textDirectory)) {

            throw new IllegalArgumentException(
                    "XHTML file path escapes Text directory: "
                            + fileName);
        }


        if (Files.exists(
                targetFile)) {

            throw new IllegalStateException(
                    "XHTML file already exists: "
                            + targetFile);
        }


        try {

            Files.writeString(
                    targetFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);


        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to create XHTML file: "
                            + targetFile,
                    exception);
        }
    }


    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project =
                currentProjectProvider
                        .getCurrentProject();


        if (project == null) {

            throw new IllegalStateException(
                    "Current EPUB project is not available.");
        }


        if (project.getTextDirectory() == null) {

            throw new IllegalStateException(
                    "Current EPUB Text directory is not available.");
        }


        return project;
    }


    private void validateXhtmlFileName(
            String fileName) {

        String value =
                fileName.trim();


        if (value.contains("/")
                || value.contains("\\")) {

            throw new IllegalArgumentException(
                    "XHTML fileName must not contain path separators: "
                            + value);
        }


        if (".".equals(value)
                || "..".equals(value)) {

            throw new IllegalArgumentException(
                    "Invalid XHTML fileName: "
                            + value);
        }


        if (!value.toLowerCase()
                .endsWith(
                        ".xhtml")) {

            throw new IllegalArgumentException(
                    "XHTML fileName must end with .xhtml: "
                            + value);
        }


        if (value.contains(":")
                || value.contains("*")
                || value.contains("?")
                || value.contains("\"")
                || value.contains("<")
                || value.contains(">")
                || value.contains("|")) {

            throw new IllegalArgumentException(
                    "XHTML fileName contains invalid characters: "
                            + value);
        }
    }


    private String requireText(
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
}