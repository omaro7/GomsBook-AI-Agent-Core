# GomsBook AI RAG Retrieval Experiment

## 1. Overview

이 문서는 GomsBook AI의 RAG(Retrieval-Augmented Generation) 검색 구조를 비교 검증하기 위한 실험 가이드입니다.

동일한 Golden Dataset을 대상으로 다음 3가지 Retrieval 방식을 비교합니다.

1. **Vector Only**
2. **Vector + Graph**
3. **Hybrid**

모든 실험은 동일한 질문, 동일한 정답 문서(`expectedDocuments`), 동일한 `Top-K` 조건을 사용하며, 검색 품질은 정량 지표로 평가합니다.

---

## 2. Experiment Goal

실험의 목적은 EPUB 기반 RAG에서 검색 구조를 단계적으로 확장했을 때 Retrieval 성능이 얼마나 개선되는지 확인하는 것입니다.

기본 실험 흐름은 다음과 같습니다.

```text
Experiment 1
Vector Only
    ↓
Baseline

Experiment 2
Vector + Graph
    ↓
Graph 구조 정보 추가

Experiment 3
Hybrid
    ↓
Vector / Graph 결과 결합 및 재정렬
```

최종적으로 다음 질문에 답하는 것을 목표로 합니다.

- Vector Search만으로 충분한 검색 품질을 확보할 수 있는가?
- Graph 정보를 추가하면 정답 문서 검색률이 개선되는가?
- Hybrid Retrieval이 Vector Only 및 Vector + Graph보다 안정적으로 높은 성능을 보이는가?
- Graph 적용 효과가 Hit Rate, Recall, MRR 중 어떤 지표에서 가장 크게 나타나는가?

---

## 3. Golden Dataset

프로젝트별 Golden Dataset을 사용합니다.

기본 경로 정책:

```text
{publishDirectory}
└─ {projectId}
   └─ eval
      └─ dataset
         └─ rag-{projectId}-golden-v1.json
```

예:

```text
C:\1004.GomsBook\02.Publish\lunchwork_seoul\eval\dataset\rag-lunchwork_seoul-golden-v1.json
```

Golden Dataset 예시:

```json
{
  "id": "rag-001",
  "question": "덕수궁 돌담길을 걸으며 어떤 느낌을 받았나요?",
  "type": "ANSWERABLE",
  "expectedDocuments": [
    "OEBPS/Text/chapter10_4.xhtml"
  ]
}
```

### Dataset Rule

- `ANSWERABLE` Case만 Retrieval 평가 대상에 포함합니다.
- `UNANSWERABLE` Case는 Retrieval Summary의 `evaluatedCases`에서 제외합니다.
- 하나의 질문은 하나 이상의 `expectedDocuments`를 가질 수 있습니다.
- 문서 경로 비교 시 공통 정규화 규칙을 적용합니다.
- GomsBook AI에서는 문자열 및 문서 경로 정규화를 `RagUtil`로 통일합니다.

현재 Baseline Golden Dataset 기준:

```text
Total Cases       : 40
ANSWERABLE Cases  : 34
Evaluation Cases  : 34
```

따라서 Retrieval Summary의 첫 번째 검증 조건은 다음과 같습니다.

```text
evaluatedCases = 34
```

---

## 4. Common Experiment Conditions

세 실험은 Retrieval 방식 외의 조건을 최대한 동일하게 유지해야 합니다.

| 항목 | 조건 |
|---|---|
| Project | 동일 프로젝트 |
| Golden Dataset | 동일 Dataset |
| Questions | 동일 질문 |
| Expected Documents | 동일 정답 문서 |
| Embedding Model | 동일 모델 |
| Chunking | 동일 정책 |
| Top-K | 동일 값 |
| Document Index | 동일 EPUB 원본 |
| Evaluation Metrics | 동일 지표 |
| Path Normalization | 동일 `RagUtil` 규칙 |

권장 기본값:

```text
Embedding Model : nomic-embed-text
Top-K           : 5
Evaluation      : Hit Rate@K / Average Recall@K / MRR
```

실험 간 Embedding Model, Chunk Size, Top-K 등을 변경하면 Retrieval 방식 자체의 효과를 정확히 비교하기 어렵습니다.

---

# 5. Experiment 1 - Vector Only

## 5.1 Purpose

Vector Only는 전체 실험의 Baseline입니다.

질문을 Embedding으로 변환한 후 Vector Store에서 의미적 유사도가 높은 Chunk를 검색합니다.

```text
Question
    ↓
Embedding
    ↓
Vector Store
    ↓
Similarity Search
    ↓
Top-K Documents
```

Graph 정보는 사용하지 않습니다.

---

## 5.2 Retrieval Flow

```text
User Question
    ↓
Embedding Model
    ↓
Vector Retriever
    ↓
Similarity Score
    ↓
Top-K
```

검색 결과에서 최소한 다음 정보를 보존합니다.

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
  "score": 0.8234
}
```

---

## 5.3 Experiment ID

권장 ID:

```text
VECTOR_ONLY_V1
```

---

## 5.4 Expected Role

Vector Only 결과는 이후 Graph 및 Hybrid 실험의 비교 기준입니다.

점수 자체의 높고 낮음보다 **재현 가능한 Baseline 확보**가 우선입니다.

---

# 6. Experiment 2 - Vector + Graph

## 6.1 Purpose

Vector 검색 결과에 EPUB의 구조적 관계를 추가하여 검색 범위를 확장하거나 보정합니다.

Graph는 단순한 의미 유사도가 아니라 문서 간 관계를 활용합니다.

예:

```text
Part
 └─ Chapter
     ├─ Section
     ├─ Previous Chapter
     ├─ Next Chapter
     ├─ Linked Document
     └─ Related Content
```

---

## 6.2 Graph Sources

GomsBook AI에서는 Graph를 단계적으로 구성할 수 있습니다.

### Content Graph

콘텐츠 간 의미적 또는 구조적 관계를 표현합니다.

예:

```text
덕수궁
 ├─ 덕수궁 돌담길
 ├─ 정동길
 ├─ 서울시립미술관
 └─ 대한문
```

### EPUB Graph

EPUB 패키지 구조 자체를 Graph로 표현합니다.

예:

```text
EPUB
 ├─ Manifest
 ├─ Spine
 │   ├─ chapter10_1.xhtml
 │   ├─ chapter10_2.xhtml
 │   └─ chapter10_4.xhtml
 ├─ Navigation
 ├─ Part
 └─ Chapter
```

---

## 6.3 Retrieval Flow

```text
Question
    ↓
Vector Search
    ↓
Initial Top-K
    ↓
Graph Expansion
    ↓
Related Nodes / Documents
    ↓
Graph-aware Result
```

예를 들어 Vector 검색 결과가:

```text
chapter10_3.xhtml
```

을 반환했을 때 Graph에서 인접한:

```text
chapter10_4.xhtml
chapter10_2.xhtml
```

를 추가 후보로 탐색할 수 있습니다.

---

## 6.4 Experiment ID

권장 ID:

```text
VECTOR_GRAPH_V1
```

---

## 6.5 Important Rule

Graph를 적용하더라도 Golden Dataset의 정답 기준은 변경하지 않습니다.

```text
Question
Expected Documents
Top-K
Evaluation Metrics
```

는 Vector Only와 동일해야 합니다.

---

# 7. Experiment 3 - Hybrid

## 7.1 Purpose

Hybrid Retrieval은 Vector Search와 Graph Retrieval 결과를 각각 생성한 뒤 결합하여 최종 Ranking을 결정합니다.

단순 Graph 확장보다 Retrieval 전략 자체를 복합화하는 단계입니다.

---

## 7.2 Retrieval Flow

```text
                    ┌─ Vector Retriever ───┐
Question ───────────┤                      ├─ Merge
                    └─ Graph Retriever ────┘
                              ↓
                           Re-rank
                              ↓
                            Top-K
```

---

## 7.3 Candidate Merge

예:

Vector 결과:

```text
1 chapter10_3.xhtml
2 chapter10_4.xhtml
3 chapter20_1.xhtml
```

Graph 결과:

```text
1 chapter10_4.xhtml
2 chapter10_5.xhtml
3 chapter10_3.xhtml
```

병합 후보:

```text
chapter10_3.xhtml
chapter10_4.xhtml
chapter20_1.xhtml
chapter10_5.xhtml
```

이후 Hybrid Score로 재정렬합니다.

---

## 7.4 Hybrid Score

초기 구현에서는 단순 Weighted Score를 사용할 수 있습니다.

예:

```text
hybridScore
=
vectorScore × vectorWeight
+
graphScore × graphWeight
```

예:

```text
vectorWeight = 0.7
graphWeight  = 0.3
```

단, Weight를 조정하는 실험은 별도의 Experiment Version으로 관리하는 것을 권장합니다.

예:

```text
HYBRID_V1_70_30
HYBRID_V2_60_40
HYBRID_V3_50_50
```

---

## 7.5 Experiment ID

기본 권장 ID:

```text
HYBRID_V1
```

---

# 8. Evaluation Metrics

세 실험은 동일한 Retrieval Metrics를 사용합니다.

---

## 8.1 Hit@K

Top-K 결과 중 하나 이상의 정답 문서가 존재하는지 평가합니다.

```text
Hit@K =
1 : 정답 문서가 Top-K에 존재
0 : 정답 문서가 Top-K에 없음
```

예:

```text
Expected:
chapter10_4.xhtml

Retrieved:
1 chapter10_2.xhtml
2 chapter10_4.xhtml
3 chapter20_1.xhtml
```

결과:

```text
Hit@3 = 1
```

---

## 8.2 Hit Rate@K

전체 평가 Case 중 Hit한 Case의 비율입니다.

```text
Hit Rate@K
=
Hit Cases
──────────────
Evaluated Cases
```

예:

```text
30 / 34 = 0.8824
```

---

## 8.3 Recall@K

하나의 질문이 여러 개의 정답 문서를 가질 때 검색된 정답 문서의 비율입니다.

```text
Recall@K
=
Retrieved Expected Documents
────────────────────────────
Expected Documents
```

예:

```text
Expected:
chapter10_4.xhtml
chapter10_5.xhtml

Retrieved:
chapter10_4.xhtml
chapter20_1.xhtml
chapter30_1.xhtml
```

결과:

```text
Recall@K = 1 / 2 = 0.5
```

---

## 8.4 Average Recall@K

전체 평가 Case의 Recall 평균입니다.

```text
Average Recall@K
=
Σ Recall@K
──────────────
Evaluated Cases
```

---

## 8.5 Reciprocal Rank

첫 번째 정답 문서가 검색된 순위를 평가합니다.

```text
RR = 1 / First Relevant Rank
```

예:

```text
Rank 1 : wrong
Rank 2 : wrong
Rank 3 : correct
```

이면:

```text
RR = 1 / 3 = 0.3333
```

정답이 Top-K에 없으면:

```text
RR = 0
```

---

## 8.6 MRR

전체 Case의 Reciprocal Rank 평균입니다.

```text
MRR
=
Σ Reciprocal Rank
─────────────────
Evaluated Cases
```

MRR은 단순 정답 포함 여부뿐 아니라 **정답 문서가 얼마나 상위에 검색되는지** 평가하는 중요한 지표입니다.

---

# 9. Evaluation Report

각 실험은 동일한 Report 구조를 사용합니다.

예:

```json
{
  "experiment": {
    "id": "VECTOR_ONLY_V1",
    "retrievalType": "VECTOR_ONLY",
    "topK": 5
  },
  "retrievalSummary": {
    "evaluatedCases": 34,
    "hitRateAtK": 0.8824,
    "averageRecallAtK": 0.8529,
    "mrr": 0.7917
  },
  "cases": []
}
```

---

# 10. Experiment Result Comparison

실험 완료 후 결과를 다음 표로 비교합니다.

| Experiment | Retrieval | Evaluated Cases | Hit Rate@5 | Avg Recall@5 | MRR |
|---|---|---:|---:|---:|---:|
| VECTOR_ONLY_V1 | Vector Only | 34 | TBD | TBD | TBD |
| VECTOR_GRAPH_V1 | Vector + Graph | 34 | TBD | TBD | TBD |
| HYBRID_V1 | Hybrid | 34 | TBD | TBD | TBD |

---

## 10.1 Improvement Calculation

Vector Only를 Baseline으로 개선율을 계산합니다.

예:

```text
MRR Improvement
=
Hybrid MRR - Vector Only MRR
```

상대 개선율이 필요하면:

```text
Relative Improvement
=
(Hybrid - Baseline)
────────────────────
Baseline
× 100
```

예:

```text
Vector Only MRR = 0.70
Hybrid MRR      = 0.80

Relative Improvement
=
(0.80 - 0.70) / 0.70 × 100
=
14.29%
```

---

# 11. Recommended Experiment Procedure

모든 실험은 다음 절차로 실행합니다.

```text
1. EPUB Project 준비
2. Golden Dataset 확인
3. Project Document Indexing
4. 동일 Embedding Model 확인
5. 동일 Top-K 확인
6. Retrieval Mode 설정
7. Golden Evaluation 실행
8. Case별 결과 저장
9. Retrieval Summary 생성
10. JSON Report 저장
11. 세 실험 결과 비교
```

---

# 12. Experiment Order

권장 실행 순서는 다음과 같습니다.

```text
Phase 1
VECTOR_ONLY_V1
    ↓
Baseline 확보

Phase 2
VECTOR_GRAPH_V1
    ↓
Graph 효과 검증

Phase 3
HYBRID_V1
    ↓
복합 Retrieval 효과 검증
```

한 실험이 완료된 후 다음 실험으로 진행해야 문제 발생 시 원인을 분리하기 쉽습니다.

---

# 13. Experiment Directory

실험 결과는 Dataset과 분리하여 저장하는 것을 권장합니다.

예:

```text
{publishDirectory}
└─ {projectId}
   └─ eval
      ├─ dataset
      │  └─ rag-{projectId}-golden-v1.json
      │
      └─ reports
         ├─ vector-only
         │  └─ rag-{projectId}-vector-only-v1.json
         │
         ├─ vector-graph
         │  └─ rag-{projectId}-vector-graph-v1.json
         │
         └─ hybrid
            └─ rag-{projectId}-hybrid-v1.json
```

예:

```text
C:\1004.GomsBook\02.Publish\lunchwork_seoul\eval\reports
```

---

# 14. Experiment Reproducibility

RAG 실험은 재현 가능해야 합니다.

Report에는 가능하면 다음 환경 정보를 함께 저장합니다.

```text
projectId
datasetVersion
experimentId
retrievalType
embeddingModel
topK
chunkingPolicy
vectorWeight
graphWeight
evaluatedAt
```

Hybrid가 아닌 경우 불필요한 Weight는 비워둘 수 있습니다.

예:

```json
{
  "experimentId": "HYBRID_V1",
  "retrievalType": "HYBRID",
  "embeddingModel": "nomic-embed-text",
  "topK": 5,
  "vectorWeight": 0.7,
  "graphWeight": 0.3
}
```

---

# 15. Success Criteria

## Functional Success

세 실험 모두 다음 조건을 만족해야 합니다.

```text
Golden Dataset 정상 로드
ANSWERABLE Case 정상 필터링
expectedDocuments 정상 로드
Retriever source 보존
Retriever rank 보존
Hit@K 계산
Recall@K 계산
Reciprocal Rank 계산
Hit Rate@K 계산
Average Recall@K 계산
MRR 계산
JSON Report 생성
```

현재 Golden Dataset 기준:

```text
evaluatedCases = 34
```

가 모든 실험에서 동일해야 합니다.

---

## Experimental Success

실험의 성공 여부는 반드시 Hybrid가 최고 점수를 내는 것으로 정의하지 않습니다.

실험의 핵심은 다음을 **정량적으로 증명**하는 것입니다.

```text
Vector Only
vs
Vector + Graph
vs
Hybrid
```

Graph 추가가 효과가 없거나 특정 지표만 개선하더라도 중요한 실험 결과입니다.

---

# 16. Analysis Guide

결과 분석 시 단순 평균 점수 외에 Case별 차이를 확인합니다.

특히 다음 Case를 별도로 분석합니다.

### Vector Only 실패 → Graph 성공

Graph 구조가 실제 Retrieval을 개선한 대표 사례입니다.

### Vector 성공 → Graph 실패

Graph Expansion 또는 Ranking이 오히려 Noise를 추가했을 가능성이 있습니다.

### Vector + Graph 성공 → Hybrid 실패

Hybrid Weight 또는 Re-ranking 정책 문제를 의심할 수 있습니다.

### 세 방식 모두 실패

다음 원인을 검토합니다.

```text
Golden Dataset 오류
expectedDocuments 오류
Chunking 문제
Embedding 한계
질문 표현 문제
Index 누락
source 경로 불일치
```

---

# 17. Final Comparison Report

최종 결과는 다음 형태로 정리합니다.

```text
GomsBook AI RAG Retrieval Evaluation
====================================

Dataset
-------
rag-lunchwork_seoul-golden-v1.json

Evaluation Cases
----------------
34

Top-K
-----
5

Results
-------

Vector Only
Hit Rate@5     : ...
Recall@5       : ...
MRR            : ...

Vector + Graph
Hit Rate@5     : ...
Recall@5       : ...
MRR            : ...

Hybrid
Hit Rate@5     : ...
Recall@5       : ...
MRR            : ...
```

그리고 최종 결론에서 다음을 기술합니다.

```text
1. Baseline 대비 Graph 적용 효과
2. Hybrid Retrieval 적용 효과
3. 가장 크게 개선된 Metric
4. 성능이 개선된 대표 Query
5. 성능이 하락한 대표 Query
6. 다음 실험 개선 방향
```

---

# 18. Architecture Roadmap

현재 실험 로드맵:

```text
Golden Dataset
      │
      ├─────────────────────────────┐
      │                             │
      ▼                             ▼
Vector Retriever               Graph Retriever
      │                             │
      │                             │
      ├─ Experiment 1               ├─ Experiment 2
      │  Vector Only                │  Vector + Graph
      │                             │
      └──────────────┬──────────────┘
                     │
                     ▼
                 Hybrid
                     │
                Experiment 3
                     │
                     ▼
              Retrieval Report
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
     Hit@K        Recall@K       MRR
```

---

# 19. Portfolio Value

이 실험은 단순히 RAG 기능을 구현하는 것에서 끝나지 않고 다음 과정을 포함합니다.

```text
Problem Definition
        ↓
Golden Dataset
        ↓
Baseline
        ↓
Architecture Improvement
        ↓
Controlled Experiment
        ↓
Quantitative Evaluation
        ↓
Failure Analysis
```

따라서 GomsBook AI의 RAG 구현을 **검색 기능 구현 프로젝트가 아니라 평가 가능한 AI 시스템 개발 프로젝트**로 설명할 수 있습니다.

---

# 20. Next Step

권장 다음 구현 순서:

```text
1. VECTOR_ONLY_V1 실제 Golden Evaluation 실행
2. Baseline Report 저장
3. Content Graph 구현
4. EPUB Graph 구현
5. VECTOR_GRAPH_V1 평가
6. Hybrid Merge / Re-ranking 구현
7. HYBRID_V1 평가
8. 세 결과 자동 비교 Report 생성
```

첫 번째 기준점은 다음입니다.

```text
VECTOR_ONLY_V1
evaluatedCases = 34
```

이 값이 정상적으로 나온 뒤 Hit Rate@5, Average Recall@5, MRR 값을 Baseline으로 고정합니다.
