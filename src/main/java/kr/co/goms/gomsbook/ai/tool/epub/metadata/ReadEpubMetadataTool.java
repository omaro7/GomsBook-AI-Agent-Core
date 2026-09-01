/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.metadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
 * Package Document의 metadata를 읽습니다.
 *
 * <p>
 * 이 Tool은 EPUB 구조 정합성을 검증하지 않습니다.
 * </p>
 *
 * <p>
 * Package Document(content.opf)의 metadata 요소를
 * DOM에서 직접 읽어 등록된 순서 그대로 반환합니다.
 * </p>
 *
 * <p>
 * manifest ID 중복, resource 누락, spine 참조 오류 등은
 * 이 Tool의 실패 원인이 아닙니다.
 * </p>
 *
 * <p>
 * EPUB 구조 검증은 ValidateEpubStructureTool,
 * EPUB 표준 검증은 EpubCheckTool의 책임입니다.
 * </p>
 */
public final class ReadEpubMetadataTool
        implements AgentTool {


    public static final String NAME =
            "read_epub_metadata";

    public static final String TOOL_NAME =
            NAME;

    public static final String DESCRIPTION =
            "Reads metadata directly from the Package Document "
                    + "of the latest published EPUB for the current project "
                    + "without validating manifest, spine, or resource references.";


    private final CurrentProjectProvider projectProvider;

    private final PublishDirectoryProvider publishDirectoryProvider;

    private final LatestPublishedEpubResolver publishedEpubResolver;

    private final EpubArchivePackageReader packageReader;


    public ReadEpubMetadataTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider) {

        this(
                projectProvider,
                publishDirectoryProvider,
                new LatestPublishedEpubResolver(),
                new EpubArchivePackageReader()
        );
    }


    public ReadEpubMetadataTool(
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
                                    "EPUB_METADATA_PROJECT_MISSING",
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
                                    "EPUB_METADATA_PUBLISH_DIRECTORY_MISSING",
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
                            "EPUB metadata read request is invalid."
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
                        "EPUB_METADATA_PACKAGE_ELEMENT_MISSING",
                        "EPUB package element was not found.",
                        null
                );
            }

            Element metadataElement =
                    findDirectChild(
                            packageElement,
                            "metadata"
                    );

            if (metadataElement == null) {

                return failure(
                        "EPUB_METADATA_ELEMENT_MISSING",
                        "EPUB metadata element was not found.",
                        null
                );
            }

            return convertResult(
                    epubFile,
                    packagePath,
                    packageElement,
                    metadataElement
            );

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_METADATA_READ_FAILED",
                    "Failed to read EPUB metadata: "
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
            Element packageElement,
            Element metadataElement) {

        List<Map<String, Object>> entries =
                readMetadataEntries(
                        metadataElement
                );

        String uniqueIdentifier =
                trimToEmpty(
                        packageElement.getAttribute(
                                "unique-identifier"
                        )
                );

        return ToolResult.builder()
                .toolName(
                        TOOL_NAME
                )
                .status(
                        ToolStatus.SUCCESS
                )
                .message(
                        "EPUB metadata was read successfully."
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
                        "uniqueIdentifier",
                        uniqueIdentifier
                )
                .data(
                        "metadataCount",
                        entries.size()
                )
                .data(
                        "metadata",
                        entries
                )
                .build();
    }


    private List<Map<String, Object>> readMetadataEntries(
            Element metadataElement) {

        List<Map<String, Object>> entries =
                new ArrayList<>();

        NodeList children =
                metadataElement
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

            Map<String, Object> entry =
                    new LinkedHashMap<>();

            entry.put(
                    "name",
                    element.getNodeName()
            );

            entry.put(
                    "localName",
                    getLocalName(
                            element
                    )
            );

            entry.put(
                    "namespace",
                    trimToEmpty(
                            element.getNamespaceURI()
                    )
            );

            entry.put(
                    "value",
                    readText(
                            element
                    )
            );

            entry.put(
                    "attributes",
                    readAttributes(
                            element
                    )
            );

            entries.add(
                    Map.copyOf(
                            entry
                    )
            );
        }

        return List.copyOf(
                entries
        );
    }


    private Map<String, String> readAttributes(
            Element element) {

        Map<String, String> attributes =
                new LinkedHashMap<>();

        NamedNodeMap attributeNodes =
                element.getAttributes();

        for (int index = 0;
                index < attributeNodes.getLength();
                index++) {

            Node attribute =
                    attributeNodes.item(
                            index
                    );

            if (attribute == null) {

                continue;
            }

            attributes.put(
                    attribute.getNodeName(),
                    trimToEmpty(
                            attribute.getNodeValue()
                    )
            );
        }

        return Map.copyOf(
                attributes
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


    private String readText(
            Element element) {

        if (element == null) {

            return "";
        }

        String value =
                element.getTextContent();

        return trimToEmpty(
                value
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
                        ? "EPUB_METADATA_READ_FAILED"
                        : errorCode.trim();

        String message =
                errorMessage == null
                        || errorMessage.isBlank()
                        ? "Failed to read EPUB metadata."
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

            return "Unknown EPUB metadata read error.";
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