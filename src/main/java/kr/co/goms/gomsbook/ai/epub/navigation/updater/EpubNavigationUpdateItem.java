/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.navigation.updater;

import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;

public class EpubNavigationUpdateItem {

    private final EpubNavigationItem item;

    private final EpubNavigationInsertPosition position;

    private final String referenceHref;


    public EpubNavigationUpdateItem(
            EpubNavigationItem item,
            EpubNavigationInsertPosition position,
            String referenceHref) {

        if (item == null) throw new IllegalArgumentException("item must not be null.");
        if (position == null) throw new IllegalArgumentException("position must not be null.");

        this.item = item;
        this.position = position;
        this.referenceHref = normalize(referenceHref);
    }


    public static EpubNavigationUpdateItem last(EpubNavigationItem item) {

        return new EpubNavigationUpdateItem(item, EpubNavigationInsertPosition.LAST, null);
    }


    public static EpubNavigationUpdateItem first(EpubNavigationItem item) {

        return new EpubNavigationUpdateItem(item, EpubNavigationInsertPosition.FIRST, null);
    }


    public static EpubNavigationUpdateItem before(EpubNavigationItem item, String referenceHref) {

        return new EpubNavigationUpdateItem(item, EpubNavigationInsertPosition.BEFORE, referenceHref);
    }


    public static EpubNavigationUpdateItem after(EpubNavigationItem item, String referenceHref) {

        return new EpubNavigationUpdateItem(item, EpubNavigationInsertPosition.AFTER, referenceHref);
    }


    public EpubNavigationItem getItem() {

        return item;
    }


    public EpubNavigationInsertPosition getPosition() {

        return position;
    }


    public String getReferenceHref() {

        return referenceHref;
    }


    private String normalize(String value) {

        if (value == null) return null;

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }
}