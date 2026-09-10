/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.publish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.Instant;

public class DefaultEpubPublisher implements EpubPublisher {

    private static final String MIMETYPE_FILE_NAME = "mimetype";
    private static final String MIMETYPE_VALUE = "application/epub+zip";
    private static final String PROJECT_FILE_NAME = ".project";
    private static final String EPUB_EXTENSION = ".epub";
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path projectRoot;
    private final Path publishDirectory;
    private final EpubArtifactFingerprintService fingerprintService;

    public DefaultEpubPublisher(
            Path projectRoot,
            Path publishDirectory,
            EpubArtifactFingerprintService fingerprintService) {

        if (fingerprintService == null) {

            throw new IllegalArgumentException("fingerprintService must not be null.");
        }

        this.projectRoot = projectRoot;
        this.publishDirectory = publishDirectory;
        this.fingerprintService = fingerprintService;
    }
    
    @Override
    public PublishEpubResult publish() throws IOException {

        validateProjectRoot(projectRoot);
        validatePublishDirectory(publishDirectory);

        String projectId = projectRoot.getFileName().toString();
        Path projectPublishDirectory = publishDirectory.resolve(projectId).toAbsolutePath().normalize();

        Files.createDirectories(projectPublishDirectory);

        String timestamp = LocalDateTime.now().format(FILE_NAME_FORMATTER);
        String epubFileName = projectId + "-" + timestamp + EPUB_EXTENSION;
        Path publishEpubPath = projectPublishDirectory.resolve(epubFileName);

        createEpub(projectRoot, publishEpubPath);

        EpubArtifactFingerprint fingerprint = fingerprintService.calculate(publishEpubPath);

        return new PublishEpubResult(
                publishEpubPath,
                fingerprint,
                Instant.now());
    }

    private void createEpub(
            Path sourceDirectory,
            Path publishEpubPath) throws IOException {

        Path tempZip = Files.createTempFile("gomsbook-epub-", ".zip");

        try {

            writeEpub(sourceDirectory, tempZip);
            Files.copy(tempZip, publishEpubPath, StandardCopyOption.REPLACE_EXISTING);

        } finally {

            Files.deleteIfExists(tempZip);
        }
    }

    private void writeEpub(
            Path sourceDirectory,
            Path tempZip) throws IOException {

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(tempZip))) {

            writeMimetype(zipOutputStream);
            writeProjectFiles(sourceDirectory, zipOutputStream);
        }
    }

    private void writeMimetype(
            ZipOutputStream zipOutputStream) throws IOException {

        byte[] mimeBytes = MIMETYPE_VALUE.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();

        crc.update(mimeBytes);

        ZipEntry entry = new ZipEntry(MIMETYPE_FILE_NAME);

        entry.setMethod(ZipEntry.STORED);
        entry.setSize(mimeBytes.length);
        entry.setCompressedSize(mimeBytes.length);
        entry.setCrc(crc.getValue());

        zipOutputStream.putNextEntry(entry);
        zipOutputStream.write(mimeBytes);
        zipOutputStream.closeEntry();
    }

    private void writeProjectFiles(
            Path sourceDirectory,
            ZipOutputStream zipOutputStream) throws IOException {

        List<Path> paths;

        try (Stream<Path> stream = Files.walk(sourceDirectory)) {

            paths = stream.filter(Files::isRegularFile).sorted().toList();
        }

        for (Path path : paths) {

            if (!shouldInclude(sourceDirectory, path)) {

                continue;
            }

            writeFile(sourceDirectory, path, zipOutputStream);
        }
    }

    private void writeFile(
            Path sourceDirectory,
            Path path,
            ZipOutputStream zipOutputStream) throws IOException {

        String entryName = toEntryName(sourceDirectory, path);
        ZipEntry entry = new ZipEntry(entryName);

        zipOutputStream.putNextEntry(entry);
        Files.copy(path, zipOutputStream);
        zipOutputStream.closeEntry();
    }

    private boolean shouldInclude(
            Path sourceDirectory,
            Path path) {

        String entryName = toEntryName(sourceDirectory, path);

        return !MIMETYPE_FILE_NAME.equals(entryName) && !PROJECT_FILE_NAME.equals(entryName);
    }

    private String toEntryName(
            Path sourceDirectory,
            Path path) {

        return sourceDirectory.relativize(path).toString().replace("\\", "/");
    }

    private void validateProjectRoot(
            Path projectRoot) {

        if (projectRoot == null) {

            throw new IllegalArgumentException("projectRoot must not be null.");
        }

        if (!Files.exists(projectRoot)) {

            throw new IllegalArgumentException("EPUB project root does not exist: " + projectRoot);
        }

        if (!Files.isDirectory(projectRoot)) {

            throw new IllegalArgumentException("EPUB project root must be a directory: " + projectRoot);
        }
    }

    private void validatePublishDirectory(
            Path publishDirectory) {

        if (publishDirectory == null) {

            throw new IllegalArgumentException("publishDirectory must not be null.");
        }
    }
}