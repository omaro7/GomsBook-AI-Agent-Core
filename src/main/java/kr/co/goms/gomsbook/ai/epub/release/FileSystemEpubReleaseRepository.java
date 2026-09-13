/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubReleaseStatus;


/**
 * 파일 시스템 기반 EPUB Release Repository 입니다.
 *
 * <p>Publish Root 아래 프로젝트별 디렉터리를 사용합니다.</p>
 *
 * <pre>
 * Publish Root
 * ├─ epub-ai-agent
 * │  └─ releases
 * │     ├─ epub-ai-agent-1.0.0-20260913072300.json
 * │     └─ epub-ai-agent-1.0.1-20260914010100.json
 * └─ lunchwork_seoul
 *    └─ releases
 *       └─ lunchwork_seoul-1.0.0-20260915090000.json
 * </pre>
 */
public final class FileSystemEpubReleaseRepository implements EpubReleaseRepository {


    private static final String RELEASE_DIRECTORY_NAME = "releases";
    private static final String JSON_EXTENSION = ".json";
    private static final String TEMP_EXTENSION = ".tmp";


    private final Path publishDirectory;
    private final Gson gson;


    public FileSystemEpubReleaseRepository(
            Path publishDirectory) {

        this(
                publishDirectory,
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
        );
    }


    public FileSystemEpubReleaseRepository(Path publishDirectory, Gson gson) {

        if (publishDirectory == null) throw new IllegalArgumentException("publishDirectory must not be null.");
        if (gson == null) throw new IllegalArgumentException("gson must not be null.");

        this.publishDirectory = publishDirectory.toAbsolutePath().normalize();
        this.gson = gson;
    }


    @Override
    public EpubReleaseItem save(EpubReleaseItem item) {

        validateItem(item);

        Path releaseDirectory = resolveReleaseDirectory(item.getProjectId());

        try {

            Files.createDirectories(releaseDirectory);

            Path releaseFile = resolveReleaseFile(releaseDirectory, item);

            if (Files.exists(releaseFile)) throw new IllegalStateException("EPUB release already exists: " + releaseFile);

            EpubReleaseRepositoryItem repositoryItem = toRepositoryItem(item);

            String json = gson.toJson(repositoryItem);

            writeAtomic(releaseFile, json);

            return item;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to save EPUB release: " + item.getReleaseId(),
                    exception
            );
        }
    }


    @Override
    public Optional<EpubReleaseItem> findByReleaseId(String releaseId) {

        String normalizedReleaseId = requireText(releaseId, "releaseId");

        if (!Files.isDirectory(publishDirectory)) return Optional.empty();

        try (Stream<Path> projectStream = Files.list(publishDirectory)) {

            List<Path> projectDirectories = projectStream
                    .filter(Files::isDirectory)
                    .toList();

            for (Path projectDirectory : projectDirectories) {

                Path releaseDirectory = projectDirectory
                        .resolve(RELEASE_DIRECTORY_NAME)
                        .normalize();

                if (!Files.isDirectory(releaseDirectory)) continue;

                Optional<EpubReleaseItem> release = findByReleaseId(
                        releaseDirectory,
                        normalizedReleaseId
                );

                if (release.isPresent()) return release;
            }

            return Optional.empty();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to find EPUB release: " + normalizedReleaseId,
                    exception
            );
        }
    }


    @Override
    public Optional<EpubReleaseItem> findByProjectIdAndVersion(String projectId, String version) {

        String normalizedProjectId = requireText(projectId, "projectId");
        String normalizedVersion = requireText(version, "version");

        return findAllByProjectId(normalizedProjectId)
                .stream()
                .filter(item -> normalizedVersion.equals(item.getVersion()))
                .findFirst();
    }


    @Override
    public Optional<EpubReleaseItem> findLatestByProjectId(String projectId) {

        return findAllByProjectId(projectId)
                .stream()
                .findFirst();
    }


    @Override
    public List<EpubReleaseItem> findAllByProjectId(String projectId) {

        String normalizedProjectId = requireText(projectId, "projectId");

        Path releaseDirectory = resolveReleaseDirectory(normalizedProjectId);

        if (!Files.isDirectory(releaseDirectory)) return List.of();

        try (Stream<Path> stream = Files.list(releaseDirectory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isJsonFile)
                    .map(this::read)
                    .filter(item -> normalizedProjectId.equals(item.getProjectId()))
                    .sorted(Comparator.comparing(EpubReleaseItem::getReleasedAt).reversed())
                    .toList();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB releases: " + normalizedProjectId,
                    exception
            );
        }
    }


    private Optional<EpubReleaseItem> findByReleaseId(Path releaseDirectory, String releaseId) {

        try (Stream<Path> stream = Files.list(releaseDirectory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isJsonFile)
                    .map(this::read)
                    .filter(item -> releaseId.equals(item.getReleaseId()))
                    .findFirst();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB release directory: " + releaseDirectory,
                    exception
            );
        }
    }


    private EpubReleaseItem read(Path releaseFile) {

        try {

            String json = Files.readString(
                    releaseFile,
                    StandardCharsets.UTF_8
            );

            EpubReleaseRepositoryItem repositoryItem = gson.fromJson(json, EpubReleaseRepositoryItem.class);

            if (repositoryItem == null) throw new IllegalStateException("EPUB release JSON is empty: " + releaseFile);

            return toReleaseItem(repositoryItem);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB release: " + releaseFile,
                    exception
            );

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                    "Invalid EPUB release JSON: " + releaseFile,
                    exception
            );
        }
    }


    private EpubReleaseRepositoryItem toRepositoryItem(EpubReleaseItem item) {

        return new EpubReleaseRepositoryItem(
                item.getReleaseId(),
                item.getProjectId(),
                item.getVersion(),
                item.getEpubPath().toString(),
                item.getSha256(),
                item.getFileSize(),
                item.getPublishedAt().toString(),
                item.getReleasedAt().toString(),
                item.getStatus().name()
        );
    }


    private EpubReleaseItem toReleaseItem(EpubReleaseRepositoryItem item) {

        String releaseId = requireText(item.getReleaseId(), "releaseId");
        String projectId = requireText(item.getProjectId(), "projectId");
        String version = requireText(item.getVersion(), "version");
        String epubPath = requireText(item.getEpubPath(), "epubPath");
        String sha256 = requireText(item.getSha256(), "sha256");
        String publishedAt = requireText(item.getPublishedAt(), "publishedAt");
        String releasedAt = requireText(item.getReleasedAt(), "releasedAt");
        String status = requireText(item.getStatus(), "status");

        return new EpubReleaseItem(
                releaseId,
                projectId,
                version,
                Path.of(epubPath).toAbsolutePath().normalize(),
                sha256,
                item.getFileSize(),
                Instant.parse(publishedAt),
                Instant.parse(releasedAt),
                EpubReleaseStatus.valueOf(status)
        );
    }


    private Path resolveReleaseDirectory(String projectId) {

        return resolveProjectPublishDirectory(projectId)
                .resolve(RELEASE_DIRECTORY_NAME)
                .normalize();
    }


    private Path resolveProjectPublishDirectory(String projectId) {

        String normalizedProjectId = requireText(projectId, "projectId");

        Path projectPublishDirectory = publishDirectory
                .resolve(normalizedProjectId)
                .normalize();

        if (!projectPublishDirectory.startsWith(publishDirectory)) {
            throw new IllegalArgumentException("Invalid projectId: " + normalizedProjectId);
        }

        return projectPublishDirectory;
    }


    private Path resolveReleaseFile(Path releaseDirectory, EpubReleaseItem item) {

        String releaseId = requireText(
                item.getReleaseId(),
                "releaseId"
        );

        String fileName = sanitizeFileName(releaseId) + JSON_EXTENSION;

        Path releaseFile = releaseDirectory
                .resolve(fileName)
                .normalize();

        if (!releaseFile.startsWith(releaseDirectory)) {
            throw new IllegalArgumentException("Invalid releaseId: " + releaseId);
        }

        return releaseFile;
    }


    private void writeAtomic(Path targetFile, String json) throws IOException {

        Path parentDirectory = targetFile.getParent();

        if (parentDirectory == null) throw new IllegalStateException("Release file parent directory is missing: " + targetFile);

        Path tempFile = Files.createTempFile(
                parentDirectory,
                targetFile.getFileName().toString(),
                TEMP_EXTENSION
        );

        boolean moved = false;

        try {

            Files.writeString(
                    tempFile,
                    json,
                    StandardCharsets.UTF_8
            );

            try {

                Files.move(
                        tempFile,
                        targetFile,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException exception) {

                Files.move(
                        tempFile,
                        targetFile
                );
            }

            moved = true;

        } finally {

            if (!moved) Files.deleteIfExists(tempFile);
        }
    }


    private boolean isJsonFile(
            Path path) {

        if (path == null) return false;

        Path fileNamePath = path.getFileName();

        if (fileNamePath == null) return false;

        String fileName = fileNamePath
                .toString()
                .toLowerCase(Locale.ROOT);

        return fileName.endsWith(JSON_EXTENSION);
    }


    private void validateItem(EpubReleaseItem item) {

        if (item == null) throw new IllegalArgumentException("item must not be null.");

        requireText(item.getReleaseId(), "releaseId");
        requireText(item.getProjectId(), "projectId");
        requireText(item.getVersion(), "version");
        requireText(item.getSha256(), "sha256");

        if (item.getEpubPath() == null) throw new IllegalArgumentException("epubPath must not be null.");
        if (item.getFileSize() <= 0) throw new IllegalArgumentException("fileSize must be greater than zero.");
        if (item.getPublishedAt() == null) throw new IllegalArgumentException("publishedAt must not be null.");
        if (item.getReleasedAt() == null) throw new IllegalArgumentException("releasedAt must not be null.");
        if (item.getStatus() == null) throw new IllegalArgumentException("status must not be null.");
    }


    private String requireText(
            String value,
            String name) {

        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }


    private String sanitizeFileName(String value) {

        return value
                .trim()
                .replaceAll("[^A-Za-z0-9._-]", "-");
    }
}