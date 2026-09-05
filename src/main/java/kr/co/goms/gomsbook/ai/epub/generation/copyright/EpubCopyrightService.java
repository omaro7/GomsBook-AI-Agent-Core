/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.generation.copyright;

import java.nio.file.Path;
import java.util.List;

import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdateItem;
import kr.co.goms.gomsbook.ai.epub.navigation.updater.EpubNavigationUpdater;
import kr.co.goms.gomsbook.ai.epub.pkg.updater.EpubPackageUpdater;

public class EpubCopyrightService {

    private static final String COPYRIGHT_XHTML_ID = "copyright";
    private static final String COPYRIGHT_NAVIGATION_LABEL = "판권";

    private final EpubCopyrightXhtmlGenerator xhtmlGenerator;
    private final EpubPackageUpdater packageUpdater;
    private final EpubNavigationUpdater navigationUpdater;

    public EpubCopyrightService(
            EpubCopyrightXhtmlGenerator xhtmlGenerator,
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
            EpubCopyrightPage page,
            Path packagePath,
            Path navigationPath,
            Path textDirectory) {

        if (page == null) throw new IllegalArgumentException("page must not be null.");
        if (packagePath == null) throw new IllegalArgumentException("packagePath must not be null.");
        if (navigationPath == null) throw new IllegalArgumentException("navigationPath must not be null.");
        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");

        Path xhtmlPath = xhtmlGenerator.generate(page, textDirectory);

        updatePackage(packagePath, page);
        updateNavigation(navigationPath, page);

        return xhtmlPath;
    }

    private void updatePackage(
            Path packagePath,
            EpubCopyrightPage page) {

        EpubManifestItem manifestItem = createManifestItem(page);
        EpubSpineItem spineItem = createSpineItem();

        packageUpdater.update(
                packagePath,
                List.of(manifestItem),
                List.of(spineItem));
    }

    private EpubManifestItem createManifestItem(
            EpubCopyrightPage page) {

        return EpubManifestItem.builder(
                COPYRIGHT_XHTML_ID,
                createHref(page))
                .mediaType("application/xhtml+xml")
                .build();
    }

    private EpubSpineItem createSpineItem() {
        return EpubSpineItem.of(COPYRIGHT_XHTML_ID);
    }

    private void updateNavigation(
            Path navigationPath,
            EpubCopyrightPage page) {

        EpubNavigationItem navigationItem =
                createNavigationItem(page);

        EpubNavigationUpdateItem updateItem =
                EpubNavigationUpdateItem.last(
                        navigationItem);

        navigationUpdater.addOrUpdateItem(
                navigationPath,
                updateItem);
    }

    private EpubNavigationItem createNavigationItem(
            EpubCopyrightPage page) {

        return EpubNavigationItem.of(
                COPYRIGHT_NAVIGATION_LABEL,
                createHref(page));
    }

    private String createHref(EpubCopyrightPage page) {
        return "Text/" + page.getFileName();
    }
}