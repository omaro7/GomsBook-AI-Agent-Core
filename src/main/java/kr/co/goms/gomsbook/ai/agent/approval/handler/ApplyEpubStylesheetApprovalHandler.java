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
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.ApplyEpubStylesheetApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public class ApplyEpubStylesheetApprovalHandler implements AgentApprovalHandler {

    private static final String STYLES_DIRECTORY = "Styles";
    private static final String STYLESHEET_FILE_NAME = "style1.css";

    private final CurrentProjectProvider currentProjectProvider;
    private final Gson gson;

    public ApplyEpubStylesheetApprovalHandler(CurrentProjectProvider currentProjectProvider) {
        this(currentProjectProvider, new Gson());
    }

    public ApplyEpubStylesheetApprovalHandler(CurrentProjectProvider currentProjectProvider, Gson gson) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.gson = gson;
    }

    @Override
    public void execute(AgentApproval approval) {

        if (approval == null) throw new IllegalArgumentException("approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        ApplyEpubStylesheetApprovalPayload payload = parsePayload(approval);

        Path sourceFile = resolveSourceFile(payload);
        Path targetDirectory = resolveTargetDirectory(project);
        Path targetFile = resolveTargetFile(targetDirectory, payload);

        applyStylesheet(sourceFile, targetFile);
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getPackageDocument() == null) throw new IllegalStateException("Current EPUB package document is not available.");
        if (!Files.isRegularFile(project.getPackageDocument())) throw new IllegalStateException("Current EPUB package document does not exist: " + project.getPackageDocument());

        return project;
    }

    private ApplyEpubStylesheetApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is empty.");

        try {

            ApplyEpubStylesheetApprovalPayload payload = gson.fromJson(content, ApplyEpubStylesheetApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("Approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse EPUB stylesheet approval payload.", exception);
        }
    }

    private Path resolveSourceFile(ApplyEpubStylesheetApprovalPayload payload) {

        if (payload == null) throw new IllegalArgumentException("ApplyEpubStylesheetApprovalPayload must not be null.");
        if (isBlank(payload.getSourcePath())) throw new IllegalArgumentException("sourcePath must not be blank.");

        Path sourceFile = Path.of(payload.getSourcePath()).toAbsolutePath().normalize();

        if (!Files.exists(sourceFile)) throw new IllegalStateException("Stylesheet source file does not exist: " + sourceFile);
        if (!Files.isRegularFile(sourceFile)) throw new IllegalStateException("Stylesheet source path is not a file: " + sourceFile);

        String fileName = sourceFile.getFileName().toString();

        if (!STYLESHEET_FILE_NAME.equalsIgnoreCase(fileName)) {
            throw new IllegalArgumentException("Stylesheet source file must be named " + STYLESHEET_FILE_NAME + ": " + sourceFile);
        }

        return sourceFile;
    }

    private Path resolveTargetDirectory(EpubProjectContext project) {

        Path packageDocument = project.getPackageDocument().toAbsolutePath().normalize();
        Path contentRoot = packageDocument.getParent();

        if (contentRoot == null) throw new IllegalStateException("Unable to resolve EPUB content root: " + packageDocument);

        return contentRoot.resolve(STYLES_DIRECTORY).toAbsolutePath().normalize();
    }

    private Path resolveTargetFile(Path targetDirectory, ApplyEpubStylesheetApprovalPayload payload) {

        if (payload == null) throw new IllegalArgumentException("ApplyEpubStylesheetApprovalPayload must not be null.");
        if (isBlank(payload.getTargetFileName())) throw new IllegalArgumentException("targetFileName must not be blank.");

        String targetFileName = payload.getTargetFileName().trim();

        if (!STYLESHEET_FILE_NAME.equalsIgnoreCase(targetFileName)) {
            throw new IllegalArgumentException("targetFileName must be " + STYLESHEET_FILE_NAME + ".");
        }

        Path targetFile = targetDirectory.resolve(targetFileName).normalize();

        if (!targetFile.startsWith(targetDirectory)) throw new IllegalStateException("Stylesheet must be inside the EPUB Styles directory.");

        return targetFile;
    }

    private void applyStylesheet(Path sourceFile, Path targetFile) {

        try {

            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to apply EPUB stylesheet: " + targetFile, exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}