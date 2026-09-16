# GomsBook AI RAG — HYBRID V1~V2 실험 기록

## 1. 개요

이 문서는 GomsBook AI의 Hybrid Retrieval 실험인 `HYBRID_V1`과 `HYBRID_V2`의 설계, 구현 의도, 평가 결과와 결론을 정리한다.

Hybrid Retrieval의 목적은 이미 검증된 두 Retrieval Branch를 결합하여 검색 성능을 추가로 향상시키는 것이다.

```text
VECTOR_ONLY_V1
        +
VECTOR_GRAPH_V6
        ↓
Weighted Reciprocal Rank Fusion
        ↓
Final Top-K
```

`VECTOR_GRAPH_V6`는 Graph Retrieval 실험의 최종 후보로 동결되었고, Hybrid 실험에서는 해당 Graph 정책을 그대로 재사용하였다.

## 2. 실험 환경

```text
Golden Dataset
C:\1004.GomsBook\02.Publish\lunchwork_seoul\eval\dataset
└─ rag-lunchwork_seoul-golden-v1.json

전체 Golden Cases  : 40
Retrieval 평가 대상 : 34
Final Top-K         : 5
Embedding Model     : nomic-embed-text
```

평가 우선순위는 다음과 같다.

```text
Primary
├─ Hit Rate@K
├─ Average Recall@K
├─ MRR
├─ MISS_TO_HIT / HIT_TO_MISS
└─ Rank Improved / Rank Regressed

Secondary
└─ Average Answer Score
```

## 3. Hybrid 공통 설계

```text
Vector Branch       = VECTOR_ONLY_V1
VectorGraph Branch  = VECTOR_GRAPH_V6

Vector Weight       = 0.70
VectorGraph Weight  = 0.30
RRF K               = 60

Graph Weight        = 0.025
Graph Seed Limit    = 1
HEADING Filter      = ON
ALT_TEXT Filter     = ON
```

Raw Score 합산 대신 Weighted Reciprocal Rank Fusion을 사용한다.

```text
hybridScore =
    vectorWeight / (rrfK + vectorRank)
  + vectorGraphWeight / (rrfK + vectorGraphRank)
```

이 방식은 VECTOR_GRAPH V1~V6에서 확인한 Score Saturation과 서로 다른 점수 스케일 문제를 피하기 위한 것이다.

## 4. HYBRID_V1

### 설계

```text
branchCandidateMultiplier = 1

VECTOR_ONLY_V1 Top-5
        +
VECTOR_GRAPH_V6 Top-5
        ↓
Weighted RRF
        ↓
Final Top-5
```

### 결과

| Metric | HYBRID_V1 |
|---|---:|
| Evaluated Cases | 34 |
| Hit Rate@5 | 0.9118 |
| Average Recall@5 | 0.9118 |
| MRR | 0.8627 |
| Average Answer Score | 0.8238 |

VECTOR_GRAPH_V6와의 비교:

```text
MISS_TO_HIT    = 0
HIT_TO_MISS    = 0
Rank Improved  = 0
Rank Regressed = 0
Stable Hit     = 31
Stable Miss    = 3
```

### 분석

HYBRID_V1은 기능적으로 정상 동작했지만 VECTOR_GRAPH_V6 대비 추가 Retrieval 이득이 없었다.

두 Branch의 Top-5 후보가 대부분 중복되어 Fusion이 새로운 Candidate를 제공하지 못한 것이 핵심 원인이었다.

```text
Fusion Algorithm 정상
+
Candidate Diversity 부족
=
추가 성능 향상 없음
```

따라서 Weight를 조정하지 않고 Candidate Pool을 확대하는 방향으로 V2를 설계했다.

## 5. HYBRID_V2

### 실험 가설

> Top-5에서는 두 Branch의 후보가 지나치게 유사하지만, 6~10위 후보까지 Fusion 대상에 포함하면 기존 Miss Case의 기대 문서가 Final Top-5로 진입할 수 있다.

### 단일 변경점

```text
HYBRID_V1
Top-5 + Top-5 → RRF → Final Top-5

HYBRID_V2
Top-10 + Top-10 → RRF → Final Top-5
```

나머지 조건은 모두 동일하게 유지한다.

```text
Vector Weight       = 0.70
VectorGraph Weight  = 0.30
RRF K               = 60
Graph Weight        = 0.025
Graph Seed Limit    = 1
Chunk Filter        = ON
Final Top-K         = 5
```

즉 독립변수는 다음 하나뿐이다.

```text
branchCandidateMultiplier
1 → 2
```

### 실행 검증

```text
[RAG-HYBRID] vectorResults=10
[RAG-HYBRID] vectorGraphResults=10
[RAG-HYBRID] Final result count=5
```

Graph V6 정책도 유지되었다.

```text
[RAG-GRAPH] candidate skipped - HEADING
```

Retrieval-only 평가:

```text
experimentId=HYBRID_V2
retrievalType=HYBRID
llmUsed=false
```

## 6. HYBRID_V1 → HYBRID_V2 결과

| Metric | HYBRID_V1 | HYBRID_V2 | Delta |
|---|---:|---:|---:|
| Evaluated Cases | 34 | 34 | - |
| Hit Rate@5 | 0.9118 | **0.9706** | **+0.0588** |
| Average Recall@5 | 0.9118 | **0.9706** | **+0.0588** |
| MRR | 0.8627 | **0.9216** | **+0.0589** |
| Hit Count | 31 | **33** | **+2** |
| Miss Count | 3 | **1** | **-2** |

Case-level 결과:

```text
MISS_TO_HIT    = 2
HIT_TO_MISS    = 0
Rank Improved  = 1
Rank Regressed = 0
```

## 7. 주요 개선 Case

### RAG-GOLD-015

질문:

```text
점심시간에 찾은 서울도서관은 저자에게
단순히 쉬어 가는 곳 이상으로 어떤 의미였나요?
```

결과:

```text
Retrieval
MISS → HIT

Answer Score
0.25 → 0.85
```

### RAG-GOLD-028

질문:

```text
젊은 시절 저자가 무료 시사회를 보기 위해 찾았던 곳은 어디인가요?
```

결과:

```text
Retrieval
MISS → HIT

Answer Score
0.25 → 0.85
```

### RAG-GOLD-010

질문:

```text
시간이 흐른 뒤 덕수궁 돌담길을 걷는 저자의 태도는 과거와 어떻게 달라졌나요?
```

VECTOR_GRAPH_V6 대비:

```text
Relevant Rank
2 → 1
```

## 8. Answer Evaluation

| Metric | HYBRID_V1 | HYBRID_V2 |
|---|---:|---:|
| Average Answer Score | 0.8238 | **0.8787** |
| Delta | - | **+0.0550** |

Retrieval 개선과 함께 Answer Score도 상승했다.

다만 Answer Evaluation은 LLM 기반이므로 실행 간 변동 가능성이 있으며, 실험의 공식 판정은 Retrieval Metrics에 기반한다.

## 9. VECTOR_GRAPH_V6 대비 HYBRID_V2

| Metric | VECTOR_GRAPH_V6 | HYBRID_V2 | Delta |
|---|---:|---:|---:|
| Hit Rate@5 | 0.9118 | **0.9706** | **+0.0588** |
| Average Recall@5 | 0.9118 | **0.9706** | **+0.0588** |
| MRR | 0.8627 | **0.9216** | **+0.0589** |
| MISS_TO_HIT | - | **2** | 개선 |
| HIT_TO_MISS | - | **0** | 악화 없음 |
| Rank Improved | - | **1** | 개선 |
| Rank Regressed | - | **0** | 악화 없음 |

Answer Score:

```text
VECTOR_GRAPH_V6 = 0.8244
HYBRID_V2       = 0.8787
Delta           ≈ +0.0544
```

## 10. 핵심 교훈

### 10.1 Fusion은 후보 다양성이 있어야 의미가 있다

HYBRID_V1은 두 Top-5 Branch가 너무 유사해 성능 차이가 없었다.

### 10.2 Weight Tuning보다 Candidate Pool이 먼저였다

Weight는 `0.70 / 0.30`으로 유지하고 Candidate Pool만 `Top-5 → Top-10`으로 확대했다.

그 결과 Hit, Recall, MRR이 모두 상승했다.

### 10.3 Final Context 수는 늘리지 않았다

```text
Branch Candidate
5 → 10

Final Top-K
5 → 5
```

최종 Answer 단계에 전달되는 Context 수는 유지하면서 Retrieval 품질만 개선했다.

### 10.4 Retrieval 개선이 Answer 품질 개선으로 연결되었다

RAG-GOLD-015, RAG-GOLD-028에서 Retrieval이 `MISS → HIT`으로 바뀌면서 Answer Score도 `0.25 → 0.85`로 상승했다.

## 11. 최종 판정

```text
HYBRID_V2
Hit Rate@5      = 0.9706
Recall@5        = 0.9706
MRR             = 0.9216

MISS_TO_HIT     = 2
HIT_TO_MISS     = 0
Rank Improved   = 1
Rank Regressed  = 0
```

`HYBRID_V2`를 현재 GomsBook AI의 Final Hybrid Retrieval Candidate로 동결한다.

## 12. 남은 Failure Case

34건 중 33건이 Hit이므로 Stable Miss는 1건만 남았다.

```text
RAG-GOLD-008
서울에 처음 올라왔을 때 저자는 시간이 날 때마다 무엇을 찾아다녔나요?
```

다음 단계에서는 무조건적인 HYBRID_V3 추가보다 이 Case의 Retrieval Trace를 분석하여 Failure 원인을 분류한다.

## Final Result

```text
HYBRID_V1
Hit Rate@5 = 0.9118
MRR        = 0.8627

HYBRID_V2
Hit Rate@5 = 0.9706
MRR        = 0.9216

Improvement
Hit +2
Miss -2
MRR +0.0589
HIT_TO_MISS 0
Rank Regression 0
```
