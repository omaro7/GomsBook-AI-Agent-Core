/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.model.EpubNavigation;
import kr.co.goms.gomsbook.ai.epub.model.EpubNavigationItem;

public final class EpubNavigationApprovalPayload {

    private final String fileName;
    private final String title;
    private final String language;
    private final String tocTitle;
    private final List<Item> items;

    public EpubNavigationApprovalPayload(String fileName, String title, String language, String tocTitle, List<Item> items) {
        this.fileName = requireText(fileName, "fileName");
        this.title = requireText(title, "title");
        this.language = normalize(language);
        this.tocTitle = requireText(tocTitle, "tocTitle");
        this.items = immutableItems(items);
    }

    public static EpubNavigationApprovalPayload from(String fileName, EpubNavigation navigation) {
        Objects.requireNonNull(navigation, "navigation must not be null.");

        return new EpubNavigationApprovalPayload(
                fileName,
                navigation.getTitle(),
                navigation.getLanguage().orElse(null),
                navigation.getTocTitle(),
                toItems(navigation.getTocItems()));
    }

    public String getFileName() {
        return fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public String getTocTitle() {
        return tocTitle;
    }

    public List<Item> getItems() {
        return items;
    }

    public EpubNavigation toNavigation() {
        EpubNavigation navigation = EpubNavigation.builder()
                .title(title)
                .language(language)
                .tocTitle(tocTitle)
                .tocItems(toNavigationItems(items))
                .includeLandmarks(false)
                .includePageList(false)
                .build();

        navigation.validate();

        return navigation;
    }

    private static List<Item> toItems(List<EpubNavigationItem> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        List<Item> result = new ArrayList<>();

        for (EpubNavigationItem item : source) result.add(Item.from(item));

        return Collections.unmodifiableList(result);
    }

    private static List<EpubNavigationItem> toNavigationItems(List<Item> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        List<EpubNavigationItem> result = new ArrayList<>();

        for (Item item : source) result.add(item.toNavigationItem());

        return Collections.unmodifiableList(result);
    }

    private static List<Item> immutableItems(List<Item> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        List<Item> result = new ArrayList<>();

        for (Item item : source) result.add(Objects.requireNonNull(item, "navigation approval item must not be null."));

        return Collections.unmodifiableList(result);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");

        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;

        return value.trim();
    }

    public static final class Item {

        private final String id;
        private final String label;
        private final String href;
        private final String epubType;
        private final List<Item> children;

        public Item(String id, String label, String href, String epubType, List<Item> children) {
            this.id = normalize(id);
            this.label = requireText(label, "label");
            this.href = requireText(href, "href");
            this.epubType = normalize(epubType);
            this.children = immutableItems(children);
        }

        public static Item from(EpubNavigationItem item) {
            Objects.requireNonNull(item, "navigation item must not be null.");

            return new Item(
                    item.getId().orElse(null),
                    item.getLabel(),
                    item.getHref(),
                    item.getEpubType().orElse(null),
                    toItems(item.getChildren()));
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getHref() {
            return href;
        }

        public String getEpubType() {
            return epubType;
        }

        public List<Item> getChildren() {
            return children;
        }

        public EpubNavigationItem toNavigationItem() {
            EpubNavigationItem.Builder builder = EpubNavigationItem.builder(label, href);

            if (id != null) builder.id(id);
            if (epubType != null) builder.epubType(epubType);
            if (!children.isEmpty()) builder.children(toNavigationItems(children));

            return builder.build();
        }
    }
}