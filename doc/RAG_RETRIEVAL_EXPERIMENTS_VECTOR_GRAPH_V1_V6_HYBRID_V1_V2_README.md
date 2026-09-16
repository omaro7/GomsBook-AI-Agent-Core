# GomsBook AI RAG Retrieval Experiments
## VECTOR_GRAPH V1~V6 + HYBRID V1~V2

## 1. 개요

이 문서는 GomsBook AI의 RAG Retrieval 성능 개선 과정을 `VECTOR_ONLY_V1` Baseline부터 `VECTOR_GRAPH_V1~V6`, `HYBRID_V1~V2`까지 하나의 실험 흐름으로 통합 정리한 기록이다.

핵심 연구 질문은 다음과 같다.

> EPUB의 구조 정보를 Vector Retrieval에 결합하고, 이후 Hybrid Rank Fusion으로 확장했을 때 검색 Coverage와 Ranking 품질을 얼마나 개선할 수 있는가?

전체 실험 흐름:

```text
Baseline
→ Graph 적용
→ 성능 악화 확인
→ 원인 분석
→ Document Policy
→ Graph Weight 조정
→ Seed 제한
→ Score Composition 분석
→ Chunk Candidate Policy 개선
→ Graph Baseline 초과
→ Hybrid Fusion
→ 후보 다양성 문제 확인
→ Candidate Pool Expansion
→ 최종 성능 개선
```

## 2. 실험 환경

```text
Golden Dataset
C:\1004.GomsBook\02.Publish\lunchwork_seoul\eval\dataset
└─ rag-lunchwork_seoul-golden-v1.json

전체 Golden Cases      : 40
Retrieval 평가 대상     : 34
NO_ANSWER 등 제외 대상  : 6
Final Top-K            : 5
Embedding Model        : nomic-embed-text
```

평가 구조:

```text
RAG Evaluation
├─ Retrieval Evaluation           ← Primary
│  ├─ Hit Rate@K
│  ├─ Average Recall@K
│  ├─ MRR
│  ├─ First Relevant Rank
│  ├─ MISS_TO_HIT / HIT_TO_MISS
│  └─ Rank Improved / Rank Regressed
│
└─ Answer Evaluation              ← Secondary
   └─ Average Answer Score
```

## 3. Baseline — VECTOR_ONLY_V1

| Metric | Result |
|---|---:|
| Evaluated Cases | 34 |
| Hit Count | 31 |
| Hit Rate@5 | 0.9118 |
| Average Recall@5 | 0.9118 |
| MRR | 0.8333 |
| Miss | 3 |

```text
VECTOR_ONLY_V1
Hit Rate@5 = 0.9118
Recall@5   = 0.9118
MRR        = 0.8333
```

이 결과를 이후 모든 Graph / Hybrid 실험의 기준으로 사용했다.

## 4. VECTOR_GRAPH 공통 구조

```text
Vector Retrieval
      │
      ▼
Vector Result
      │
      ▼
Graph Seed
      │
      ▼
EPUB Spine Expansion
 ├─ PREVIOUS
 └─ NEXT
      │
      ▼
Graph Candidate
      │
      ▼
Score Merge
      │
      ▼
Final Ranking
```

기본 Graph 관계:

```text
Hop            = 1
Adjacent Score = 1.0
```

## 5. VECTOR_GRAPH_V1

설정:

```text
Graph Seed   = Vector Top-K 전체
Graph Weight = 0.10
```

결과:

| Metric | VECTOR_ONLY_V1 | VECTOR_GRAPH_V1 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.8235 |
| Recall@5 | 0.9118 | 0.8235 |
| MRR | 0.8333 | 0.5515 |
| Answer Score | 약 0.8200 | 0.7756 |

분석:

```text
구조적 인접성 ≠ 의미적 관련성
```

비본문 문서가 Graph Candidate로 유입되며 검색 노이즈가 증가했다.

## 6. VECTOR_GRAPH_V2

Graph Document Policy 도입.

제외:

```text
cover.xhtml
nav.xhtml
author.xhtml
copyright.xhtml
part*.xhtml
quiz.xhtml
```

결과:

| Metric | V1 | V2 |
|---|---:|---:|
| Hit Rate@5 | 0.8235 | 0.8529 |
| Recall@5 | 0.8235 | 0.8529 |
| MRR | 0.5515 | 0.5574 |
| Answer Score | 0.7756 | 0.7894 |

Vector-only 대비:

```text
HIT_TO_MISS    = 2
MISS_TO_HIT    = 0
Rank Improved  = 0
Rank Regressed = 15
```

Document Filtering은 효과가 있었지만 Graph Boost가 너무 강했다.

## 7. VECTOR_GRAPH_V3

단일 변경:

```text
Graph Weight
0.10 → 0.05
```

결과:

| Metric | V2 | V3 |
|---|---:|---:|
| Hit Rate@5 | 0.8529 | 0.9118 |
| Recall@5 | 0.8529 | 0.9118 |
| MRR | 0.5574 | 0.6446 |
| Answer Score | 0.7894 | 0.8162 |

```text
MISS_TO_HIT    = 2
HIT_TO_MISS    = 0
Rank Improved  = 5
Rank Regressed = 0
```

Hit는 복구되었지만 Baseline 대비 Ranking Regression은 남았다.

## 8. VECTOR_GRAPH_V4

단일 변경:

```text
Graph Seed
ALL → Top-1
```

설정:

```text
Graph Weight     = 0.05
Graph Seed Limit = 1
```

결과:

| Metric | V3 | V4 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 |
| Recall@5 | 0.9118 | 0.9118 |
| MRR | 0.6446 | 0.6618 |
| Answer Score | 0.8162 | 0.8294 |

```text
Rank Improved  = 3
Rank Regressed = 1
```

Graph Seed 범위도 Candidate Noise를 결정하는 변수임을 확인했다.

## 9. VECTOR_GRAPH_V5

단일 변경:

```text
Graph Weight
0.05 → 0.025
```

결과:

| Metric | V4 | V5 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 |
| Recall@5 | 0.9118 | 0.9118 |
| MRR | 0.6618 | 0.6912 |
| Answer Score | 0.8294 | 0.8244 |

```text
Rank Improved  = 4
Rank Regressed = 0
```

그러나 Vector-only 대비 Rank Regression이 11건 남아 있었다.

추가 Weight 감소는 Vector-only로 수렴하는 방향이므로 Weight Tuning을 중단했다.

## 10. Score Composition 분석

Instrumentation:

```text
rawVectorScore
headingBoost
contentBoost
relationBoost
patternBoost
rerankBoost
uncappedRerankedVectorScore
rerankedVectorScore
graphScore
graphWeight
finalScore
```

대표 Case `RAG-GOLD-023`:

```text
rawVectorScore = 0.759119
headingBoost   = 0.120
contentBoost   = 0.080
relationBoost  = 0.050
patternBoost   = 0.000
rerankBoost    = 0.250

uncapped       = 1.009119
reranked       = 1.000000

graphScore     = 1.0
graphWeight    = 0.025

finalScore     = 1.025
```

Score Saturation 이후 Graph Boost가 추가되면서 기대 문서를 역전시키는 문제가 발생했다.

## 11. HEADING / ALT_TEXT 문제

특히 HEADING Chunk는:

```text
heading == content
```

인 경우가 많았다.

동일 문자열이:

```text
Heading Boost
+
Content Boost
+
Relation Boost
```

를 중복으로 받아 Score Saturation을 빠르게 유발했다.

V5의 대표 Regression Case는 총 11건이었다.

```text
009, 012, 013, 018, 020, 021,
022, 023, 030, 032, 033
```

## 12. VECTOR_GRAPH_V6

단일 변경:

```text
Graph Candidate에서
HEADING 제외
ALT_TEXT 제외
```

나머지는 V5와 동일.

```text
Graph Weight       = 0.025
Graph Seed Limit   = 1
Graph Hop          = 1
```

결과:

| Metric | VECTOR_ONLY_V1 | V5 | V6 |
|---|---:|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 | **0.9118** |
| Recall@5 | 0.9118 | 0.9118 | **0.9118** |
| MRR | 0.8333 | 0.6912 | **0.8627** |
| Hit Count | 31 | 31 | **31** |

V5 → V6:

```text
Rank Improved  = 11
Rank Regressed = 0
```

Vector-only → V6:

```text
Rank Improved  = 2
Rank Regressed = 0
MRR            = 0.8333 → 0.8627
```

`VECTOR_GRAPH_V6`를 Final Graph Candidate로 동결했다.

## 13. HYBRID 설계

```text
Question
   │
   ├───────────────────────┐
   ▼                       ▼
VECTOR_ONLY_V1        VECTOR_GRAPH_V6
   │                       │
   └──────────┬────────────┘
              ▼
       Weighted RRF
              ▼
          Final Top-5
```

Fusion:

```text
hybridScore =
    0.70 / (60 + vectorRank)
  + 0.30 / (60 + vectorGraphRank)
```

Raw Score Fusion은 사용하지 않는다.

## 14. HYBRID_V1

설정:

```text
branchCandidateMultiplier = 1
Top-5 + Top-5 → RRF → Final Top-5
```

결과:

| Metric | VECTOR_GRAPH_V6 | HYBRID_V1 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 |
| Recall@5 | 0.9118 | 0.9118 |
| MRR | 0.8627 | 0.8627 |

```text
MISS_TO_HIT    = 0
HIT_TO_MISS    = 0
Rank Improved  = 0
Rank Regressed = 0
Stable Hit     = 31
Stable Miss    = 3
```

결론:

```text
Fusion 정상
후보 다양성 부족
```

## 15. HYBRID_V2

단일 변경:

```text
branchCandidateMultiplier
1 → 2
```

즉:

```text
HYBRID_V1
Top-5 + Top-5 → RRF → Final Top-5

HYBRID_V2
Top-10 + Top-10 → RRF → Final Top-5
```

다른 조건은 모두 고정했다.

## 16. HYBRID_V2 결과

| Metric | HYBRID_V1 | HYBRID_V2 | Delta |
|---|---:|---:|---:|
| Hit Rate@5 | 0.9118 | **0.9706** | **+0.0588** |
| Recall@5 | 0.9118 | **0.9706** | **+0.0588** |
| MRR | 0.8627 | **0.9216** | **+0.0589** |
| Hit Count | 31 | **33** | **+2** |
| Miss Count | 3 | **1** | **-2** |

Case-level:

```text
MISS_TO_HIT    = 2
HIT_TO_MISS    = 0
Rank Improved  = 1
Rank Regressed = 0
```

## 17. 주요 개선 사례

### RAG-GOLD-015

```text
Retrieval
MISS → HIT

Answer Score
0.25 → 0.85
```

### RAG-GOLD-028

```text
Retrieval
MISS → HIT

Answer Score
0.25 → 0.85
```

### RAG-GOLD-010

```text
Relevant Rank
2 → 1
```

## 18. Answer Evaluation

```text
VECTOR_GRAPH_V6 = 0.8244
HYBRID_V1       = 0.8238
HYBRID_V2       = 0.8787
```

대표 Delta:

```text
VECTOR_GRAPH_V6 → HYBRID_V2
≈ +0.0544

HYBRID_V1 → HYBRID_V2
≈ +0.0550
```

Answer Evaluation은 Secondary Metric으로 관리한다.

## 19. 전체 성능 변화

| Version | 주요 변경 | Hit Rate@5 | MRR |
|---|---|---:|---:|
| VECTOR_ONLY_V1 | Baseline | 0.9118 | 0.8333 |
| VECTOR_GRAPH_V1 | 단순 Spine Graph | 0.8235 | 0.5515 |
| VECTOR_GRAPH_V2 | Document Policy | 0.8529 | 0.5574 |
| VECTOR_GRAPH_V3 | Weight 0.10 → 0.05 | 0.9118 | 0.6446 |
| VECTOR_GRAPH_V4 | Seed Top-1 | 0.9118 | 0.6618 |
| VECTOR_GRAPH_V5 | Weight 0.05 → 0.025 | 0.9118 | 0.6912 |
| VECTOR_GRAPH_V6 | HEADING/ALT_TEXT Candidate 제외 | 0.9118 | 0.8627 |
| HYBRID_V1 | Top-5 + Top-5 Weighted RRF | 0.9118 | 0.8627 |
| HYBRID_V2 | Top-10 + Top-10 Weighted RRF | **0.9706** | **0.9216** |

전체 흐름:

```text
VECTOR_ONLY_V1    0.8333
       ↓
VECTOR_GRAPH_V1   0.5515
       ↓
VECTOR_GRAPH_V2   0.5574
       ↓
VECTOR_GRAPH_V3   0.6446
       ↓
VECTOR_GRAPH_V4   0.6618
       ↓
VECTOR_GRAPH_V5   0.6912
       ↓
VECTOR_GRAPH_V6   0.8627
       ↓
HYBRID_V1         0.8627
       ↓
HYBRID_V2         0.9216
```

## 20. Baseline 대비 최종 개선

```text
VECTOR_ONLY_V1
Hit Rate@5 = 0.9118
MRR        = 0.8333
Hit        = 31 / 34

HYBRID_V2
Hit Rate@5 = 0.9706
MRR        = 0.9216
Hit        = 33 / 34
```

개선:

```text
Hit Rate +0.0588
MRR      +0.0883
Hit      +2
Miss     -2
```

## 21. 핵심 기술적 교훈

### 21.1 Graph를 추가한다고 자동으로 좋아지지 않는다

V1은 Baseline보다 크게 악화되었다.

### 21.2 구조적 인접성과 의미적 관련성은 다르다

EPUB Spine Neighbor는 Candidate Policy 없이 사용할 경우 노이즈가 될 수 있다.

### 21.3 Weight Tuning만으로는 근본 문제를 해결할 수 없다

Weight를 낮출수록 Vector-only에 수렴한다.

### 21.4 Score Instrumentation이 Root Cause를 드러냈다

HEADING Chunk의 중복 Lexical Boost와 Score Saturation을 확인했다.

### 21.5 Candidate Policy가 Graph 품질을 결정했다

V6에서 HEADING/ALT_TEXT를 Graph Candidate에서 제외하여 Baseline MRR을 넘어섰다.

### 21.6 Hybrid Fusion은 Candidate Diversity가 필요하다

HYBRID_V1에서는 차이가 없었고, HYBRID_V2에서 Candidate Pool을 넓히자 성능이 상승했다.

### 21.7 Final Top-K를 늘리지 않고 성능을 개선했다

```text
Branch Top-K
5 → 10

Final Top-K
5 → 5
```

Context 수 증가 없이 Retrieval 품질을 개선했다.

### 21.8 Retrieval과 Answer Evaluation은 분리해야 한다

```text
retrievalSummary
→ Primary

answerSummary
→ Secondary
```

## 22. Retrieval Trace

각 Case에 다음을 기록한다.

```text
expectedDocuments

retrievedDocuments
├─ chunkId
├─ sourcePath
├─ title
├─ rank
├─ score
├─ retrievalSource
├─ vectorScore
├─ graphScore
├─ graphWeight
├─ finalScore
└─ metadata
```

Hybrid Metadata:

```text
retrievalMode
retrievalSource
vectorMatched
vectorGraphMatched
vectorRank
vectorGraphRank
vectorWeight
vectorGraphWeight
rrfK
branchCandidateMultiplier
branchTopK
finalTopK
vectorRrfScore
vectorGraphRrfScore
hybridScore
finalScore
```

## 23. 남은 Failure Case

HYBRID_V2 이후 34건 중 33건이 Hit이다.

남은 Stable Miss:

```text
RAG-GOLD-008
서울에 처음 올라왔을 때 저자는 시간이 날 때마다 무엇을 찾아다녔나요?
```

다음 단계는 무조건적인 V3 추가가 아니라 이 Case의 Failure Analysis다.

## 24. 전체 실험 흐름

```text
VECTOR_ONLY_V1
Baseline
MRR 0.8333
       │
       ▼
VECTOR_GRAPH_V1
단순 Graph
MRR 0.5515
       │
       ▼
VECTOR_GRAPH_V2
Document Policy
MRR 0.5574
       │
       ▼
VECTOR_GRAPH_V3
Weight 0.05
MRR 0.6446
       │
       ▼
VECTOR_GRAPH_V4
Seed Top-1
MRR 0.6618
       │
       ▼
VECTOR_GRAPH_V5
Weight 0.025
MRR 0.6912
       │
       ▼
Score Composition Analysis
       │
       ├─ Score Saturation
       ├─ HEADING double counting
       └─ ALT_TEXT 영향
       │
       ▼
VECTOR_GRAPH_V6
Chunk Candidate Policy
MRR 0.8627
Rank Regression 0
       │
       ▼
HYBRID_V1
Top-5 + Top-5
Weighted RRF
MRR 0.8627
       │
       ├─ Fusion 정상
       └─ Candidate Diversity 부족
       │
       ▼
HYBRID_V2
Top-10 + Top-10
Weighted RRF
Final Top-5
       │
       ▼
MRR 0.9216
Hit Rate 0.9706
MISS_TO_HIT 2
HIT_TO_MISS 0
Rank Improved 1
Rank Regressed 0
```

## 25. 최종 판정

현재 최종 Retrieval Candidate:

```text
HYBRID_V2
```

최종 결과:

```text
Evaluated Cases = 34
Hit              = 33
Miss             = 1

Hit Rate@5       = 0.9706
Recall@5         = 0.9706
MRR              = 0.9216

MISS_TO_HIT      = 2
HIT_TO_MISS      = 0
Rank Improved    = 1
Rank Regressed   = 0
```

## 26. 포트폴리오 관점의 핵심 메시지

이 실험은 단순 기능 추가가 아니라 다음 전체 사이클을 구현한 것이다.

```text
Golden Dataset 설계
→ Baseline 측정
→ Graph Retrieval
→ Regression 발견
→ Controlled Experiment
→ Candidate Policy 설계
→ Score Instrumentation
→ Root Cause 분석
→ Graph Ranking 개선
→ Weighted Rank Fusion
→ Candidate Diversity 분석
→ Candidate Pool Expansion
→ Final 성능 검증
→ Failure Analysis 대상으로 축소
```

보여주는 역량:

- RAG Evaluation Pipeline 설계
- Golden Dataset 기반 정량 평가
- Vector Retrieval
- EPUB 구조 기반 Graph Retrieval
- Hybrid Retrieval
- Weighted Reciprocal Rank Fusion
- Hit@K / Recall@K / MRR
- Case-level Regression Analysis
- Retrieval Trace 설계
- Score Decomposition
- Controlled Experiment
- Version 관리
- Retrieval / Answer Evaluation 분리

## Final Result

```text
Baseline
VECTOR_ONLY_V1
Hit Rate@5 = 0.9118
MRR        = 0.8333

Final Graph
VECTOR_GRAPH_V6
Hit Rate@5 = 0.9118
MRR        = 0.8627

Final Hybrid
HYBRID_V2
Hit Rate@5 = 0.9706
MRR        = 0.9216

Final Improvement
Hit Rate +0.0588
MRR      +0.0883
Hit      31 → 33
Miss      3 → 1
```

`HYBRID_V2`를 현재 GomsBook AI RAG Retrieval의 최종 Candidate로 동결하고, 남은 `RAG-GOLD-008`은 별도 Failure Analysis 대상으로 관리한다.
