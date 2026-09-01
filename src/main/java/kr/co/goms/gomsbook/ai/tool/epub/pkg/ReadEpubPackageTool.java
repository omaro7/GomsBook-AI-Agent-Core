/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.pkg;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import kr.co.goms.gomsbook.ai.epub.service.EpubArchivePackageReader;
import kr.co.goms.gomsbook.ai.epub.service.LatestPublishedEpubResolver;
import kr.co.goms.gomsbook.ai.epub.service.PublishDirectoryProvider;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;


/**
 * 현재 EPUB 프로젝트의 최신 출판 EPUB 파일에서
 * Package Document의 기본 정보를 읽습니다.
 *
 * <p>
 * 이 Tool은 EPUB 구조 정합성을 검증하지 않습니다.
 * metadata, manifest, spine 전체 모델도 생성하지 않습니다.
 * </p>
 *
 * <p>
 * Package Document(content.opf)의 package 요소에서
 * version, unique-identifier, prefix, xml:lang, dir 속성만 읽습니다.
 * </p>
 *
 * <p>
 * EPUB 구조 검증은 ValidateEpubStructureTool,
 * EPUB 표준 검증은 EpubCheckTool의 책임입니다.
 * </p>
 */
public final class ReadEpubPackageTool implements AgentTool {


    public static final String NAME = "read_epub_package";

    public static final String TOOL_NAME = NAME;

    public static final String DESCRIPTION =
            "Reads package-level information from the latest published EPUB "
            + "for the current project without validating metadata, manifest, spine, "
            + "or resource references.";


    private final CurrentProjectProvider projectProvider;

    private final PublishDirectoryProvider publishDirectoryProvider;

    private final LatestPublishedEpubResolver publishedEpubResolver;

    private final EpubArchivePackageReader packageReader;


    public ReadEpubPackageTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider) {

        this(
                projectProvider,
                publishDirectoryProvider,
                new LatestPublishedEpubResolver(),
                new EpubArchivePackageReader());
    }


    public ReadEpubPackageTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            LatestPublishedEpubResolver publishedEpubResolver,
            EpubArchivePackageReader packageReader) {

        if (projectProvider == null) {

            throw new IllegalArgumentException(
                    "projectProvider must not be null.");
        }

        if (publishDirectoryProvider == null) {

            throw new IllegalArgumentException(
                    "publishDirectoryProvider must not be null.");
        }

        if (publishedEpubResolver == null) {

            throw new IllegalArgumentException(
                    "publishedEpubResolver must not be null.");
        }

        if (packageReader == null) {

            throw new IllegalArgumentException(
                    "packageReader must not be null.");
        }

        this.projectProvider = projectProvider;

        this.publishDirectoryProvider = publishDirectoryProvider;

        this.publishedEpubResolver = publishedEpubResolver;

        this.packageReader = packageReader;
    }


    @Override
    public String getName() {

        return TOOL_NAME;
    }


    @Override
    public String getDescription() {

        return DESCRIPTION;
    }


    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult.Builder result =
                ToolValidationResult.builder();

        EpubProjectContext project =
                projectProvider.getCurrentProject();

        if (project == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_PACKAGE_PROJECT_MISSING",
                                    "Current EPUB project is not available."))
                    .build();
        }

        Path publishDirectory =
                publishDirectoryProvider.getPublishDirectory();

        if (publishDirectory == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_PACKAGE_PUBLISH_DIRECTORY_MISSING",
                                    "Publish directory is not configured."))
                    .build();
        }

        return result
                .valid(true)
                .build();
    }


    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult validation =
                validate(
                        request,
                        context);

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.VALIDATION_FAILED)
                    .validationResult(validation)
                    .message("EPUB package read request is invalid.")
                    .build();
        }

        try {

            Path publishDirectory =
                    publishDirectoryProvider.getPublishDirectory();

            Path epubFile =
                    publishedEpubResolver.resolve(
                            publishDirectory);

            String packagePath =
                    packageReader.findPackageDocumentPath(
                            epubFile);

            Document document =
                    packageReader.readPackageDocument(
                            epubFile,
                            packagePath);

            Element packageElement =
                    document.getDocumentElement();

            if (packageElement == null) {

                return failure(
                        "EPUB_PACKAGE_ELEMENT_MISSING",
                        "EPUB package element was not found.",
                        null);
            }

            return convertResult(
                    epubFile,
                    packagePath,
                    packageElement);

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_PACKAGE_READ_FAILED",
                    "Failed to read EPUB package: "
                            + safeMessage(exception),
                    exception);
        }
    }


    private ToolResult convertResult(
            Path epubFile,
            String packagePath,
            Element packageElement) {

        String version =
                trimToEmpty(
                        packageElement.getAttribute(
                                "version"));

        String uniqueIdentifier =
                trimToEmpty(
                        packageElement.getAttribute(
                                "unique-identifier"));

        String prefix =
                trimToEmpty(
                        packageElement.getAttribute(
                                "prefix"));

        String language =
                readLanguage(
                        packageElement);

        String direction =
                trimToEmpty(
                        packageElement.getAttribute(
                                "dir"));

        return ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.SUCCESS)
                .message("EPUB package information was read successfully.")
                .data(
                        "epubFile",
                        normalizePath(epubFile))
                .data(
                        "packagePath",
                        packagePath)
                .data(
                        "version",
                        version)
                .data(
                        "uniqueIdentifier",
                        uniqueIdentifier)
                .data(
                        "prefix",
                        prefix)
                .data(
                        "language",
                        language)
                .data(
                        "direction",
                        direction)
                .build();
    }


    private String readLanguage(
            Element packageElement) {

        String language =
                trimToEmpty(
                        packageElement.getAttributeNS(
                                XMLConstants.XML_NS_URI,
                                "lang"));

        if (!language.isEmpty()) {

            return language;
        }

        return trimToEmpty(
                packageElement.getAttribute(
                        "xml:lang"));
    }


    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object");

        schema.put(
                "properties",
                Map.of());

        schema.put(
                "required",
                List.of());

        schema.put(
                "additionalProperties",
                false);

        return Map.copyOf(
                schema);
    }


    private ToolResult failure(
            String errorCode,
            String errorMessage,
            Throwable cause) {

        String code =
                errorCode == null
                        || errorCode.isBlank()
                        ? "EPUB_PACKAGE_READ_FAILED"
                        : errorCode.trim();

        String message =
                errorMessage == null
                        || errorMessage.isBlank()
                        ? "Failed to read EPUB package."
                        : errorMessage.trim();

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(TOOL_NAME)
                        .status(ToolStatus.FAILED)
                        .message(message)
                        .errorCode(code)
                        .errorMessage(message)
                        .issue(
                                errorIssue(
                                        code,
                                        message));

        if (cause != null) {

            builder.cause(
                    cause);

            builder.data(
                    "exceptionType",
                    cause.getClass().getName());
        }

        return builder.build();
    }


    private ToolIssue errorIssue(
            String code,
            String message) {

        return ToolIssue.builder()
                .severity(ToolIssueSeverity.ERROR)
                .code(code)
                .message(message)
                .build();
    }


    private String normalizePath(
            Path path) {

        if (path == null) {

            return "";
        }

        return path
                .toAbsolutePath()
                .normalize()
                .toString();
    }


    private String trimToEmpty(
            String value) {

        if (value == null) {

            return "";
        }

        return value.trim();
    }


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null) {

            return "Unknown EPUB package read error.";
        }

        String message =
                throwable.getMessage();

        if (message == null
                || message.isBlank()) {

            return throwable
                    .getClass()
                    .getSimpleName();
        }

        return message.trim();
    }
}