package kr.co.goms.gomsbook.ai.epub.generation.xhtml;

import java.io.IOException;
import java.nio.file.Path;

public interface BasicXhtmlService {

    Path create(
            Path textDirectory,
            String fileName,
            String title
    ) throws IOException;
}