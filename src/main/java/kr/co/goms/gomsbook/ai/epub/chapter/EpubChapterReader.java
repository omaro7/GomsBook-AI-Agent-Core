/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.chapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EpubChapterReader {

    public String read(String fileName, Path textDirectory) {

        validateArguments(fileName, textDirectory);

        Path normalizedTextDirectory = normalizeTextDirectory(textDirectory);
        Path targetFile = resolveTargetFile(normalizedTextDirectory, fileName);

        validateTargetFile(normalizedTextDirectory, targetFile);
        validateChapterExists(targetFile);

        return readXhtml(targetFile);
    }

    private void validateArguments(String fileName, Path textDirectory) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.trim().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");
    }

    private Path normalizeTextDirectory(Path textDirectory) {
        return textDirectory.toAbsolutePath().normalize();
    }

    private Path resolveTargetFile(Path textDirectory, String fileName) {
        return textDirectory.resolve(fileName.trim()).normalize();
    }

    private void validateTargetFile(Path textDirectory, Path targetFile) {

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("Chapter XHTML must be inside the EPUB Text directory.");

        Path fileName = targetFile.getFileName();

        if (fileName == null || !fileName.toString().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("Chapter file must be XHTML.");
    }

    private void validateChapterExists(Path targetFile) {

        if (!Files.exists(targetFile)) throw new IllegalStateException("EPUB chapter does not exist: " + targetFile + ".");
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB chapter path is not a file: " + targetFile);
    }

    private String readXhtml(Path targetFile) {

        try {

            return Files.readString(targetFile, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to read EPUB chapter XHTML: " + targetFile, exception);
        }
    }
}