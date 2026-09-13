/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.updater.xhtml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyChangeItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyOperation;
import kr.co.goms.gomsbook.ai.epub.model.EpubTypographyUpdateResult;
import kr.co.goms.gomsbook.ai.util.EpubXmlUtil;

public final class DefaultEpubTypographyUpdater implements EpubTypographyUpdater {

    private static final String XHTML_EXTENSION = ".xhtml";

    @Override
    public EpubTypographyUpdateResult preview(Path projectRoot, EpubTypographyOperation operation, String fileName) {
        return process(projectRoot, operation, fileName, false);
    }

    @Override
    public EpubTypographyUpdateResult update(Path projectRoot, EpubTypographyOperation operation, String fileName) {
        return process(projectRoot, operation, fileName, true);
    }

    private EpubTypographyUpdateResult process(Path projectRoot, EpubTypographyOperation operation, String fileName, boolean save) {
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");
        if (operation == null) operation = EpubTypographyOperation.ALL;

        Path textDirectory = resolveTextDirectory(projectRoot);
        List<Path> files = resolveTargetFiles(textDirectory, fileName);
        List<EpubTypographyChangeItem> items = new ArrayList<>();

        for (Path file : files) processFile(file, operation, save, items);

        return new EpubTypographyUpdateResult(items);
    }

    private void processFile(Path file, EpubTypographyOperation operation, boolean save, List<EpubTypographyChangeItem> items) {
        Document document = EpubXmlUtil.readDocument(file);
        QuoteState quoteState = new QuoteState();
        boolean changed = processNode(document, file.getFileName().toString(), operation, quoteState, items);

        if (save && changed) EpubXmlUtil.writeDocument(file, document);
    }

    private boolean processNode(Node node, String fileName, EpubTypographyOperation operation, QuoteState quoteState, List<EpubTypographyChangeItem> items) {
        if (node.getNodeType() == Node.TEXT_NODE) return processTextNode(node, fileName, operation, quoteState, items);
        if (isExcludedNode(node)) return false;

        boolean changed = false;
        NodeList children = node.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            if (processNode(children.item(index), fileName, operation, quoteState, items)) changed = true;
        }

        return changed;
    }

    private boolean processTextNode(Node node, String fileName, EpubTypographyOperation operation, QuoteState quoteState, List<EpubTypographyChangeItem> items) {
        String original = node.getNodeValue();

        if (original == null || original.isEmpty()) return false;

        String current = original;

        if (operation.includesSingleQuotes()) current = applySingleQuotes(fileName, current, quoteState, items);
        if (operation.includesDoubleQuotes()) current = applyDoubleQuotes(fileName, current, quoteState, items);
        if (operation.includesEllipsis()) current = applyEllipsis(fileName, current, items);

        if (original.equals(current)) return false;

        node.setNodeValue(current);

        return true;
    }

    private String applySingleQuotes(String fileName, String text, QuoteState quoteState, List<EpubTypographyChangeItem> items) {
        TypographyConversion conversion = normalizeSingleQuotes(text, quoteState);

        if (conversion.changeCount() > 0) items.add(new EpubTypographyChangeItem(fileName, EpubTypographyOperation.SINGLE_QUOTES, text, conversion.text(), conversion.changeCount()));

        return conversion.text();
    }

    private String applyDoubleQuotes(String fileName, String text, QuoteState quoteState, List<EpubTypographyChangeItem> items) {
        TypographyConversion conversion = normalizeDoubleQuotes(text, quoteState);

        if (conversion.changeCount() > 0) items.add(new EpubTypographyChangeItem(fileName, EpubTypographyOperation.DOUBLE_QUOTES, text, conversion.text(), conversion.changeCount()));

        return conversion.text();
    }

    private String applyEllipsis(String fileName, String text, List<EpubTypographyChangeItem> items) {
        TypographyConversion conversion = normalizeEllipsis(text);

        if (conversion.changeCount() > 0) items.add(new EpubTypographyChangeItem(fileName, EpubTypographyOperation.ELLIPSIS, text, conversion.text(), conversion.changeCount()));

        return conversion.text();
    }

    private TypographyConversion normalizeSingleQuotes(String text, QuoteState quoteState) {
        StringBuilder result = new StringBuilder(text.length());
        int changeCount = 0;

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);

            if (current != '\'') {
                result.append(current);
                continue;
            }

            char previous = index > 0 ? text.charAt(index - 1) : '\0';
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';

            if (isApostrophe(previous, next)) {
                result.append('’');
                changeCount++;
                continue;
            }

            boolean opening = isOpeningQuote(previous, next, quoteState.singleQuoteOpen);

            result.append(opening ? '‘' : '’');

            quoteState.singleQuoteOpen = opening;
            changeCount++;
        }

        return new TypographyConversion(result.toString(), changeCount);
    }

    private TypographyConversion normalizeDoubleQuotes(String text, QuoteState quoteState) {
        StringBuilder result = new StringBuilder(text.length());
        int changeCount = 0;

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);

            if (current != '"') {
                result.append(current);
                continue;
            }

            char previous = index > 0 ? text.charAt(index - 1) : '\0';
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            boolean opening = isOpeningQuote(previous, next, quoteState.doubleQuoteOpen);

            result.append(opening ? '“' : '”');

            quoteState.doubleQuoteOpen = opening;
            changeCount++;
        }

        return new TypographyConversion(result.toString(), changeCount);
    }

    private TypographyConversion normalizeEllipsis(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int changeCount = 0;
        int index = 0;

        while (index < text.length()) {
            if (text.charAt(index) != '.') {
                result.append(text.charAt(index));
                index++;
                continue;
            }

            int start = index;

            while (index < text.length() && text.charAt(index) == '.') index++;

            int count = index - start;

            if (count < 3) {
                result.append(".".repeat(count));
                continue;
            }

            int ellipsisCount = count / 3;
            int remainder = count % 3;

            result.append("…".repeat(ellipsisCount));
            result.append(".".repeat(remainder));

            changeCount += ellipsisCount;
        }

        return new TypographyConversion(result.toString(), changeCount);
    }

    private boolean isApostrophe(char previous, char next) {
        return Character.isLetterOrDigit(previous) && Character.isLetterOrDigit(next);
    }

    private boolean isOpeningQuote(char previous, char next, boolean currentlyOpen) {
        if (isOpeningBoundary(previous) && !isClosingBoundary(next)) return true;
        if (!isOpeningBoundary(previous) && isClosingBoundary(next)) return false;

        return !currentlyOpen;
    }

    private boolean isOpeningBoundary(char value) {
        return value == '\0' || Character.isWhitespace(value) || "([{<“‘".indexOf(value) >= 0;
    }

    private boolean isClosingBoundary(char value) {
        return value == '\0' || Character.isWhitespace(value) || ".,!?;:)]}>”’".indexOf(value) >= 0;
    }

    private boolean isExcludedNode(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return false;

        String name = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();

        if (name == null) return false;

        return "script".equalsIgnoreCase(name) || "style".equalsIgnoreCase(name);
    }

    private Path resolveTextDirectory(Path projectRoot) {
        Path directText = projectRoot.resolve("Text");

        if (Files.isDirectory(directText)) return directText;

        Path oebpsText = projectRoot.resolve("OEBPS").resolve("Text");

        if (Files.isDirectory(oebpsText)) return oebpsText;

        throw new IllegalStateException("EPUB Text directory not found: " + projectRoot);
    }

    private List<Path> resolveTargetFiles(Path textDirectory, String fileName) {
        if (fileName != null && !fileName.isBlank()) return List.of(resolveTargetFile(textDirectory, fileName));

        try (var stream = Files.list(textDirectory)) {
            return stream.filter(Files::isRegularFile).filter(this::isXhtmlFile).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();

        } catch (Exception exception) {
            throw new IllegalStateException("Failed to list EPUB XHTML files: " + textDirectory, exception);
        }
    }

    private Path resolveTargetFile(Path textDirectory, String fileName) {
        Path normalizedTextDirectory = textDirectory.normalize();
        Path target = normalizedTextDirectory.resolve(fileName).normalize();

        if (!target.startsWith(normalizedTextDirectory)) throw new IllegalArgumentException("Invalid XHTML fileName: " + fileName);
        if (!Files.isRegularFile(target)) throw new IllegalStateException("XHTML file not found: " + target);
        if (!isXhtmlFile(target)) throw new IllegalArgumentException("Target file must be XHTML: " + fileName);

        return target;
    }

    private boolean isXhtmlFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(XHTML_EXTENSION);
    }

    private record TypographyConversion(String text, int changeCount) {
    }

    private static final class QuoteState {

        private boolean singleQuoteOpen;

        private boolean doubleQuoteOpen;
    }
}