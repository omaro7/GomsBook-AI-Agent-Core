/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;


/**
 * 출판 결과 디렉터리에서 가장 최근에 생성된 EPUB 파일을 찾습니다.
 */
public final class LatestPublishedEpubResolver {


    public Path resolve(
            Path publishDirectory) {

        validatePublishDirectory(publishDirectory);

        try (Stream<Path> stream = Files.list(publishDirectory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isEpubFile)
                    .max(Comparator.comparingLong(this::lastModified))
                    .orElseThrow(() -> new IllegalStateException(
                            "Published EPUB file was not found: " + publishDirectory));

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to search published EPUB directory: " + publishDirectory,
                    exception);
        }
    }


    private boolean isEpubFile(
            Path path) {

        if (path == null) {

            return false;
        }

        Path fileNamePath = path.getFileName();

        if (fileNamePath == null) {

            return false;
        }

        String fileName = fileNamePath.toString().toLowerCase(Locale.ROOT);

        return fileName.endsWith(".epub");
    }


    private long lastModified(
            Path path) {

        try {

            return Files.getLastModifiedTime(path).toMillis();

        } catch (IOException exception) {

            return Long.MIN_VALUE;
        }
    }


    private void validatePublishDirectory(
            Path publishDirectory) {

        if (publishDirectory == null) {

            throw new IllegalArgumentException(
                    "publishDirectory must not be null.");
        }

        if (!Files.exists(publishDirectory)) {

            throw new IllegalStateException(
                    "Publish directory does not exist: " + publishDirectory);
        }

        if (!Files.isDirectory(publishDirectory)) {

            throw new IllegalStateException(
                    "Publish path is not a directory: " + publishDirectory);
        }

        if (!Files.isReadable(publishDirectory)) {

            throw new IllegalStateException(
                    "Publish directory is not readable: " + publishDirectory);
        }
    }
}