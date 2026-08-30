/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.tool.xhtml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.json.JsonMapper;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.util.ToolUtil;

/**
 * 생성된 XHTML을 곰스북 프로젝트 파일에 적용하는 Tool.
 *
 * <p>지원 기능:</p>
 * <ul>
 *     <li>UTF-8 XHTML 파일 저장</li>
 *     <li>상위 디렉터리 자동 생성</li>
 *     <li>기존 파일 덮어쓰기 제어</li>
 *     <li>기존 파일 백업 생성</li>
 *     <li>프로젝트 루트 외부 경로 접근 차단</li>
 * </ul>
 */
public final class ApplyXhtmlTool implements AgentTool {

    public static final String TOOL_NAME = "apply_xhtml";

    private static final String TOOL_DESCRIPTION =
            "생성된 XHTML을 곰스북 프로젝트의 실제 XHTML 파일에 적용합니다.";

    private static final String DEFAULT_TEXT_DIRECTORY = "Text";
    private static final String XHTML_EXTENSION = ".xhtml";
    private static final String BACKUP_EXTENSION = ".bak";

    private final JsonMapper jsonMapper;

    public ApplyXhtmlTool(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public ToolResult execute(
            ToolRequest toolRequest,
            ToolContext toolContext) {

        if (toolRequest == null) {
            return failure(
                    ToolStatus.FAILED,
                    "TOOL_REQUEST_REQUIRED",
                    "ToolRequest가 없습니다.");
        }

        try {
            ApplyXhtmlRequest request =
                    parseRequest(toolRequest);

            ToolResult validationResult =
                    validateRequest(request, toolContext);

            if (validationResult != null) {
                return validationResult;
            }

            Path projectRoot =
                    resolveProjectRoot(request, toolContext);

            Path targetFile =
                    resolveTargetFile(projectRoot, request);

            boolean existedBefore =
                    Files.exists(targetFile);

            if (existedBefore && !request.isOverwrite()) {
                return failure(
                        ToolStatus.FAILED,
                        "FILE_ALREADY_EXISTS",
                        "대상 XHTML 파일이 이미 존재합니다: "
                                + targetFile);
            }

            Path backupFile = null;

            if (existedBefore && request.isCreateBackup()) {
                backupFile = createBackup(targetFile);
            }

            writeXhtml(
                    targetFile,
                    normalizeXhtml(request.getXhtml()));

            ApplyXhtmlResponse response =
                    createResponse(
                            projectRoot,
                            targetFile,
                            backupFile,
                            existedBefore,
                            request);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put(
                    "response",
                    response);
            
            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message(createSuccessMessage(
                            targetFile,
                            existedBefore))
                    .data(data)
                    .build();

        } catch (InvalidPathException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_FILE_PATH",
                    "유효하지 않은 파일 경로입니다: "
                            + exception.getInput());

        } catch (SecurityException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "PATH_ACCESS_DENIED",
                    buildExceptionMessage(exception));

        } catch (IOException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_FILE_WRITE_FAILED",
                    buildExceptionMessage(exception));

        } catch (IllegalArgumentException exception) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_APPLY_REQUEST",
                    buildExceptionMessage(exception));

        } catch (Exception exception) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_APPLY_FAILED",
                    buildExceptionMessage(exception));
        }
    }
    
    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectRoot", Map.of("type", "string", "description", "프로젝트 루트 경로입니다. 일반적으로 생략하고 현재 프로젝트를 사용합니다."));
        properties.put("targetDirectory", Map.of("type", "string", "description", "XHTML을 저장할 프로젝트 기준 디렉터리입니다."));
        properties.put("relativePath", Map.of("type", "string", "description", "프로젝트 루트 기준 XHTML 상대 경로입니다."));
        properties.put("fileName", Map.of("type", "string", "description", "저장할 XHTML 파일명입니다."));
        properties.put("xhtml", Map.of("type", "string", "description", "저장할 완전한 XHTML 문서 내용입니다."));
        properties.put("overwrite", Map.of("type", "boolean", "description", "기존 파일 덮어쓰기 여부입니다."));
        properties.put("createBackup", Map.of("type", "boolean", "description", "기존 파일 수정 전에 백업 파일을 생성할지 여부입니다."));

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("xhtml"));

        return schema;
    }
    
    /**
     * ToolRequest arguments를 ApplyXhtmlRequest로 변환한다.
     */
    private ApplyXhtmlRequest parseRequest(
            ToolRequest toolRequest) {

        Object arguments = toolRequest.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "XHTML 적용 인자가 없습니다.");
        }

        if (arguments instanceof ApplyXhtmlRequest) {
            return (ApplyXhtmlRequest) arguments;
        }
        
        String json = jsonMapper.toJson(arguments);

        ApplyXhtmlRequest request =
                jsonMapper.fromJson(
                        json,
                        ApplyXhtmlRequest.class);
        
        if (request == null) {
            throw new IllegalArgumentException(
                    "XHTML 적용 요청을 변환할 수 없습니다.");
        }

        return request;
    }

    /**
     * 적용 요청을 검증한다.
     *
     * @return 검증 실패 ToolResult. 정상인 경우 null
     */
    private ToolResult validateRequest(
            ApplyXhtmlRequest request,
            ToolContext toolContext) {

        if (request == null) {
            return failure(
                    ToolStatus.FAILED,
                    "REQUEST_REQUIRED",
                    "XHTML 적용 요청이 없습니다.");
        }

        if (ToolUtil.isBlank(request.getXhtml())) {
            return failure(
                    ToolStatus.FAILED,
                    "XHTML_REQUIRED",
                    "적용할 XHTML 내용이 없습니다.");
        }

        if (ToolUtil.isBlank(request.getFileName())
                && ToolUtil.isBlank(request.getRelativePath())) {

            return failure(
                    ToolStatus.FAILED,
                    "TARGET_FILE_REQUIRED",
                    "적용할 XHTML 파일명 또는 상대 경로가 필요합니다.");
        }

        String fileName =ToolUtil.resolveRequestedFileName(request);

        if (!ToolUtil.hasXhtmlExtension(fileName)) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_XHTML_EXTENSION",
                    "대상 파일의 확장자는 .xhtml이어야 합니다.");
        }

        if (!ToolUtil.containsHtmlElement(request.getXhtml())) {
            return failure(
                    ToolStatus.FAILED,
                    "INVALID_XHTML_CONTENT",
                    "적용할 내용에 html 요소가 없습니다.");
        }

        if (!ToolUtil.hasProjectRoot(request, toolContext)) {
            return failure(
                    ToolStatus.FAILED,
                    "PROJECT_ROOT_REQUIRED",
                    "곰스북 프로젝트 루트 경로가 없습니다.");
        }

        return null;
    }

    /**
     * 프로젝트 루트 경로를 결정한다.
     *
     * 우선순위:
     * 1. ApplyXhtmlRequest.projectRoot
     * 2. ToolContext.variables.projectRoot
     * 3. ToolContext.variables.projectPath
     */
    private Path resolveProjectRoot(ApplyXhtmlRequest request, ToolContext toolContext) {

        String requestProjectRoot = ToolUtil.trimToNull(request.getProjectRoot());

        if (requestProjectRoot != null) return ToolUtil.validateProjectRoot(Path.of(requestProjectRoot));

        if (toolContext != null && toolContext.getProjectRoot() != null) return ToolUtil.validateProjectRoot(toolContext.getProjectRoot());

        String contextProjectRoot = ToolUtil.getContextString(toolContext, "projectRoot");

        if (contextProjectRoot != null) return ToolUtil.validateProjectRoot(Path.of(contextProjectRoot));

        String contextProjectPath = ToolUtil.getContextString(toolContext, "projectPath");

        if (contextProjectPath != null) return ToolUtil.validateProjectRoot(Path.of(contextProjectPath));

        throw new IllegalArgumentException("프로젝트 루트 경로를 확인할 수 없습니다.");
    }

    /**
     * 실제 저장할 XHTML 파일 경로를 결정한다.
     */
    private Path resolveTargetFile(
            Path projectRoot,
            ApplyXhtmlRequest request) {

        String requestedPath = ToolUtil.trimToNull(request.getRelativePath());

        Path relativeTarget;

        if (requestedPath != null) {
            relativeTarget = Path.of(requestedPath);
        } else {
            String directory = ToolUtil.defaultIfBlank(
                            request.getTargetDirectory(),
                            DEFAULT_TEXT_DIRECTORY);

            relativeTarget =
                    Path.of(
                            directory,
                            request.getFileName().trim());
        }

        if (relativeTarget.isAbsolute()) {
            throw new IllegalArgumentException(
                    "대상 경로는 프로젝트 기준 상대 경로여야 합니다.");
        }

        Path targetFile =
                projectRoot.resolve(relativeTarget)
                        .toAbsolutePath()
                        .normalize();

        /*
         * ../../../ 등의 경로를 이용해 프로젝트 외부 파일을
         * 수정하는 것을 차단한다.
         */
        if (!targetFile.startsWith(projectRoot)) {
            throw new SecurityException(
                    "프로젝트 루트 외부의 파일에는 적용할 수 없습니다.");
        }

        if (!ToolUtil.hasXhtmlExtension(
                targetFile.getFileName().toString())) {

            throw new IllegalArgumentException(
                    "대상 파일의 확장자는 .xhtml이어야 합니다.");
        }

        return targetFile;
    }

    /**
     * XHTML 파일을 UTF-8로 저장한다.
     */
    private void writeXhtml(
            Path targetFile,
            String xhtml) throws IOException {

        Path parentDirectory =
                targetFile.getParent();

        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        /*
         * CREATE:
         * 파일이 없으면 새로 생성한다.
         *
         * TRUNCATE_EXISTING:
         * 파일이 있으면 기존 내용을 제거하고 새 XHTML을 기록한다.
         */
        Files.writeString(
                targetFile,
                xhtml,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * 기존 XHTML 파일의 백업본을 생성한다.
     *
     * 예:
     * chapter10_1.xhtml
     * chapter10_1.xhtml.bak
     */
    private Path createBackup(
            Path targetFile) throws IOException {

        Path backupFile =
                targetFile.resolveSibling(
                        targetFile.getFileName().toString()
                                + BACKUP_EXTENSION);

        Files.copy(
                targetFile,
                backupFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);

        return backupFile;
    }

    /**
     * 저장 전 XHTML 문자열을 정규화한다.
     */
    private String normalizeXhtml(String xhtml) {

        String normalized = removeByteOrderMark(xhtml).trim();

        normalized = removeCodeFence(normalized);

        int doctypeIndex =
        		ToolUtil.indexOfIgnoreCase(
                        normalized,
                        "<!DOCTYPE html>");

        int htmlIndex =
        		ToolUtil.indexOfIgnoreCase(
                        normalized,
                        "<html");

        if (doctypeIndex > 0) {
            normalized = normalized.substring(doctypeIndex);

        } else if (doctypeIndex < 0 && htmlIndex > 0) {
            normalized = normalized.substring(htmlIndex);
        }

        int closingHtmlIndex =ToolUtil.lastIndexOfIgnoreCase( normalized, "</html>");

        if (closingHtmlIndex >= 0) {
            normalized =
                    normalized.substring(
                            0,
                            closingHtmlIndex
                                    + "</html>".length());
        }

        if (!ToolUtil.containsIgnoreCase(
                normalized,
                "<!DOCTYPE html>")) {

            normalized =
                    "<!DOCTYPE html>\n"
                            + normalized;
        }

        /*
         * 운영체제와 무관하게 XHTML 파일 줄바꿈을 LF로 통일한다.
         */
        normalized =
                normalized.replace("\r\n", "\n")
                        .replace('\r', '\n');

        return normalized + "\n";
    }

    private String removeByteOrderMark(String value) {

        if (value != null
                && !value.isEmpty()
                && value.charAt(0) == '\uFEFF') {

            return value.substring(1);
        }

        return value;
    }

    private String removeCodeFence(String value) {

        String normalized = value;

        if (normalized.startsWith("```")) {
            int firstLineEnd =
                    normalized.indexOf('\n');

            if (firstLineEnd >= 0) {
                normalized =
                        normalized.substring(
                                firstLineEnd + 1);
            }
        }

        if (normalized.endsWith("```")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 3);
        }

        return normalized.trim();
    }

    private ApplyXhtmlResponse createResponse(
            Path projectRoot,
            Path targetFile,
            Path backupFile,
            boolean existedBefore,
            ApplyXhtmlRequest request) throws IOException {

        ApplyXhtmlResponse response =
                new ApplyXhtmlResponse();

        response.setApplied(true);
        response.setFileName(
                targetFile.getFileName().toString());

        response.setAbsolutePath(
                targetFile.toString());

        response.setRelativePath(ToolUtil.normalizeSeparator( projectRoot.relativize(targetFile).toString()));

        response.setCreated(!existedBefore);
        response.setUpdated(existedBefore);
        response.setOverwrite(request.isOverwrite());

        response.setBackupCreated(
                backupFile != null);

        if (backupFile != null) {
            response.setBackupPath(
                    backupFile.toString());
        }

        response.setSize(
                Files.size(targetFile));

        response.setCharset(
                StandardCharsets.UTF_8.name());

        return response;
    }

    private String createSuccessMessage( Path targetFile, boolean existedBefore) {

        if (existedBefore) {
            return "기존 XHTML 파일을 수정했습니다: "
                    + targetFile.getFileName();
        }

        return "새 XHTML 파일을 생성했습니다: "
                + targetFile.getFileName();
    }

    private ToolResult failure(
            ToolStatus status,
            String code,
            String message) {

        ToolIssue issue =
                ToolIssue.builder()
                        .code(code)
                        .severity(
                                ToolIssueSeverity.ERROR)
                        .message(message)
                        .build();

        return ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(status)
                .message(message)
                .issues(
                        Collections.singletonList(issue))
                .build();
    }

    private String buildExceptionMessage(
            Exception exception) {

        if (exception == null) {
            return "알 수 없는 오류가 발생했습니다.";
        }

        if (!ToolUtil.isBlank(exception.getMessage())) {
            return exception.getMessage();
        }

        return exception.getClass()
                .getSimpleName()
                + " 오류가 발생했습니다.";
    }


    /**
     * XHTML 적용 요청 DTO.
     *
     * 기존에 별도 ApplyXhtmlRequest.java를 생성한다면
     * 이 내부 클래스를 제거하고 해당 클래스를 import하면 된다.
     */
    public static final class ApplyXhtmlRequest {

        private String projectRoot;
        private String targetDirectory;
        private String relativePath;
        private String fileName;
        private String xhtml;

        private boolean overwrite = true;
        private boolean createBackup = true;

        public ApplyXhtmlRequest() {
        }

        public String getProjectRoot() {
            return projectRoot;
        }

        public void setProjectRoot(String projectRoot) {
            this.projectRoot = projectRoot;
        }

        public String getTargetDirectory() {
            return targetDirectory;
        }

        public void setTargetDirectory(
                String targetDirectory) {
            this.targetDirectory = targetDirectory;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public void setRelativePath(
                String relativePath) {
            this.relativePath = relativePath;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getXhtml() {
            return xhtml;
        }

        public void setXhtml(String xhtml) {
            this.xhtml = xhtml;
        }

        public boolean isOverwrite() {
            return overwrite;
        }

        public void setOverwrite(boolean overwrite) {
            this.overwrite = overwrite;
        }

        public boolean isCreateBackup() {
            return createBackup;
        }

        public void setCreateBackup(
                boolean createBackup) {
            this.createBackup = createBackup;
        }
    }

    /**
     * XHTML 적용 결과 DTO.
     *
     * 기존에 별도 ApplyXhtmlResponse.java를 생성한다면
     * 이 내부 클래스를 제거하고 해당 클래스를 import하면 된다.
     */
    public static final class ApplyXhtmlResponse {

        private boolean applied;
        private boolean created;
        private boolean updated;
        private boolean overwrite;
        private boolean backupCreated;

        private String fileName;
        private String relativePath;
        private String absolutePath;
        private String backupPath;
        private String charset;

        private long size;

        public ApplyXhtmlResponse() {
        }

        public boolean isApplied() {
            return applied;
        }

        public void setApplied(boolean applied) {
            this.applied = applied;
        }

        public boolean isCreated() {
            return created;
        }

        public void setCreated(boolean created) {
            this.created = created;
        }

        public boolean isUpdated() {
            return updated;
        }

        public void setUpdated(boolean updated) {
            this.updated = updated;
        }

        public boolean isOverwrite() {
            return overwrite;
        }

        public void setOverwrite(boolean overwrite) {
            this.overwrite = overwrite;
        }

        public boolean isBackupCreated() {
            return backupCreated;
        }

        public void setBackupCreated(
                boolean backupCreated) {
            this.backupCreated = backupCreated;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public void setRelativePath(
                String relativePath) {
            this.relativePath = relativePath;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public void setAbsolutePath(
                String absolutePath) {
            this.absolutePath = absolutePath;
        }

        public String getBackupPath() {
            return backupPath;
        }

        public void setBackupPath(
                String backupPath) {
            this.backupPath = backupPath;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }
    }
}