/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class EpubStylesheetResolver {

    private static final String DEFAULT_STYLESHEET_FILE_NAME = "style1.css";
    private static final String CSS_MEDIA_TYPE = "text/css";

    public String resolveHref(Path xhtmlFile) {

        if (xhtmlFile == null) throw new IllegalArgumentException("xhtmlFile must not be null.");

        Path normalizedXhtmlFile = xhtmlFile.toAbsolutePath().normalize();
        Path textDirectory = normalizedXhtmlFile.getParent();

        if (textDirectory == null) throw new IllegalStateException("XHTML parent directory is not available: " + normalizedXhtmlFile);

        Path packageDirectory = textDirectory.getParent();

        if (packageDirectory == null) throw new IllegalStateException("EPUB package directory is not available: " + textDirectory);

        System.out.println("[GomsBook EPUB] XHTML File       = " + normalizedXhtmlFile);
        System.out.println("[GomsBook EPUB] Text Directory   = " + textDirectory);
        System.out.println("[GomsBook EPUB] Package Directory= " + packageDirectory);

        Path stylesheet = resolve(packageDirectory);

        System.out.println("[GomsBook EPUB] Stylesheet       = " + stylesheet);

        return textDirectory.relativize(stylesheet).toString().replace('\\', '/');
    }

    public Path resolve(Path packageDirectory) {

        if (packageDirectory == null) throw new IllegalArgumentException("packageDirectory must not be null.");

        Path normalizedPackageDirectory = packageDirectory.toAbsolutePath().normalize();

        System.out.println("[GomsBook EPUB] Resolve Package = " + normalizedPackageDirectory);
        System.out.println("[GomsBook EPUB] content.opf     = " + normalizedPackageDirectory.resolve("content.opf"));
        System.out.println("[GomsBook EPUB] Styles          = " + normalizedPackageDirectory.resolve("Styles"));

        if (!Files.isDirectory(normalizedPackageDirectory)) throw new IllegalStateException("EPUB package directory does not exist: " + normalizedPackageDirectory);

        Path manifestStylesheet = resolveFromPackageDocument(normalizedPackageDirectory);

        System.out.println("[GomsBook EPUB] Manifest CSS    = " + manifestStylesheet);

        if (manifestStylesheet != null) return manifestStylesheet;

        Path directoryStylesheet = resolveFromStylesDirectory(normalizedPackageDirectory.resolve("Styles"));

        System.out.println("[GomsBook EPUB] Directory CSS   = " + directoryStylesheet);

        if (directoryStylesheet != null) return directoryStylesheet;

        throw new IllegalStateException("EPUB stylesheet could not be resolved: " + normalizedPackageDirectory);
    }
    
    private Path resolveFromPackageDocument(Path packageDirectory) {

        Path packageDocument = findPackageDocument(packageDirectory);

        if (packageDocument == null) return null;

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            Document document = factory.newDocumentBuilder().parse(packageDocument.toFile());
            NodeList items = document.getElementsByTagNameNS("*", "item");
            Path packageDocumentDirectory = packageDocument.getParent();

            for (int priority = 0; priority <= 2; priority++) {

                for (int index = 0; index < items.getLength(); index++) {

                    Element item = (Element) items.item(index);
                    String href = item.getAttribute("href");
                    String mediaType = item.getAttribute("media-type");

                    if (!isCss(href, mediaType)) continue;
                    if (stylesheetPriority(href) != priority) continue;

                    Path stylesheet = packageDocumentDirectory.resolve(href).normalize();

                    if (stylesheet.startsWith(packageDirectory) && Files.isRegularFile(stylesheet)) return stylesheet;
                }
            }

            return null;

        } catch (Exception exception) {

            throw new IllegalStateException("Failed to read EPUB package document: " + packageDocument, exception);
        }
    }

    private Path findPackageDocument(Path packageDirectory) {

        Path contentOpf = packageDirectory.resolve("content.opf");

        if (Files.isRegularFile(contentOpf)) return contentOpf;

        try (Stream<Path> stream = Files.list(packageDirectory)) {

            List<Path> opfFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isOpfFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            if (opfFiles.size() == 1) return opfFiles.get(0);

            return null;

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to inspect EPUB package directory: " + packageDirectory, exception);
        }
    }

    private Path resolveFromStylesDirectory(Path stylesDirectory) {

        if (!Files.isDirectory(stylesDirectory)) return null;

        Path style1 = stylesDirectory.resolve(DEFAULT_STYLESHEET_FILE_NAME);

        if (Files.isRegularFile(style1)) return style1;

        try (Stream<Path> stream = Files.list(stylesDirectory)) {

        	List<Path> cssFiles = stream
        	        .filter(Files::isRegularFile)
        	        .filter(this::isCssFile)
        	        .sorted(Comparator.comparingInt((Path path) -> stylesheetPriority(path))
        	                .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
        	        .toList();
        	
            if (cssFiles.isEmpty()) return null;

            return cssFiles.get(0);

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to inspect Styles directory: " + stylesDirectory, exception);
        }
    }

    private boolean isCss(String href, String mediaType) {
        if (CSS_MEDIA_TYPE.equalsIgnoreCase(mediaType)) return true;
        return href != null && href.toLowerCase(Locale.ROOT).endsWith(".css");
    }

    private boolean isCssFile(Path path) {
        return path != null && path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css");
    }

    private boolean isOpfFile(Path path) {
        return path != null && path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".opf");
    }

    private int stylesheetPriority(Path path) {
        if (path == null || path.getFileName() == null) return 2;
        return stylesheetPriority(path.getFileName().toString());
    }

    private int stylesheetPriority(String href) {

        if (href == null) return 2;

        String normalized = href.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);

        if (DEFAULT_STYLESHEET_FILE_NAME.equals(fileName)) return 0;
        if ("nav.css".equals(fileName) || "quiz.css".equals(fileName)) return 2;

        return 1;
    }
}