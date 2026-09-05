/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.copyright;

import java.nio.file.Path;

import kr.co.goms.gomsbook.ai.epub.copyright.EpubCopyrightLocator;
import kr.co.goms.gomsbook.ai.epub.copyright.EpubCopyrightReader;
import kr.co.goms.gomsbook.ai.epub.generation.copyright.EpubCopyrightPage;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class ReadEpubCopyrightTool implements AgentTool {

    public static final String NAME = "read_epub_copyright";

    private final CurrentProjectProvider currentProjectProvider;
    private final EpubCopyrightReader copyrightReader;
    private final EpubCopyrightLocator copyrightLocator;

    public ReadEpubCopyrightTool(CurrentProjectProvider currentProjectProvider) {

        this(
                currentProjectProvider,
                new EpubCopyrightLocator(),
                new EpubCopyrightReader()
        );
    }


    public ReadEpubCopyrightTool(CurrentProjectProvider currentProjectProvider, EpubCopyrightLocator copyrightLocator, EpubCopyrightReader copyrightReader) {

        if (currentProjectProvider == null) throw new IllegalArgumentException("currentProjectProvider must not be null.");
        if (copyrightLocator == null) throw new IllegalArgumentException("copyrightLocator must not be null.");
        if (copyrightReader == null) throw new IllegalArgumentException("copyrightReader must not be null.");

        this.currentProjectProvider = currentProjectProvider;
        this.copyrightLocator = copyrightLocator;
        this.copyrightReader = copyrightReader;
    }
    
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "현재 EPUB 프로젝트의 copyright.xhtml 판권 페이지를 읽고 도서명, 저자, 발행처, 발행일, ISBN, 저작권 정보 및 전체 내용을 반환합니다.";
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolContext context) {

        try {

            EpubProjectContext project = currentProjectProvider.getCurrentProject();

            if (project == null) throw new IllegalStateException("Current EPUB project is not available.");

            Path copyrightFile = copyrightLocator.locate(project.getTextDirectory());
                    
            EpubCopyrightPage page = copyrightReader.read(copyrightFile);

            return ToolResult.builder()
                    .toolName(NAME)
                    .status(ToolStatus.SUCCESS)
                    .message("EPUB copyright page was read successfully.")
                    .data("fileName", page.getFileName())
                    .data("title", page.getTitle())
                    .data("publicationDate", page.getPublicationDate())
                    .data("author", page.getAuthor())
                    .data("publisherRepresentative", page.getPublisherRepresentative())
                    .data("publisher", page.getPublisher())
                    .data("address", page.getAddress())
                    .data("email", page.getEmail())
                    .data("website", page.getWebsite())
                    .data("publishingRegistration", page.getPublishingRegistration())
                    .data("isbn", page.getIsbn())
                    .data("price", page.getPrice())
                    .data("supportText", page.getSupportText())
                    .data("copyrightHolder", page.getCopyrightHolder())
                    .data("copyrightYear", page.getCopyrightYear())
                    .data("copyrightText", page.getCopyrightText())
                    .build();

        } catch (RuntimeException exception) {

            return ToolResult.builder()
                    .toolName(NAME)
                    .status(ToolStatus.FAILED)
                    .message("Failed to read EPUB copyright page: " + safeMessage(exception))
                    .cause(exception)
                    .build();
        }
    }

    private String safeMessage(Throwable throwable) {

        if (throwable == null) return "";
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable.getClass().getSimpleName();

        return throwable.getMessage();
    }
}