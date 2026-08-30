/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EpubInspectionResult {


    private final Path epubFile;

    private final long fileSize;

    private final int entryCount;

    private final boolean mimetypePresent;

    private final boolean mimetypeValid;

    private final boolean mimetypeStored;

    private final boolean mimetypeFirstEntry;

    private final String mimetype;

    private final boolean containerPresent;

    private final String packageDocumentPath;

    private final boolean packageDocumentPresent;

    private final String epubVersion;

    private final String title;

    private final String language;

    private final String creator;

    private final String identifier;

    private final String uniqueIdentifierId;

    private final int manifestItemCount;

    private final int spineItemCount;

    private final int linearSpineItemCount;

    private final String spineToc;

    private final String pageProgressionDirection;

    private final boolean navigationDocumentPresent;

    private final boolean ncxPresent;

    private final int xhtmlCount;

    private final int cssCount;

    private final int imageCount;

    private final int fontCount;

    private final int audioCount;

    private final int videoCount;

    private final boolean accessModePresent;

    private final boolean accessibilityFeaturePresent;

    private final boolean accessibilityHazardPresent;

    private final boolean accessibilitySummaryPresent;

    private final List<String> entryPaths;


    private EpubInspectionResult(
            Builder builder) {

        this.epubFile = Objects.requireNonNull(builder.epubFile, "EPUB file must not be null.").toAbsolutePath().normalize();

        this.fileSize = builder.fileSize;

        this.entryCount = builder.entryCount;

        this.mimetypePresent = builder.mimetypePresent;

        this.mimetypeValid = builder.mimetypeValid;

        this.mimetypeStored = builder.mimetypeStored;

        this.mimetypeFirstEntry = builder.mimetypeFirstEntry;

        this.mimetype = normalizeOptionalText(builder.mimetype);

        this.containerPresent = builder.containerPresent;

        this.packageDocumentPath = normalizeOptionalEpubPath(builder.packageDocumentPath);

        this.packageDocumentPresent = builder.packageDocumentPresent;

        this.epubVersion = normalizeOptionalText(builder.epubVersion);

        this.title = normalizeOptionalText(builder.title);

        this.language = normalizeOptionalText(builder.language);

        this.creator = normalizeOptionalText(builder.creator);

        this.identifier = normalizeOptionalText(builder.identifier);

        this.uniqueIdentifierId = normalizeOptionalText(builder.uniqueIdentifierId);

        this.manifestItemCount = builder.manifestItemCount;

        this.spineItemCount = builder.spineItemCount;

        this.linearSpineItemCount = builder.linearSpineItemCount;

        this.spineToc = normalizeOptionalText(builder.spineToc);

        this.pageProgressionDirection = normalizeOptionalText(builder.pageProgressionDirection);

        this.navigationDocumentPresent = builder.navigationDocumentPresent;

        this.ncxPresent = builder.ncxPresent;

        this.xhtmlCount = builder.xhtmlCount;

        this.cssCount = builder.cssCount;

        this.imageCount = builder.imageCount;

        this.fontCount = builder.fontCount;

        this.audioCount = builder.audioCount;

        this.videoCount = builder.videoCount;

        this.accessModePresent = builder.accessModePresent;

        this.accessibilityFeaturePresent = builder.accessibilityFeaturePresent;

        this.accessibilityHazardPresent = builder.accessibilityHazardPresent;

        this.accessibilitySummaryPresent = builder.accessibilitySummaryPresent;

        this.entryPaths = List.copyOf(builder.entryPaths);
    }


    public static Builder builder() {

        return new Builder();
    }


    public Path getEpubFile() {

        return epubFile;
    }


    public long getFileSize() {

        return fileSize;
    }


    public int getEntryCount() {

        return entryCount;
    }


    public boolean isMimetypePresent() {

        return mimetypePresent;
    }


    public boolean isMimetypeValid() {

        return mimetypeValid;
    }


    public boolean isMimetypeStored() {

        return mimetypeStored;
    }


    public boolean isMimetypeFirstEntry() {

        return mimetypeFirstEntry;
    }


    public Optional<String> getMimetype() {

        return Optional.ofNullable(mimetype);
    }


    public boolean isContainerPresent() {

        return containerPresent;
    }


    public Optional<String> getPackageDocumentPath() {

        return Optional.ofNullable(packageDocumentPath);
    }


    public boolean isPackageDocumentPresent() {

        return packageDocumentPresent;
    }


    public Optional<String> getEpubVersion() {

        return Optional.ofNullable(epubVersion);
    }


    public Optional<String> getTitle() {

        return Optional.ofNullable(title);
    }


    public Optional<String> getLanguage() {

        return Optional.ofNullable(language);
    }


    public Optional<String> getCreator() {

        return Optional.ofNullable(creator);
    }


    public Optional<String> getIdentifier() {

        return Optional.ofNullable(identifier);
    }


    public Optional<String> getUniqueIdentifierId() {

        return Optional.ofNullable(uniqueIdentifierId);
    }


    public int getManifestItemCount() {

        return manifestItemCount;
    }


    public int getSpineItemCount() {

        return spineItemCount;
    }


    public int getLinearSpineItemCount() {

        return linearSpineItemCount;
    }


    public Optional<String> getSpineToc() {

        return Optional.ofNullable(spineToc);
    }


    public Optional<String> getPageProgressionDirection() {

        return Optional.ofNullable(pageProgressionDirection);
    }


    public boolean isNavigationDocumentPresent() {

        return navigationDocumentPresent;
    }


    public boolean isNcxPresent() {

        return ncxPresent;
    }


    public int getXhtmlCount() {

        return xhtmlCount;
    }


    public int getCssCount() {

        return cssCount;
    }


    public int getImageCount() {

        return imageCount;
    }


    public int getFontCount() {

        return fontCount;
    }


    public int getAudioCount() {

        return audioCount;
    }


    public int getVideoCount() {

        return videoCount;
    }


    public boolean isAccessModePresent() {

        return accessModePresent;
    }


    public boolean isAccessibilityFeaturePresent() {

        return accessibilityFeaturePresent;
    }


    public boolean isAccessibilityHazardPresent() {

        return accessibilityHazardPresent;
    }


    public boolean isAccessibilitySummaryPresent() {

        return accessibilitySummaryPresent;
    }


    public List<String> getEntryPaths() {

        return entryPaths;
    }


    public boolean hasStructuralWarnings() {

        if (!mimetypePresent
                || !mimetypeValid
                || !mimetypeStored
                || !mimetypeFirstEntry
                || !containerPresent
                || packageDocumentPath == null
                || !packageDocumentPresent
                || spineItemCount == 0) {

            return true;
        }

        return epubVersion != null
                && epubVersion.startsWith("3")
                && !navigationDocumentPresent;
    }


    public boolean hasBasicAccessibilityMetadata() {

        return accessModePresent
                && accessibilityFeaturePresent
                && accessibilityHazardPresent;
    }


    public String createSummary() {

        StringBuilder result = new StringBuilder();

        result.append("EPUB inspection completed");

        if (title != null) {

            result.append(": ").append(title);
        }

        result.append(" [version=").append(epubVersion == null ? "unknown" : epubVersion);
        result.append(", manifest=").append(manifestItemCount);
        result.append(", spine=").append(spineItemCount);
        result.append(", xhtml=").append(xhtmlCount);
        result.append(", images=").append(imageCount);
        result.append(", nav=").append(navigationDocumentPresent);
        result.append(", ncx=").append(ncxPresent);
        result.append(']');

        return result.toString();
    }


    @Override
    public String toString() {

        return createSummary();
    }


    private static String normalizeEpubPath(
            String value) {

        if (value == null) {

            return "";
        }

        String normalized = value.trim().replace('\\', '/');

        while (normalized.startsWith("/")) {

            normalized = normalized.substring(1);
        }

        while (normalized.startsWith("./")) {

            normalized = normalized.substring(2);
        }

        return normalized;
    }


    private static String normalizeOptionalEpubPath(
            String value) {

        String normalized = normalizeOptionalText(value);

        return normalized == null ? null : normalizeEpubPath(normalized);
    }


    private static String normalizeOptionalText(
            String value) {

        if (value == null || value.isBlank()) {

            return null;
        }

        return value.trim();
    }


    public static final class Builder {


        private Path epubFile;

        private long fileSize;

        private int entryCount;

        private boolean mimetypePresent;

        private boolean mimetypeValid;

        private boolean mimetypeStored;

        private boolean mimetypeFirstEntry;

        private String mimetype;

        private boolean containerPresent;

        private String packageDocumentPath;

        private boolean packageDocumentPresent;

        private String epubVersion;

        private String title;

        private String language;

        private String creator;

        private String identifier;

        private String uniqueIdentifierId;

        private int manifestItemCount;

        private int spineItemCount;

        private int linearSpineItemCount;

        private String spineToc;

        private String pageProgressionDirection;

        private boolean navigationDocumentPresent;

        private boolean ncxPresent;

        private int xhtmlCount;

        private int cssCount;

        private int imageCount;

        private int fontCount;

        private int audioCount;

        private int videoCount;

        private boolean accessModePresent;

        private boolean accessibilityFeaturePresent;

        private boolean accessibilityHazardPresent;

        private boolean accessibilitySummaryPresent;

        private final List<String> entryPaths = new ArrayList<>();


        private Builder() {
        }


        public Builder epubFile(Path value) {

            this.epubFile = value;

            return this;
        }


        public Builder fileSize(long value) {

            this.fileSize = value;

            return this;
        }


        public Builder entryCount(int value) {

            this.entryCount = value;

            return this;
        }


        public Builder mimetypePresent(boolean value) {

            this.mimetypePresent = value;

            return this;
        }


        public Builder mimetypeValid(boolean value) {

            this.mimetypeValid = value;

            return this;
        }


        public Builder mimetypeStored(boolean value) {

            this.mimetypeStored = value;

            return this;
        }


        public Builder mimetypeFirstEntry(boolean value) {

            this.mimetypeFirstEntry = value;

            return this;
        }


        public Builder mimetype(String value) {

            this.mimetype = value;

            return this;
        }


        public Builder containerPresent(boolean value) {

            this.containerPresent = value;

            return this;
        }


        public Builder packageDocumentPath(String value) {

            this.packageDocumentPath = value;

            return this;
        }


        public Builder packageDocumentPresent(boolean value) {

            this.packageDocumentPresent = value;

            return this;
        }


        public Builder epubVersion(String value) {

            this.epubVersion = value;

            return this;
        }


        public Builder title(String value) {

            this.title = value;

            return this;
        }


        public Builder language(String value) {

            this.language = value;

            return this;
        }


        public Builder creator(String value) {

            this.creator = value;

            return this;
        }


        public Builder identifier(String value) {

            this.identifier = value;

            return this;
        }


        public Builder uniqueIdentifierId(String value) {

            this.uniqueIdentifierId = value;

            return this;
        }


        public Builder manifestItemCount(int value) {

            this.manifestItemCount = value;

            return this;
        }


        public Builder spineItemCount(int value) {

            this.spineItemCount = value;

            return this;
        }


        public Builder linearSpineItemCount(int value) {

            this.linearSpineItemCount = value;

            return this;
        }


        public Builder spineToc(String value) {

            this.spineToc = value;

            return this;
        }


        public Builder pageProgressionDirection(String value) {

            this.pageProgressionDirection = value;

            return this;
        }


        public Builder navigationDocumentPresent(boolean value) {

            this.navigationDocumentPresent = value;

            return this;
        }


        public Builder ncxPresent(boolean value) {

            this.ncxPresent = value;

            return this;
        }


        public Builder xhtmlCount(int value) {

            this.xhtmlCount = value;

            return this;
        }


        public Builder cssCount(int value) {

            this.cssCount = value;

            return this;
        }


        public Builder imageCount(int value) {

            this.imageCount = value;

            return this;
        }


        public Builder fontCount(int value) {

            this.fontCount = value;

            return this;
        }


        public Builder audioCount(int value) {

            this.audioCount = value;

            return this;
        }


        public Builder videoCount(int value) {

            this.videoCount = value;

            return this;
        }


        public Builder accessModePresent(boolean value) {

            this.accessModePresent = value;

            return this;
        }


        public Builder accessibilityFeaturePresent(boolean value) {

            this.accessibilityFeaturePresent = value;

            return this;
        }


        public Builder accessibilityHazardPresent(boolean value) {

            this.accessibilityHazardPresent = value;

            return this;
        }


        public Builder accessibilitySummaryPresent(boolean value) {

            this.accessibilitySummaryPresent = value;

            return this;
        }


        public Builder entryPath(String value) {

            if (value != null && !value.isBlank()) {

                entryPaths.add(value);
            }

            return this;
        }


        public Builder entryPaths(
                Iterable<String> values) {

            if (values == null) {

                return this;
            }

            for (String value : values) {

                entryPath(value);
            }

            return this;
        }


        public EpubInspectionResult build() {

            return new EpubInspectionResult(this);
        }
    }
}