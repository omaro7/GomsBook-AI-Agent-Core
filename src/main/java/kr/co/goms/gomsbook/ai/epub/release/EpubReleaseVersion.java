/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.release;

import java.util.Objects;

public final class EpubReleaseVersion implements Comparable<EpubReleaseVersion> {

    private final int major;

    private final int minor;

    private final int patch;

    private EpubReleaseVersion(int major, int minor, int patch) {

        this.major = major;

        this.minor = minor;

        this.patch = patch;

    }

    public static EpubReleaseVersion parse(String version) {

        if (version == null || version.isBlank()) throw new IllegalArgumentException("version must not be blank.");

        String normalizedVersion = version.trim();

        if (!normalizedVersion.matches("^\\d+\\.\\d+\\.\\d+$")) throw new IllegalArgumentException("version must use semantic version format such as 1.0.0.");

        String[] parts = normalizedVersion.split("\\.");

        try {

            int major = Integer.parseInt(parts[0]);

            int minor = Integer.parseInt(parts[1]);

            int patch = Integer.parseInt(parts[2]);

            return new EpubReleaseVersion(
                    major,
                    minor,
                    patch
            );

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException("Invalid EPUB release version: " + normalizedVersion, exception);

        }

    }

    public int getMajor() {

        return major;

    }

    public int getMinor() {

        return minor;

    }

    public int getPatch() {

        return patch;

    }

    public boolean isGreaterThan(EpubReleaseVersion other) {

        return compareTo(other) > 0;

    }

    public boolean isLessThan(EpubReleaseVersion other) {

        return compareTo(other) < 0;

    }

    public boolean isSameAs(EpubReleaseVersion other) {

        return compareTo(other) == 0;

    }

    @Override
    public int compareTo(EpubReleaseVersion other) {

        if (other == null) throw new IllegalArgumentException("other must not be null.");

        int comparison = Integer.compare(major, other.major);

        if (comparison != 0) return comparison;

        comparison = Integer.compare(minor, other.minor);

        if (comparison != 0) return comparison;

        return Integer.compare(patch, other.patch);

    }

    @Override
    public boolean equals(Object object) {

        if (this == object) return true;

        if (!(object instanceof EpubReleaseVersion other)) return false;

        return major == other.major
                && minor == other.minor
                && patch == other.patch;

    }

    @Override
    public int hashCode() {

        return Objects.hash(
                major,
                minor,
                patch
        );

    }

    @Override
    public String toString() {

        return major + "." + minor + "." + patch;

    }

}