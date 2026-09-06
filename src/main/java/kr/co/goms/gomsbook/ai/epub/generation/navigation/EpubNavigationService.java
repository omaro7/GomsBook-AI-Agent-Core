/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.generation.navigation;

import java.nio.file.Files;
import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;

public class EpubNavigationService {

    private final EpubNavigationXhtmlGenerator xhtmlGenerator;

    public EpubNavigationService(EpubNavigationXhtmlGenerator xhtmlGenerator) {
        if (xhtmlGenerator == null) throw new IllegalArgumentException("xhtmlGenerator must not be null.");

        this.xhtmlGenerator = xhtmlGenerator;
    }

    public Path generate(EpubNavigation navigation, Path navigationPath) {
        validateArguments(navigation, navigationPath);

        if (Files.isRegularFile(navigationPath)) throw new IllegalStateException("EPUB navigation already exists: " + navigationPath);

        navigation.validate();

        Path xhtmlPath = xhtmlGenerator.generate(navigation, navigationPath);

        validateGeneratedPath(xhtmlPath);

        return xhtmlPath;
    }

    private void validateArguments(EpubNavigation navigation, Path navigationPath) {
        if (navigation == null) throw new IllegalArgumentException("navigation must not be null.");
        if (navigationPath == null) throw new IllegalArgumentException("navigationPath must not be null.");
    }

    private void validateGeneratedPath(Path xhtmlPath) {
        if (xhtmlPath == null) throw new IllegalStateException("Generated EPUB navigation path must not be null.");
        if (!Files.isRegularFile(xhtmlPath)) throw new IllegalStateException("Generated EPUB navigation file does not exist: " + xhtmlPath);
    }
}