/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.generation.author;

import java.nio.file.Path;

public interface EpubAuthorXhtmlGenerator {

    String render(EpubAuthorPage page, Path outputDirectory);
    Path generate(EpubAuthorPage page, Path outputDirectory);
}