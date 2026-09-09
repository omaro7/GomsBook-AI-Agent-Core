/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.part;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.updater.navigation.EpubNavigationUpdateItem;
import kr.co.goms.gomsbook.ai.epub.updater.navigation.EpubNavigationUpdater;
import kr.co.goms.gomsbook.ai.epub.updater.pkg.EpubPackageUpdater;

public final class EpubPartService {

    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    private final EpubPackageUpdater packageUpdater;
    private final EpubNavigationUpdater navigationUpdater;

    public EpubPartService(EpubPackageUpdater packageUpdater, EpubNavigationUpdater navigationUpdater) {

        if (packageUpdater == null) throw new IllegalArgumentException("packageUpdater must not be null.");
        if (navigationUpdater == null) throw new IllegalArgumentException("navigationUpdater must not be null.");

        this.packageUpdater = packageUpdater;
        this.navigationUpdater = navigationUpdater;
    }

    public void generate(EpubPartPage page, Path packageDocument, Path navigationFile, Path textDirectory) {

        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (packageDocument == null) throw new IllegalArgumentException("packageDocument must not be null.");
        if (navigationFile == null) throw new IllegalArgumentException("navigationFile must not be null.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");

        Path normalizedTextDirectory = textDirectory.toAbsolutePath().normalize();
        Path targetFile = normalizedTextDirectory.resolve(page.getFileName()).normalize();

        validateTargetFile(normalizedTextDirectory, targetFile);
        validatePartNotExists(targetFile);

        writeXhtml(targetFile, page.getXhtml());
        updatePackage(packageDocument, page);
        updateNavigation(navigationFile, page);
    }

    public void update(EpubPartPage page, Path textDirectory) {

        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");
        if (page.getXhtml() == null || page.getXhtml().isBlank()) throw new IllegalArgumentException("xhtml must not be blank.");

        Path normalizedTextDirectory = textDirectory.toAbsolutePath().normalize();
        Path targetFile = normalizedTextDirectory.resolve(page.getFileName()).normalize();

        validateTargetFile(normalizedTextDirectory, targetFile);
        validatePartExists(targetFile);

        writeXhtml(targetFile, page.getXhtml());
        verifyUpdatedXhtml(targetFile, page.getXhtml());
    }

    private void verifyUpdatedXhtml(Path targetFile, String expectedXhtml) {

        try {

            String actualXhtml = Files.readString(targetFile, StandardCharsets.UTF_8);

            if (!actualXhtml.equals(expectedXhtml)) throw new IllegalStateException("EPUB part XHTML verification failed: " + targetFile);

            System.out.println("[GomsBook EPUB] Part Verified = " + targetFile);
            System.out.println("[GomsBook EPUB] Part Size     = " + actualXhtml.length());

        } catch (IllegalStateException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to verify EPUB part XHTML: " + targetFile, exception);
        }
    }

    private void validateTargetFile(Path textDirectory, Path targetFile) {

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("Part XHTML must be inside the EPUB Text directory.");

        Path fileName = targetFile.getFileName();

        if (fileName == null || !fileName.toString().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("Part file must be XHTML.");
    }

    private void validatePartNotExists(Path targetFile) {

        if (Files.exists(targetFile)) throw new IllegalStateException("EPUB part already exists: " + targetFile);
    }

    private void validatePartExists(Path targetFile) {

        if (!Files.exists(targetFile)) throw new IllegalStateException("EPUB part does not exist: " + targetFile + ". Use create_epub_part instead.");
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB part path is not a file: " + targetFile);
    }

    private void writeXhtml(Path targetFile, String xhtml) {

        if (xhtml == null || xhtml.isBlank()) throw new IllegalArgumentException("xhtml must not be blank.");

        try {

            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, xhtml, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to write EPUB part XHTML: " + targetFile, exception);
        }
    }

    private void updatePackage(Path packageDocument, EpubPartPage page) {

        String href = "Text/" + page.getFileName();

        EpubManifestItem manifestItem = EpubManifestItem.builder(page.getManifestId(), href).mediaType(XHTML_MEDIA_TYPE).build();
        EpubSpineItem spineItem = EpubSpineItem.of(page.getManifestId());

        packageUpdater.update(packageDocument, List.of(manifestItem), List.of(spineItem));
    }

    private void updateNavigation(Path navigationFile, EpubPartPage page) {

        EpubNavigationItem navigationItem = EpubNavigationItem.part(page.getDisplayTitle(), page.getFileName());
        EpubNavigationUpdateItem updateItem = EpubNavigationUpdateItem.last(navigationItem);

        navigationUpdater.addOrUpdateItem(navigationFile, updateItem);
    }
}