/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class EpubArchiveReader {


    public boolean exists( Path epubFile, String entryPath) {

        validateEpubFile(epubFile);

        String normalizedEntryPath = normalizeEntryPath(entryPath);

        if (normalizedEntryPath == null) {

            return false;
        }

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            ZipEntry entry = zipFile.getEntry(normalizedEntryPath);

            return entry != null;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to inspect EPUB archive: " + epubFile,
                    exception);
        }
    }


    public String readText( Path epubFile, String entryPath) {

        validateEpubFile(epubFile);

        String normalizedEntryPath = requireEntryPath(entryPath);

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            ZipEntry entry = zipFile.getEntry(normalizedEntryPath);

            if (entry == null) {

                throw new IllegalStateException(
                        "EPUB archive entry was not found: " + normalizedEntryPath);
            }

            if (entry.isDirectory()) {

                throw new IllegalStateException(
                        "EPUB archive entry is a directory: " + normalizedEntryPath);
            }

            return readText(zipFile, entry);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB archive entry: " + normalizedEntryPath,
                    exception);
        }
    }


    public List<String> listEntries( Path epubFile) {

        validateEpubFile(epubFile);

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            List<String> entries = new ArrayList<>();

            for (ZipEntry entry : Collections.list(zipFile.entries())) {

                if (entry == null) {

                    continue;
                }

                if (entry.isDirectory()) {

                    continue;
                }

                String path = normalizeEntryPath(entry.getName());

                if (path != null) {

                    entries.add(path);
                }
            }

            return List.copyOf(entries);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to list EPUB archive entries: " + epubFile,
                    exception);
        }
    }


    public int getEntryCount( Path epubFile) {

        validateEpubFile(epubFile);

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            return zipFile.size();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB archive entry count: " + epubFile,
                    exception);
        }
    }


    public long getEntrySize( Path epubFile, String entryPath) {

        validateEpubFile(epubFile);

        String normalizedEntryPath = requireEntryPath(entryPath);

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            ZipEntry entry = zipFile.getEntry(normalizedEntryPath);

            if (entry == null) {

                return -1L;
            }

            return entry.getSize();

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB archive entry size: " + normalizedEntryPath,
                    exception);
        }
    }


    public boolean isStored( Path epubFile, String entryPath) {

        validateEpubFile(epubFile);

        String normalizedEntryPath = requireEntryPath(entryPath);

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            ZipEntry entry = zipFile.getEntry(normalizedEntryPath);

            if (entry == null) {

                return false;
            }

            return entry.getMethod() == ZipEntry.STORED;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to inspect EPUB archive entry method: " + normalizedEntryPath,
                    exception);
        }
    }


    public String getFirstEntryPath( Path epubFile) {

        validateEpubFile(epubFile);

        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {

            List<? extends ZipEntry> entries = Collections.list(zipFile.entries());

            if (entries.isEmpty()) {

                return null;
            }

            ZipEntry entry = entries.get(0);

            if (entry == null) {

                return null;
            }

            return normalizeEntryPath(entry.getName());

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read first EPUB archive entry: " + epubFile,
                    exception);
        }
    }


    private String readText( ZipFile zipFile, ZipEntry entry) throws IOException {

        try (InputStream inputStream = zipFile.getInputStream(entry)) {

            byte[] bytes = inputStream.readAllBytes();

            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
    }


    private void validateEpubFile( Path epubFile) {

        if (epubFile == null) {

            throw new IllegalArgumentException(
                    "epubFile must not be null.");
        }

        Path normalized = epubFile.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) {

            throw new IllegalStateException(
                    "EPUB file does not exist: " + normalized);
        }

        if (!Files.isRegularFile(normalized)) {

            throw new IllegalStateException(
                    "EPUB path is not a regular file: " + normalized);
        }

        if (!Files.isReadable(normalized)) {

            throw new IllegalStateException(
                    "EPUB file is not readable: " + normalized);
        }

        Path fileNamePath = normalized.getFileName();

        if (fileNamePath == null) {

            throw new IllegalStateException(
                    "EPUB file name is not available: " + normalized);
        }

        String fileName = fileNamePath.toString().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".epub")) {

            throw new IllegalStateException(
                    "EPUB archive must use the .epub extension: " + normalized);
        }
    }


    private String requireEntryPath( String entryPath) {

        String normalized = normalizeEntryPath(entryPath);

        if (normalized == null) {

            throw new IllegalArgumentException(
                    "entryPath must not be empty.");
        }

        return normalized;
    }


    private String normalizeEntryPath( String value) {

        if (value == null) {

            return null;
        }

        String normalized = value.trim().replace('\\', '/');

        if (normalized.isEmpty()) {

            return null;
        }

        while (normalized.startsWith("/")) {

            normalized = normalized.substring(1);
        }

        while (normalized.startsWith("./")) {

            normalized = normalized.substring(2);
        }

        if (normalized.isEmpty()) {

            return null;
        }

        return normalized;
    }
}