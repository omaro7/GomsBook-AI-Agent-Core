/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.rag.eval.model.RagRetrievalResult;

/**
 * RAG 실행 결과.
 *
 * Retriever가 검색한 Context와 LLM이 생성한 최종 답변을 보관한다.
 */
public final class RagExecutionResult {

	private final List<String> retrievedContexts;
	private final String answer;
	private final RagRetrievalResult retrievalResult;

	public RagExecutionResult(List<String> retrievedContexts, String answer) {
		this(retrievedContexts, answer, null);
	}

	public RagExecutionResult(List<String> retrievedContexts, String answer, RagRetrievalResult retrievalResult) {
		this.retrievedContexts = immutableCopy(retrievedContexts);
		this.answer = Objects.requireNonNull(answer, "answer must not be null");
		this.retrievalResult = retrievalResult;
	}

	public List<String> getRetrievedContexts() {
		return retrievedContexts;
	}

	public String getAnswer() {
		return answer;
	}

	public RagRetrievalResult getRetrievalResult() {
		return retrievalResult;
	}

	public boolean hasRetrievalResult() {
		return retrievalResult != null;
	}

	private static <T> List<T> immutableCopy(List<T> values) {
		if (values == null || values.isEmpty()) return Collections.emptyList();
		return Collections.unmodifiableList(new ArrayList<>(values));
	}
}