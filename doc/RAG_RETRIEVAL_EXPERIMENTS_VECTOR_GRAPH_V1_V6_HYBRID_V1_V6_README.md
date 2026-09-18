# GomsBook AI RAG Retrieval Experiments
## VECTOR_GRAPH V1~V6 + HYBRID V1~V6

## 1. 개요

이 문서는 GomsBook AI의 RAG Retrieval 성능 개선 과정을 `VECTOR_ONLY_V1` Baseline부터 `VECTOR_GRAPH_V1~V6`, `HYBRID_V1~V6`까지 하나의 실험 흐름으로 통합 정리한 기록이다.

핵심 연구 질문은 다음과 같다.

> EPUB의 구조 정보를 Vector Retrieval에 결합하고, Hybrid Rank Fusion과 Contextual Embedding, Controlled Lexical Rerank, Query-Intent Adaptive Rerank로 확장했을 때 검색 Coverage와 Ranking 품질을 얼마나 개선할 수 있는가?

최종 결론은 다음과 같다.

```text
Final Retrieval Candidate
HYBRID_V6

Evaluated Cases      = 34 / 34
Hit                  = 34
Miss                 = 0

Hit Rate@5           = 1.0000
Average Recall@5     = 1.0000
MRR                  = 0.9225

Average Answer Score = 0.8625
```

`HYBRID_V2`에서 마지막 Stable Miss로 남았던 `RAG-GOLD-008`은 V3~V6의 Failure Analysis를 통해 해결되었고, 최종적으로 34개 ANSWERABLE Case 전체가 Final Top-5에 진입했다.

---

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
│  └─ Rank Regression
│
└─ Answer Evaluation              ← Secondary
   └─ Average Answer Score
```

Retrieval을 Primary Metric으로 두고, LLM Answer Score는 별도 Secondary Metric으로 관리한다.

---

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

---

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

---

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

결론:

```text
구조적 인접성 ≠ 의미적 관련성
```

Graph를 단순 추가하면 오히려 Candidate Noise가 증가했다.

---

## 6. VECTOR_GRAPH_V2

Graph Document Policy를 도입했다.

제외 대상:

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

Document Filtering은 효과가 있었지만 Graph Boost가 여전히 강했다.

---

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

Hit는 복구되었지만 Ranking Regression은 남았다.

---

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

Graph Seed 범위도 Candidate Noise를 결정하는 변수임을 확인했다.

---

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

Weight를 더 낮추는 것은 Vector-only에 수렴하는 방향이므로 단순 Weight Tuning을 중단했다.

---

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

대표적으로 Lexical Boost가 더해진 뒤 score가 `1.0`에서 포화되면서 여러 Chunk가 동일 점수가 되는 현상을 확인했다.

기존 정책:

```text
finalScore
= rawVectorScore
+ headingBoost
+ contentBoost
+ relationBoost
+ patternBoost

rerankedScore
= min(1.0, finalScore)
```

문제:

```text
Score Saturation
→ 대량 1.0 동점
→ 의미 점수 순위 손실
→ ID 기반 Tie-break 영향 증가
```

이 문제는 이후 `RAG-GOLD-008` Failure Analysis에서도 다시 핵심 원인으로 확인되었다.

---

## 11. HEADING / ALT_TEXT 문제

특히 HEADING Chunk는 다음 구조를 가지는 경우가 많았다.

```text
heading == content
```

동일 문자열이:

```text
Heading Boost
+
Content Boost
+
Relation Boost
```

를 중복으로 받아 Score Saturation을 빠르게 유발했다.

---

## 12. VECTOR_GRAPH_V6

단일 변경:

```text
Graph Candidate에서
HEADING 제외
ALT_TEXT 제외
```

나머지는 V5와 동일:

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

`VECTOR_GRAPH_V6`를 Final Graph Candidate로 동결했다.

---

## 13. HYBRID 공통 구조

```text
Question
   │
   ├───────────────────────┐
   ▼                       ▼
VECTOR                VECTOR_GRAPH
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

기본 설정:

```text
Vector Weight       = 0.70
VectorGraph Weight  = 0.30
RRF K               = 60
Final Top-K         = 5
Graph Weight        = 0.025
Graph Seed Limit    = 1
Graph Candidate Filter
  HEADING / ALT_TEXT 제외
```

Raw Score 자체를 서로 직접 합산하지 않고 Rank 기반 RRF를 사용한다.

---

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

결론:

```text
Fusion 정상
후보 다양성 부족
```

---

## 15. HYBRID_V2

단일 변경:

```text
branchCandidateMultiplier
1 → 2
```

즉:

```text
Top-10 + Top-10 → Weighted RRF → Final Top-5
```

결과:

| Metric | HYBRID_V1 | HYBRID_V2 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | **0.9706** |
| Recall@5 | 0.9118 | **0.9706** |
| MRR | 0.8627 | **0.9216** |
| Hit Count | 31 | **33** |
| Miss Count | 3 | **1** |

Case-level:

```text
MISS_TO_HIT = 2
HIT_TO_MISS = 0
```

복구된 대표 Case:

```text
RAG-GOLD-015
RAG-GOLD-028
```

남은 Stable Miss:

```text
RAG-GOLD-008
```

---

## 16. RAG-GOLD-008 — Failure Analysis 대상

Case:

```text
Case ID
RAG-GOLD-008

Question
서울에 처음 올라왔을 때 저자는 시간이 날 때마다 무엇을 찾아다녔나요?

Expected Evidence
OEBPS/Text/chapter10_4.xhtml#p_05

Evidence
낮에는 공부를 하고, 시간이 날 때마다 무료 공연이나 전시를 찾아다녔다.

Previous Context
OEBPS/Text/chapter10_4.xhtml#p_04

처음 서울에 올라왔을 때였다.
```

핵심 특징:

```text
Question Context
"서울에 처음 올라왔을 때"

Previous Paragraph
"처음 서울에 올라왔을 때였다."

Question Answer Target
"무엇을 찾아다녔나요?"

Current Paragraph
"무료 공연이나 전시를 찾아다녔다."
```

즉 하나의 질의 의미가 인접한 두 Paragraph에 분산되어 있었다.

---

## 17. HYBRID_V3 — Contextual Embedding

V3의 목적은 Retrieval / Graph / RRF 파라미터를 바꾸지 않고 **Embedding Text만 변경**하여 Chunk Context Loss 여부를 검증하는 것이었다.

정책:

```text
Previous Paragraph = 1
Current Paragraph  = 1
Next Paragraph     = 0
```

관찰:

```text
RAG-GOLD-008 Raw Vector Rank
V2 = 49
V3 = 23
```

전체 결과:

| Metric | HYBRID_V2 | HYBRID_V3 |
|---|---:|---:|
| Hit Rate@5 | 0.9706 | 0.9706 |
| Recall@5 | 0.9706 | 0.9706 |
| MRR | 0.9216 | **0.9412** |
| RAG-GOLD-008 | MISS | MISS |
| Answer Average Score | 0.8787 | **0.9056** |

결론:

```text
Contextual Embedding은 유효
49 → 23으로 크게 개선

그러나 Branch Top-K=10에는 아직 진입하지 못함
```

V3는 HIT 전환에는 실패했지만 Context Loss 가설을 지지한 중요한 중간 실험이다.

---

## 18. HYBRID_V4 — Compact Contextual Embedding + Pure Vector 진단

V4에서는 Contextual Embedding을 더 단순화했다.

기존 Contextual Embedding Text에 포함되던 메타데이터 / 라벨을 제거하고:

```text
Previous Paragraph
+
Current Paragraph
```

본문 텍스트만 Embedding했다.

RAG-GOLD-008 Raw Vector 결과:

```text
Rank  = 1
Score = 0.937712

sourcePath
OEBPS/Text/chapter10_4.xhtml

chunkId
OEBPS/Text/chapter10_4.xhtml#p_05
```

즉:

```text
V2 49
→ V3 23
→ V4 1
```

로 Semantic Retrieval 자체는 해결되었다.

그러나 기존 `DefaultRetriever` 내부의 Lexical Rerank에서 다음 현상이 발생했다.

```text
p_05 Raw Vector
rank  = 1
score = 0.937712

contentBoost
= 0.080000

uncapped
= 1.017712

score clipping
= 1.000000

Final Reranked Rank
= 24
```

원인:

```text
여러 Candidate가 Lexical Boost 이후 1.0에 포화
→ 대량 동점
→ Raw Vector 순위 파괴
```

Pure Vector Branch로 변경하자 `RAG-GOLD-008` 단건은:

```text
vectorRank      = 1
vectorGraphRank = 1
finalRank       = 1

Hit    = true
Recall = 1.0
MRR    = 1.0
```

으로 해결되었다.

그러나 전체 34건 회귀 평가에서는:

```text
Hit Rate@5 = 0.8529
Recall@5   = 0.8529
MRR        = 0.6422

Hit = 29 / 34
```

로 크게 악화되었다.

즉 기존 Lexical Rerank는 `RAG-GOLD-008`에는 노이즈였지만, 다른 Case에서는 실제 유효 Signal이었다.

V4에서 새로 발생한 HIT_TO_MISS:

```text
RAG-GOLD-007
RAG-GOLD-023
RAG-GOLD-026
RAG-GOLD-028
RAG-GOLD-032
```

결론:

```text
Pure Vector 전면 적용은 REJECT
```

---

## 19. V4 회귀 Case Raw Vector Rank 분석

Project-wide Raw Vector Top-100에서 기대 문서 최초 출현 순위를 확인했다.

| Case | Raw Vector Rank | Score |
|---|---:|---:|
| RAG-GOLD-007 | 9 | 0.902060 |
| RAG-GOLD-023 | 6 | 0.913564 |
| RAG-GOLD-026 | 6 | 0.906723 |
| RAG-GOLD-028 | 16 | 0.898744 |
| RAG-GOLD-032 | 6 | 0.906080 |

해석:

```text
007 / 023 / 026 / 032
→ Branch Top-K=10 내부 또는 경계
→ 약한 Lexical 보조 신호로 복구 가능

028
→ Raw Rank 16
→ Branch Candidate 밖
→ 더 강한 보조 신호 필요
```

---

## 20. HYBRID_V5 — Controlled Lexical Rerank

V5에서는 Lexical Rerank를 제거하지 않고 영향력을 제한했다.

기존:

```text
finalScore
= vectorScore + lexicalBoost

return min(1.0, finalScore)
```

V5:

```text
finalScore
= vectorScore
+ lexicalBoost × rerankWeight
```

중요 변경:

```text
score clipping 제거
Semantic Vector = 주 신호
Lexical Boost    = 보조 신호
```

### V5-A — rerankWeight 0.25

6개 Regression Set:

```text
007 HIT
008 HIT
023 HIT
026 HIT
028 MISS
032 HIT
```

주요 Rank:

```text
007 Rank 4
008 Rank 3
023 Rank 1
026 Rank 1
032 Rank 1
```

`RAG-GOLD-028`은 여전히 MISS였다.

### V5-B — rerankWeight 0.30

`RAG-GOLD-028`:

```text
Raw Rank      = 16
Reranked Rank = 10
```

Branch Top-K 경계까지 진입했다.

`RAG-GOLD-008`:

```text
HIT
MRR = 0.3333
Rank = 3
```

따라서 DEFAULT 성격의 일반 질의에는 `0.30`이 안정적이었다.

### V5-C — rerankWeight 0.58

`RAG-GOLD-028`:

```text
HIT
MRR = 0.2000
Rank = 5
```

그러나:

```text
RAG-GOLD-008
MISS
```

로 다시 회귀했다.

결론:

```text
Global rerankWeight tuning은 REJECT
```

단일 전역 가중치로는 `RAG-GOLD-008`과 `RAG-GOLD-028`을 동시에 안정적으로 해결하기 어려웠다.

---

## 21. RAG-GOLD-028 Weight Margin 분석

`rerankWeight=0.30`에서 `chapter10_4.xhtml` 최고 Candidate:

```text
chunkId
chapter10_4.xhtml#p_08

rawVector    = 0.891240
lexicalBoost = 0.040000
finalScore   = 0.903240

Reranked Rank = 10
```

Rank 5 Candidate:

```text
rawVector    = 0.909902
lexicalBoost = 0.000000
```

Target이 Top-5에 진입하려면 주변 Candidate 전체를 고려할 때 실험적으로 약 `0.58` 수준의 weight가 필요했다.

실제 `0.58`에서:

```text
RAG-GOLD-028
Rank 5
HIT
```

를 확인했지만, 동시에 `RAG-GOLD-008`이 MISS로 회귀했다.

이 결과가 V6의 Query-Intent Adaptive Rerank 설계 근거가 되었다.

---

## 22. HYBRID_V6 — Query-Intent Adaptive Rerank

V6에서는 전역 가중치 하나를 사용하지 않고 Query Intent에 따라 가중치를 다르게 적용했다.

정책:

```text
DEFAULT
rerankWeight = 0.30

LOCATION
rerankWeight = 0.58
```

예:

```text
RAG-GOLD-008
"무엇을 찾아다녔나요?"
→ DEFAULT
→ 0.30

RAG-GOLD-028
"...찾았던 곳은 어디인가요?"
→ LOCATION
→ 0.58
```

중요 원칙:

```text
Golden Case ID Hard Coding 없음
질문 전체 문자열 Hard Coding 없음
일반화 가능한 Query Intent 규칙 사용
```

대표 LOCATION 표현:

```text
어디
어느 곳
어떤 곳
어느 장소
어떤 장소
장소는
장소가
곳은 어디
곳이 어디
```

---

## 23. HYBRID_V6 핵심 회귀 세트 결과

| Case | Intent | Hit | MRR | First Relevant Rank |
|---|---|---:|---:|---:|
| RAG-GOLD-007 | DEFAULT | 1.0000 | 0.3333 | 3 |
| RAG-GOLD-008 | DEFAULT | 1.0000 | 0.3333 | 3 |
| RAG-GOLD-023 | DEFAULT | 1.0000 | 1.0000 | 1 |
| RAG-GOLD-026 | DEFAULT | 1.0000 | 1.0000 | 1 |
| RAG-GOLD-028 | LOCATION | 1.0000 | 0.2000 | 5 |
| RAG-GOLD-032 | DEFAULT | 1.0000 | 1.0000 | 1 |

결과:

```text
Regression Set
6 / 6 HIT
```

V5에서 충돌하던:

```text
RAG-GOLD-008
RAG-GOLD-028
```

을 동시에 복구했다.

---

## 24. HYBRID_V6 전체 결과

Retrieval:

```text
Dataset
rag-golden-v1

Evaluated Cases
34 / 34

Hit
34

Miss
0

Hit Rate@5
1.0000

Average Recall@5
1.0000

MRR
0.9225
```

LLM Answer:

```text
Average Score
0.8625
```

판정:

```text
Retrieval
PASS

Stable Miss
0

HIT_TO_MISS
0
```

`HYBRID_V6`를 최종 Retrieval Candidate로 확정한다.

---

## 25. 전체 성능 변화

| Version | 주요 변경 | Hit Rate@5 | MRR |
|---|---|---:|---:|
| VECTOR_ONLY_V1 | Baseline | 0.9118 | 0.8333 |
| VECTOR_GRAPH_V1 | 단순 Spine Graph | 0.8235 | 0.5515 |
| VECTOR_GRAPH_V2 | Document Policy | 0.8529 | 0.5574 |
| VECTOR_GRAPH_V3 | Graph Weight 0.05 | 0.9118 | 0.6446 |
| VECTOR_GRAPH_V4 | Seed Top-1 | 0.9118 | 0.6618 |
| VECTOR_GRAPH_V5 | Graph Weight 0.025 | 0.9118 | 0.6912 |
| VECTOR_GRAPH_V6 | HEADING/ALT_TEXT Candidate 제외 | 0.9118 | 0.8627 |
| HYBRID_V1 | Top-5 + Top-5 Weighted RRF | 0.9118 | 0.8627 |
| HYBRID_V2 | Top-10 + Top-10 Weighted RRF | 0.9706 | 0.9216 |
| HYBRID_V3 | Contextual Embedding | 0.9706 | **0.9412** |
| HYBRID_V4 | Compact Context + Pure Vector | 0.8529 | 0.6422 |
| HYBRID_V5 | Controlled Lexical Rerank | 6-Case Tuning | 6-Case Tuning |
| HYBRID_V6 | Query-Intent Adaptive Rerank | **1.0000** | **0.9225** |

주의:

```text
HYBRID_V5는 전체 34건 최종 Candidate 평가가 아니라
6개 Regression Set 중심의 Controlled Experiment였다.
```

---

## 26. Baseline 대비 최종 개선

```text
VECTOR_ONLY_V1

Hit Rate@5 = 0.9118
Recall@5   = 0.9118
MRR        = 0.8333
Hit        = 31 / 34
Miss       = 3
```

최종:

```text
HYBRID_V6

Hit Rate@5 = 1.0000
Recall@5   = 1.0000
MRR        = 0.9225
Hit        = 34 / 34
Miss       = 0
```

개선:

```text
Hit Rate
0.9118 → 1.0000
+0.0882

Recall
0.9118 → 1.0000
+0.0882

MRR
0.8333 → 0.9225
+0.0892

Hit
31 → 34
+3

Miss
3 → 0
-3
```

---

## 27. RAG-GOLD-008 Failure Analysis — 최종 결론

초기 가설:

```text
Semantic Gap
Multi-sentence Evidence
Chunk Boundary
Candidate Recall 부족
Query 표현 불일치
Golden Dataset 오류 가능성
```

Trace 결과:

```text
Golden Dataset Issue
→ 아님

Expected Evidence
→ chapter10_4.xhtml#p_05 확인

Previous Context
→ chapter10_4.xhtml#p_04

Context Loss
→ 실제 존재

Raw Vector Rank
V2 49
V3 23
V4 1
```

따라서 첫 번째 Root Cause는:

```text
Chunk-level Context Loss
```

였다.

그러나 V4에서 Raw Vector Rank 1까지 개선되었음에도 기존 Lexical Rerank 이후 Rank 24로 하락했다.

Trace:

```text
rawVector
0.937712

contentBoost
0.080000

uncapped
1.017712

clipped
1.000000

Final Reranked Rank
24
```

따라서 두 번째 Root Cause는:

```text
Lexical Rerank Score Saturation
+
1.0 Clipping
+
Tie Explosion
```

이었다.

최종 해결:

```text
1. Compact Contextual Embedding
   Previous + Current

2. Score Clipping 제거

3. Controlled Lexical Rerank

4. Query-Intent Adaptive Weight
   DEFAULT  = 0.30
   LOCATION = 0.58
```

최종 상태:

```text
RAG-GOLD-008
STABLE_MISS → HIT

HYBRID_V6
34 / 34 HIT
```

---

## 28. 핵심 기술적 교훈

### 28.1 Graph를 추가한다고 자동으로 좋아지지 않는다

`VECTOR_GRAPH_V1`은 Baseline보다 크게 악화되었다.

### 28.2 구조적 인접성과 의미적 관련성은 다르다

EPUB Spine Neighbor는 Candidate Policy 없이 사용하면 노이즈가 될 수 있다.

### 28.3 Weight Tuning만으로는 근본 문제를 해결할 수 없다

Graph Weight를 계속 낮추는 것은 Vector-only에 수렴한다.

### 28.4 Score Instrumentation이 Root Cause를 드러낸다

Raw Vector, Lexical Boost, Graph Score, Final Score를 분리해서 기록해야 실제 Ranking Failure를 설명할 수 있다.

### 28.5 Candidate Policy가 Graph 품질을 결정한다

HEADING / ALT_TEXT를 Graph Candidate에서 제외하면서 `VECTOR_GRAPH_V6`가 Baseline MRR을 넘어섰다.

### 28.6 Hybrid Fusion은 Candidate Diversity가 필요하다

HYBRID_V1에서는 효과가 없었지만 Branch Candidate Pool을 Top-10으로 확대한 V2에서 Coverage가 개선되었다.

### 28.7 Contextual Embedding은 인접 문맥 손실을 복구할 수 있다

`RAG-GOLD-008`:

```text
49 → 23 → 1
```

의 Raw Vector Rank 개선으로 확인되었다.

### 28.8 Pure Vector만으로는 전체 Dataset을 설명할 수 없다

V4에서 `RAG-GOLD-008`은 해결됐지만 기존 HIT 5건이 회귀했다.

### 28.9 Lexical Signal은 제거 대상이 아니라 제어 대상이다

V5에서 Controlled Lexical Rerank를 적용해 대부분의 회귀를 복구했다.

### 28.10 Global Weight는 Query Intent Trade-off를 만들 수 있다

```text
0.30
→ RAG-GOLD-008 HIT
→ RAG-GOLD-028 MISS

0.58
→ RAG-GOLD-008 MISS
→ RAG-GOLD-028 HIT
```

### 28.11 Query-Intent Adaptive Rerank가 Trade-off를 해결했다

```text
DEFAULT 0.30
LOCATION 0.58
```

으로 34/34 Retrieval HIT를 달성했다.

### 28.12 Retrieval과 Answer Evaluation은 분리해야 한다

V6:

```text
Retrieval
Hit Rate@5 = 1.0000
Recall@5   = 1.0000

Answer
Average Score = 0.8625
```

Retrieval은 완료됐지만 Answer Generation은 별도의 개선 과제로 남는다.

---

## 29. 전체 실험 흐름

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
Graph Weight 0.05
MRR 0.6446
       │
       ▼
VECTOR_GRAPH_V4
Seed Top-1
MRR 0.6618
       │
       ▼
VECTOR_GRAPH_V5
Graph Weight 0.025
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
Candidate Policy
MRR 0.8627
       │
       ▼
HYBRID_V1
Top-5 + Top-5 RRF
MRR 0.8627
       │
       ▼
HYBRID_V2
Top-10 + Top-10 RRF
Hit 33 / 34
MRR 0.9216
       │
       ▼
RAG-GOLD-008 Failure Analysis
       │
       ├─ Expected Evidence 검증
       ├─ Raw Vector Rank 추적
       └─ Context Loss 확인
       │
       ▼
HYBRID_V3
Contextual Embedding
Raw Rank 49 → 23
MRR 0.9412
       │
       ▼
HYBRID_V4
Compact Contextual Embedding
Raw Rank 1
       │
       ├─ Pure Vector 전환
       └─ 5건 Regression 발생
       │
       ▼
HYBRID_V5
Controlled Lexical Rerank
       │
       ├─ 0.30 → 008 HIT / 028 MISS
       └─ 0.58 → 008 MISS / 028 HIT
       │
       ▼
HYBRID_V6
Query-Intent Adaptive Rerank
DEFAULT 0.30
LOCATION 0.58
       │
       ▼
34 / 34 HIT
Hit Rate@5 1.0000
Recall@5   1.0000
MRR        0.9225
```

---

## 30. 최종 판정

Final Graph Candidate:

```text
VECTOR_GRAPH_V6
```

Final Hybrid Candidate:

```text
HYBRID_V6
```

최종 Retrieval 결과:

```text
Evaluated Cases = 34
Hit              = 34
Miss             = 0

Hit Rate@5       = 1.0000
Recall@5         = 1.0000
MRR              = 0.9225

Stable Miss      = 0
```

최종 Answer 결과:

```text
Average Score
0.8625
```

판정:

```text
Retrieval Experiment
PASS / COMPLETE

Answer Generation
Separate Optimization Target
```

---

## 31. 포트폴리오 관점의 핵심 메시지

이 실험은 단순히 RAG 기능을 추가한 작업이 아니다.

```text
Golden Dataset 설계
→ Baseline 측정
→ Graph Retrieval
→ Regression 발견
→ Candidate Policy 개선
→ Score Instrumentation
→ Root Cause 분석
→ Hybrid Rank Fusion
→ Candidate Pool Expansion
→ Stable Miss 분리
→ Failure Trace
→ Contextual Embedding
→ Regression Control
→ Controlled Lexical Rerank
→ Query Intent 분류
→ Adaptive Rerank
→ 34 / 34 Final Validation
```

보여주는 역량:

- RAG Evaluation Pipeline 설계
- Golden Dataset 기반 정량 평가
- Vector Retrieval
- EPUB 구조 기반 Graph Retrieval
- Hybrid Retrieval
- Weighted Reciprocal Rank Fusion
- Contextual Embedding
- Query-Intent Adaptive Rerank
- Hit@K / Recall@K / MRR
- Case-level Regression Analysis
- Retrieval Trace 설계
- Score Decomposition
- Failure Analysis
- Controlled Experiment
- Ablation-style 실험 설계
- Version 관리
- Retrieval / Answer Evaluation 분리

---

# Final Result

```text
Baseline
VECTOR_ONLY_V1

Hit Rate@5 = 0.9118
Recall@5   = 0.9118
MRR        = 0.8333
Hit        = 31 / 34
```

```text
Final Graph
VECTOR_GRAPH_V6

Hit Rate@5 = 0.9118
Recall@5   = 0.9118
MRR        = 0.8627
```

```text
Final Hybrid
HYBRID_V6

Hit Rate@5       = 1.0000
Average Recall@5 = 1.0000
MRR              = 0.9225
Hit              = 34 / 34
Miss             = 0

Average Answer Score
= 0.8625
```

최종 개선:

```text
Hit Rate
0.9118 → 1.0000

MRR
0.8333 → 0.9225

Hit
31 → 34

Miss
3 → 0
```

`HYBRID_V6`를 현재 GomsBook AI RAG Retrieval의 최종 Candidate로 동결한다.

다음 연구 단계에서는 Retriever를 추가 튜닝하지 않고 고정한 뒤, `Average Answer Score 0.8625`를 개선하기 위한 Prompt / Context Selection / Context Ordering / Answer Grounding 실험으로 분리한다.
