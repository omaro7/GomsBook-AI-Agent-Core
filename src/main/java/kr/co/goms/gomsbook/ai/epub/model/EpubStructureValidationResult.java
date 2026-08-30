/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * EPUB 구조 검증 결과를 표현합니다.
 *
 * <p>Package Document, manifest, spine, navigation 구조 검증 결과와
 * 발견된 ERROR 및 WARNING Issue를 보관합니다.</p>
 */
public final class EpubStructureValidationResult {


    private final List<EpubStructureValidationIssue> issues = new ArrayList<>();

    private String packagePath;

    private String navId;

    private String navHref;

    private int manifestItemCount;

    private int spineItemCount;


    public String getPackagePath() {

        return packagePath;
    }


    public void setPackagePath(
            String packagePath) {

        this.packagePath = trimToNull(packagePath);
    }


    public Optional<String> getNavId() {

        return Optional.ofNullable(navId);
    }


    public void setNavId(
            String navId) {

        this.navId = trimToNull(navId);
    }


    public Optional<String> getNavHref() {

        return Optional.ofNullable(navHref);
    }


    public void setNavHref(
            String navHref) {

        this.navHref = trimToNull(navHref);
    }


    public int getManifestItemCount() {

        return manifestItemCount;
    }


    public void setManifestItemCount(
            int manifestItemCount) {

        if (manifestItemCount < 0) {

            throw new IllegalArgumentException(
                    "manifestItemCount must not be negative.");
        }

        this.manifestItemCount = manifestItemCount;
    }


    public int getSpineItemCount() {

        return spineItemCount;
    }


    public void setSpineItemCount(
            int spineItemCount) {

        if (spineItemCount < 0) {

            throw new IllegalArgumentException(
                    "spineItemCount must not be negative.");
        }

        this.spineItemCount = spineItemCount;
    }


    public List<EpubStructureValidationIssue> getIssues() {

        return Collections.unmodifiableList(issues);
    }


    public void addIssue(
            EpubStructureValidationIssue issue) {

        if (issue == null) {

            return;
        }

        issues.add(issue);
    }


    public boolean isValid() {

        return getErrorCount() == 0;
    }


    public boolean hasIssues() {

        return !issues.isEmpty();
    }


    public boolean hasErrors() {

        return getErrorCount() > 0;
    }


    public boolean hasWarnings() {

        return getWarningCount() > 0;
    }


    public int getIssueCount() {

        return issues.size();
    }


    public int getErrorCount() {

        int count = 0;

        for (EpubStructureValidationIssue issue : issues) {

            if (issue.isError()) {

                count++;
            }
        }

        return count;
    }


    public int getWarningCount() {

        int count = 0;

        for (EpubStructureValidationIssue issue : issues) {

            if (issue.isWarning()) {

                count++;
            }
        }

        return count;
    }


    public List<EpubStructureValidationIssue> getErrors() {

        List<EpubStructureValidationIssue> result = new ArrayList<>();

        for (EpubStructureValidationIssue issue : issues) {

            if (issue.isError()) {

                result.add(issue);
            }
        }

        return Collections.unmodifiableList(result);
    }


    public List<EpubStructureValidationIssue> getWarnings() {

        List<EpubStructureValidationIssue> result = new ArrayList<>();

        for (EpubStructureValidationIssue issue : issues) {

            if (issue.isWarning()) {

                result.add(issue);
            }
        }

        return Collections.unmodifiableList(result);
    }


    public String createSummary() {

        return "EPUB structure validation: "
                + (isValid() ? "VALID" : "INVALID")
                + ", manifestItems=" + manifestItemCount
                + ", spineItems=" + spineItemCount
                + ", errors=" + getErrorCount()
                + ", warnings=" + getWarningCount();
    }


    @Override
    public String toString() {

        return "EpubStructureValidationResult{"
                + "valid=" + isValid()
                + ", packagePath='" + packagePath + '\''
                + ", navId='" + navId + '\''
                + ", navHref='" + navHref + '\''
                + ", manifestItemCount=" + manifestItemCount
                + ", spineItemCount=" + spineItemCount
                + ", errorCount=" + getErrorCount()
                + ", warningCount=" + getWarningCount()
                + ", issueCount=" + issues.size()
                + '}';
    }


    private static String trimToNull(
            String value) {

        if (value == null) {

            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}