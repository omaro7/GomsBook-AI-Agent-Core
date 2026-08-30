package kr.co.goms.gomsbook.ai.tool.epub.generation.chapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalService;
import kr.co.goms.gomsbook.ai.agent.event.AgentEvent;
import kr.co.goms.gomsbook.ai.agent.event.AgentEventPublisher;
import kr.co.goms.gomsbook.ai.agent.event.AgentEventType;
import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;
import kr.co.goms.gomsbook.ai.tool.AgentTool;
import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.ToolRequest;
import kr.co.goms.gomsbook.ai.tool.ToolResult;
import kr.co.goms.gomsbook.ai.tool.ToolStatus;

public final class CreateBasicXhtmlTool
        implements AgentTool {

    public static final String TOOL_NAME =
            "create_basic_xhtml";

    public static final String ACTION_CREATE_BASIC_XHTML =
            "CREATE_BASIC_XHTML";

    private static final String FILE_NAME_ARGUMENT =
            "fileName";

    private static final String TITLE_ARGUMENT =
            "title";

    private final CurrentProjectProvider projectProvider;
    private final AgentApprovalService approvalService;
    private final AgentEventPublisher eventPublisher;

    public CreateBasicXhtmlTool(
            CurrentProjectProvider projectProvider,
            AgentApprovalService approvalService,
            AgentEventPublisher eventPublisher) {

        this.projectProvider =
                Objects.requireNonNull(
                        projectProvider,
                        "projectProvider must not be null"
                );

        this.approvalService =
                Objects.requireNonNull(
                        approvalService,
                        "approvalService must not be null"
                );
        
        this.eventPublisher =
                Objects.requireNonNull(
                        eventPublisher,
                        "eventPublisher must not be null"
                );
    }

    @Override
    public String getName() {

        return TOOL_NAME;
    }

    @Override
    public String getDescription() {

        return "현재 EPUB 프로젝트에 곰스북 기본 XHTML 파일 생성을 요청합니다. "
                + "실제 파일 생성 전 사용자 승인이 필요합니다.";
    }

    @Override
    public Map<String, Object> getInputSchema() {

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                FILE_NAME_ARGUMENT,
                Map.of(
                        "type",
                        "string",
                        "description",
                        "생성할 XHTML 파일명. 예: chapter10_11.xhtml"
                )
        );

        properties.put(
                TITLE_ARGUMENT,
                Map.of(
                        "type",
                        "string",
                        "description",
                        "선택적인 XHTML 문서 제목"
                )
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                properties
        );

        schema.put(
                "required",
                List.of(
                        FILE_NAME_ARGUMENT
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return Map.copyOf(
                schema
        );
    }

    @Override
    public ToolResult execute(
            ToolRequest request,
            ToolContext context) {

        try {

            if (request == null) {

                return failure(
                        "TOOL_REQUEST_REQUIRED",
                        "ToolRequest가 없습니다."
                );
            }

            String fileName =
                    readString(
                            request,
                            FILE_NAME_ARGUMENT
                    );

            String title =
                    readString(
                            request,
                            TITLE_ARGUMENT
                    );

            if (fileName == null
                    || fileName.isBlank()) {

                return failure(
                        "FILE_NAME_REQUIRED",
                        "생성할 XHTML 파일명이 없습니다."
                );
            }

            EpubProjectContext project =
                    projectProvider.getCurrentProject();

            if (project == null) {

                return failure(
                        "CURRENT_PROJECT_REQUIRED",
                        "현재 EPUB 프로젝트가 없습니다."
                );
            }

            String normalizedFileName =
                    normalizeFileName(
                            fileName
                    );

            String normalizedTitle =
                    normalizeTitle(
                            title
                    );

            String xhtml =
                    createBasicXhtml(
                            normalizedTitle
                    );

            String runId =
                    resolveRunId(
                            request
                    );

            String projectId =
                    resolveProjectId(
                            project
                    );

            AgentApproval approval =
                    approvalService.create(
                            runId,
                            projectId,
                            ACTION_CREATE_BASIC_XHTML,
                            "XHTML 파일 생성",
                            normalizedFileName
                                    + " 파일을 생성하시겠습니까?",
                            normalizedFileName,
                            xhtml
                    );

            eventPublisher.publish(
                    new AgentEvent(
                            approval.getRunId(),
                            AgentEventType.APPROVAL_REQUIRED,
                            approval.getMessage(),
                            null,
                            TOOL_NAME,
                            approval.getApprovalId(),
                            approval.getTitle(),
                            approval.getFileName(),
                            approval.getContent()
                    )
            );
            
            System.out.println(
                    "[GomsBook AI] Approval Requested"
                            + " | approvalId="
                            + approval.getApprovalId()
                            + " | action="
                            + approval.getAction()
                            + " | fileName="
                            + approval.getFileName()
            );

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "approvalRequired",
                    true
            );

            data.put(
                    "approvalId",
                    approval.getApprovalId()
            );

            data.put(
                    "action",
                    approval.getAction()
            );

            data.put(
                    "status",
                    approval.getStatus().name()
            );

            data.put(
                    "title",
                    approval.getTitle()
            );

            data.put(
                    "message",
                    approval.getMessage()
            );

            data.put(
                    "fileName",
                    approval.getFileName()
            );

            data.put(
                    "content",
                    approval.getContent()
            );

            return ToolResult.builder()
                    .toolName(TOOL_NAME)
                    .status(ToolStatus.SUCCESS)
                    .message(
                            "XHTML 파일 생성 승인이 필요합니다."
                    )
                    .data(data)
                    .build();

        } catch (Exception exception) {

            return failure(
                    "CREATE_BASIC_XHTML_FAILED",
                    exception.getMessage()
            );
        }
    }

    private String createBasicXhtml(
            String title) {

        return """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml"
                      xmlns:epub="http://www.idpf.org/2007/ops"
                      lang="ko"
                      xml:lang="ko">
                <head>
                    <title>%s</title>
                    <link rel="stylesheet"
                          type="text/css"
                          href="../Styles/style1.css" />
                </head>
                <body>
                    <section epub:type="chapter"
                             role="doc-chapter">
                        <h1>%s</h1>
                        <p></p>
                    </section>
                </body>
                </html>
                """.formatted(
                        escapeXml(title),
                        escapeXml(title)
                );
    }

    private String normalizeFileName(
            String fileName) {

        String normalized =
                fileName
                        .trim()
                        .replace('\\', '/');

        if (normalized.contains("/")) {

            normalized =
                    normalized.substring(
                            normalized.lastIndexOf('/') + 1
                    );
        }

        String lower =
                normalized.toLowerCase();

        if (lower.endsWith(".xhml")) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 5
                    )
                            + ".xhtml";

        } else if (!lower.endsWith(".xhtml")) {

            normalized += ".xhtml";
        }

        return normalized;
    }

    private String normalizeTitle(
            String title) {

        if (title == null
                || title.isBlank()) {

            return "제목";
        }

        return title.trim();
    }

    private String resolveRunId(
            ToolRequest request) {

        if (request.getRequestId() != null
                && !request.getRequestId().isBlank()) {

            return request.getRequestId();
        }

        return "manual";
    }

    private String resolveProjectId(
            EpubProjectContext project) {

        /*
         * 현재 EpubProjectContext에 projectId getter가 없으므로
         * 임시로 projectRoot 기반 문자열을 사용합니다.
         *
         * 추후 projectId 정책 확정 시 교체합니다.
         */

        if (project.getProjectRoot() != null) {

            return project
                    .getProjectRoot()
                    .getFileName()
                    .toString();
        }

        return "current-project";
    }

    private String readString(
            ToolRequest request,
            String name) {

        if (request.getArguments() == null) {

            return null;
        }

        Object value =
                request
                        .getArguments()
                        .get(name);

        if (value == null) {

            return null;
        }

        String text =
                String.valueOf(value)
                        .trim();

        return text.isBlank()
                ? null
                : text;
    }

    private String escapeXml(
            String value) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private ToolResult failure(
            String errorCode,
            String message) {

        return ToolResult.builder()
                .toolName(TOOL_NAME)
                .status(ToolStatus.FAILED)
                .message(message)
                .errorCode(errorCode)
                .errorMessage(message)
                .build();
    }
}