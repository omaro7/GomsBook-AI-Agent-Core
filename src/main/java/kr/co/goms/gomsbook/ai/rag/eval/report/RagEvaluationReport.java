/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import kr.co.goms.gomsbook.ai.rag.eval.RagEvaluationResult;
import kr.co.goms.gomsbook.ai.rag.eval.RagMetricResult;
import kr.co.goms.gomsbook.ai.rag.eval.model.RagRetrievalEvaluationResult;
import kr.co.goms.gomsbook.ai.rag.eval.model.RagRetrievalSummary;

/**
 * RAG Evaluation 전체 Report.
 *
 * Dataset 단위의 평가 결과를 보관하고 전체 평균 점수를 제공한다.
 * 
 * Golden Dataset
 *    ├─ Case 1
 *    │    └─ RagEvaluationResult
 *    ├─ Case 2
 *    │    └─ RagEvaluationResult
 *    └─ Case N
 *         └─ RagEvaluationResult
 *              ↓
 *       RagEvaluationReport
 *              ↓
 *       averageScore
 *       
 * List<RagEvaluationReport.Entry> entries = new ArrayList<>();
 * 
 * entries.add(
 *         new RagEvaluationReport.Entry(
 *                 "LUNCH-001",
 *                 "덕수궁 돌담길은 어떤 장소인가요?",
 *                 result1));
 * 
 * entries.add(
 *         new RagEvaluationReport.Entry(
 *                 "LUNCH-002",
 *                 "서울시립미술관은 어떤 공간인가요?",
 *                 result2));
 * 
 * RagEvaluationReport report =
 *         new RagEvaluationReport(
 *                 "lunchwork-seoul-v1",
 *                 entries);
 */
public final class RagEvaluationReport {

	private final String datasetName;
	private final List<Entry> entries;
	private final double averageScore;
	private final RagRetrievalSummary retrievalSummary;

	public RagEvaluationReport(String datasetName, List<Entry> entries) {
		this.datasetName = requireText(datasetName, "datasetName");
		this.entries = normalizeEntries(entries);
		this.averageScore = calculateAverageScore(this.entries);
		this.retrievalSummary = calculateRetrievalSummary(this.entries);
	}

	public double getAverageMetricScore(String metricName) {
		if (metricName == null || metricName.trim().isEmpty()) throw new IllegalArgumentException("metricName must not be blank");

		double total = 0.0;
		int count = 0;

		for (Entry entry : entries) {
			RagMetricResult metricResult = entry.getResult().getMetricResult(metricName);

			if (metricResult == null) continue;

			total += metricResult.getScore();
			count++;
		}

		if (count == 0) throw new IllegalStateException("Metric not found in evaluation report: " + metricName);

		return total / count;
	}

	public String getDatasetName() {
		return datasetName;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public double getAverageScore() {
		return averageScore;
	}

	public RagRetrievalSummary getRetrievalSummary() {
		return retrievalSummary;
	}

	public Entry getEntry(String caseId) {
		if (caseId == null || caseId.trim().isEmpty()) return null;

		for (Entry entry : entries) {
			if (caseId.equals(entry.getCaseId())) return entry;
		}

		return null;
	}

	private static List<Entry> normalizeEntries(List<Entry> entries) {
		if (entries == null || entries.isEmpty()) return Collections.emptyList();

		List<Entry> normalized = new ArrayList<>();

		for (Entry entry : entries) {
			if (entry != null) normalized.add(entry);
		}

		return Collections.unmodifiableList(normalized);
	}

	private static double calculateAverageScore(List<Entry> entries) {
		if (entries.isEmpty()) return 0.0;

		double total = 0.0;

		for (Entry entry : entries) {
			total += entry.getResult().getOverallScore();
		}

		return total / entries.size();
	}

	private static RagRetrievalSummary calculateRetrievalSummary(List<Entry> entries) {
		int evaluatedCases = 0;
		int hitCount = 0;
		double recallTotal = 0.0;
		double mrrTotal = 0.0;

		for (Entry entry : entries) {
			RagRetrievalEvaluationResult retrievalResult = entry.getRetrievalEvaluationResult();

			if (!retrievalResult.isApplicable()) continue;

			evaluatedCases++;

			if (retrievalResult.isHitAtK()) hitCount++;

			recallTotal += retrievalResult.getRecallAtK();
			mrrTotal += retrievalResult.getMrr();
		}

		if (evaluatedCases == 0) return RagRetrievalSummary.empty();

		double hitRateAtK = (double) hitCount / evaluatedCases;
		double averageRecallAtK = recallTotal / evaluatedCases;
		double meanReciprocalRank = mrrTotal / evaluatedCases;

		return new RagRetrievalSummary(evaluatedCases, hitCount, hitRateAtK, averageRecallAtK, meanReciprocalRank);
	}

	private static String requireText(String value, String fieldName) {
		if (value == null) throw new NullPointerException(fieldName + " must not be null");

		String normalized = value.trim();

		if (normalized.isEmpty()) throw new IllegalArgumentException(fieldName + " must not be blank");

		return normalized;
	}

	@Override
	public String toString() {
		return "RagEvaluationReport{" + "datasetName='" + datasetName + '\'' + ", size=" + entries.size() + ", averageScore=" + averageScore + ", retrievalSummary=" + retrievalSummary + '}';
	}

	/**
	 * 개별 Evaluation Case의 Report Entry.
	 */
	public static final class Entry {

		private final String caseId;
		private final String question;
		private final RagEvaluationResult result;
		private final RagRetrievalEvaluationResult retrievalEvaluationResult;

		public Entry(String caseId, String question, RagEvaluationResult result) {
			this(caseId, question, result, RagRetrievalEvaluationResult.notApplicable());
		}

		public Entry(String caseId, String question, RagEvaluationResult result, RagRetrievalEvaluationResult retrievalEvaluationResult) {
			this.caseId = Objects.requireNonNull(caseId, "caseId must not be null");
			this.question = Objects.requireNonNull(question, "question must not be null");
			this.result = Objects.requireNonNull(result, "result must not be null");
			this.retrievalEvaluationResult = Objects.requireNonNull(retrievalEvaluationResult, "retrievalEvaluationResult must not be null");
		}

		public String getCaseId() {
			return caseId;
		}

		public String getQuestion() {
			return question;
		}

		public RagEvaluationResult getResult() {
			return result;
		}

		public RagRetrievalEvaluationResult getRetrievalEvaluationResult() {
			return retrievalEvaluationResult;
		}
	}
}