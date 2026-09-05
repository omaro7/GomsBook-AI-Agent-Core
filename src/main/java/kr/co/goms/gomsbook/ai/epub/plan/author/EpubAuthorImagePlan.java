/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.plan.author;

public class EpubAuthorImagePlan {

    private final boolean enabled;
    private final EpubAuthorImageSourceType sourceType;
    private final String prompt;
    private final String sourcePath;
    private final String outputPath;
    private final String alt;

    public EpubAuthorImagePlan(boolean enabled, EpubAuthorImageSourceType sourceType, String prompt, String sourcePath, String outputPath, String alt) {
        this.enabled = enabled;
        this.sourceType = sourceType;
        this.prompt = prompt;
        this.sourcePath = sourcePath;
        this.outputPath = outputPath;
        this.alt = alt;
    }

    public boolean isEnabled() { return enabled; }
    public EpubAuthorImageSourceType getSourceType() { return sourceType; }
    public String getPrompt() { return prompt; }
    public String getSourcePath() { return sourcePath; }
    public String getOutputPath() { return outputPath; }
    public String getAlt() { return alt; }

    public boolean isGenerated() { return sourceType == EpubAuthorImageSourceType.GENERATED; }
    public boolean isUploaded() { return sourceType == EpubAuthorImageSourceType.UPLOADED; }
    public boolean isExisting() { return sourceType == EpubAuthorImageSourceType.EXISTING; }
    public boolean isNone() { return sourceType == null || sourceType == EpubAuthorImageSourceType.NONE; }

    public boolean hasPrompt() { return hasText(prompt); }
    public boolean hasSourcePath() { return hasText(sourcePath); }
    public boolean hasOutputPath() { return hasText(outputPath); }
    public boolean hasAlt() { return hasText(alt); }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}