package kr.co.goms.gomsbook.ai.agent.approval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.project.CurrentProjectProvider;
import kr.co.goms.gomsbook.ai.project.EpubProjectContext;

public final class DefaultAgentApprovalExecutor
        implements AgentApprovalExecutor {

    public static final String ACTION_CREATE_BASIC_XHTML =
            "CREATE_BASIC_XHTML";

    private final CurrentProjectProvider projectProvider;

    public DefaultAgentApprovalExecutor(
            CurrentProjectProvider projectProvider) {

        this.projectProvider =
                Objects.requireNonNull(
                        projectProvider,
                        "projectProvider must not be null"
                );
    }

    @Override
    public void execute(
            AgentApproval approval) {

        Objects.requireNonNull(
                approval,
                "approval must not be null"
        );

        if (!approval.isApproved()) {

            throw new IllegalStateException(
                    "Approval is not approved: "
                            + approval.getApprovalId()
                            + ", status="
                            + approval.getStatus()
            );
        }

        if (ACTION_CREATE_BASIC_XHTML.equals(
                approval.getAction()
        )) {

            executeCreateBasicXhtml(
                    approval
            );

            return;
        }

        throw new IllegalArgumentException(
                "Unsupported approval action: "
                        + approval.getAction()
        );
    }

    private void executeCreateBasicXhtml(
            AgentApproval approval) {

        EpubProjectContext project =
                projectProvider.getCurrentProject();

        if (project == null) {

            throw new IllegalStateException(
                    "Current EPUB project is not available."
            );
        }

        validateProject(
                approval,
                project
        );

        Path textDirectory =
                project.getTextDirectory();

        if (textDirectory == null) {

            throw new IllegalStateException(
                    "Current EPUB Text directory is not available."
            );
        }

        Path target =
                resolveTarget(
                        textDirectory,
                        approval.getFileName()
                );

        try {

            Files.createDirectories(
                    textDirectory
            );

            Files.writeString(
                    target,
                    approval.getContent(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to create XHTML file: "
                            + target,
                    exception
            );
        }
    }

    private void validateProject(
            AgentApproval approval,
            EpubProjectContext project) {

        System.out.println(
                "[GomsBook AI] Approval Project Validation"
                        + " | status=SKIPPED"
                        + " | approvalId="
                        + approval.getApprovalId()
                        + " | projectId="
                        + approval.getProjectId()
                        + " | projectRoot="
                        + project.getProjectRoot()
        );
    }

    private Path resolveTarget(
            Path textDirectory,
            String fileName) {

        if (fileName == null
                || fileName.isBlank()) {

            throw new IllegalArgumentException(
                    "fileName must not be blank."
            );
        }

        Path root =
                textDirectory
                        .toAbsolutePath()
                        .normalize();

        Path target =
                root.resolve(
                        fileName
                )
                        .normalize();

        if (!target.startsWith(
                root
        )) {

            throw new IllegalArgumentException(
                    "XHTML target path escapes Text directory: "
                            + fileName
            );
        }

        if (Files.exists(
                target
        )) {

            throw new IllegalStateException(
                    "XHTML file already exists: "
                            + target
            );
        }

        return target;
    }
}