/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.manifest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 * Package Document의 manifest를 읽습니다.
 *
 * <p>
 * 이 Tool은 EPUB 구조 정합성을 검증하지 않습니다.
 * </p>
 *
 * <p>
 * Package Document(content.opf)의 manifest 요소를
 * DOM에서 직접 읽어 item 등록 순서 그대로 반환합니다.
 * </p>
 *
 * <p>
 * manifest ID 중복, href 중복, resource 누락,
 * media-type 오류 등은 이 Tool의 실패 원인이 아닙니다.
 * </p>
 *
 * <p>
 * EPUB 구조 검증은 ValidateEpubStructureTool,
 * EPUB 표준 검증은 EpubCheckTool의 책임입니다.
 * </p>
 */
public final class ReadEpubManifestTool
        implements AgentTool {


    public static final String NAME =
            "read_epub_manifest";

    public static final String TOOL_NAME =
            NAME;

    public static final String DESCRIPTION =
            "Reads manifest items directly from the Package Document "
                    + "of the latest published EPUB for the current project "
                    + "without validating duplicate IDs, resource existence, "
                    + "or manifest references.";


    private final CurrentProjectProvider projectProvider;

    private final PublishDirectoryProvider publishDirectoryProvider;

    private final LatestPublishedEpubResolver publishedEpubResolver;

    private final EpubArchivePackageReader packageReader;


    public ReadEpubManifestTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider) {

        this(
                projectProvider,
                publishDirectoryProvider,
                new LatestPublishedEpubResolver(),
                new EpubArchivePackageReader()
        );
    }


    public ReadEpubManifestTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider,
            LatestPublishedEpubResolver publishedEpubResolver,
            EpubArchivePackageReader packageReader) {

        if (projectProvider == null) {

            throw new IllegalArgumentException(
                    "projectProvider must not be null."
            );
        }

        if (publishDirectoryProvider == null) {

            throw new IllegalArgumentException(
                    "publishDirectoryProvider must not be null."
            );
        }

        if (publishedEpubResolver == null) {

            throw new IllegalArgumentException(
                    "publishedEpubResolver must not be null."
            );
        }

        if (packageReader == null) {

            throw new IllegalArgumentException(
                    "packageReader must not be null."
            );
        }

        this.projectProvider =
                projectProvider;

        this.publishDirectoryProvider =
                publishDirectoryProvider;

        this.publishedEpubResolver =
                publishedEpubResolver;

        this.packageReader =
                packageReader;
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
                                    "EPUB_MANIFEST_PROJECT_MISSING",
                                    "Current EPUB project is not available."
                            )
                    )
                    .build();
        }

        Path publishDirectory =
                publishDirectoryProvider
                        .getPublishDirectory();

        if (publishDirectory == null) {

            return result
                    .valid(false)
                    .issue(
                            errorIssue(
                                    "EPUB_MANIFEST_PUBLISH_DIRECTORY_MISSING",
                                    "Publish directory is not configured."
                            )
                    )
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
                        context
                );

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME
                    )
                    .status(
                            ToolStatus.VALIDATION_FAILED
                    )
                    .validationResult(
                            validation
                    )
                    .message(
                            "EPUB manifest read request is invalid."
                    )
                    .build();
        }

        try {

            Path publishDirectory =
                    publishDirectoryProvider
                            .getPublishDirectory();

            Path epubFile =
                    publishedEpubResolver
                            .resolve(
                                    publishDirectory
                            );

            String packagePath =
                    packageReader
                            .findPackageDocumentPath(
                                    epubFile
                            );

            Document document =
                    packageReader
                            .readPackageDocument(
                                    epubFile,
                                    packagePath
                            );

            Element packageElement =
                    document.getDocumentElement();

            if (packageElement == null) {

                return failure(
                        "EPUB_MANIFEST_PACKAGE_ELEMENT_MISSING",
                        "EPUB package element was not found.",
                        null
                );
            }

            Element manifestElement =
                    findDirectChild(
                            packageElement,
                            "manifest"
                    );

            if (manifestElement == null) {

                return failure(
                        "EPUB_MANIFEST_ELEMENT_MISSING",
                        "EPUB manifest element was not found.",
                        null
                );
            }

            return convertResult(
                    epubFile,
                    packagePath,
                    manifestElement
            );

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_MANIFEST_READ_FAILED",
                    "Failed to read EPUB manifest: "
                            + safeMessage(
                                    exception
                            ),
                    exception
            );
        }
    }


    private ToolResult convertResult(
            Path epubFile,
            String packagePath,
            Element manifestElement) {

        List<Map<String, Object>> items =
                readManifestItems(
                        manifestElement
                );

        return ToolResult.builder()
                .toolName(
                        TOOL_NAME
                )
                .status(
                        ToolStatus.SUCCESS
                )
                .message(
                        "EPUB manifest was read successfully."
                )
                .data(
                        "epubFile",
                        normalizePath(
                                epubFile
                        )
                )
                .data(
                        "packagePath",
                        packagePath
                )
                .data(
                        "manifestItemCount",
                        items.size()
                )
                .data(
                        "items",
                        items
                )
                .build();
    }


    private List<Map<String, Object>> readManifestItems(
            Element manifestElement) {

        List<Map<String, Object>> items =
                new ArrayList<>();

        NodeList children =
                manifestElement
                        .getChildNodes();

        for (int index = 0;
                index < children.getLength();
                index++) {

            Node node =
                    children.item(
                            index
                    );

            if (!(node instanceof Element element)) {

                continue;
            }

            if (!"item".equalsIgnoreCase(
                    getLocalName(
                            element
                    ))) {

                continue;
            }

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "id",
                    readAttribute(
                            element,
                            "id"
                    )
            );

            item.put(
                    "href",
                    readAttribute(
                            element,
                            "href"
                    )
            );

            item.put(
                    "mediaType",
                    readAttribute(
                            element,
                            "media-type"
                    )
            );

            item.put(
                    "properties",
                    readAttribute(
                            element,
                            "properties"
                    )
            );

            item.put(
                    "fallback",
                    readAttribute(
                            element,
                            "fallback"
                    )
            );

            item.put(
                    "mediaOverlay",
                    readAttribute(
                            element,
                            "media-overlay"
                    )
            );

            items.add(
                    Map.copyOf(
                            item
                    )
            );
        }

        return List.copyOf(
                items
        );
    }


    private Element findDirectChild(
            Element parent,
            String localName) {

        if (parent == null
                || localName == null) {

            return null;
        }

        NodeList children =
                parent.getChildNodes();

        for (int index = 0;
                index < children.getLength();
                index++) {

            Node node =
                    children.item(
                            index
                    );

            if (!(node instanceof Element element)) {

                continue;
            }

            if (localName.equalsIgnoreCase(
                    getLocalName(
                            element
                    ))) {

                return element;
            }
        }

        return null;
    }


    private String getLocalName(
            Element element) {

        if (element == null) {

            return "";
        }

        String localName =
                element.getLocalName();

        if (localName != null
                && !localName.isBlank()) {

            return localName;
        }

        String tagName =
                element.getTagName();

        if (tagName == null
                || tagName.isBlank()) {

            return "";
        }

        int separatorIndex =
                tagName.indexOf(
                        ':'
                );

        if (separatorIndex >= 0
                && separatorIndex + 1 < tagName.length()) {

            return tagName.substring(
                    separatorIndex + 1
            );
        }

        return tagName;
    }


    private String readAttribute(
            Element element,
            String name) {

        if (element == null
                || name == null) {

            return "";
        }

        return trimToEmpty(
                element.getAttribute(
                        name
                )
        );
    }


    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                Map.of()
        );

        schema.put(
                "required",
                List.of()
        );

        schema.put(
                "additionalProperties",
                false
        );

        return Map.copyOf(
                schema
        );
    }


    private ToolResult failure(
            String errorCode,
            String errorMessage,
            Throwable cause) {

        String code =
                errorCode == null
                        || errorCode.isBlank()
                        ? "EPUB_MANIFEST_READ_FAILED"
                        : errorCode.trim();

        String message =
                errorMessage == null
                        || errorMessage.isBlank()
                        ? "Failed to read EPUB manifest."
                        : errorMessage.trim();

        ToolResult.Builder builder =
                ToolResult.builder()
                        .toolName(
                                TOOL_NAME
                        )
                        .status(
                                ToolStatus.FAILED
                        )
                        .message(
                                message
                        )
                        .errorCode(
                                code
                        )
                        .errorMessage(
                                message
                        )
                        .issue(
                                errorIssue(
                                        code,
                                        message
                                )
                        );

        if (cause != null) {

            builder.cause(
                    cause
            );

            builder.data(
                    "exceptionType",
                    cause.getClass()
                            .getName()
            );
        }

        return builder.build();
    }


    private ToolIssue errorIssue(
            String code,
            String message) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.ERROR
                )
                .code(
                        code
                )
                .message(
                        message
                )
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

            return "Unknown EPUB manifest read error.";
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