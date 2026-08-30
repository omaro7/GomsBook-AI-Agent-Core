/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.epub.inspect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import kr.co.goms.gomsbook.ai.epub.model.EpubInspectionResult;
import kr.co.goms.gomsbook.ai.epub.model.EpubManifest;
import kr.co.goms.gomsbook.ai.epub.model.EpubMetadata;
import kr.co.goms.gomsbook.ai.epub.model.EpubPackage;
import kr.co.goms.gomsbook.ai.epub.model.EpubSpine;
import kr.co.goms.gomsbook.ai.epub.service.EpubArchivePackageReader;
import kr.co.goms.gomsbook.ai.epub.service.EpubArchiveReader;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;


/**
 * 생성된 EPUB 파일의 구조와 주요 정보를 조회하는 Agent Tool입니다.
 * C:\1004.GomsBook\02.Publish\lunchwork_seoul\lunchwork_seoul-202608163712.epub
 * 
 * <p>EPUB 파일을 수정하지 않고 archive 구조와 Package Document를
 * 읽어 Agent가 현재 EPUB 상태를 파악할 수 있도록 합니다.</p>
 *
 * <p>Package Document 일부에 오류가 있더라도 가능한 범위까지
 * EPUB 정보를 조회하고 문제를 WARNING으로 반환합니다.</p>
 *
 * <p>정식 EPUB 구조 검증은 ValidateEpubStructureTool,
 * EPUB 표준 검증은 EpubCheckTool의 책임입니다.</p>
 * 
 * TODO 추후 현재 프로젝트에 생성된 epub 파일을 검증해죠 라고 질의 시, 자동으로 epub 파일을 찾고 검증 진행할 수 있는 시나리오 필요.
 */
public final class InspectEpubTool implements AgentTool {


    public static final String NAME = "inspect_epub";

    public static final String TOOL_NAME = NAME;

    public static final String DESCRIPTION = "Inspects an EPUB file and returns its structure, metadata, manifest, spine, navigation, and resource summary without modifying it.";

    private static final String EPUB_FILE_ARGUMENT = "epubFile";

    private static final String MIMETYPE_ENTRY = "mimetype";

    private static final String CONTAINER_ENTRY = "META-INF/container.xml";

    private static final String EXPECTED_MIMETYPE = "application/epub+zip";


    private final EpubArchiveReader archiveReader;

    private final EpubArchivePackageReader packageReader;


    public InspectEpubTool() {

        this(new EpubArchiveReader());
    }


    public InspectEpubTool(
            EpubArchiveReader archiveReader) {

        this(archiveReader, new EpubArchivePackageReader(archiveReader));
    }


    public InspectEpubTool(
            EpubArchiveReader archiveReader,
            EpubArchivePackageReader packageReader) {

        if (archiveReader == null) {

            throw new IllegalArgumentException(
                    "archiveReader must not be null.");
        }

        if (packageReader == null) {

            throw new IllegalArgumentException(
                    "packageReader must not be null.");
        }

        this.archiveReader = archiveReader;

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

        ToolValidationResult.Builder result = ToolValidationResult.builder();

        if (request == null) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_REQUEST_NULL",
                            "Tool request must not be null."))
                    .build();
        }

        Path epubFile;

        try {

            epubFile = resolveEpubFile(request, context);

        } catch (RuntimeException exception) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_ARGUMENT_INVALID",
                            safeMessage(exception)))
                    .build();
        }

        if (epubFile == null) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_FILE_MISSING",
                            "EPUB file was not provided."))
                    .build();
        }

        Path normalized = epubFile.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_FILE_NOT_FOUND",
                            "EPUB file does not exist: " + normalized))
                    .build();
        }

        if (!Files.isRegularFile(normalized)) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_NOT_FILE",
                            "EPUB path is not a regular file: " + normalized))
                    .build();
        }

        if (!Files.isReadable(normalized)) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_NOT_READABLE",
                            "EPUB file is not readable: " + normalized))
                    .build();
        }

        Path fileNamePath = normalized.getFileName();

        String fileName = fileNamePath == null ? "" : fileNamePath.toString().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".epub")) {

            return result.valid(false)
                    .issue(errorIssue(
                            "EPUB_INSPECT_EXTENSION_INVALID",
                            "Inspection target must use the .epub extension."))
                    .build();
        }

        return result.valid(true).build();
    }


    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        ToolValidationResult validation = validate(request, context);

        if (!validation.isValid()) {

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.VALIDATION_FAILED)
                    .validationResult(validation)
                    .message("EPUB inspection request is invalid.")
                    .build();
        }

        Path epubFile;

        try {

            epubFile = Objects.requireNonNull(resolveEpubFile(request, context), "EPUB file must not be null.").toAbsolutePath().normalize();

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_INSPECT_ARGUMENT_INVALID",
                    safeMessage(exception),
                    null,
                    exception);
        }

        try {

            List<ToolIssue> inspectionIssues = new ArrayList<>();

            EpubInspectionResult inspectionResult = inspect(epubFile, inspectionIssues);

            return convertResult(inspectionResult, inspectionIssues);

        } catch (IOException exception) {

            return failure(
                    "EPUB_INSPECT_IO_ERROR",
                    "Failed to inspect EPUB file: " + safeMessage(exception),
                    epubFile,
                    exception);

        } catch (SecurityException exception) {

            return failure(
                    "EPUB_INSPECT_ACCESS_DENIED",
                    "Access to EPUB file was denied: " + safeMessage(exception),
                    epubFile,
                    exception);

        } catch (RuntimeException exception) {

            return failure(
                    "EPUB_INSPECT_UNEXPECTED_ERROR",
                    "Unexpected EPUB inspection error: " + safeMessage(exception),
                    epubFile,
                    exception);
        }
    }


    private EpubInspectionResult inspect(
            Path epubFile,
            List<ToolIssue> inspectionIssues) throws IOException {

        EpubInspectionResult.Builder result = EpubInspectionResult.builder();

        result.epubFile(epubFile);

        result.fileSize(Files.size(epubFile));

        result.entryCount(archiveReader.getEntryCount(epubFile));

        List<String> entries = archiveReader.listEntries(epubFile);

        inspectEntryStatistics(entries, result);

        inspectMimetype(epubFile, result);

        String packagePath = inspectContainerSafely(
                epubFile,
                result,
                inspectionIssues);

        if (packagePath != null
                && archiveReader.exists(epubFile, packagePath)) {

            inspectPackageSafely(
                    epubFile,
                    result,
                    inspectionIssues);
        }

        return result.build();
    }


    private void inspectEntryStatistics(
            List<String> entries,
            EpubInspectionResult.Builder result) {

        int xhtmlCount = 0;

        int cssCount = 0;

        int imageCount = 0;

        int fontCount = 0;

        int audioCount = 0;

        int videoCount = 0;

        Set<String> paths = new TreeSet<>();

        for (String entryPath : entries) {

            if (entryPath == null) {

                continue;
            }

            String name = normalizeEpubPath(entryPath);

            paths.add(name);

            String lower = name.toLowerCase(Locale.ROOT);

            if (lower.endsWith(".xhtml")
                    || lower.endsWith(".html")
                    || lower.endsWith(".htm")) {

                xhtmlCount++;

            } else if (lower.endsWith(".css")) {

                cssCount++;

            } else if (isImageFile(lower)) {

                imageCount++;

            } else if (isFontFile(lower)) {

                fontCount++;

            } else if (isAudioFile(lower)) {

                audioCount++;

            } else if (isVideoFile(lower)) {

                videoCount++;
            }
        }

        result.xhtmlCount(xhtmlCount);

        result.cssCount(cssCount);

        result.imageCount(imageCount);

        result.fontCount(fontCount);

        result.audioCount(audioCount);

        result.videoCount(videoCount);

        result.entryPaths(paths);
    }


    private void inspectMimetype(
            Path epubFile,
            EpubInspectionResult.Builder result) {

        boolean mimetypePresent = archiveReader.exists(epubFile, MIMETYPE_ENTRY);

        result.mimetypePresent(mimetypePresent);

        if (!mimetypePresent) {

            return;
        }

        String mimetype = archiveReader.readText(epubFile, MIMETYPE_ENTRY);

        result.mimetype(mimetype);

        result.mimetypeValid(EXPECTED_MIMETYPE.equals(mimetype));

        result.mimetypeStored(archiveReader.isStored(epubFile, MIMETYPE_ENTRY));

        String firstEntryPath = archiveReader.getFirstEntryPath(epubFile);

        result.mimetypeFirstEntry(MIMETYPE_ENTRY.equals(firstEntryPath));
    }


    private String inspectContainerSafely(
            Path epubFile,
            EpubInspectionResult.Builder result,
            List<ToolIssue> inspectionIssues) {

        boolean containerPresent = archiveReader.exists(epubFile, CONTAINER_ENTRY);

        result.containerPresent(containerPresent);

        if (!containerPresent) {

            return null;
        }

        try {

            String packagePath = packageReader.findPackageDocumentPath(epubFile);

            result.packageDocumentPath(packagePath);

            boolean packageDocumentPresent = archiveReader.exists(epubFile, packagePath);

            result.packageDocumentPresent(packageDocumentPresent);

            return packagePath;

        } catch (RuntimeException exception) {

            inspectionIssues.add(
                    warningIssue(
                            "EPUB_INSPECT_CONTAINER_READ_ERROR",
                            "Failed to read EPUB container: " + safeMessage(exception)));

            return null;
        }
    }


    private void inspectPackageSafely(
            Path epubFile,
            EpubInspectionResult.Builder result,
            List<ToolIssue> inspectionIssues) {

        try {

            EpubPackage epubPackage = packageReader.read(epubFile);

            inspectPackageInformation(epubPackage, result);

            inspectMetadata(epubPackage.getMetadata(), result);

            inspectManifest(epubPackage.getManifest(), result);

            inspectSpine(epubPackage.getSpine(), result);

            inspectAccessibilityMetadata(epubPackage.getMetadata(), result);

        } catch (RuntimeException exception) {

            inspectionIssues.add(
                    warningIssue(
                            "EPUB_INSPECT_PACKAGE_READ_ERROR",
                            "Failed to read EPUB Package Document: " + safeMessage(exception)));
        }
    }


    private void inspectPackageInformation(
            EpubPackage epubPackage,
            EpubInspectionResult.Builder result) {

        result.epubVersion(epubPackage.getPackageVersion());

        result.language(epubPackage.getLanguage());
    }


    private void inspectMetadata(
            EpubMetadata metadata,
            EpubInspectionResult.Builder result) {

        String title = metadata.getPrimaryTitleValue().orElse(null);

        String creator = metadata.getPrimaryCreatorValue().orElse(null);

        String identifier = metadata.getUniqueIdentifierValue().orElse(null);

        String uniqueIdentifierId = metadata.getUniqueIdentifierId().orElse(null);

        result.title(title);

        result.creator(creator);

        result.identifier(identifier);

        result.uniqueIdentifierId(uniqueIdentifierId);
    }


    private void inspectManifest(
            EpubManifest manifest,
            EpubInspectionResult.Builder result) {

        result.manifestItemCount(manifest.size());

        result.navigationDocumentPresent(manifest.getNavigationDocument().isPresent());

        result.ncxPresent(manifest.getNcxResource().isPresent());
    }


    private void inspectSpine(
            EpubSpine spine,
            EpubInspectionResult.Builder result) {

        result.spineItemCount(spine.size());

        result.linearSpineItemCount(spine.linearSize());

        result.spineToc(spine.getTocId().orElse(null));

        if (spine.getPageProgressionDirection() != null) {

            result.pageProgressionDirection(spine.getPageProgressionDirection().toString());
        }
    }


    private void inspectAccessibilityMetadata(
            EpubMetadata metadata,
            EpubInspectionResult.Builder result) {

        boolean accessModePresent = !metadata.findByProperty("schema:accessMode").isEmpty();

        boolean accessibilityFeaturePresent = !metadata.findByProperty("schema:accessibilityFeature").isEmpty();

        boolean accessibilityHazardPresent = !metadata.findByProperty("schema:accessibilityHazard").isEmpty();

        boolean accessibilitySummaryPresent = !metadata.findByProperty("schema:accessibilitySummary").isEmpty();

        result.accessModePresent(accessModePresent);

        result.accessibilityFeaturePresent(accessibilityFeaturePresent);

        result.accessibilityHazardPresent(accessibilityHazardPresent);

        result.accessibilitySummaryPresent(accessibilitySummaryPresent);
    }


    private ToolResult convertResult(
            EpubInspectionResult result,
            List<ToolIssue> inspectionIssues) {

        Objects.requireNonNull(result, "EPUB inspection result must not be null.");

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.SUCCESS)
                .message(result.createSummary())
                .data("inspectionResult", result)
                .data("epubFile", result.getEpubFile().toString())
                .data("fileSize", result.getFileSize())
                .data("entryCount", result.getEntryCount())
                .data("xhtmlCount", result.getXhtmlCount())
                .data("cssCount", result.getCssCount())
                .data("imageCount", result.getImageCount())
                .data("fontCount", result.getFontCount())
                .data("audioCount", result.getAudioCount())
                .data("videoCount", result.getVideoCount())
                .data("manifestItemCount", result.getManifestItemCount())
                .data("spineItemCount", result.getSpineItemCount())
                .data("linearSpineItemCount", result.getLinearSpineItemCount())
                .data("navigationDocumentPresent", result.isNavigationDocumentPresent())
                .data("ncxPresent", result.isNcxPresent())
                .data("mimetypePresent", result.isMimetypePresent())
                .data("mimetypeValid", result.isMimetypeValid())
                .data("mimetypeStored", result.isMimetypeStored())
                .data("mimetypeFirstEntry", result.isMimetypeFirstEntry())
                .data("containerPresent", result.isContainerPresent())
                .data("packageDocumentPresent", result.isPackageDocumentPresent())
                .data("accessModePresent", result.isAccessModePresent())
                .data("accessibilityFeaturePresent", result.isAccessibilityFeaturePresent())
                .data("accessibilityHazardPresent", result.isAccessibilityHazardPresent())
                .data("accessibilitySummaryPresent", result.isAccessibilitySummaryPresent())
                .data("basicAccessibilityMetadata", result.hasBasicAccessibilityMetadata())
                .data("structuralWarnings", result.hasStructuralWarnings())
                .data("entryPaths", result.getEntryPaths());

        result.getMimetype().ifPresent(value -> builder.data("mimetype", value));

        result.getEpubVersion().ifPresent(value -> builder.data("epubVersion", value));

        result.getTitle().ifPresent(value -> builder.data("title", value));

        result.getLanguage().ifPresent(value -> builder.data("language", value));

        result.getCreator().ifPresent(value -> builder.data("creator", value));

        result.getIdentifier().ifPresent(value -> builder.data("identifier", value));

        result.getUniqueIdentifierId().ifPresent(value -> builder.data("uniqueIdentifierId", value));

        result.getPackageDocumentPath().ifPresent(value -> builder.data("packageDocumentPath", value));

        result.getSpineToc().ifPresent(value -> builder.data("spineToc", value));

        result.getPageProgressionDirection().ifPresent(value -> builder.data("pageProgressionDirection", value));

        appendInspectionIssues(inspectionIssues, builder);

        appendStructuralIssues(result, builder);

        return builder.build();
    }


    private void appendInspectionIssues(
            List<ToolIssue> inspectionIssues,
            ToolResult.Builder builder) {

        if (inspectionIssues == null) {

            return;
        }

        for (ToolIssue issue : inspectionIssues) {

            if (issue != null) {

                builder.issue(issue);
            }
        }
    }


    private void appendStructuralIssues(
            EpubInspectionResult result,
            ToolResult.Builder builder) {

        if (!result.isMimetypePresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_MIMETYPE_MISSING",
                            "The EPUB archive does not contain mimetype."));

        } else {

            if (!result.isMimetypeValid()) {

                builder.issue(
                        warningIssue(
                                "EPUB_INSPECT_MIMETYPE_INVALID",
                                "The EPUB mimetype value is invalid."));
            }

            if (!result.isMimetypeStored()) {

                builder.issue(
                        warningIssue(
                                "EPUB_INSPECT_MIMETYPE_COMPRESSED",
                                "The EPUB mimetype entry is compressed."));
            }

            if (!result.isMimetypeFirstEntry()) {

                builder.issue(
                        warningIssue(
                                "EPUB_INSPECT_MIMETYPE_ORDER",
                                "The mimetype entry is not the first ZIP entry."));
            }
        }

        if (!result.isContainerPresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_CONTAINER_MISSING",
                            "META-INF/container.xml is missing."));
        }

        if (result.getPackageDocumentPath().isEmpty()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_PACKAGE_PATH_MISSING",
                            "The package document path could not be determined."));

        } else if (!result.isPackageDocumentPresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_PACKAGE_MISSING",
                            "The package document referenced by container.xml is missing."));
        }

        boolean epub3 = result.getEpubVersion()
                .map(value -> value.startsWith("3"))
                .orElse(false);

        if (epub3 && !result.isNavigationDocumentPresent()) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_NAV_MISSING",
                            "EPUB 3 package does not appear to contain a Navigation Document."));
        }

        if (result.getEpubVersion().isPresent()
                && result.getSpineItemCount() == 0) {

            builder.issue(
                    warningIssue(
                            "EPUB_INSPECT_SPINE_EMPTY",
                            "The EPUB spine contains no itemref elements."));
        }
    }


    private Path resolveEpubFile(
            ToolRequest request,
            ToolContext context) {

        if (request == null) {

            return null;
        }

        Path path = resolvePathFromArguments(request.getArguments());

        if (path != null) {

            return path;
        }

        if (context != null) {

            Object value = context.getAttribute(EPUB_FILE_ARGUMENT);

            path = toPath(value);

            if (path != null) {

                return path;
            }
        }

        return null;
    }


    private Path resolvePathFromArguments(
            Object arguments) {

        if (arguments == null) {

            return null;
        }

        Path directPath = toPath(arguments);

        if (directPath != null) {

            return directPath;
        }

        if (arguments instanceof Map<?, ?> map) {

            Object value = map.get(EPUB_FILE_ARGUMENT);

            return toPath(value);
        }

        return null;
    }


    private Path toPath(
            Object value) {

        if (value == null) {

            return null;
        }

        if (value instanceof Path path) {

            return path;
        }

        if (value instanceof String text) {

            String normalized = text.trim();

            if (normalized.isEmpty()) {

                return null;
            }

            return Path.of(normalized);
        }

        return null;
    }


    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");

        schema.put(
                "properties",
                Map.of(
                        EPUB_FILE_ARGUMENT,
                        Map.of(
                                "type",
                                "string",
                                "description",
                                "Path of the EPUB file to inspect.")));

        schema.put("required", List.of(EPUB_FILE_ARGUMENT));

        return Map.copyOf(schema);
    }


    private ToolResult failure(
            String errorCode,
            String errorMessage,
            Path epubFile,
            Throwable cause) {

        String code = errorCode == null || errorCode.isBlank() ? "EPUB_INSPECT_FAILED" : errorCode.trim();

        String message = errorMessage == null || errorMessage.isBlank() ? "EPUB inspection failed." : errorMessage.trim();

        ToolResult.Builder builder = ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.FAILED)
                .message(message)
                .errorCode(code)
                .errorMessage(message)
                .issue(errorIssue(code, message));

        if (epubFile != null) {

            builder.data("epubFile", epubFile.toAbsolutePath().normalize().toString());
        }

        if (cause != null) {

            builder.cause(cause);

            builder.data("exceptionType", cause.getClass().getName());
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


    private ToolIssue warningIssue(
            String code,
            String message) {

        return ToolIssue.builder()
                .severity(ToolIssueSeverity.WARNING)
                .code(code)
                .message(message)
                .build();
    }


    private static String normalizeEpubPath(
            String value) {

        if (value == null) {

            return "";
        }

        String normalized = value.trim().replace('\\', '/');

        while (normalized.startsWith("/")) {

            normalized = normalized.substring(1);
        }

        while (normalized.startsWith("./")) {

            normalized = normalized.substring(2);
        }

        return normalized;
    }


    private static boolean isImageFile(
            String value) {

        return value.endsWith(".png")
                || value.endsWith(".jpg")
                || value.endsWith(".jpeg")
                || value.endsWith(".gif")
                || value.endsWith(".svg")
                || value.endsWith(".webp");
    }


    private static boolean isFontFile(
            String value) {

        return value.endsWith(".ttf")
                || value.endsWith(".otf")
                || value.endsWith(".woff")
                || value.endsWith(".woff2");
    }


    private static boolean isAudioFile(
            String value) {

        return value.endsWith(".mp3")
                || value.endsWith(".m4a")
                || value.endsWith(".aac")
                || value.endsWith(".ogg")
                || value.endsWith(".wav");
    }


    private static boolean isVideoFile(
            String value) {

        return value.endsWith(".mp4")
                || value.endsWith(".webm")
                || value.endsWith(".m4v");
    }


    private static String safeMessage(
            Throwable throwable) {

        if (throwable == null) {

            return "Unknown EPUB inspection error.";
        }

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {

            return throwable.getClass().getSimpleName();
        }

        return message.trim();
    }
}