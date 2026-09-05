/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.generation.author;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdateItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdater;
import kr.co.goms.gomsbook.ai.epub.pkg.updater.EpubPackageUpdater;

public class EpubAuthorService {

    private static final String AUTHOR_XHTML_ID = "author";
    private static final String AUTHOR_NAVIGATION_LABEL = "작가소개";

    private final EpubAuthorXhtmlGenerator xhtmlGenerator;
    private final EpubPackageUpdater packageUpdater;
    private final EpubNavigationUpdater navigationUpdater;

    public EpubAuthorService(
            EpubAuthorXhtmlGenerator xhtmlGenerator,
            EpubPackageUpdater packageUpdater,
            EpubNavigationUpdater navigationUpdater) {

        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");
        if (packageUpdater == null) throw new IllegalArgumentException("packageUpdater must not be null.");
        if (navigationUpdater == null) throw new IllegalArgumentException("navigationUpdater must not be null.");

        this.xhtmlGenerator = xhtmlGenerator;
        this.packageUpdater = packageUpdater;
        this.navigationUpdater = navigationUpdater;
    }

    public Path generate(
            EpubAuthorPage page,
            Path packagePath,
            Path navigationPath,
            Path textDirectory) {

        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (packagePath == null) throw new IllegalArgumentException("packagePath must not be null.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");

        Path xhtmlPath = xhtmlGenerator.generate(page, textDirectory);

        updatePackage(packagePath, page);
        
        if (navigationPath != null && Files.exists(navigationPath)) updateNavigation(navigationPath, page);

        return xhtmlPath;
    }

    public void delete(
            String fileName,
            Path packagePath,
            Path navigationPath,
            Path textDirectory) {

        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be empty.");
        if (packagePath == null) throw new IllegalArgumentException("packagePath must not be null.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");

        String normalizedFileName = fileName.trim();
        Path normalizedTextDirectory = textDirectory.toAbsolutePath().normalize();
        Path xhtmlPath = normalizedTextDirectory.resolve(normalizedFileName).normalize();

        if (!xhtmlPath.startsWith(normalizedTextDirectory)) throw new IllegalArgumentException("Author XHTML must be inside the EPUB Text directory.");

        String href = createHref(normalizedFileName);

        removeNavigationIfExists(navigationPath, href);
        removePackage(packagePath);
        deleteXhtml(xhtmlPath);
    }

    private void removeNavigationIfExists(Path navigationPath, String href) {

        if (navigationPath == null) return;
        if (!Files.exists(navigationPath)) return;
        if (!Files.isRegularFile(navigationPath)) return;

        navigationUpdater.removeItem(navigationPath, href);
    }

    private void updatePackage(
            Path packagePath,
            EpubAuthorPage page) {

        EpubManifestItem manifestItem = createManifestItem(page);
        EpubSpineItem spineItem = createSpineItem();

        packageUpdater.update(
                packagePath,
                List.of(manifestItem),
                List.of(spineItem));
    }

    private EpubManifestItem createManifestItem(
            EpubAuthorPage page) {

        return EpubManifestItem.builder(
                AUTHOR_XHTML_ID,
                createHref(page))
                .mediaType("application/xhtml+xml")
                .build();
    }

    private EpubSpineItem createSpineItem() {
        return EpubSpineItem.of(AUTHOR_XHTML_ID);
    }

    private void updateNavigation(
            Path navigationPath,
            EpubAuthorPage page) {

        EpubNavigationItem navigationItem = createNavigationItem(page);

        EpubNavigationUpdateItem updateItem = EpubNavigationUpdateItem.after(navigationItem, "cover");

        navigationUpdater.addOrUpdateItem(
                navigationPath,
                updateItem);
    }

    private EpubNavigationItem createNavigationItem(
            EpubAuthorPage page) {

        return EpubNavigationItem.of(
                AUTHOR_NAVIGATION_LABEL,
                createHref(page));
    }

    private void removePackage(Path packagePath) {

        packageUpdater.removeSpineItem(
                packagePath,
                AUTHOR_XHTML_ID);

        packageUpdater.removeManifestItem(
                packagePath,
                AUTHOR_XHTML_ID);
    }

    private void removeNavigation(
            Path navigationPath,
            String href) {

        navigationUpdater.removeItem(
                navigationPath,
                href);
    }

    private void deleteXhtml(Path xhtmlPath) {

        try {

            Files.deleteIfExists(xhtmlPath);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to delete author XHTML: " + xhtmlPath,
                    exception);
        }
    }

    private String createHref(EpubAuthorPage page) {
        return createHref(page.getFileName());
    }

    private String createHref(String fileName) {
        return "Text/" + fileName;
    }
}