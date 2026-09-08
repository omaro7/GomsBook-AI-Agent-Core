/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.chapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdateItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdater;
import kr.co.goms.gomsbook.ai.epub.pkg.updater.EpubPackageUpdater;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;

public final class EpubChapterService {

    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    private final EpubChapterXhtmlGenerator xhtmlGenerator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final EpubPackageUpdater packageUpdater;
    private final EpubNavigationUpdater navigationUpdater;

    public EpubChapterService(EpubChapterXhtmlGenerator xhtmlGenerator, EpubStylesheetResolver stylesheetResolver, EpubPackageUpdater packageUpdater, EpubNavigationUpdater navigationUpdater) {

        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");
        if (stylesheetResolver == null) throw new IllegalArgumentException("stylesheetResolver must not be null.");
        if (packageUpdater == null) throw new IllegalArgumentException("packageUpdater must not be null.");
        if (navigationUpdater == null) throw new IllegalArgumentException("navigationUpdater must not be null.");

        this.xhtmlGenerator = xhtmlGenerator;
        this.stylesheetResolver = stylesheetResolver;
        this.packageUpdater = packageUpdater;
        this.navigationUpdater = navigationUpdater;
    }

    public void create(int partNumber, int chapterNumber, String fileName, String title, String content, Path packageDocument, Path navigationFile, Path textDirectory) {

        validateCreateArguments(partNumber, chapterNumber, fileName, title, content, packageDocument, navigationFile, textDirectory);

        Path normalizedTextDirectory = normalizeTextDirectory(textDirectory);
        Path targetFile = resolveTargetFile(normalizedTextDirectory, fileName);

        validateTargetFile(normalizedTextDirectory, targetFile);
        validateChapterNotExists(targetFile);

        String stylesheetHref = stylesheetResolver.resolveHref(targetFile);
        String xhtml = xhtmlGenerator.generate(partNumber, chapterNumber, title, content, stylesheetHref);

        EpubChapterPage page = new EpubChapterPage(partNumber, chapterNumber, fileName.trim(), title.trim(), xhtml);

        writeXhtml(targetFile, page.getXhtml());
        updatePackage(packageDocument, page);
        updateNavigation(navigationFile, page);
    }

    public void update(String fileName, String xhtml, Path textDirectory) {

        validateUpdateArguments(fileName, xhtml, textDirectory);

        Path normalizedTextDirectory = normalizeTextDirectory(textDirectory);
        Path targetFile = resolveTargetFile(normalizedTextDirectory, fileName);

        validateTargetFile(normalizedTextDirectory, targetFile);
        validateChapterExists(targetFile);

        writeXhtml(targetFile, xhtml);
    }

    public void delete(String fileName, Path packageDocument, Path navigationFile, Path textDirectory) {

        validateDeleteArguments(fileName, packageDocument, navigationFile, textDirectory);

        Path normalizedTextDirectory = normalizeTextDirectory(textDirectory);
        Path targetFile = resolveTargetFile(normalizedTextDirectory, fileName);

        validateTargetFile(normalizedTextDirectory, targetFile);
        validateChapterExists(targetFile);

        removePackageReferencesIfExists(packageDocument, fileName);
        removeNavigationReferenceIfExists(navigationFile, fileName);
        deleteXhtml(targetFile);
    }

    private void validateCreateArguments(int partNumber, int chapterNumber, String fileName, String title, String content, Path packageDocument, Path navigationFile, Path textDirectory) {

        if (partNumber <= 0) throw new IllegalArgumentException("partNumber must be greater than 0.");
        if (chapterNumber <= 0) throw new IllegalArgumentException("chapterNumber must be greater than 0.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.trim().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank.");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be blank.");
        if (packageDocument == null) throw new IllegalArgumentException("packageDocument must not be null.");
        if (navigationFile == null) throw new IllegalArgumentException("navigationFile must not be null.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");
    }

    private void validateUpdateArguments(String fileName, String xhtml, Path textDirectory) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.trim().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");
        if (xhtml == null || xhtml.isBlank()) throw new IllegalArgumentException("xhtml must not be blank.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");
    }

    private void validateDeleteArguments(String fileName, Path packageDocument, Path navigationFile, Path textDirectory) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");
        if (!fileName.trim().toLowerCase().endsWith(".xhtml")) throw new IllegalArgumentException("fileName must have the .xhtml extension.");
        if (packageDocument == null) throw new IllegalArgumentException("packageDocument must not be null.");
        if (navigationFile == null) throw new IllegalArgumentException("navigationFile must not be null.");
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

    private void validateChapterNotExists(Path targetFile) {

        if (Files.exists(targetFile)) throw new IllegalStateException("EPUB chapter already exists: " + targetFile + ". Use update_epub_chapter instead.");
    }

    private void validateChapterExists(Path targetFile) {

        if (!Files.exists(targetFile)) throw new IllegalStateException("EPUB chapter does not exist: " + targetFile + ". Use create_epub_chapter instead.");
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB chapter path is not a file: " + targetFile);
    }

    private void writeXhtml(Path targetFile, String xhtml) {

        try {

            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, xhtml, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to write EPUB chapter XHTML: " + targetFile, exception);
        }
    }

    private void deleteXhtml(Path targetFile) {

        try {

            Files.delete(targetFile);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to delete EPUB chapter XHTML: " + targetFile, exception);
        }
    }

    private void updatePackage(Path packageDocument, EpubChapterPage page) {

        String href = "Text/" + page.getFileName();

        EpubManifestItem manifestItem = EpubManifestItem.builder(page.getManifestId(), href)
                .mediaType(XHTML_MEDIA_TYPE)
                .build();

        EpubSpineItem spineItem = EpubSpineItem.of(page.getManifestId());

        packageUpdater.update(packageDocument, List.of(manifestItem), List.of(spineItem));
    }

    private void updateNavigation(Path navigationFile, EpubChapterPage page) {

        EpubNavigationItem navigationItem = EpubNavigationItem.chapter(page.getDisplayTitle(), page.getFileName());
        EpubNavigationUpdateItem updateItem = EpubNavigationUpdateItem.last(navigationItem);

        navigationUpdater.addOrUpdateItem(navigationFile, updateItem);
    }

    private void removePackageReferencesIfExists(Path packageDocument, String fileName) {

        String href = "Text/" + fileName.trim();

        packageUpdater.removeByHrefIfExists(packageDocument, href);
    }

    private void removeNavigationReferenceIfExists(Path navigationFile, String fileName) {
        navigationUpdater.removeItemIfExists(navigationFile, fileName.trim());
    }
}