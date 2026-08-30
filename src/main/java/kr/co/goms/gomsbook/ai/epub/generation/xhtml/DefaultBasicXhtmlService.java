package kr.co.goms.gomsbook.ai.epub.generation.xhtml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class DefaultBasicXhtmlService
        implements BasicXhtmlService {

    private static final String XHTML_EXTENSION =
            ".xhtml";

    private final BasicXhtmlGenerator generator;

    public DefaultBasicXhtmlService(
            BasicXhtmlGenerator generator) {

        this.generator =
                Objects.requireNonNull(
                        generator,
                        "generator must not be null"
                );
    }

    @Override
    public Path create(
            Path textDirectory,
            String fileName,
            String title)
            throws IOException {

        Path directory =
                validateTextDirectory(
                        textDirectory
                );

        String normalizedFileName =
                normalizeFileName(
                        fileName
                );

        Path targetFile =
                directory
                        .resolve(normalizedFileName)
                        .toAbsolutePath()
                        .normalize();

        if (!targetFile.startsWith(directory)) {

            throw new IllegalArgumentException(
                    "XHTML file must be created "
                            + "inside the Text directory."
            );
        }

        if (Files.exists(targetFile)) {

            throw new IllegalStateException(
                    "XHTML file already exists: "
                            + targetFile
            );
        }

        Files.createDirectories(
                directory
        );

        String xhtml =
                generator.generate(
                        title
                );

        Files.writeString(
                targetFile,
                xhtml,
                StandardCharsets.UTF_8
        );

        return targetFile;
    }

    private Path validateTextDirectory(
            Path textDirectory) {

        if (textDirectory == null) {

            throw new IllegalArgumentException(
                    "Text directory must not be null."
            );
        }

        return textDirectory
                .toAbsolutePath()
                .normalize();
    }

    private String normalizeFileName(
            String fileName) {

        if (fileName == null
                || fileName.isBlank()) {

            throw new IllegalArgumentException(
                    "fileName must not be blank."
            );
        }

        String normalized =
                fileName
                        .trim()
                        .replace('\\', '/');

        if (normalized.contains("/")) {

            normalized =
                    normalized.substring(
                            normalized.lastIndexOf('/') + 1
                    );
        }

        String lower =
                normalized.toLowerCase(
                        Locale.ROOT
                );

        /*
         * 흔한 오타 보정:
         * chapter10_11.xhml
         *          ↓
         * chapter10_11.xhtml
         */
        if (lower.endsWith(".xhml")) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 5
                    )
                            + XHTML_EXTENSION;

        } else if (!lower.endsWith(
                XHTML_EXTENSION)) {

            normalized +=
                    XHTML_EXTENSION;
        }

        if (normalized.equalsIgnoreCase(
                XHTML_EXTENSION)) {

            throw new IllegalArgumentException(
                    "Invalid XHTML file name."
            );
        }

        return normalized;
    }
}