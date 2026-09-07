/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.part;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import kr.co.goms.gomsbook.ai.epub.generation.part.EpubPartPage;

public final class EpubPartReader {

    private static final Pattern PART_FILE_PATTERN = Pattern.compile("^part(\\d+)\\.xhtml$", Pattern.CASE_INSENSITIVE);

    public EpubPartPage read(Path textDirectory, String fileName) {

        if (textDirectory == null) throw new IllegalArgumentException("textDirectory must not be null.");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank.");

        String normalizedFileName = fileName.trim();

        validateFileName(normalizedFileName);

        Path normalizedTextDirectory = textDirectory.toAbsolutePath().normalize();
        Path targetFile = normalizedTextDirectory.resolve(normalizedFileName).normalize();

        if (!targetFile.startsWith(normalizedTextDirectory)) throw new IllegalArgumentException("Part file must be inside the EPUB Text directory.");
        if (!Files.exists(targetFile)) throw new IllegalStateException("EPUB part does not exist: " + targetFile);
        if (!Files.isRegularFile(targetFile)) throw new IllegalStateException("EPUB part is not a regular file: " + targetFile);

        int partNumber = extractPartNumber(normalizedFileName);
        String xhtml = readXhtml(targetFile);
        String title = extractTitle(xhtml, normalizedFileName, partNumber);

        return new EpubPartPage(partNumber, normalizedFileName, title, xhtml);
    }

    private void validateFileName(String fileName) {

        if (fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("Part fileName must contain only the file name.");
        if (!PART_FILE_PATTERN.matcher(fileName).matches()) throw new IllegalArgumentException("Part fileName must match partNN.xhtml.");
    }

    private int extractPartNumber(String fileName) {

        Matcher matcher = PART_FILE_PATTERN.matcher(fileName);

        if (!matcher.matches()) throw new IllegalArgumentException("Unable to determine part number from fileName: " + fileName);

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid part number in fileName: " + fileName, exception);
        }
    }

    private String readXhtml(Path targetFile) {

        try {
            String xhtml = Files.readString(targetFile, StandardCharsets.UTF_8);

            if (xhtml.isBlank()) throw new IllegalStateException("EPUB part XHTML is empty: " + targetFile);

            return xhtml;

        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read EPUB part XHTML: " + targetFile, exception);
        }
    }

    private String extractTitle(String xhtml, String fileName, int partNumber) {

        Document document = Jsoup.parse(xhtml, "", Parser.xmlParser());
        Element heading = document.selectFirst("h1");

        if (heading == null) heading = document.selectFirst("h2");
        if (heading == null) heading = document.selectFirst("title");
        if (heading == null) throw new IllegalStateException("EPUB part title was not found: " + fileName);

        String title = heading.text().trim();

        if (title.isBlank()) throw new IllegalStateException("EPUB part title is blank: " + fileName);

        return normalizeTitle(title, partNumber);
    }

    private String normalizeTitle(String title, int partNumber) {

        String normalized = title.trim();
        String prefixWithDot = partNumber + "부.";
        String prefixWithSpace = partNumber + "부 ";

        if (normalized.startsWith(prefixWithDot)) return normalized.substring(prefixWithDot.length()).trim();
        if (normalized.startsWith(prefixWithSpace)) return normalized.substring(prefixWithSpace.length()).trim();

        return normalized;
    }
}