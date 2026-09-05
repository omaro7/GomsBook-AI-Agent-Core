/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.policy.spine;

public class DefaultEpubSpineOrderPolicy implements EpubSpineOrderPolicy {

    private static final int COVER_ORDER = 10000;
    private static final int AUTHOR_ORDER = 20000;
    private static final int PROLOGUE_ORDER = 30000;
    private static final int CONTENT_BASE_ORDER = 100000;
    private static final int UNKNOWN_CONTENT_ORDER = 700000;
    private static final int EPILOGUE_ORDER = 800000;
    private static final int QUIZ_ORDER = 810000;
    private static final int COPYRIGHT_ORDER = 820000;

    @Override
    public int getOrder(String href) {

        if (href == null || href.isBlank()) return UNKNOWN_CONTENT_ORDER;

        String fileName = getFileName(href).toLowerCase();

        if (isCover(fileName)) return COVER_ORDER;
        if (isAuthor(fileName)) return AUTHOR_ORDER;
        if (isPrologue(fileName)) return PROLOGUE_ORDER;

        Integer contentOrder = getContentOrder(fileName);
        if (contentOrder != null) return contentOrder;

        if (isEpilogue(fileName)) return EPILOGUE_ORDER;
        if (isQuiz(fileName)) return QUIZ_ORDER;
        if (isCopyright(fileName)) return COPYRIGHT_ORDER;

        return UNKNOWN_CONTENT_ORDER;
    }

    @Override
    public boolean isOrderedBefore(String firstHref, String secondHref) {
        return getOrder(firstHref) <= getOrder(secondHref);
    }

    private Integer getContentOrder(String fileName) {

        Integer partOrder = getPartOrder(fileName);
        if (partOrder != null) return partOrder;

        return getChapterOrder(fileName);
    }

    private Integer getPartOrder(String fileName) {

        if (fileName == null || !fileName.matches("part\\d+\\.xhtml")) return null;

        try {

            String numberText = fileName.substring(4, fileName.length() - 6);
            int partNumber = Integer.parseInt(numberText);

            return CONTENT_BASE_ORDER + (partNumber * 10000);

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    private Integer getChapterOrder(String fileName) {

        if (fileName == null || !fileName.matches("chapter\\d+_\\d+\\.xhtml")) return null;

        try {

            String value = fileName.substring(7, fileName.length() - 6);
            String[] values = value.split("_");

            if (values.length != 2) return null;

            int chapterGroup = Integer.parseInt(values[0]);
            int chapterSequence = Integer.parseInt(values[1]);

            return CONTENT_BASE_ORDER + (chapterGroup * 1000) + chapterSequence;

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    private boolean isCover(String fileName) {
        return "cover.xhtml".equals(fileName);
    }

    private boolean isAuthor(String fileName) {
        return "author.xhtml".equals(fileName);
    }

    private boolean isPrologue(String fileName) {
        return "prologue.xhtml".equals(fileName) || "chapter00_1.xhtml".equals(fileName);
    }

    private boolean isEpilogue(String fileName) {
        return "epilogue.xhtml".equals(fileName);
    }

    private boolean isQuiz(String fileName) {
        return "quiz.xhtml".equals(fileName);
    }

    private boolean isCopyright(String fileName) {
        return "copyright.xhtml".equals(fileName);
    }

    private String getFileName(String href) {

        String normalized = href.trim().replace('\\', '/');
        int index = normalized.lastIndexOf('/');

        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }
}