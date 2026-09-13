/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.w3c.dom.Document;

import kr.co.goms.gomsbook.ai.epub.model.EpubXhtmlCleanupFileResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubXhtmlCleanupResult;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

public class DefaultEpubXhtmlCleanupService implements EpubXhtmlCleanupService {

    public DefaultEpubXhtmlCleanupService() {
    }

    @Override
    public EpubXhtmlCleanupResult cleanupTextDirectory(Path projectRoot) {
        Path textDirectory = resolveTextDirectory(projectRoot);
        List<EpubXhtmlCleanupFileResult> results = findXhtmlFiles(textDirectory).stream().map(this::cleanup).toList();
        return EpubXhtmlCleanupResult.of(results);
    }

    @Override
    public EpubXhtmlCleanupResult cleanupFile(Path projectRoot, String fileName) {
        Path file = resolveTextDirectory(projectRoot).resolve(fileName).normalize();
        validateFile(file);
        return EpubXhtmlCleanupResult.of(List.of(cleanup(file)));
    }

    private EpubXhtmlCleanupFileResult cleanup(Path file) {
        String fileName = file.getFileName().toString();

        try {
            String before = Files.readString(file);
            Document document = EpubXmlUtil.readDocument(file);
            String after = EpubXmlUtil.xmlDocumentToString(document);

            if (before.equals(after)) return EpubXhtmlCleanupFileResult.unchanged(fileName);

            EpubXmlUtil.writeDocument(file, document);

            return EpubXhtmlCleanupFileResult.updated(fileName);

        } catch (Exception exception) {
            return EpubXhtmlCleanupFileResult.failed(fileName, safeMessage(exception));
        }
    }

    private List<Path> findXhtmlFiles(Path directory) {
        if (!Files.isDirectory(directory)) throw new IllegalStateException("Text directory not found: " + directory);

        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).filter(this::isXhtml).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read Text directory: " + directory, exception);
        }
    }

    private boolean isXhtml(Path file) {
        return file.getFileName().toString().toLowerCase().endsWith(".xhtml");
    }

    private Path resolveTextDirectory(Path projectRoot) {
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");
        return projectRoot.resolve("OEBPS").resolve("Text").normalize();
    }

    private void validateFile(Path file) {
        if (!Files.isRegularFile(file)) throw new IllegalStateException("XHTML file not found: " + file);
        if (!isXhtml(file)) throw new IllegalArgumentException("File is not XHTML: " + file);
    }

    private String safeMessage(Exception exception) {
        if (exception == null) return "알 수 없는 오류";
        if (exception.getMessage() == null || exception.getMessage().isBlank()) return exception.getClass().getSimpleName();
        return exception.getMessage();
    }
}