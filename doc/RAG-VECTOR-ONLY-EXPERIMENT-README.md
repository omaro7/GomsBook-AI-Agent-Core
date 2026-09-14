# GomsBook AI RAG Vector Only Experiment

## 1. Overview

이 문서는 GomsBook AI의 RAG(Retrieval-Augmented Generation) 검색 성능을 검증하기 위해 수행한 **Vector Only Baseline 실험**의 과정과 결과를 기록합니다.

이번 실험은 향후 진행할 다음 두 실험의 비교 기준(Baseline)을 확보하는 것을 목적으로 합니다.

```text
Experiment 1
VECTOR_ONLY_V1
    ↓
Baseline

Experiment 2
VECTOR_GRAPH_V1
    ↓
Graph 적용 효과 검증

Experiment 3
HYBRID_V1
    ↓
Vector + Graph 결합 효과 검증
```

이번 문서의 대상은 첫 번째 실험인 `VECTOR_ONLY_V1`입니다.

---

## 2. Experiment Goal

Vector Only 실험의 목적은 Graph 구조나 Hybrid Re-ranking을 적용하지 않은 상태에서 현재 GomsBook AI의 순수 Vector Retrieval 성능을 정량적으로 측정하는 것입니다.

핵심 검증 항목은 다음과 같습니다.

- Golden Dataset이 정상적으로 로드되는가
- 현재 EPUB 프로젝트가 정상적으로 RAG Indexing 되는가
- Vector Retriever가 Golden Dataset 질문에 대해 적절한 문서를 검색하는가
- 정답 문서가 Top-K 안에 포함되는가
- 정답 문서가 얼마나 높은 순위에 배치되는가
- 이후 Graph / Hybrid 실험과 비교할 수 있는 Baseline을 확보할 수 있는가

---

## 3. Experiment ID

```text
Experiment ID   : VECTOR_ONLY_V1
Retrieval Type  : VECTOR_ONLY
Project         : lunchwork_seoul
Top-K           : 5
Embedding Model : nomic-embed-text
```

---

## 4. Golden Dataset

프로젝트별 Golden Dataset을 외부 Publish 경로에서 관리합니다.

기본 경로 정책:

```text
{publishDirectory}
└─ {projectId}
   └─ eval
      └─ dataset
         └─ rag-{projectId}-golden-v1.json
```

현재 프로젝트:

```text
C:\1004.GomsBook\02.Publish
└─ lunchwork_seoul
   └─ eval
      └─ dataset
         └─ rag-lunchwork_seoul-golden-v1.json
```

Golden Dataset은 총 40개 Case로 구성되어 있으며, 이 중 Retrieval 평가 대상인 `ANSWERABLE` Case는 34개입니다.

```text
Total Cases      : 40
ANSWERABLE Cases : 34
Evaluated Cases  : 34
```

`UNANSWERABLE` Case는 Retrieval Summary 집계에서 제외합니다.

---

## 5. Golden Dataset Source Mapping

Golden Dataset에는 문서 정답 위치가 `sourceHints` 형태로 저장될 수 있습니다.

예:

```json
{
  "id": "RAG-GOLD-001",
  "question": "덕수궁 돌담길은 저자에게 어떤 의미였나요?",
  "type": "ANSWERABLE",
  "sourceHints": [
    "OEBPS/Text/chapter10_2.xhtml#p_13",
    "OEBPS/Text/chapter10_2.xhtml#p_14"
  ]
}
```

Retrieval 평가는 Chunk Fragment가 아니라 문서 단위로 수행하므로 다음과 같이 변환합니다.

```text
OEBPS/Text/chapter10_2.xhtml#p_13
OEBPS/Text/chapter10_2.xhtml#p_14
                ↓
OEBPS/Text/chapter10_2.xhtml
```

최종 `expectedDocuments`:

```text
[
  OEBPS/Text/chapter10_2.xhtml
]
```

중복 문서는 제거합니다.

---

## 6. Common RAG Utility Policy

문서 경로 정규화 및 Golden Dataset 정답 문서 정규화는 공통 `RagUtil`을 사용합니다.

주요 공통 기능:

```text
RagUtil.normalizeDocumentPath()
RagUtil.normalizeExpectedDocuments()
RagUtil.requireText()
RagUtil.normalizeOptional()
RagUtil.requireProjectId()
RagUtil.isExcludedDocument()
```

동일한 문자열/경로 처리 로직을 개별 RAG 클래스에서 중복 구현하지 않습니다.

---

## 7. Excluded Document Policy

이번 실험에서는 다음 문서를 RAG 대상에서 제외했습니다.

```text
quiz.xhtml
```

`quiz.xhtml`은 프로젝트 RAG Index 동기화 중 XML Parser 오류를 발생시켰습니다.

```text
[Fatal Error] quiz.xhtml:1:1:
Content is not allowed in prolog.
```

따라서 `RagUtil.isExcludedDocument()`를 사용하여 다음 두 단계에서 제외했습니다.

```text
1. Project RAG Indexing
   └─ XML Parse 이전에 quiz.xhtml 제외

2. Golden Dataset expectedDocuments
   └─ Retrieval 정답 문서에서도 quiz.xhtml 제외
```

이를 통해 평가와 실제 Indexing의 제외 정책을 동일하게 유지했습니다.

---

## 8. Project ID Policy

Golden Dataset의 프로젝트 ID와 Vector Store 내부 프로젝트 ID는 역할이 다릅니다.

### Logical Project ID

Golden Dataset 검증에 사용:

```text
lunchwork_seoul
```

현재 EPUB 프로젝트의 `projectName`과 비교합니다.

### Internal RAG Project ID

Vector Store Namespace에 사용:

```text
f1b415f62fe7243637477e7de75f2011b6012fdac9d5c56a2317358be058831c
```

두 ID를 혼용하지 않습니다.

```text
validateProject()
    → project.getProjectName()
    → lunchwork_seoul

Retriever execution
    → ProjectIndexResult.getProjectId()
    → internal hash project ID
```

---

## 9. Vector Indexing

Golden Evaluation 실행 전에 현재 EPUB 프로젝트와 Vector Store를 동기화합니다.

실행 구조:

```text
EPUB Project
    ↓
ProjectRagIndexer
    ↓
XHTML Scan
    ↓
Document Load
    ↓
Chunking
    ↓
Embedding
    ↓
Vector Store
```

실험 당시 기존 Vector Index는 대부분 최신 상태였습니다.

예:

```text
[RAG][CONTEXT] projectId=f1b415f62fe7243637477e7de75f2011b6012fdac9d5c56a2317358be058831c, chunks=175

[RAG][SKIP] OEBPS/Text/author.xhtml - source unchanged
[RAG][SKIP] OEBPS/Text/chapter00_1.xhtml - source unchanged
[RAG][SKIP] OEBPS/Text/chapter10_1.xhtml - source unchanged
...
```

`source unchanged`는 현재 파일과 기존 Index가 동일하여 재임베딩이 필요하지 않음을 의미합니다.

---

## 10. Evaluation Tool

Golden Dataset 평가는 다음 Agent Tool을 통해 실행합니다.

```text
evaluate_rag_golden
```

Tool 책임:

```text
EvaluateRagGoldenTool
        ↓
RagEvaluationService.evaluateGolden()
        ↓
DefaultRagEvaluationService
        ↓
RagEvaluationRuntime
        ↓
Golden Dataset Loader
        ↓
Rag Evaluation Runner
        ↓
Retriever
        ↓
Retrieval Metrics
        ↓
Report Writer
```

Tool 내부에는 평가 알고리즘을 중복 구현하지 않고 기존 Evaluation Service를 호출합니다.

---

## 11. Evaluation Execution Flow

전체 실행 흐름은 다음과 같습니다.

```text
Golden Dataset
      ↓
40 Cases Load
      ↓
Current Project Validation
      ↓
Project RAG Index Synchronization
      ↓
ANSWERABLE Case Selection
      ↓
34 Cases
      ↓
Vector Retriever
      ↓
Top-K Documents
      ↓
expectedDocuments 비교
      ↓
Case-level Evaluation
      ├─ Hit@K
      ├─ Recall@K
      └─ Reciprocal Rank
      ↓
Retrieval Summary
      ├─ Evaluated Cases
      ├─ Hit Rate@K
      ├─ Average Recall@K
      └─ MRR
      ↓
Evaluation Report
```

---

## 12. Retrieval Result

Retriever 결과에서는 최소 다음 정보를 유지합니다.

```text
rank
source
score
content
```

예:

```json
{
  "rank": 1,
  "source": "OEBPS/Text/chapter10_4.xhtml",
  "score": 0.8423
}
```

Golden Dataset의 `expectedDocuments`와 Retriever의 `source`를 비교하여 Retrieval 성능을 계산합니다.

---

## 13. Evaluation Metrics

### 13.1 Hit@K

Top-K 결과에 정답 문서가 하나 이상 존재하는지 평가합니다.

```text
Hit@K = 1
```

정답 문서가 Top-K 안에 존재하지 않으면:

```text
Hit@K = 0
```

### 13.2 Hit Rate@K

전체 평가 Case 중 Hit한 Case의 비율입니다.

```text
Hit Rate@K
=
Hit Cases
──────────────
Evaluated Cases
```

### 13.3 Recall@K

정답 문서 중 Top-K에 검색된 문서의 비율입니다.

```text
Recall@K
=
Retrieved Expected Documents
────────────────────────────
Expected Documents
```

### 13.4 Average Recall@K

전체 평가 Case의 Recall 평균입니다.

```text
Average Recall@K
=
Σ Recall@K
──────────────
Evaluated Cases
```

### 13.5 Reciprocal Rank

첫 번째 정답 문서의 순위를 평가합니다.

```text
RR = 1 / First Relevant Rank
```

예:

```text
Rank 1 correct → 1.0000
Rank 2 correct → 0.5000
Rank 3 correct → 0.3333
Rank 4 correct → 0.2500
Miss           → 0.0000
```

### 13.6 MRR

전체 Case의 Reciprocal Rank 평균입니다.

```text
MRR
=
Σ Reciprocal Rank
─────────────────
Evaluated Cases
```

MRR은 단순 정답 검색 여부가 아니라 정답 문서가 얼마나 상위에 검색되는지를 측정합니다.

---

## 14. Experiment Result

`VECTOR_ONLY_V1`의 실제 Golden Dataset 평가 결과는 다음과 같습니다.

| Metric | Result |
|---|---:|
| Total Cases | 40 |
| Evaluated Cases | 34 |
| Hit Rate@K | **0.9118** |
| Average Recall@K | **0.9118** |
| MRR | **0.8333** |
| Average Score | **0.8231** |

---

## 15. Result Interpretation

### 15.1 Evaluated Cases

```text
34 / 40
```

Golden Dataset 40건 중 Retrieval 평가 대상인 34건이 정상적으로 평가되었습니다.

이는 `ANSWERABLE` Case 분리 및 Retrieval Evaluation 파이프라인이 정상적으로 동작했음을 의미합니다.

### 15.2 Hit Rate@K

```text
0.9118
```

34건 중 약 91.18%에서 정답 문서가 Top-K 안에 포함되었습니다.

34건 기준으로 해석하면 약 31건의 Case가 Hit한 수준입니다.

```text
31 / 34 ≈ 0.9118
```

따라서 Vector Only Retriever만으로도 높은 정답 문서 검색 성공률을 확보했습니다.

### 15.3 Average Recall@K

```text
0.9118
```

Average Recall@K가 Hit Rate@K와 동일합니다.

현재 Golden Dataset의 Retrieval 정답이 대부분 단일 문서 단위로 구성되어 있기 때문에 자연스러운 결과로 해석할 수 있습니다.

향후 하나의 질문에 여러 `expectedDocuments`를 포함하는 Case가 증가하면 Recall@K는 Graph Retrieval의 효과를 분석하는 데 더 중요한 지표가 됩니다.

### 15.4 MRR

```text
0.8333
```

MRR 0.8333은 정답 문서를 단순히 Top-K 안에서 찾는 것뿐만 아니라 상당수의 Case에서 높은 Rank에 배치하고 있음을 의미합니다.

현재 Vector Only의 개선 여지는 주로 다음 영역에 있습니다.

```text
정답 검색 실패 Case
+
정답은 검색했지만 Rank 2~5에 위치한 Case
```

향후 Graph 또는 Hybrid Retrieval은 이 부분을 개선하는 것을 주요 목표로 합니다.

### 15.5 Average Score

```text
0.8231
```

전체 평가 Score 역시 안정적인 수준으로 나타났습니다.

단, Vector / Graph / Hybrid 검색 구조 비교의 핵심 지표는 다음 세 가지로 유지합니다.

```text
Hit Rate@K
Average Recall@K
MRR
```

`Average Score`는 보조 지표로 활용합니다.

---

## 16. Baseline Decision

이번 결과를 GomsBook AI의 공식 Vector Retrieval Baseline으로 고정합니다.

```text
VECTOR_ONLY_V1
────────────────────────────
Total Cases         40
Evaluated Cases     34
Hit Rate@K          0.9118
Average Recall@K    0.9118
MRR                 0.8333
Average Score       0.8231
```

향후 실험에서는 동일 Golden Dataset과 동일 조건을 사용하여 이 값을 기준으로 성능 변화를 비교합니다.

---

## 17. Remaining Error Cases

Hit Rate@K가 `0.9118`이므로 약 3개의 ANSWERABLE Case가 Top-K에서 정답 문서를 찾지 못한 것으로 해석할 수 있습니다.

```text
Evaluated Cases : 34
Hit Cases       : 약 31
Miss Cases      : 약 3
```

다음 단계에서는 이 실패 Case를 별도로 분석해야 합니다.

분석 항목:

```text
caseId
question
expectedDocuments
retrievedDocuments
retrievedRank
retrievedScore
```

실패 원인은 다음 관점에서 확인합니다.

```text
Embedding 의미 유사도 부족
Chunk 분할 문제
질문의 표현 차이
정답 문서의 정보량 부족
인접 Chapter에 유사 표현 존재
Vector similarity ranking 문제
```

---

## 18. Why Graph Experiment Is Needed

Vector Only의 Hit Rate는 이미 높지만 MRR은 0.8333입니다.

따라서 Graph 실험의 주요 목표는 단순히 Hit Rate를 높이는 것만이 아닙니다.

```text
Vector Only 실패 Case
        ↓
Graph 관계로 정답 문서를 추가 탐색

Vector Only Rank 2~5
        ↓
Graph 관계를 이용하여 Rank 상승
```

예:

```text
Vector Search
chapter10_3.xhtml → Rank 1

Expected
chapter10_4.xhtml
```

EPUB Graph에서 두 Chapter가 인접 관계라면:

```text
chapter10_3.xhtml
        ↓ NEXT
chapter10_4.xhtml
```

을 추가 후보로 활용할 수 있습니다.

---

## 19. Next Experiment

다음 실험은:

```text
VECTOR_GRAPH_V1
```

입니다.

실험 조건은 가능한 한 그대로 유지합니다.

```text
Project         : lunchwork_seoul
Golden Dataset  : 동일
Total Cases     : 40
Evaluated Cases : 34
Embedding       : nomic-embed-text
Top-K           : 5
```

변경하는 것은 Retrieval Strategy뿐입니다.

```text
VECTOR_ONLY
        ↓
VECTOR + GRAPH
```

---

## 20. Future Comparison Table

향후 세 실험을 다음 표로 비교합니다.

| Experiment | Retrieval | Evaluated Cases | Hit Rate@K | Avg Recall@K | MRR | Avg Score |
|---|---|---:|---:|---:|---:|---:|
| VECTOR_ONLY_V1 | Vector Only | 34 | **0.9118** | **0.9118** | **0.8333** | **0.8231** |
| VECTOR_GRAPH_V1 | Vector + Graph | 34 | TBD | TBD | TBD | TBD |
| HYBRID_V1 | Hybrid | 34 | TBD | TBD | TBD | TBD |

---

## 21. Improvement Calculation

Vector Only를 기준으로 다음 실험의 절대 개선량을 계산합니다.

```text
Hit Rate Improvement
=
New Hit Rate - 0.9118
```

```text
Recall Improvement
=
New Average Recall - 0.9118
```

```text
MRR Improvement
=
New MRR - 0.8333
```

상대 개선율:

```text
Relative Improvement
=
(New Metric - Baseline Metric)
──────────────────────────────
Baseline Metric
× 100
```

---

## 22. Experimental Conclusion

이번 `VECTOR_ONLY_V1` 실험을 통해 GomsBook AI의 현재 Vector Retrieval은 다음 수준의 성능을 확인했습니다.

```text
Hit Rate@K       : 91.18%
Average Recall@K : 91.18%
MRR              : 83.33%
Average Score    : 82.31%
```

34개의 ANSWERABLE Golden Case 중 대부분에서 정답 문서를 Top-K 내에 검색했으며, 정답 문서의 Ranking 품질도 높은 수준으로 확인되었습니다.

따라서 현재 Vector Only 구현은 향후 Graph 및 Hybrid Retrieval 실험을 위한 **신뢰 가능한 Baseline**으로 사용할 수 있습니다.

다음 단계는 Vector Only에서 실패한 Case와 낮은 Rank Case를 분석하고, 해당 실패 유형을 개선하도록 `VECTOR_GRAPH_V1`을 설계하는 것입니다.

---

## 23. Experiment Roadmap

```text
Golden Dataset
      │
      ▼
VECTOR_ONLY_V1
      │
      ├─ Hit Rate@K       0.9118
      ├─ Avg Recall@K     0.9118
      ├─ MRR              0.8333
      └─ Avg Score        0.8231
      │
      ▼
Failure Case Analysis
      │
      ▼
Content Graph / EPUB Graph
      │
      ▼
VECTOR_GRAPH_V1
      │
      ▼
Hybrid Merge / Re-ranking
      │
      ▼
HYBRID_V1
      │
      ▼
Final Comparative Evaluation
```

---

## 24. Summary

`VECTOR_ONLY_V1`은 성공적으로 완료되었습니다.

```text
Status             : BASELINE ESTABLISHED
Experiment         : VECTOR_ONLY_V1
Project            : lunchwork_seoul
Total Cases        : 40
Evaluated Cases    : 34
Hit Rate@K         : 0.9118
Average Recall@K   : 0.9118
MRR                : 0.8333
Average Score      : 0.8231
```

이 결과를 변경하지 않고 Baseline으로 보존한 뒤, 동일 Golden Dataset을 사용하여 `VECTOR_GRAPH_V1` 및 `HYBRID_V1` 실험을 진행합니다.
