/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.tool.epub.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlan;
import kr.co.goms.gomsbook.ai.epub.project.plan.CreateEpubProjectPlanService;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolIssue;
import kr.co.goms.gomsbook.ai.tool.ToolIssueSeverity;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;
import kr.co.goms.gomsbook.ai.tool.ToolValidationResult;

/**
 * 신규 EPUB 프로젝트의 기본 파일을 생성하는 Tool.
 *
 * <p>
 * CreateEpubProjectTool 및 CreateEpubProjectStructureTool 실행 후
 * Plan 상태가 CREATING인 경우 실행한다.
 * </p>
 *
 * <p>
 * 생성 파일:
 * </p>
 *
 * <pre>
 * project/
 * ├─ META-INF/
 * │  └─ container.xml
 * └─ OEBPS/
 *    ├─ content.opf
 *    ├─ nav.xhtml
 *    ├─ Styles/
 *    │  └─ style1.css
 *    └─ Scripts/
 *       └─ quiz.js
 * </pre>
 *
 * <p>
 * 모든 기본 파일 생성이 완료되면
 * CreateEpubProjectPlan 상태를 CREATED로 변경한다.
 * </p>
 */
public final class CreateEpubBaseFilesTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "create_epub_base_files";

    private static final String DESCRIPTION =
            "Creates the standard base files for an EPUB project "
                    + "currently being created.";

    private final CreateEpubProjectPlanService planService;

    private final Path projectsRoot;


    public CreateEpubBaseFilesTool(
            CreateEpubProjectPlanService planService,
            Path projectsRoot) {

        this.planService =
                Objects.requireNonNull(
                        planService,
                        "planService must not be null");

        this.projectsRoot =
                Objects.requireNonNull(
                        projectsRoot,
                        "projectsRoot must not be null")
                        .toAbsolutePath()
                        .normalize();
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
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "planId",
                property(
                        "string",
                        "EPUB project creation plan identifier "
                                + "whose status is CREATING."));


        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object");

        schema.put(
                "properties",
                properties);

        schema.put(
                "required",
                List.of(
                        "planId"));

        schema.put(
                "additionalProperties",
                false);

        return Collections.unmodifiableMap(
                schema);
    }


    @Override
    public ToolValidationResult validate(
            ToolRequest request,
            ToolContext context) {

        List<ToolIssue> issues =
                new ArrayList<>();


        if (request == null) {

            issues.add(
                    error(
                            "request",
                            "Tool request must not be null."));

            return ToolValidationResult.invalid(
                    issues);
        }


        if (context == null) {

            issues.add(
                    error(
                            "context",
                            "Tool context must not be null."));

            return ToolValidationResult.invalid(
                    issues);
        }


        if (!TOOL_NAME.equals(
                request.getToolName())) {

            issues.add(
                    error(
                            "toolName",
                            "Invalid tool name: "
                                    + request.getToolName()));
        }


        Map<String, Object> arguments =
                safeArguments(
                        request);

        Object planIdValue =
                arguments.get(
                        "planId");


        if (!(planIdValue instanceof String)) {

            issues.add(
                    error(
                            "planId",
                            "planId must be a string value."));

        } else {

            String planId =
                    ((String) planIdValue)
                            .trim();

            if (planId.isEmpty()) {

                issues.add(
                        error(
                                "planId",
                                "planId must not be blank."));
            }
        }


        if (!issues.isEmpty()) {

            return ToolValidationResult.invalid(
                    issues);
        }

        return ToolValidationResult.valid();
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
                    .toolName(
                            TOOL_NAME)
                    .status(
                            ToolStatus.FAILED)
                    .message(
                            "Invalid EPUB base files creation request.")
                    .issues(
                            validation.getIssues())
                    .build();
        }


        try {

            Map<String, Object> arguments =
                    safeArguments(
                            request);

            String planId =
                    readRequiredString(
                            arguments,
                            "planId");


            CreateEpubProjectPlan plan =
                    planService.get(
                            planId);


            requireCreating(
                    plan);


            Path projectPath =
                    resolveProjectPath(
                            plan.getFolderName());


            validateProjectStructure(
                    projectPath);


            List<String> createdFiles =
                    createBaseFiles(
                            plan,
                            projectPath);


            /*
             * 모든 기본 파일 생성이 성공한 경우에만
             * 프로젝트 생성 완료 상태로 변경한다.
             */
            CreateEpubProjectPlan createdPlan =
                    planService.markCreated(
                            planId);


            return ToolResult.builder()
                    .toolName(
                            TOOL_NAME)
                    .status(
                            ToolStatus.SUCCESS)
                    .message(
                            "EPUB base files were created successfully.")
                    .data(
                            createOutput(
                                    createdPlan,
                                    projectPath,
                                    createdFiles))
                    .build();


        } catch (IllegalArgumentException exception) {

            return failure(
                    "Invalid EPUB base files creation request: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (IllegalStateException exception) {

            return failure(
                    "EPUB base files creation failed: "
                            + safeMessage(
                                    exception),
                    exception);

        } catch (Exception exception) {

            return failure(
                    "Failed to create EPUB base files: "
                            + safeMessage(
                                    exception),
                    exception);
        }
    }


    private List<String> createBaseFiles(
            CreateEpubProjectPlan plan,
            Path projectPath) {

        List<String> createdFiles =
                new ArrayList<>();


        createFile(
                projectPath
                        .resolve(
                                "META-INF")
                        .resolve(
                                "container.xml"),
                createContainerXml(),
                createdFiles);


        createFile(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "content.opf"),
                createContentOpf(
                        plan),
                createdFiles);


        createFile(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "nav.xhtml"),
                createNavXhtml(
                        plan),
                createdFiles);


        createFile(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Styles")
                        .resolve(
                                "style1.css"),
                createStyleCss(),
                createdFiles);


        createFile(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Scripts")
                        .resolve(
                                "quiz.js"),
                createQuizJavaScript(),
                createdFiles);


        return List.copyOf(
                createdFiles);
    }


    private void createFile(
            Path file,
            String content,
            List<String> createdFiles) {

        try {

            if (Files.exists(
                    file)) {

                throw new IllegalStateException(
                        "EPUB base file already exists: "
                                + file);
            }


            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);


            createdFiles.add(
                    file.toString());


        } catch (IllegalStateException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to create EPUB base file: "
                            + file,
                    exception);
        }
    }


    private String createContainerXml() {

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0"
                    xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile
                            full-path="OEBPS/content.opf"
                            media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;
    }


    private String createContentOpf(
            CreateEpubProjectPlan plan) {

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package
                    xmlns="http://www.idpf.org/2007/opf"
                    version="%s"
                    unique-identifier="pub-id"
                    xml:lang="%s">

                    <metadata
                        xmlns:dc="http://purl.org/dc/elements/1.1/">

                        <dc:identifier id="pub-id">
                            urn:uuid:%s
                        </dc:identifier>

                        <dc:title>%s</dc:title>

                        <dc:language>%s</dc:language>

                    </metadata>

                    <manifest>

                        <item
                            id="nav"
                            href="nav.xhtml"
                            media-type="application/xhtml+xml"
                            properties="nav"/>

                        <item
                            id="style1"
                            href="Styles/style1.css"
                            media-type="text/css"/>

                        <item
                            id="quiz-js"
                            href="Scripts/quiz.js"
                            media-type="application/javascript"/>

                    </manifest>

                    <spine>
                    </spine>

                </package>
                """.formatted(
                        escapeXml(
                                plan.getEpubVersion()),
                        escapeXml(
                                plan.getLanguage()),
                        escapeXml(
                                plan.getPlanId()),
                        escapeXml(
                                plan.getProjectName()),
                        escapeXml(
                                plan.getLanguage()));
    }


    private String createNavXhtml(
            CreateEpubProjectPlan plan) {

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html
                    xmlns="http://www.w3.org/1999/xhtml"
                    xmlns:epub="http://www.idpf.org/2007/ops"
                    lang="%s"
                    xml:lang="%s">

                <head>
                    <meta charset="UTF-8"/>
                    <title>목차</title>
                    <link
                        rel="stylesheet"
                        type="text/css"
                        href="Styles/style1.css"/>
                </head>

                <body>

                    <nav
                        epub:type="toc"
                        role="doc-toc"
                        aria-labelledby="toc-title">

                        <h1 id="toc-title">목차</h1>

                        <ol>
                        </ol>

                    </nav>

                </body>
                </html>
                """.formatted(
                        escapeXml(
                                plan.getLanguage()),
                        escapeXml(
                                plan.getLanguage()));
    }


    private String createStyleCss() {

        return """
                @charset "UTF-8";

                html,
                body {
                    margin: 0;
                    padding: 0;
                }

                body {
                    line-height: 1.8;
                }

                img {
                    max-width: 100%;
                    height: auto;
                }
                """;
    }


    private String createQuizJavaScript() {

        return """
                document.addEventListener("DOMContentLoaded", function () {
                    initializeQuiz();
                });

                function initializeQuiz() {
                }
                """;
    }


    private void requireCreating(
            CreateEpubProjectPlan plan) {

        if (plan == null) {

            throw new IllegalStateException(
                    "EPUB project creation plan is not available.");
        }


        if (plan.getStatus()
                != CreateEpubProjectPlan.Status.CREATING) {

            throw new IllegalStateException(
                    "EPUB project creation plan is not in CREATING status. "
                            + "planId="
                            + plan.getPlanId()
                            + ", status="
                            + plan.getStatus());
        }
    }


    private Path resolveProjectPath(
            String folderName) {

        String normalizedFolderName =
                requireFolderName(
                        folderName);


        Path projectPath =
                projectsRoot
                        .resolve(
                                normalizedFolderName)
                        .toAbsolutePath()
                        .normalize();


        if (!projectPath.startsWith(
                projectsRoot)) {

            throw new IllegalArgumentException(
                    "Project path escapes projects root: "
                            + projectPath);
        }


        return projectPath;
    }


    private void validateProjectStructure(
            Path projectPath) {

        requireDirectory(
                projectPath);

        requireDirectory(
                projectPath.resolve(
                        "META-INF"));

        requireDirectory(
                projectPath.resolve(
                        "OEBPS"));

        requireDirectory(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Text"));

        requireDirectory(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Styles"));

        requireDirectory(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Images"));

        requireDirectory(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Fonts"));

        requireDirectory(
                projectPath
                        .resolve(
                                "OEBPS")
                        .resolve(
                                "Scripts"));
    }


    private void requireDirectory(
            Path directory) {

        if (!Files.exists(
                directory)) {

            throw new IllegalStateException(
                    "Required EPUB directory does not exist: "
                            + directory);
        }


        if (!Files.isDirectory(
                directory)) {

            throw new IllegalStateException(
                    "Required EPUB path is not a directory: "
                            + directory);
        }
    }


    private Map<String, Object> createOutput(
            CreateEpubProjectPlan plan,
            Path projectPath,
            List<String> createdFiles) {

        Map<String, Object> output =
                new LinkedHashMap<>();


        output.put(
                "planId",
                plan.getPlanId());

        output.put(
                "projectName",
                plan.getProjectName());

        output.put(
                "folderName",
                plan.getFolderName());

        output.put(
                "projectPath",
                projectPath.toString());

        output.put(
                "language",
                plan.getLanguage());

        output.put(
                "epubVersion",
                plan.getEpubVersion());

        output.put(
                "planStatus",
                plan.getStatus()
                        .name());

        output.put(
                "createdFileCount",
                createdFiles.size());

        output.put(
                "createdFiles",
                createdFiles);

        output.put(
                "baseFilesCreated",
                true);


        return Collections.unmodifiableMap(
                output);
    }


    private Map<String, Object> property(
            String type,
            String description) {

        Map<String, Object> property =
                new LinkedHashMap<>();

        property.put(
                "type",
                type);

        property.put(
                "description",
                description);

        return property;
    }


    private Map<String, Object> safeArguments(
            ToolRequest request) {

        if (request == null
                || request.getArguments() == null) {

            return Collections.emptyMap();
        }

        return request.getArguments();
    }


    private String readRequiredString(
            Map<String, Object> arguments,
            String key) {

        Object value =
                arguments.get(
                        key);


        if (!(value instanceof String text)
                || text.isBlank()) {

            throw new IllegalArgumentException(
                    key
                            + " must not be blank.");
        }


        return text.trim();
    }


    private String requireFolderName(
            String folderName) {

        if (folderName == null
                || folderName.isBlank()) {

            throw new IllegalArgumentException(
                    "folderName must not be blank.");
        }


        String normalized =
                folderName.trim();


        if (".".equals(
                normalized)
                || "..".equals(
                        normalized)) {

            throw new IllegalArgumentException(
                    "Invalid project folder name: "
                            + normalized);
        }


        if (normalized.contains("/")
                || normalized.contains("\\")) {

            throw new IllegalArgumentException(
                    "Project folder name must not contain path separators: "
                            + normalized);
        }


        return normalized;
    }


    private String escapeXml(
            String value) {

        if (value == null) {

            return "";
        }

        return value
                .replace(
                        "&",
                        "&amp;")
                .replace(
                        "<",
                        "&lt;")
                .replace(
                        ">",
                        "&gt;")
                .replace(
                        "\"",
                        "&quot;")
                .replace(
                        "'",
                        "&apos;");
    }


    private ToolResult failure(
            String message,
            Exception exception) {

        return ToolResult.builder()
                .toolName(
                        TOOL_NAME)
                .status(
                        ToolStatus.FAILED)
                .message(
                        message)
                .cause(
                        exception)
                .build();
    }


    private ToolIssue error(
            String field,
            String message) {

        return ToolIssue.builder()
                .severity(
                        ToolIssueSeverity.ERROR)
                .field(
                        field)
                .message(
                        message)
                .build();
    }


    private String safeMessage(
            Throwable throwable) {

        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage()
                        .isBlank()) {

            return "Unknown error";
        }

        return throwable.getMessage();
    }
}