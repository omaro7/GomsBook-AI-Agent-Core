/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.agent.approval.payload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EpubAuthorApprovalPayload {

    private String fileName;
    private String authorName;
    private String introduction;
    private String profile;
    private List<String> careers = new ArrayList<>();
    private String imageFileName;
    private String imageAlt;

    public String getFileName() { return fileName; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getAuthorName() { return authorName; }

    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getIntroduction() { return introduction; }

    public void setIntroduction(String introduction) { this.introduction = introduction; }

    public String getProfile() { return profile; }

    public void setProfile(String profile) { this.profile = profile; }

    public List<String> getCareers() { return Collections.unmodifiableList(careers); }

    public void setCareers(List<String> careers) { this.careers = careers == null ? new ArrayList<>() : new ArrayList<>(careers); }

    public String getImageFileName() { return imageFileName; }

    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    public String getImageAlt() { return imageAlt; }

    public void setImageAlt(String imageAlt) { this.imageAlt = imageAlt; }
}