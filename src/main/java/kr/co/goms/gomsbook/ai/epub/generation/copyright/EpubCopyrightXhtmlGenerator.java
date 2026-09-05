/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.generation.copyright;

import java.nio.file.Path;


public interface EpubCopyrightXhtmlGenerator {

    Path generate(EpubCopyrightPage plan, Path outputDirectory);
}