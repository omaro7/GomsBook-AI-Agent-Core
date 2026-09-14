/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */

package kr.co.goms.gomsbook.ai.rag.eval.report;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import kr.co.goms.gomsbook.ai.rag.eval.RagMetricResult;
import kr.co.goms.gomsbook.ai.rag.eval.model.RagRetrievalEvaluationResult;
import kr.co.goms.gomsbook.ai.rag.eval.model.RagRetrievalSummary;

/**
 * RAG Evaluation Report를 JSON 형식으로 저장한다.
 *
 * {
 *   "datasetName": "rag-golden-v1",
 *   "averageScore": 0.9175,
 *	 "retrievalSummary": {
 *	    "evaluatedCases": 34,						// 40질문 중에 NO ANSWER 6개 제외
 *	    "hitCount": 32,
 *	    "hitRateAtK": 0.9411764705882353,
 *	    "averageRecallAtK": 0.9411764705882353,
 *	    "meanReciprocalRank": 0.8529411764705882
 *	 },   
 *   "entries": [
 *     {
 *       "caseId": "RAG-GOLD-001",
 *       "question": "서울시립미술관은 점심시간에 어떤 장점이 있는 공간으로 소개되나요?",
 *       "overallScore": 0.93,
 *       "metrics": [
 *         {
 *           "name": "faithfulness",
 *           "score": 0.96,
 *           "reason": "The answer is supported by the retrieved contexts."
 *         },
 *         {
 *           "name": "answer_relevancy",
 *           "score": 0.94,
 *           "reason": "The answer directly addresses the question."
 *         },
 *         {
 *           "name": "context_precision",
 *           "score": 0.82,
 *           "reason": "Most retrieved contexts are relevant."
 *         },
 *         {
 *           "name": "context_recall",
 *           "score": 1.0,
 *           "reason": "All reference information is covered."
 *         }
 *       ],
 *       "retrieval": {
 *         "applicable": true,
 *         "hitAtK": true,
 *         "recallAtK": 1.0,
 *         "mrr": 0.5
 *       }
 *     }
 *   ]
 * }
 */
public final class RagEvaluationReportWriter {

	private final Gson gson;

	public RagEvaluationReportWriter() {
		this(new GsonBuilder().setPrettyPrinting().create());
	}

	public RagEvaluationReportWriter(Gson gson) {
		if (gson == null) throw new NullPointerException("gson must not be null");
		this.gson = gson;
	}

	public void write(RagEvaluationReport report, Path path) throws IOException {
		if (report == null) throw new NullPointerException("report must not be null");
		if (path == null) throw new NullPointerException("path must not be null");

		Path parent = path.getParent();

		if (parent != null) Files.createDirectories(parent);

		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			write(report, writer);
		}
	}

	public void write(RagEvaluationReport report, Writer writer) {
		if (report == null) throw new NullPointerException("report must not be null");
		if (writer == null) throw new NullPointerException("writer must not be null");

		ReportJson reportJson = convert(report);

		gson.toJson(reportJson, writer);
	}

	private ReportJson convert(RagEvaluationReport report) {
		ReportJson result = new ReportJson();

		result.datasetName = report.getDatasetName();
		result.averageScore = report.getAverageScore();
		result.retrievalSummary = convertRetrievalSummary(report.getRetrievalSummary());
		result.entries = new ArrayList<>();

		for (RagEvaluationReport.Entry entry : report.getEntries()) {
			result.entries.add(convertEntry(entry));
		}

		return result;
	}

	private RetrievalSummaryJson convertRetrievalSummary(RagRetrievalSummary summary) {
		RetrievalSummaryJson result = new RetrievalSummaryJson();
		result.evaluatedCases = summary.getEvaluatedCases();
		result.hitCount = summary.getHitCount();
		result.hitRateAtK = summary.getHitRateAtK();
		result.averageRecallAtK = summary.getAverageRecallAtK();
		result.meanReciprocalRank = summary.getMeanReciprocalRank();
		return result;
	}

	private EntryJson convertEntry(RagEvaluationReport.Entry entry) {
		EntryJson result = new EntryJson();

		result.caseId = entry.getCaseId();
		result.question = entry.getQuestion();
		result.overallScore = entry.getResult().getOverallScore();
		result.metrics = new ArrayList<>();

		for (RagMetricResult metricResult : entry.getResult().getMetricResults()) {
			MetricJson metricJson = new MetricJson();
			metricJson.name = metricResult.getMetricName();
			metricJson.score = metricResult.getScore();
			metricJson.reason = metricResult.getReason();
			result.metrics.add(metricJson);
		}

		RagRetrievalEvaluationResult retrievalResult = entry.getRetrievalEvaluationResult();
		RetrievalJson retrievalJson = new RetrievalJson();
		retrievalJson.applicable = retrievalResult.isApplicable();
		retrievalJson.hitAtK = retrievalResult.isHitAtK();
		retrievalJson.recallAtK = retrievalResult.getRecallAtK();
		retrievalJson.mrr = retrievalResult.getMrr();
		result.retrieval = retrievalJson;

		return result;
	}

	private static final class ReportJson {

		private String datasetName;
		private double averageScore;
		private RetrievalSummaryJson retrievalSummary;
		private List<EntryJson> entries;
	}

	private static final class RetrievalSummaryJson {

		private int evaluatedCases;
		private int hitCount;
		private double hitRateAtK;
		private double averageRecallAtK;
		private double meanReciprocalRank;
	}

	private static final class EntryJson {

		private String caseId;
		private String question;
		private double overallScore;
		private List<MetricJson> metrics;
		private RetrievalJson retrieval;
	}

	private static final class MetricJson {

		private String name;
		private double score;
		private String reason;
	}

	private static final class RetrievalJson {

		private boolean applicable;
		private boolean hitAtK;
		private double recallAtK;
		private double mrr;
	}
}