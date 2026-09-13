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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import kr.co.goms.gomsbook.ai.epub.generation.loi.EpubLoiGenerator;
import kr.co.goms.gomsbook.ai.epub.model.EpubLoiItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubLoiResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifestItem;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpineItem;
import kr.co.goms.gomsbook.ai.epub.reader.pkg.EpubManifestReader;
import kr.co.goms.gomsbook.ai.epub.reader.pkg.EpubSpineReader;
import kr.co.goms.gomsbook.ai.epub.resource.stylesheet.EpubStylesheetResolver;
import kr.co.goms.gomsbook.ai.epub.updater.pkg.EpubPackageUpdater;

public final class DefaultEpubLoiService implements EpubLoiService {

    private static final String LOI_FILE_NAME = "loi.xhtml";
    private static final String LOI_MANIFEST_ID = "loi";
    private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";

    private final EpubLoiGenerator loiGenerator;
    private final EpubStylesheetResolver stylesheetResolver;
    private final EpubPackageUpdater packageUpdater;
    private final EpubManifestReader manifestReader;
    private final EpubSpineReader spineReader;

    public DefaultEpubLoiService(EpubLoiGenerator loiGenerator, EpubStylesheetResolver stylesheetResolver, EpubPackageUpdater packageUpdater, EpubManifestReader manifestReader, EpubSpineReader spineReader) {

        if (loiGenerator == null) throw new IllegalArgumentException("loiGenerator must not be null.");
        if (stylesheetResolver == null) throw new IllegalArgumentException("stylesheetResolver must not be null.");
        if (packageUpdater == null) throw new IllegalArgumentException("packageUpdater must not be null.");
        if (manifestReader == null) throw new IllegalArgumentException("manifestReader must not be null.");
        if (spineReader == null) throw new IllegalArgumentException("spineReader must not be null.");

        this.loiGenerator = loiGenerator;
        this.stylesheetResolver = stylesheetResolver;
        this.packageUpdater = packageUpdater;
        this.manifestReader = manifestReader;
        this.spineReader = spineReader;
    }

    @Override
    public EpubLoiResult create(Path projectRoot) {

        if (projectRoot == null) throw new IllegalArgumentException("projectRoot must not be null.");

        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        Path packageDirectory = normalizedProjectRoot.resolve("OEBPS").normalize();
        Path packageDocument = packageDirectory.resolve("content.opf").normalize();
        Path textDirectory = packageDirectory.resolve("Text").normalize();
        Path targetFile = textDirectory.resolve(LOI_FILE_NAME).normalize();

        validateProject(packageDirectory, packageDocument, textDirectory);
        validateTargetFile(textDirectory, targetFile);

        List<EpubManifestItem> manifestItems = manifestReader.read(packageDocument);
        List<EpubSpineItem> spineItems = spineReader.read(packageDocument);
        List<Path> xhtmlFiles = findXhtmlFiles(packageDocument, textDirectory, manifestItems, spineItems);

        List<EpubLoiItem> loiItems = new ArrayList<>();
        Set<String> discoveredImageHrefs = new LinkedHashSet<>();

        for (Path xhtmlFile : xhtmlFiles) collectItems(packageDirectory, xhtmlFile, loiItems, discoveredImageHrefs);

        String stylesheetHref = stylesheetResolver.resolveNavigationHref(targetFile);
        String xhtml = loiGenerator.generate(loiItems, stylesheetHref);

        writeXhtml(targetFile, xhtml);

        boolean loiManifestExists = containsManifestHref(manifestItems, createLoiHref());
        boolean loiSpineExists = packageUpdater.containsSpineItem(packageDocument, LOI_MANIFEST_ID);
        int addedManifestImageCount = updateImageManifest(packageDirectory, packageDocument, manifestItems, discoveredImageHrefs);

        updateLoiPackage(packageDocument);

        boolean packageUpdated = !loiManifestExists || !loiSpineExists || addedManifestImageCount > 0;

        return new EpubLoiResult(LOI_FILE_NAME, loiItems.size(), xhtmlFiles.size(), discoveredImageHrefs.size(), addedManifestImageCount, packageUpdated);
    }

    private void validateProject(Path packageDirectory, Path packageDocument, Path textDirectory) {

        if (!Files.isDirectory(packageDirectory)) throw new IllegalStateException("EPUB package directory does not exist: " + packageDirectory);
        if (!Files.isRegularFile(packageDocument)) throw new IllegalStateException("EPUB package document does not exist: " + packageDocument);
        if (!Files.isDirectory(textDirectory)) throw new IllegalStateException("EPUB Text directory does not exist: " + textDirectory);
    }

    private void validateTargetFile(Path textDirectory, Path targetFile) {

        if (!targetFile.startsWith(textDirectory)) throw new IllegalStateException("LOI XHTML must be inside the EPUB Text directory.");
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
            if (LOI_FILE_NAME.equalsIgnoreCase(xhtmlFile.getFileName().toString())) continue;

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

    private void collectItems(Path packageDirectory, Path xhtmlFile, List<EpubLoiItem> loiItems, Set<String> discoveredImageHrefs) {

        try {

            Document document = Jsoup.parse(xhtmlFile.toFile(), StandardCharsets.UTF_8.name());

            for (Element image : document.select("img[src]")) collectImage(packageDirectory, xhtmlFile, image, loiItems, discoveredImageHrefs);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to inspect EPUB XHTML: " + xhtmlFile, exception);
        }
    }

    private void collectImage(Path packageDirectory, Path xhtmlFile, Element image, List<EpubLoiItem> loiItems, Set<String> discoveredImageHrefs) {

        String src = normalize(image.attr("src"));
        String imageId = normalize(image.attr("id"));
        String alt = normalize(image.attr("alt"));

        if (src != null) {

            String imageHref = resolvePackageHref(packageDirectory, xhtmlFile, src);

            if (imageHref != null) discoveredImageHrefs.add(imageHref);
        }

        if (imageId == null || alt == null) return;

        if ("cover".equalsIgnoreCase(imageId)) alt = "표지";

        String fileName = xhtmlFile.getFileName().toString();
        String href = fileName + "#" + imageId;

        loiItems.add(new EpubLoiItem(fileName, imageId, src, alt, href));
    }

    private String resolvePackageHref(Path packageDirectory, Path xhtmlFile, String src) {

        if (src == null || src.isBlank()) return null;

        String value = stripQueryAndFragment(src.trim());

        if (value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) return null;

        Path resourcePath = xhtmlFile.getParent().resolve(value).normalize();

        if (!resourcePath.startsWith(packageDirectory)) return null;

        return normalizeHref(packageDirectory.relativize(resourcePath).toString());
    }

    private void writeXhtml(Path targetFile, String xhtml) {

        try {

            Files.writeString(targetFile, xhtml, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to create EPUB LOI XHTML: " + targetFile, exception);
        }
    }

    private void updateLoiPackage(Path packageDocument) {

        EpubManifestItem manifestItem = EpubManifestItem.builder(LOI_MANIFEST_ID, createLoiHref())
                .mediaType(XHTML_MEDIA_TYPE)
                .build();

        EpubSpineItem spineItem = EpubSpineItem.of(LOI_MANIFEST_ID);

        packageUpdater.update(packageDocument, List.of(manifestItem), List.of(spineItem));
    }

    private int updateImageManifest(Path packageDirectory, Path packageDocument, List<EpubManifestItem> manifestItems, Set<String> discoveredImageHrefs) {

        int addedCount = 0;

        for (String href : discoveredImageHrefs) {

            if (containsManifestHref(manifestItems, href)) continue;

            Path imagePath = packageDirectory.resolve(href).normalize();

            if (!imagePath.startsWith(packageDirectory)) continue;
            if (!Files.isRegularFile(imagePath)) continue;

            String mediaType = resolveImageMediaType(imagePath.getFileName().toString());

            if (mediaType == null) continue;

            String id = createUniqueManifestId(manifestItems, imagePath.getFileName().toString());
            EpubManifestItem manifestItem = EpubManifestItem.builder(id, href).mediaType(mediaType).build();

            packageUpdater.addOrUpdateManifestItem(packageDocument, manifestItem);

            manifestItems.add(manifestItem);
            addedCount++;
        }

        return addedCount;
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

    private boolean containsManifestId(List<EpubManifestItem> manifestItems, String id) {

        if (manifestItems == null || id == null) return false;

        for (EpubManifestItem manifestItem : manifestItems) {

            if (manifestItem == null) continue;
            if (id.equals(manifestItem.getId())) return true;
        }

        return false;
    }

    private String createUniqueManifestId(List<EpubManifestItem> manifestItems, String fileName) {

        String baseId = stripExtension(fileName).replaceAll("[^A-Za-z0-9._-]", "_");

        if (baseId.isBlank()) baseId = "image";
        if (!Character.isLetter(baseId.charAt(0)) && baseId.charAt(0) != '_') baseId = "image_" + baseId;

        String candidate = baseId + "_image";
        int sequence = 2;

        while (containsManifestId(manifestItems, candidate)) candidate = baseId + "_image_" + sequence++;

        return candidate;
    }

    private String resolveImageMediaType(String fileName) {

        String extension = getExtension(fileName).toLowerCase(Locale.ROOT);

        if ("jpg".equals(extension) || "jpeg".equals(extension)) return "image/jpeg";
        if ("png".equals(extension)) return "image/png";
        if ("webp".equals(extension)) return "image/webp";
        if ("gif".equals(extension)) return "image/gif";
        if ("svg".equals(extension)) return "image/svg+xml";

        return null;
    }

    private String createLoiHref() {

        return "Text/" + LOI_FILE_NAME;
    }

    private String stripQueryAndFragment(String value) {

        int endIndex = value.length();
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');

        if (queryIndex >= 0) endIndex = Math.min(endIndex, queryIndex);
        if (fragmentIndex >= 0) endIndex = Math.min(endIndex, fragmentIndex);

        return value.substring(0, endIndex);
    }

    private String getExtension(String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        return dotIndex >= 0 && dotIndex < fileName.length() - 1 ? fileName.substring(dotIndex + 1) : "";
    }

    private String stripExtension(String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
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