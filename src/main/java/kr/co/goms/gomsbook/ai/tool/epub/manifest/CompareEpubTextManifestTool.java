/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.manifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
 * 최신 EPUB 내부 Text 디렉터리의 문서 파일과
 * content.opf manifest에 등록된 문서 리소스를 비교합니다.
 */
public final class CompareEpubTextManifestTool
        implements AgentTool {


    public static final String NAME =
            "compare_epub_text_manifest";

    public static final String TOOL_NAME =
            NAME;

    public static final String DESCRIPTION =
            "Compares XHTML/HTML files in the EPUB Text directory "
                    + "with document resources registered in content.opf manifest.";


    private static final String XHTML_MEDIA_TYPE =
            "application/xhtml+xml";

    private static final String HTML_MEDIA_TYPE =
            "text/html";


    private final CurrentProjectProvider projectProvider;

    private final PublishDirectoryProvider publishDirectoryProvider;

    private final LatestPublishedEpubResolver publishedEpubResolver;

    private final EpubArchivePackageReader packageReader;


    public CompareEpubTextManifestTool(
            CurrentProjectProvider projectProvider,
            PublishDirectoryProvider publishDirectoryProvider) {

        this(
                projectProvider,
                publishDirectoryProvider,
                new LatestPublishedEpubResolver(),
                new EpubArchivePackageReader()
        );
    }


    public CompareEpubTextManifestTool(
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
                                    "EPUB_TEXT_MANIFEST_PROJECT_MISSING",
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
                                    "EPUB_TEXT_MANIFEST_PUBLISH_DIRECTORY_MISSING",
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
                            "EPUB Text/manifest comparison request is invalid."
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
                        "EPUB_TEXT_MANIFEST_PACKAGE_ELEMENT_MISSING",
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
                        "EPUB_TEXT_MANIFEST_ELEMENT_MISSING",
                        "EPUB manifest element was not found.",
                        null
                );
            }

            String packageDirectory =
                    resolvePackageDirectory(
                            packagePath
                    );

            List<ManifestDocument> manifestDocuments =
                    readManifestDocuments(
                            manifestElement,
                            packageDirectory
                    );

            Set<String> textFiles =
                    readTextFiles(
                            epubFile,
                            packageDirectory
                    );

            return compare(
                    epubFile,
                    packagePath,
                    textFiles,
                    manifestDocuments
            );

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_TEXT_MANIFEST_COMPARE_FAILED",
                    "Failed to compare EPUB Text files and manifest: "
                            + safeMessage(
                                    exception
                            ),
                    exception
            );
        }
    }


    private ToolResult compare(
            Path epubFile,
            String packagePath,
            Set<String> textFiles,
            List<ManifestDocument> manifestDocuments) {

        Set<String> manifestPaths =
                new LinkedHashSet<>();

        for (ManifestDocument document
                : manifestDocuments) {

            manifestPaths.add(
                    document.archivePath()
            );
        }

        List<Map<String, Object>> matched =
                new ArrayList<>();

        List<Map<String, Object>> notInManifest =
                new ArrayList<>();

        List<Map<String, Object>> fileMissing =
                new ArrayList<>();


        for (String textFile
                : textFiles) {

            if (manifestPaths.contains(
                    textFile
            )) {

                matched.add(
                        fileResult(
                                textFile,
                                "MATCHED"
                        )
                );

            } else {

                notInManifest.add(
                        fileResult(
                                textFile,
                                "NOT_IN_MANIFEST"
                        )
                );
            }
        }


        for (ManifestDocument document
                : manifestDocuments) {

            if (textFiles.contains(
                    document.archivePath()
            )) {

                continue;
            }

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "path",
                    document.archivePath()
            );

            item.put(
                    "id",
                    document.id()
            );

            item.put(
                    "href",
                    document.href()
            );

            item.put(
                    "mediaType",
                    document.mediaType()
            );

            item.put(
                    "status",
                    "FILE_MISSING"
            );

            fileMissing.add(
                    Map.copyOf(
                            item
                    )
            );
        }


        Comparator<Map<String, Object>> comparator =
                Comparator.comparing(
                        value ->
                                String.valueOf(
                                        value.get(
                                                "path"
                                        )
                                ),
                        String.CASE_INSENSITIVE_ORDER
                );

        matched.sort(
                comparator
        );

        notInManifest.sort(
                comparator
        );

        fileMissing.sort(
                comparator
        );


        return ToolResult.builder()
                .toolName(
                        TOOL_NAME
                )
                .status(
                        ToolStatus.SUCCESS
                )
                .message(
                        createMessage(
                                matched.size(),
                                notInManifest.size(),
                                fileMissing.size()
                        )
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
                        "textFileCount",
                        textFiles.size()
                )
                .data(
                        "manifestDocumentCount",
                        manifestDocuments.size()
                )
                .data(
                        "matchedCount",
                        matched.size()
                )
                .data(
                        "notInManifestCount",
                        notInManifest.size()
                )
                .data(
                        "fileMissingCount",
                        fileMissing.size()
                )
                .data(
                        "matched",
                        List.copyOf(
                                matched
                        )
                )
                .data(
                        "notInManifest",
                        List.copyOf(
                                notInManifest
                        )
                )
                .data(
                        "fileMissing",
                        List.copyOf(
                                fileMissing
                        )
                )
                .build();
    }


    private List<ManifestDocument> readManifestDocuments(
            Element manifestElement,
            String packageDirectory) {

        List<ManifestDocument> documents =
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

            String mediaType =
                    readAttribute(
                            element,
                            "media-type"
                    );

            if (!isDocumentMediaType(
                    mediaType
            )) {

                continue;
            }

            String id =
                    readAttribute(
                            element,
                            "id"
                    );

            String href =
                    readAttribute(
                            element,
                            "href"
                    );

            if (href.isBlank()) {

                continue;
            }

            String archivePath =
                    resolveArchivePath(
                            packageDirectory,
                            href
                    );

            if (!isTextPath(
                    archivePath
            )) {

                continue;
            }

            documents.add(
                    new ManifestDocument(
                            id,
                            href,
                            mediaType,
                            archivePath
                    )
            );
        }

        return List.copyOf(
                documents
        );
    }


    private Set<String> readTextFiles(
            Path epubFile,
            String packageDirectory) {

        Set<String> files =
                new LinkedHashSet<>();

        String textDirectory =
                normalizeArchivePath(
                        packageDirectory
                                + "/Text/"
                );

        try (ZipFile zipFile =
                new ZipFile(
                        epubFile.toFile()
                )) {

            zipFile.stream()
                    .filter(
                            entry ->
                                    !entry.isDirectory()
                    )
                    .map(
                            ZipEntry::getName
                    )
                    .map(
                            this::normalizeArchivePath
                    )
                    .filter(
                            path ->
                                    path.startsWith(
                                            textDirectory
                                    )
                    )
                    .filter(
                            this::isDocumentFile
                    )
                    .forEach(
                            files::add
                    );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to read EPUB archive: "
                            + epubFile,
                    exception
            );
        }

        return files;
    }


    private boolean isDocumentMediaType(
            String mediaType) {

        if (mediaType == null) {

            return false;
        }

        return XHTML_MEDIA_TYPE.equalsIgnoreCase(
                mediaType
        )
                || HTML_MEDIA_TYPE.equalsIgnoreCase(
                        mediaType
                );
    }


    private boolean isDocumentFile(
            String path) {

        if (path == null) {

            return false;
        }

        String lower =
                path.toLowerCase(
                        Locale.ROOT
                );

        return lower.endsWith(
                ".xhtml"
        )
                || lower.endsWith(
                        ".html"
                )
                || lower.endsWith(
                        ".htm"
                );
    }


    private boolean isTextPath(
            String path) {

        if (path == null) {

            return false;
        }

        String normalized =
                normalizeArchivePath(
                        path
                );

        return normalized.contains(
                "/Text/"
        )
                || normalized.startsWith(
                        "Text/"
                );
    }


    private String resolvePackageDirectory(
            String packagePath) {

        if (packagePath == null
                || packagePath.isBlank()) {

            return "";
        }

        String normalized =
                normalizeArchivePath(
                        packagePath
                );

        int separator =
                normalized.lastIndexOf(
                        '/'
                );

        if (separator < 0) {

            return "";
        }

        return normalized.substring(
                0,
                separator
        );
    }


    private String resolveArchivePath(
            String packageDirectory,
            String href) {

        String cleanHref =
                removeFragmentAndQuery(
                        href
                );

        Path resolved =
                packageDirectory == null
                        || packageDirectory.isBlank()
                        ? Path.of(
                                cleanHref
                        )
                        : Path.of(
                                packageDirectory
                        ).resolve(
                                cleanHref
                        );

        return normalizeArchivePath(
                resolved.normalize()
                        .toString()
        );
    }


    private String removeFragmentAndQuery(
            String href) {

        if (href == null) {

            return "";
        }

        String value =
                href.trim();

        int fragment =
                value.indexOf(
                        '#'
                );

        if (fragment >= 0) {

            value =
                    value.substring(
                            0,
                            fragment
                    );
        }

        int query =
                value.indexOf(
                        '?'
                );

        if (query >= 0) {

            value =
                    value.substring(
                            0,
                            query
                    );
        }

        return value;
    }


    private String normalizeArchivePath(
            String path) {

        if (path == null) {

            return "";
        }

        String normalized =
                path.replace(
                        '\\',
                        '/'
                );

        while (normalized.startsWith(
                "/"
        )) {

            normalized =
                    normalized.substring(
                            1
                    );
        }

        return normalized;
    }


    private Map<String, Object> fileResult(
            String path,
            String status) {

        Map<String, Object> item =
                new LinkedHashMap<>();

        item.put(
                "path",
                path
        );

        item.put(
                "status",
                status
        );

        return Map.copyOf(
                item
        );
    }


    private String createMessage(
            int matchedCount,
            int notInManifestCount,
            int fileMissingCount) {

        if (notInManifestCount == 0
                && fileMissingCount == 0) {

            return "EPUB Text files and manifest document resources match. "
                    + "matched="
                    + matchedCount;
        }

        return "EPUB Text/manifest comparison completed: "
                + "matched="
                + matchedCount
                + ", notInManifest="
                + notInManifestCount
                + ", fileMissing="
                + fileMissingCount;
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

        int separator =
                tagName.indexOf(
                        ':'
                );

        if (separator >= 0
                && separator + 1 < tagName.length()) {

            return tagName.substring(
                    separator + 1
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

        String value =
                element.getAttribute(
                        name
                );

        return value == null
                ? ""
                : value.trim();
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
                        ? "EPUB_TEXT_MANIFEST_COMPARE_FAILED"
                        : errorCode.trim();

        String message =
                errorMessage == null
                        || errorMessage.isBlank()
                        ? "Failed to compare EPUB Text files and manifest."
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


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null) {

            return "Unknown EPUB Text/manifest comparison error.";
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


    private record ManifestDocument(
            String id,
            String href,
            String mediaType,
            String archivePath) {
    }
}