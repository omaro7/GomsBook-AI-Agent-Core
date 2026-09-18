/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.agent.approval.handler;

import java.util.Objects;

import com.google.gson.Gson;

import kr.co.goms.gomsbook.ai.agent.approval.AgentApproval;
import kr.co.goms.gomsbook.ai.agent.approval.AgentApprovalHandler;
import kr.co.goms.gomsbook.ai.agent.approval.payload.DeleteRagProjectIndexApprovalPayload;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexException;
import kr.co.goms.gomsbook.ai.rag.index.RagIndexer;
import kr.co.goms.gomsbook.ai.rag.util.RagUtil;

/**
 * 승인된 현재 프로젝트 RAG Vector Index 삭제 작업을 수행합니다.
 */
public final class DeleteRagProjectIndexApprovalHandler implements AgentApprovalHandler {

	private final RagIndexer ragIndexer;
	private final Gson gson;

	public DeleteRagProjectIndexApprovalHandler(RagIndexer ragIndexer, Gson gson) {
		this.ragIndexer = Objects.requireNonNull(ragIndexer, "ragIndexer must not be null");
		this.gson = Objects.requireNonNull(gson, "gson must not be null");
	}

	@Override
	public void execute(AgentApproval approval) {

		Objects.requireNonNull(approval, "approval must not be null");

		DeleteRagProjectIndexApprovalPayload payload = parsePayload(approval);
		String projectId = RagUtil.requireProjectId(payload.getProjectId());
		String approvalProjectId = RagUtil.requireProjectId(approval.getProjectId());

		if (!projectId.equals(approvalProjectId)) {
			throw new IllegalStateException(
					"RAG index deletion project mismatch. approvalProjectId="
							+ approvalProjectId
							+ ", payloadProjectId="
							+ projectId);
		}

		try {

			int deletedCount = ragIndexer.removeCurrentModel(projectId);

			System.out.println(
					"[RAG][DELETE] projectId="
							+ projectId
							+ " | scope=PROJECT_CURRENT_MODEL"
							+ " | deletedCount="
							+ deletedCount);

		} catch (RagIndexException exception) {

			throw new IllegalStateException(
					"Failed to delete current project RAG index. projectId="
							+ projectId
							+ ", message="
							+ safeMessage(exception),
					exception);
		}
	}

	private DeleteRagProjectIndexApprovalPayload parsePayload(AgentApproval approval) {

		String content = requireText(approval.getContent(), "approval.content");

		DeleteRagProjectIndexApprovalPayload payload =
				gson.fromJson(content, DeleteRagProjectIndexApprovalPayload.class);

		if (payload == null) throw new IllegalStateException("RAG index deletion approval payload is empty.");

		return payload;
	}

	private String requireText(String value, String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank.");
		return value.trim();
	}

	private String safeMessage(Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) return throwable == null ? "Unknown error." : throwable.getClass().getSimpleName();
		return throwable.getMessage();
	}
}