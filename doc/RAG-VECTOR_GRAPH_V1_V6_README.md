# GomsBook AI RAG — VECTOR_GRAPH V1~V6 실험 기록

## 1. 개요

이 문서는 GomsBook AI의 RAG 검색 성능 개선 과정에서 수행한 `VECTOR_GRAPH_V1` ~ `VECTOR_GRAPH_V6` 실험을 정리한 기록이다.

실험의 핵심 목적은 단순 Vector Retrieval에 EPUB 구조 정보를 결합했을 때 실제 검색 성능이 향상되는지 검증하는 것이다. 모든 실험은 동일한 Golden Dataset과 동일한 Retrieval 평가 기준을 사용하여 비교 가능성을 유지했다.

최종적으로 `VECTOR_GRAPH_V6`는 Vector-only Baseline 대비 **Hit Rate와 Recall을 유지하면서 MRR을 향상**시켰고, Rank Regression 없이 Graph 구조의 실질적 효과를 확인했다.

---

## 2. 실험 목표

검증하고자 한 핵심 가설은 다음과 같다.

> EPUB의 Spine 기반 문서 구조를 Graph 관계로 활용하면 의미 기반 Vector Retrieval만 사용할 때보다 관련 문서를 더 높은 순위로 배치할 수 있는가?

단순히 Hit 여부만 높이는 것이 아니라 다음을 함께 평가했다.

- Hit Rate@K
- Average Recall@K
- MRR
- First Relevant Rank
- HIT_TO_MISS / MISS_TO_HIT
- Rank Improved / Rank Regressed
- Graph Candidate의 실제 유입 여부
- Retrieval Score 구성
- LLM Answer Score는 Secondary Metric으로 별도 관리

---

## 3. 실험 환경

### Golden Dataset

```text
C:\1004.GomsBook\02.Publish\lunchwork_seoul\eval\dataset
└─ rag-lunchwork_seoul-golden-v1.json
```

### Dataset 규모

```text
전체 Golden Cases     : 40
Retrieval 평가 대상    : 34
NO_ANSWER 등 제외 대상 : 6
Top-K                 : 5
```

### Embedding Model

```text
nomic-embed-text
```

### 평가 구분

```text
RAG Evaluation
├─ Retrieval Evaluation          ← Primary
│  ├─ Hit Rate@K
│  ├─ Average Recall@K
│  ├─ MRR
│  ├─ First Relevant Rank
│  ├─ Rank Improved / Regressed
│  └─ HIT_TO_MISS / MISS_TO_HIT
│
└─ Answer Evaluation             ← Secondary
   ├─ LLM Answer Score
   └─ Average Answer Score
```

Retrieval 성능을 실험의 Primary Metric으로 사용한다.

LLM 기반 Answer Score는 생성 모델과 평가 모델의 변동성이 존재하기 때문에 Retrieval 실험의 성공 여부를 결정하는 기준으로 사용하지 않는다.

---

## 4. Baseline — VECTOR_ONLY_V1

Graph 적용 전 기준 성능이다.

| Metric | Result |
|---|---:|
| Evaluated Cases | 34 |
| Hit Count | 31 |
| Hit Rate@5 | 0.9118 |
| Average Recall@5 | 0.9118 |
| MRR | 0.8333 |
| Miss | 3 |

Baseline의 역할은 이후 모든 Graph 실험의 기준점이다.

```text
VECTOR_ONLY_V1
Hit Rate@5 = 0.9118
Recall@5   = 0.9118
MRR        = 0.8333
```

Vector-only 결과의 기본 Trace는 다음과 같다.

```text
retrievalSource = VECTOR
vectorScore     = result.getScore()
finalScore      = result.getScore()
```

---

# 5. VECTOR_GRAPH 공통 구조

초기 Graph Retrieval은 EPUB Spine 구조를 기반으로 구현했다.

```text
Vector Retrieval
      │
      ▼
Vector Top-K
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
Graph Candidate Retrieval
      │
      ▼
Vector + Graph Score
      │
      ▼
Final Ranking
```

Graph 관계는 1-hop 기준으로 사용했다.

Graph Score의 기본값은 다음과 같이 사용했다.

```text
Adjacent Graph Score = 1.0
```

---

# 6. VECTOR_GRAPH_V1

## 6.1 설계

첫 번째 실험은 가장 단순한 Graph Expansion이다.

```text
Graph Seed       = Vector Top-K 전체
Graph Hop        = 1
Graph Relation   = PREVIOUS / NEXT
Graph Weight     = 0.10
Candidate Policy = 기본 구조
```

Graph Final Score는 다음 방식으로 결합했다.

```text
finalScore = vectorScore + (graphScore × graphWeight)
```

## 6.2 결과

| Metric | VECTOR_ONLY_V1 | VECTOR_GRAPH_V1 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.8235 |
| Recall@5 | 0.9118 | 0.8235 |
| MRR | 0.8333 | 0.5515 |
| Answer Score | 약 0.8200 | 0.7756 |

약 28/34 Case만 Hit했다.

## 6.3 분석

단순 Spine 인접성만 적용하면 관련성이 낮은 구조적 이웃 문서까지 검색 결과에 유입되었다.

즉 다음 가설은 실패했다.

```text
"물리적으로 인접한 EPUB 문서는 의미적으로도 관련성이 높다."
```

실제 EPUB에는 다음과 같은 비본문 문서가 섞여 있다.

```text
cover.xhtml
nav.xhtml
author.xhtml
copyright.xhtml
part*.xhtml
quiz.xhtml
```

이 문서들이 Graph Candidate로 유입되면서 검색 노이즈가 크게 증가했다.

### 결론

> 구조적 인접성만으로 Graph Retrieval을 적용하면 Vector Retrieval보다 성능이 악화된다.

---

# 7. VECTOR_GRAPH_V2

## 7.1 변경사항

V1에서 확인된 비본문 문서 노이즈를 제거하기 위해 Graph Document Policy를 추가했다.

제외 대상:

```text
cover.xhtml
nav.xhtml
author.xhtml
copyright.xhtml
part*.xhtml
quiz.xhtml
```

중요한 원칙은 문서를 Spine 자체에서 삭제하는 것이 아니라 **Graph Seed / Candidate 단계에서 제외**하는 것이다.

Spine 노드를 실제로 제거하면 원래 인접하지 않았던 문서가 서로 인접한 것으로 잘못 연결될 수 있기 때문이다.

## 7.2 설정

```text
Graph Weight = 0.10
Graph Seed   = Vector Top-K 전체
Graph Hop    = 1
```

## 7.3 결과

| Metric | V1 | V2 |
|---|---:|---:|
| Hit Rate@5 | 0.8235 | 0.8529 |
| Recall@5 | 0.8235 | 0.8529 |
| MRR | 0.5515 | 0.5574 |
| Answer Score | 0.7756 | 0.7894 |

Vector-only와 비교하면:

```text
HIT_TO_MISS   = 2
MISS_TO_HIT   = 0
Rank Improved = 0
Rank Regressed= 15
```

대표적인 HIT_TO_MISS:

```text
RAG-GOLD-012
RAG-GOLD-031
```

## 7.4 분석

비본문 문서를 제거한 것은 효과가 있었다.

하지만 Graph Weight `0.10`은 여전히 너무 강했다.

Graph Candidate가 원래 Vector Ranking보다 높은 위치로 과도하게 올라가면서 Rank Regression이 대량 발생했다.

### 결론

> Graph Candidate Filtering만으로는 충분하지 않으며 Graph Boost 강도 자체를 조정해야 한다.

---

# 8. VECTOR_GRAPH_V3

## 8.1 변경사항

V2 대비 단 하나만 변경했다.

```text
Graph Weight
0.10 → 0.05
```

Seed 정책은 그대로 유지했다.

```text
Graph Seed = Vector Top-K 전체
```

## 8.2 결과

| Metric | V2 | V3 |
|---|---:|---:|
| Hit Rate@5 | 0.8529 | 0.9118 |
| Recall@5 | 0.8529 | 0.9118 |
| MRR | 0.5574 | 0.6446 |
| Answer Score | 0.7894 | 0.8162 |

V2 → V3:

```text
MISS_TO_HIT    = 2
HIT_TO_MISS    = 0
Stable Hit     = 29
Rank Improved  = 5
Rank Regressed = 0
```

V2에서 놓쳤던 `RAG-GOLD-012`, `RAG-GOLD-031`이 복구되었다.

Vector-only와 비교하면:

```text
Hit Rate        동일
Rank Improved   = 0
Rank Regressed  = 12
```

## 8.3 분석

`graphWeight=0.10`은 확실히 과도했다.

`0.05`로 줄이자 Hit 손실이 모두 복구되었다.

하지만 Vector-only 대비 MRR은 여전히 크게 낮았다.

### 결론

> Graph Weight는 낮출 필요가 있지만 단순 Weight 조정만으로는 Ranking Regression을 해결할 수 없다.

---

# 9. VECTOR_GRAPH_V4

## 9.1 변경사항

V3의 문제는 Graph Seed가 너무 많다는 점에 주목했다.

기존:

```text
Vector Top-K 전체 → Graph Expansion
```

변경:

```text
Vector Top-1 → Graph Expansion
```

설정:

```text
Graph Weight     = 0.05
Graph Seed Limit = 1
Graph Hop        = 1
```

## 9.2 결과

| Metric | V3 | V4 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 |
| Recall@5 | 0.9118 | 0.9118 |
| MRR | 0.6446 | 0.6618 |
| Answer Score | 0.8162 | 0.8294 |

V3 → V4:

```text
Hit Transition = 0
Stable Hit     = 31
Stable Miss    = 3
Rank Improved  = 3
Rank Regressed = 1
```

Vector-only 대비:

```text
Rank Improved   = 2
Rank Regressed  = 12
```

## 9.3 분석

Graph Expansion Seed를 Top-1으로 제한하자 불필요한 Graph Candidate 수가 줄었다.

이는 Graph Search의 핵심 노이즈 원인이 다음에 있음을 보여준다.

```text
Graph Weight뿐 아니라
Graph Expansion Seed의 범위도 Ranking 품질에 큰 영향을 준다.
```

### 결론

> Graph Expansion은 모든 Vector 결과가 아니라 가장 강한 Seed 중심으로 제한하는 것이 더 안정적이다.

---

# 10. VECTOR_GRAPH_V5

## 10.1 변경사항

V4 대비 Graph Weight만 다시 절반으로 낮췄다.

```text
0.05 → 0.025
```

Seed Top-1 정책은 유지했다.

```text
Graph Weight     = 0.025
Graph Seed Limit = 1
```

## 10.2 결과

| Metric | V4 | V5 |
|---|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 |
| Recall@5 | 0.9118 | 0.9118 |
| MRR | 0.6618 | 0.6912 |
| Answer Score | 0.8294 | 0.8244 |

V4 → V5:

```text
Hit Transition = 0
Stable Hit     = 31
Stable Miss    = 3
Rank Improved  = 4
Rank Regressed = 0
MRR Delta      = +0.0294
```

Vector-only 대비:

```text
Rank Improved   = 2
Rank Regressed  = 11
MRR Gap         = -0.1421
```

## 10.3 중요한 판단

Weight를 계속 줄이면 결과는 결국 Vector-only에 수렴한다.

```text
Graph Weight → 0
      ↓
Graph 영향 감소
      ↓
Vector-only와 동일
```

따라서 V5 이후에는 Weight를 계속 낮추는 방식의 실험을 중단했다.

### 결론

> 추가 Weight Tuning보다 왜 Graph Candidate가 Vector 후보를 역전시키는지 Score Composition을 분석해야 한다.

---

# 11. Score Composition 분석

V5 이후 Retrieval Score를 세부적으로 추적하도록 Instrumentation을 추가했다.

## 11.1 Vector Reranking 구성

`DefaultRetriever`에서 사용하는 주요 Boost:

```java
EXACT_HEADING_BOOST       = 0.18
HEADING_KEYWORD_BOOST     = 0.06
CONTENT_KEYWORD_BOOST     = 0.04
HEADING_CONTENT_BOOST     = 0.05
TITLE_PATTERN_BOOST       = 0.10

MAX_HEADING_KEYWORD_BOOST = 0.18
MAX_CONTENT_KEYWORD_BOOST = 0.16
```

실제 점수 흐름:

```text
rawVectorScore
    +
headingBoost
    +
contentBoost
    +
relationBoost
    +
patternBoost
    ↓
uncappedRerankedVectorScore
    ↓
Math.min(1.0, score)
    ↓
rerankedVectorScore
    ↓
Graph Candidate
    +
graphScore × graphWeight
    ↓
finalScore
```

Instrumentation Metadata:

```text
rawVectorScore
headingBoost
contentBoost
relationBoost
patternBoost
rerankBoost
uncappedRerankedVectorScore
rerankedVectorScore
```

---

# 12. Score Saturation 문제 발견

대표 분석 Case:

```text
RAG-GOLD-023
```

Graph Candidate:

```text
chapter10_7.xhtml#chapter10-image-title
```

점수:

```text
rawVectorScore = 0.759119

headingBoost   = 0.120
contentBoost   = 0.080
relationBoost  = 0.050
patternBoost   = 0.000

rerankBoost    = 0.250

uncapped       = 1.009119
reranked       = 1.000000
```

이후 Graph Boost:

```text
graphScore  = 1.0
graphWeight = 0.025

finalScore
= 1.000 + (1.0 × 0.025)
= 1.025
```

즉 원래 Vector Score가 이미 `1.0`에 Saturation 되었는데 Graph Boost가 추가되면서 `1.025`가 되어 기대 문서를 역전했다.

---

# 13. HEADING / ALT_TEXT Chunk 문제

Graph Candidate의 Chunk Type을 분석한 결과 중요한 문제가 발견되었다.

특히 `HEADING` Chunk는 다음 구조를 갖는 경우가 많았다.

```text
heading == content
```

즉 동일한 문자열이 다음 Boost를 중복으로 받는다.

```text
Heading Match
+
Content Match
+
Relation Match
```

결과적으로 lexical signal이 중복 반영되고 Score가 빠르게 1.0으로 Saturation 된다.

`ALT_TEXT` 역시 본문 검색 목적에서는 Graph 후보로서 과도한 영향력을 갖는 경우가 있었다.

대표적인 V5 Rank Regression Case:

```text
RAG-GOLD-009
RAG-GOLD-012
RAG-GOLD-013
RAG-GOLD-018
RAG-GOLD-020
RAG-GOLD-021
RAG-GOLD-022
RAG-GOLD-023
RAG-GOLD-030
RAG-GOLD-032
RAG-GOLD-033
```

총 11건이었다.

---

# 14. VECTOR_GRAPH_V6

## 14.1 실험 가설

V5까지의 결과로 다음 가설을 세웠다.

> Graph Candidate 전체가 문제가 아니라, HEADING / ALT_TEXT Chunk가 Graph Candidate로 유입될 때 lexical reranking과 Graph Boost가 중첩되는 것이 핵심 Rank Regression 원인이다.

따라서 V6에서는 다른 모든 설정을 유지하고 **Graph Candidate Chunk Type Filter 하나만 추가**했다.

## 14.2 설정

```text
Graph Weight                 = 0.025
Graph Seed Limit             = 1
Graph Hop                    = 1
Graph Document Policy        = V5와 동일
Graph Candidate Chunk Filter = ENABLED
```

제외 Chunk:

```text
HEADING
ALT_TEXT
```

주의:

```text
Base Vector Retrieval에서는 HEADING / ALT_TEXT를 제거하지 않는다.
오직 Graph Candidate에만 Filter를 적용한다.
```

이렇게 해야 실험 변화가 Graph Candidate Policy 하나로 통제된다.

---

# 15. V6 구현 핵심

Profile:

```java
public double getGraphWeight() {
    if (!isVectorGraph()) return 0.0;
    return switch (version) {
        case V1, V2 -> 0.10;
        case V3, V4 -> 0.05;
        case V5, V6 -> 0.025;
    };
}

public int getGraphSeedLimit() {
    if (!isVectorGraph()) return 0;
    return switch (version) {
        case V1, V2, V3 -> Integer.MAX_VALUE;
        case V4, V5, V6 -> 1;
    };
}

public boolean isGraphCandidateChunkFilterEnabled() {
    return isVectorGraph() && version == RagEvaluationVersion.V6;
}
```

Graph Candidate Filter:

```java
private boolean isEligibleGraphCandidate(VectorSearchResult result) {

    if (!graphCandidateChunkFilterEnabled) return true;
    if (result == null || result.getChunk() == null) return false;
    if (result.getChunk().getType() == null) return true;

    String chunkType = String.valueOf(result.getChunk().getType()).trim().toUpperCase(Locale.ROOT);

    if (CHUNK_TYPE_HEADING.equals(chunkType)) return false;
    if (CHUNK_TYPE_ALT_TEXT.equals(chunkType)) return false;

    return true;
}
```

중요한 구현 포인트:

```java
if (!isEligibleGraphCandidate(result)) continue;
```

이 조건은 실제 Graph Candidate Merge 전에 반드시 수행되어야 한다.

---

# 16. VECTOR_GRAPH_V6 결과

최종 Retrieval 결과:

| Metric | VECTOR_ONLY_V1 | VECTOR_GRAPH_V5 | VECTOR_GRAPH_V6 |
|---|---:|---:|---:|
| Hit Rate@5 | 0.9118 | 0.9118 | **0.9118** |
| Average Recall@5 | 0.9118 | 0.9118 | **0.9118** |
| MRR | 0.8333 | 0.6912 | **0.8627** |
| Hit Count | 31 | 31 | **31** |
| Miss | 3 | 3 | **3** |

최신 V6 전체 평가:

```text
Evaluated Retrieval Cases = 34
Hit Count                 = 31
Hit Rate@5                = 0.9117647059
Average Recall@5          = 0.9117647059
MRR                       = 0.8627450980
```

최신 Answer Evaluation:

```text
Evaluated Answer Cases = 40
Average Answer Score   = 0.828125
```

Answer Score는 LLM 기반 평가이므로 실행마다 변동될 수 있다.

실제 V6의 이전 실행에서는 약 `0.8098`도 관찰되었다.

따라서 Answer Score는 Secondary Metric으로 취급한다.

---

# 17. V5 → V6 비교

```text
Hit/Miss Transition
────────────────────
HIT_TO_MISS = 0
MISS_TO_HIT = 0

Stable Hit  = 31
Stable Miss = 3

Rank Improved  = 11
Rank Regressed = 0
```

MRR:

```text
V5 = 0.6912
V6 = 0.8627

Delta = +0.1715
```

V5에서 확인했던 11개의 Regression Case가 모두 개선되었다.

---

# 18. VECTOR_ONLY_V1 → VECTOR_GRAPH_V6 비교

```text
Hit Rate
0.9118 → 0.9118

Recall
0.9118 → 0.9118

MRR
0.8333 → 0.8627

MRR Delta
+0.0294
```

Rank 변화:

```text
Rank Improved  = 2
Rank Regressed = 0

HIT_TO_MISS = 0
MISS_TO_HIT = 0
```

즉 V6는 Baseline보다 검색 성공률을 떨어뜨리지 않으면서 관련 문서를 더 높은 순위에 배치했다.

---

# 19. V6 Rank 분포

Retrieval 평가 대상 34건의 최종 분포:

```text
Rank 1 Hit = 28
Rank 2 Hit = 2
Rank 3 Hit = 1
Miss       = 3
──────────────
Total      = 34
```

MRR 계산:

```text
28 × 1.0
+ 2 × 0.5
+ 1 × 0.333333
+ 3 × 0

= 29.333333

29.333333 / 34
= 0.862745
```

Report의 MRR과 일치한다.

---

# 20. 최종 Miss Case

V6에서도 Retrieval Top-5에 기대 문서가 들어오지 않은 Case는 3건이다.

```text
RAG-GOLD-008
서울에 처음 올라왔을 때 저자는 시간이 날 때마다 무엇을 찾아다녔나요?

RAG-GOLD-015
점심시간에 찾은 서울도서관은 저자에게 단순히 쉬어 가는 곳 이상으로 어떤 의미였나요?

RAG-GOLD-028
젊은 시절 저자가 무료 시사회를 보기 위해 찾았던 곳은 어디인가요?
```

이 세 Case는 이후 Hybrid Retrieval에서 개선 가능성을 확인할 주요 대상이다.

---

# 21. V1~V6 전체 결과 요약

| Version | 주요 변경 | Graph Weight | Seed | Hit Rate@5 | MRR |
|---|---|---:|---|---:|---:|
| VECTOR_ONLY_V1 | Baseline | - | - | 0.9118 | 0.8333 |
| VECTOR_GRAPH_V1 | 단순 Spine Graph | 0.10 | ALL | 0.8235 | 0.5515 |
| VECTOR_GRAPH_V2 | 비본문 문서 Filter | 0.10 | ALL | 0.8529 | 0.5574 |
| VECTOR_GRAPH_V3 | Weight 감소 | 0.05 | ALL | 0.9118 | 0.6446 |
| VECTOR_GRAPH_V4 | Seed Top-1 | 0.05 | TOP-1 | 0.9118 | 0.6618 |
| VECTOR_GRAPH_V5 | Weight 추가 감소 | 0.025 | TOP-1 | 0.9118 | 0.6912 |
| VECTOR_GRAPH_V6 | HEADING/ALT_TEXT Graph Candidate 제외 | 0.025 | TOP-1 | **0.9118** | **0.8627** |

MRR 변화:

```text
VECTOR_GRAPH_V1  0.5515
        ↓
VECTOR_GRAPH_V2  0.5574
        ↓
VECTOR_GRAPH_V3  0.6446
        ↓
VECTOR_GRAPH_V4  0.6618
        ↓
VECTOR_GRAPH_V5  0.6912
        ↓
VECTOR_GRAPH_V6  0.8627
```

최종적으로:

```text
VECTOR_GRAPH_V6 0.8627
        >
VECTOR_ONLY_V1  0.8333
```

---

# 22. 실험에서 얻은 핵심 교훈

## 22.1 Graph를 추가한다고 자동으로 성능이 좋아지지 않는다

V1은 Vector-only보다 크게 나빴다.

```text
Vector Only MRR = 0.8333
Graph V1 MRR    = 0.5515
```

Graph 구조 자체보다 **어떤 노드를 Candidate로 허용할 것인가**가 더 중요했다.

## 22.2 구조적 인접성과 의미적 관련성은 다르다

EPUB Spine에서 바로 앞뒤에 위치한다고 해서 질문과 의미적으로 관련된 것은 아니다.

따라서 다음 정책이 필요했다.

```text
Physical EPUB Structure
        +
Semantic Candidate Policy
```

## 22.3 Graph Weight만 계속 줄이는 것은 해결책이 아니다

V3~V5에서 Weight를 낮출수록 성능은 개선되었지만 이는 Graph 영향력을 제거해 Vector-only로 수렴하는 방향이었다.

```text
Graph Weight ↓
Graph Effect ↓
Vector-only 접근
```

따라서 V5 이후에는 Weight Tuning을 중단하고 Score Composition 자체를 분석했다.

## 22.4 Score Instrumentation이 원인 분석의 핵심이었다

단순 최종 Score만 보면 왜 Regression이 발생했는지 알기 어려웠다.

다음 값을 분리해 기록하면서 실제 원인을 찾을 수 있었다.

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

## 22.5 HEADING Chunk는 중복 Lexical Boost 위험이 있다

특히 다음 형태가 문제였다.

```text
heading == content
```

같은 문구가 Heading과 Content 양쪽에서 평가되어 Score가 과도하게 상승했다.

Graph Boost까지 더해지면서 Ranking을 역전시키는 현상이 발생했다.

## 22.6 Retrieval과 Answer Evaluation을 분리해야 한다

V6 Retrieval은 반복 실행에서도 동일하게 재현되었다.

```text
Hit Rate@5 = 0.9118
MRR        = 0.8627
```

반면 Answer Score는 실행마다 변동했다.

```text
예시
0.8098
0.828125
```

따라서 현재 Report는 다음 구조로 분리한다.

```json
{
  "retrievalSummary": {
    "evaluatedCases": 34,
    "hitCount": 31,
    "hitRateAtK": 0.9118,
    "averageRecallAtK": 0.9118,
    "meanReciprocalRank": 0.8627
  },

  "answerSummary": {
    "evaluatedCases": 40,
    "averageScore": 0.828125
  }
}
```

Retrieval은 Primary, Answer는 Secondary다.

---

# 23. Retrieval Trace

각 Golden Case에는 비교 및 원인 분석을 위해 다음 Trace를 저장한다.

```text
expectedDocuments
retrievedDocuments
```

Retrieved Document Trace:

```text
chunkId
sourcePath
title
rank
score
retrievalSource
vectorScore
graphScore
graphWeight
finalScore
metadata
```

이를 통해 단순 Hit/Miss뿐 아니라 다음을 분석할 수 있다.

```text
어떤 문서가 Rank를 차지했는가?
Vector Candidate인가?
Graph Candidate인가?
Graph Weight는 얼마였는가?
Expected Document보다 앞선 후보는 무엇인가?
왜 Rank가 개선/악화되었는가?
```

---

# 24. V6 최종 판정

`VECTOR_GRAPH_V6`는 현재 Graph Retrieval 실험의 Final Candidate로 동결한다.

판정:

```text
Hit Rate 유지        PASS
Recall 유지          PASS
MRR Baseline 초과    PASS
HIT_TO_MISS          0
Rank Regression      0
Graph 효과 확인       PASS
Retrieval Trace       적용
```

최종 결과:

```text
VECTOR_ONLY_V1
MRR = 0.8333

VECTOR_GRAPH_V6
MRR = 0.8627

Improvement
+0.0294
```

따라서 Graph Retrieval이 단순 구조 확장이 아니라 **Candidate Policy와 Score 분석을 통해 통제될 경우 실제 Ranking 개선에 기여할 수 있음**을 확인했다.

---

# 25. 실험 발전 과정

전체 실험 흐름을 요약하면 다음과 같다.

```text
VECTOR_ONLY_V1
│
│ Baseline
│
▼
VECTOR_GRAPH_V1
│
│ 문제: 구조적 Neighbor Noise
│
▼
VECTOR_GRAPH_V2
│
│ 개선: EPUB Document Policy
│ 문제: Graph Weight 과다
│
▼
VECTOR_GRAPH_V3
│
│ 개선: Weight 0.10 → 0.05
│ 문제: Graph Seed 범위 과다
│
▼
VECTOR_GRAPH_V4
│
│ 개선: Graph Seed Top-1
│ 문제: Ranking Regression 잔존
│
▼
VECTOR_GRAPH_V5
│
│ 개선: Weight 0.05 → 0.025
│ 문제: 추가 Weight 감소는 Vector-only 수렴
│
▼
Score Composition Instrumentation
│
│ 발견:
│ - Score Saturation
│ - HEADING lexical double counting
│ - ALT_TEXT Graph Candidate 영향
│
▼
VECTOR_GRAPH_V6
│
│ 개선:
│ Graph Candidate에서
│ HEADING / ALT_TEXT 제외
│
▼
FINAL GRAPH CANDIDATE
MRR 0.8627
Rank Regression 0
```

---

# 26. 다음 단계 — HYBRID_V1

V6 이후 Graph 자체를 추가 튜닝하지 않는다.

다음 실험은:

```text
HYBRID_V1
```

이다.

목표는 Vector-only와 VectorGraph의 강점을 결합하는 것이다.

초기 설계:

```text
Question
   │
   ├───────────────────────┐
   ▼                       ▼
VECTOR_ONLY_V1        VECTOR_GRAPH_V6
   │                       │
   └──────────┬────────────┘
              ▼
       Rank-based Fusion
              ▼
            Top-K
```

Raw Score 합산보다는 Score Scale과 Saturation 문제를 피하기 위해 **Rank 기반 Fusion**을 우선 검토한다.

후보 방식:

```text
Weighted Reciprocal Rank Fusion

hybridScore =
    vectorWeight / (rrfK + vectorRank)
  + graphWeight  / (rrfK + graphRank)
```

`HYBRID_V1`도 동일한 Golden Dataset과 동일한 Primary Retrieval Metrics로 평가한다.

성공 기준:

```text
Hit Rate@5      >= 0.9118
Recall@5        >= 0.9118
MRR             >= 0.8627
HIT_TO_MISS      = 0
Rank Regressed    = 0
```

특히 V6의 Miss Case 3건에 대한 개선 여부를 집중적으로 확인한다.

---

# 27. 포트폴리오 관점의 핵심 메시지

이 실험의 핵심은 단순히 Graph 기능을 추가한 것이 아니다.

```text
가설 수립
→ Baseline 설정
→ Graph 적용
→ 성능 악화 확인
→ 원인 분리
→ Document Policy
→ Weight Control
→ Seed Control
→ Score Instrumentation
→ Saturation 원인 분석
→ Chunk Policy 개선
→ Baseline 초과 검증
```

즉 다음 능력을 보여주는 실험이다.

- Golden Dataset 기반 RAG 평가 설계
- Retrieval Evaluation 자동화
- Vector Retrieval 분석
- EPUB 구조 기반 Graph Retrieval
- Controlled Experiment
- 단계별 변경을 통한 실험 설계
- Retrieval Trace 설계
- Score Decomposition
- Ranking Regression 분석
- Hit@K / Recall@K / MRR 기반 정량 검증
- LLM Answer 평가와 Retrieval 평가 분리
- 실험 재현성을 고려한 Version 관리

---

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

Result
Hit Rate 유지
MRR +0.0294
Rank Regression 0
```

**`VECTOR_GRAPH_V6`는 GomsBook AI의 최종 Graph Retrieval Candidate로 동결하고, 다음 실험은 `HYBRID_V1`으로 진행한다.**
