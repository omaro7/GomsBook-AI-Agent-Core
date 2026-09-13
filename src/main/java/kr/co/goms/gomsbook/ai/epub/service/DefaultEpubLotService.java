/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import kr.co.goms.gomsbook.ai.epub.generation.lot.EpubLotGenerator;
import kr.co.goms.gomsbook.ai.epub.model.EpubLotItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubLotResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.reader.pkg.EpubManifestReader;
import kr.co.goms.gomsbook.ai.epub.reader.pkg.EpubSpineReader;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.epub.updater.pkg.EpubPackageUpdater;

public final class DefaultEpubLotService implements EpubLotService {

    private static final String LOT_FILE_NAME = "lot.xhtml";
    private static final String LOT_MANIFEST_ID = "lot";
    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    private final EpubLotGenerator lotGenerator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final EpubPackageUpdater packageUpdater;
    private final EpubManifestReader manifestReader;
    private final EpubSpineReader spineReader;

    public DefaultEpubLotService(EpubLotGenerator lotGenerator, EpubStylesheetResolver stylesheetResolver, EpubPackageUpdater packageUpdater, EpubManifestReader manifestReader, EpubSpineReader spineReader) {

        if (lotGenerator == null) throw new IllegalArgumentException("lotGenerator must not be null.");
        if (stylesheetResolver == null) throw new IllegalArgumentException("stylesheetResolver must not be null.");
        if (packageUpdater == null) throw new IllegalArgumentException("packageUpdater must not be null.");
        if (manifestReader == null) throw new IllegalArgumentException("manifestReader must not be null.");
        if (spineReader == null) throw new IllegalArgumentException("spineReader must not be null.");

        this.lotGenerator = lotGenerator;
        this.stylesheetResolver = stylesheetResolver;
        this.packageUpdater = packageUpdater;
        this.manifestReader = manifestReader;
        this.spineReader = spineReader;
    }

    @Override
    public EpubLotResult create(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        Path packageDirectory = normalizedProjectRoot.resolve("OEBPS").normalize();
        Path packageDocument = packageDirectory.resolve("content.opf").normalize();
        Path textDirectory = packageDirectory.resolve("Text").normalize();
        Path targetFile = textDirectory.resolve(LOT_FILE_NAME).normalize();

        validateProject(packageDirectory, packageDocument, textDirectory);
        validateTargetFile(textDirectory, targetFile);

        List<EpubManifestItem> manifestItems = manifestReader.read(packageDocument);
        List<EpubSpineItem> spineItems = spineReader.read(packageDocument);
        List<Path> xhtmlFiles = findXhtmlFiles(packageDocument, textDirectory, manifestItems, spineItems);
        List<EpubLotItem> lotItems = new ArrayList<>();

        for (Path xhtmlFile : xhtmlFiles) collectItems(xhtmlFile, lotItems);

        String stylesheetHref = stylesheetResolver.resolveNavigationHref(targetFile);
        String xhtml = lotGenerator.generate(lotItems, stylesheetHref);

        writeXhtml(targetFile, xhtml);

        boolean lotManifestExists = containsManifestHref(manifestItems, createLotHref());
        boolean lotSpineExists = packageUpdater.containsSpineItem(packageDocument, LOT_MANIFEST_ID);

        updateLotPackage(packageDocument);

        boolean packageUpdated = !lotManifestExists || !lotSpineExists;

        return new EpubLotResult(LOT_FILE_NAME, lotItems.size(), xhtmlFiles.size(), packageUpdated);
    }

    private void validateProject(Path packageDirectory, Path packageDocument, Path textDirectory) {

        if (!Files.isDirectory(packageDirectory)) throw new IllegalStateException("EPUB package directory does not exist: " + packageDirectory);
        if (!Files.isRegularFile(packageDocument)) throw new IllegalStateException("EPUB package document does not exist: " + packageDocument);
        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("EPUB Text directory does not exist: " + textDirectory);
    }

    private void validateTargetFile(Path textDirectory, Path targetFile) {

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("LOT XHTML must be inside the EPUB Text directory.");
    }

    private List<Path> findXhtmlFiles(Path packageDocument, Path textDirectory, List<EpubManifestItem> manifestItems, List<EpubSpineItem> spineItems) {

        List<Path> xhtmlFiles = new ArrayList<>();

        for (EpubSpineItem spineItem : spineItems) {

            if (spineItem == null) continue;

            EpubManifestItem manifestItem = findManifestItemById(manifestItems, spineItem.getIdref());

            if (manifestItem == null) continue;
            if (!XHTML_MEDIA_TYPE.equalsIgnoreCase(normalize(manifestItem.getMediaType()))) continue;

            String href = normalize(manifestItem.getHref());

            if (href == null) continue;

            Path xhtmlFile = packageDocument.getParent().resolve(href).normalize();

            if (!xhtmlFile.startsWith(textDirectory)) continue;
            if (!Files.isRegularFile(xhtmlFile)) continue;
            if (LOT_FILE_NAME.equalsIgnoreCase(xhtmlFile.getFileName().toString())) continue;

            xhtmlFiles.add(xhtmlFile);
        }

        return xhtmlFiles;
    }

    private EpubManifestItem findManifestItemById(List<EpubManifestItem> manifestItems, String idref) {

        if (manifestItems == null || idref == null) return null;

        for (EpubManifestItem manifestItem : manifestItems) {

            if (manifestItem == null) continue;
            if (idref.equals(manifestItem.getId())) return manifestItem;
        }

        return null;
    }

    private void collectItems(Path xhtmlFile, List<EpubLotItem> lotItems) {

        try {

            Document document = Jsoup.parse(xhtmlFile.toFile(), StandardCharsets.UTF_8.name());

            for (Element table : document.select("table")) collectTable(xhtmlFile, document, table, lotItems);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to inspect EPUB XHTML: " + xhtmlFile, exception);
        }
    }

    private void collectTable(Path xhtmlFile, Document document, Element table, List<EpubLotItem> lotItems) {

        String tableId = normalize(table.attr("id"));

        if (tableId == null) return;

        String title = resolveTableTitle(document, table);

        if (title == null) return;

        String fileName = xhtmlFile.getFileName().toString();
        String href = fileName + "#" + tableId;

        lotItems.add(new EpubLotItem(fileName, tableId, title, href));
    }

    private String resolveTableTitle(Document document, Element table) {

        Element caption = table.selectFirst("caption");

        if (caption != null) {

            String title = normalize(caption.text());

            if (title != null) return title;
        }

        String ariaLabel = normalize(table.attr("aria-label"));

        if (ariaLabel != null) return ariaLabel;

        String ariaLabelledBy = normalize(table.attr("aria-labelledby"));

        if (ariaLabelledBy != null) {

            Element labelElement = document.getElementById(ariaLabelledBy);

            if (labelElement != null) {

                String title = normalize(labelElement.text());

                if (title != null) return title;
            }
        }

        return null;
    }

    private void writeXhtml(Path targetFile, String xhtml) {

        try {

            Files.writeString(targetFile, xhtml, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to create EPUB LOT XHTML: " + targetFile, exception);
        }
    }

    private void updateLotPackage(Path packageDocument) {

        EpubManifestItem manifestItem = EpubManifestItem.builder(LOT_MANIFEST_ID, createLotHref())
                .mediaType(XHTML_MEDIA_TYPE)
                .build();

        EpubSpineItem spineItem = EpubSpineItem.of(LOT_MANIFEST_ID);

        packageUpdater.update(packageDocument, List.of(manifestItem), List.of(spineItem));
    }

    private boolean containsManifestHref(List<EpubManifestItem> manifestItems, String href) {

        if (manifestItems == null || href == null) return false;

        String normalizedHref = normalizeHref(href);

        for (EpubManifestItem manifestItem : manifestItems) {

            if (manifestItem == null) continue;
            if (normalizedHref.equals(normalizeHref(manifestItem.getHref()))) return true;
        }

        return false;
    }

    private String createLotHref() {

        return "Text/" + LOT_FILE_NAME;
    }

    private String normalizeHref(String href) {

        if (href == null) return null;

        return href.trim().replace('\\', '/');
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) return null;

        return value.trim();
    }

}