/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.plan.author;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateEpubAuthorPlan {

    private final boolean enabled;
    private final String introduction;
    private final String profile;
    private final List<String> careers;
    private final EpubAuthorImagePlan image;
    private final String xhtmlPath;

    public CreateEpubAuthorPlan(boolean enabled, String introduction, String profile, List<String> careers, EpubAuthorImagePlan image, String xhtmlPath) {
        this.enabled = enabled;
        this.introduction = introduction;
        this.profile = profile;
        this.careers = immutableCopy(careers);
        this.image = image;
        this.xhtmlPath = xhtmlPath;
    }

    public boolean isEnabled() { return enabled; }
    public String getIntroduction() { return introduction; }
    public String getProfile() { return profile; }
    public List<String> getCareers() { return careers; }
    public EpubAuthorImagePlan getImage() { return image; }
    public String getXhtmlPath() { return xhtmlPath; }

    public boolean hasIntroduction() { return hasText(introduction); }
    public boolean hasProfile() { return hasText(profile); }
    public boolean hasCareers() { return !careers.isEmpty(); }
    public boolean hasImage() { return image != null && image.isEnabled(); }
    public boolean hasXhtmlPath() { return hasText(xhtmlPath); }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}