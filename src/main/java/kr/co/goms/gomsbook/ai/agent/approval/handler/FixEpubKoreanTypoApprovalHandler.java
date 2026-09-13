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
import java.util.Locale;
import java.util.Objects;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.FixEpubKoreanTypoApprovalPayload;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class FixEpubKoreanTypoApprovalHandler implements AgentApprovalHandler {

    private final CurrentProjectProvider currentProjectProvider;
    private final Gson gson;

    public FixEpubKoreanTypoApprovalHandler(CurrentProjectProvider currentProjectProvider) {
        this(currentProjectProvider, new Gson());
    }

    public FixEpubKoreanTypoApprovalHandler(CurrentProjectProvider currentProjectProvider, Gson gson) {

        this.currentProjectProvider = Objects.requireNonNull(currentProjectProvider, "currentProjectProvider must not be null.");
        this.gson = Objects.requireNonNull(gson, "gson must not be null.");
    }

    @Override
    public void execute(AgentApproval approval) {

        Objects.requireNonNull(approval, "approval must not be null.");

        EpubProjectContext project = requireCurrentProject();
        FixEpubKoreanTypoApprovalPayload payload = parsePayload(approval);

        validateApprovalFileName(approval, payload);
        validateFileName(payload.fileName());

        Path textDirectory = project.getTextDirectory().toAbsolutePath().normalize();
        Path targetFile = textDirectory.resolve(payload.fileName()).toAbsolutePath().normalize();

        validateTargetPath(textDirectory, targetFile);

        String content = readContent(targetFile);

        validatePayloadOffsets(content, payload);
        validateOriginal(content, payload);

        String updatedContent = replace(content, payload);

        writeContent(targetFile, updatedContent);
    }

    private EpubProjectContext requireCurrentProject() {

        EpubProjectContext project = currentProjectProvider.getCurrentProject();

        if (project == null) throw new IllegalStateException("Current EPUB project is not available.");
        if (project.getProjectRoot() == null) throw new IllegalStateException("Current EPUB project root is not available.");
        if (project.getTextDirectory() == null) throw new IllegalStateException("Current EPUB Text directory is not available.");
        if (!Files.isDirectory(project.getTextDirectory())) throw new IllegalStateException("Current EPUB Text directory does not exist: " + project.getTextDirectory());

        return project;
    }

    private FixEpubKoreanTypoApprovalPayload parsePayload(AgentApproval approval) {

        String content = approval.getContent();

        if (content == null || content.isBlank()) throw new IllegalStateException("Approval content is not available.");

        try {

            FixEpubKoreanTypoApprovalPayload payload = gson.fromJson(content, FixEpubKoreanTypoApprovalPayload.class);

            if (payload == null) throw new IllegalStateException("EPUB Korean typo approval payload is empty.");

            return payload;

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUB Korean typo approval payload.", exception);
        }
    }

    private void validateApprovalFileName(AgentApproval approval, FixEpubKoreanTypoApprovalPayload payload) {

        String approvalFileName = approval.getFileName();

        if (approvalFileName == null || approvalFileName.isBlank()) throw new IllegalStateException("Approval fileName is not available.");

        if (!approvalFileName.trim().equals(payload.fileName())) {
            throw new IllegalStateException(
                    "Approval file mismatch. approvalFileName="
                            + approvalFileName
                            + ", payloadFileName="
                            + payload.fileName());
        }
    }

    private void validateFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("fileName must contain only the XHTML file name.");

        String normalized = fileName.toLowerCase(Locale.ROOT);

        if (!normalized.endsWith(".xhtml") && !normalized.endsWith(".html")) {
            throw new IllegalArgumentException("fileName must use the .xhtml or .html extension.");
        }
    }

    private void validateTargetPath(Path textDirectory, Path targetFile) {

        if (!targetFile.startsWith(textDirectory)) throw new IllegalArgumentException("Target XHTML file must be inside the EPUB Text directory.");
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB XHTML file was not found: " + targetFile);
        if (!Files.isReadable(targetFile)) throw new IllegalStateException("EPUB XHTML file is not readable: " + targetFile);
        if (!Files.isWritable(targetFile)) throw new IllegalStateException("EPUB XHTML file is not writable: " + targetFile);
    }

    private String readContent(Path targetFile) {

        try {

            return Files.readString(targetFile, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to read EPUB XHTML file: " + targetFile, exception);
        }
    }

    private void validatePayloadOffsets(String content, FixEpubKoreanTypoApprovalPayload payload) {

        if (payload.startOffset() < 0) throw new IllegalStateException("startOffset must be greater than or equal to 0.");
        if (payload.endOffset() <= payload.startOffset()) throw new IllegalStateException("endOffset must be greater than startOffset.");
        if (payload.endOffset() > content.length()) throw new IllegalStateException("Typo offset exceeds the current XHTML content length.");

        int expectedLength = payload.original().length();
        int actualLength = payload.endOffset() - payload.startOffset();

        if (expectedLength != actualLength) {
            throw new IllegalStateException(
                    "Typo offset length does not match the original text length. expected="
                            + expectedLength
                            + ", actual="
                            + actualLength);
        }
    }

    private void validateOriginal(String content, FixEpubKoreanTypoApprovalPayload payload) {

        String current = content.substring(payload.startOffset(), payload.endOffset());

        if (!current.equals(payload.original())) {
            throw new IllegalStateException(
                    "The XHTML content has changed since the typo was checked. expected="
                            + payload.original()
                            + ", current="
                            + current
                            + ", startOffset="
                            + payload.startOffset()
                            + ", endOffset="
                            + payload.endOffset());
        }
    }

    private String replace(String content, FixEpubKoreanTypoApprovalPayload payload) {

        return content.substring(0, payload.startOffset())
                + payload.suggestion()
                + content.substring(payload.endOffset());
    }

    private void writeContent(Path targetFile, String content) {

        try {

            Files.writeString(targetFile, content, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to write EPUB XHTML file: " + targetFile, exception);
        }
    }
}