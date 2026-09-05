/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.copyright;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class EpubCopyrightLocator {

    public Optional<Path> find(Path textDirectory) {

        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");
        if (!Files.isDirectory(textDirectory)) return Optional.empty();

        Path preferredFile = findPreferredFile(textDirectory);

        if (preferredFile != null) return Optional.of(preferredFile);

        Path copyrightPage = findCopyrightPageByContent(textDirectory);

        return Optional.ofNullable(copyrightPage);
    }

    public Path locate(Path textDirectory) {

        return find(textDirectory)
                .orElseThrow(() -> new IllegalStateException("Copyright XHTML was not found: " + textDirectory));
    }

    private Path findPreferredFile(Path textDirectory) {

        Path copyrightFile = textDirectory.resolve("copyright.xhtml");
        if (Files.isRegularFile(copyrightFile)) return copyrightFile;

        Path publisherFile = textDirectory.resolve("publisher.xhtml");
        if (Files.isRegularFile(publisherFile)) return publisherFile;

        return null;
    }

    private Path findCopyrightPageByContent(Path textDirectory) {

        try (Stream<Path> stream = Files.list(textDirectory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isXhtml)
                    .filter(this::isCopyrightPage)
                    .findFirst()
                    .orElse(null);

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to inspect copyright XHTML directory: " + textDirectory, exception);
        }
    }

    private boolean isXhtml(Path path) {

        String fileName = path.getFileName().toString().toLowerCase();

        return fileName.endsWith(".xhtml");
    }

    private boolean isCopyrightPage(Path path) {

        try {

            String content = Files.readString(path, StandardCharsets.UTF_8);

            return content.contains("epub:type=\"copyright-page\"")
                    || content.contains("epub:type='copyright-page'");

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to read XHTML file: " + path, exception);
        }
    }
}