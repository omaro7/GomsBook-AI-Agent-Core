# GomsBook AI RAG Vector + Graph Experiment — V1 / V2

## 1. Overview

이 문서는 GomsBook AI의 RAG 검색 성능 검증 과정에서 수행한 `VECTOR_GRAPH_V1`과 `VECTOR_GRAPH_V2` 실험을 비교·정리합니다.

비교 기준은 고정된 Vector Only Baseline인 `VECTOR_ONLY_V1`입니다.

```text
VECTOR_ONLY_V1
    ↓
Baseline

VECTOR_GRAPH_V1
    ↓
Naive EPUB Spine adjacency

VECTOR_GRAPH_V2
    ↓
Structure document filtering
```

본 문서는 Graph 적용이 Retrieval 성능에 어떤 영향을 주었는지, V1의 문제를 V2에서 어떻게 보완했는지, 그리고 다음 실험에서 무엇을 확인해야 하는지를 기록합니다.

---

## 2. Experiment Goal

Graph 실험의 주요 목적은 다음과 같습니다.

- Vector Retrieval 결과를 Seed로 사용한다.
- EPUB Spine의 구조적 인접 관계를 Graph Edge로 활용한다.
- Vector Only에서 놓친 정답 문서를 Graph Expansion으로 복구할 수 있는지 검증한다.
- Graph Candidate가 정답 문서의 Rank를 개선하는지 확인한다.
- EPUB Reading Order가 의미적 Retrieval Graph로 유효한지 평가한다.
- Graph Noise를 정량적으로 확인하고 개선한다.

---

## 3. Baseline — VECTOR_ONLY_V1

고정 Baseline:

```text
Experiment ID       VECTOR_ONLY_V1
Retrieval Type      VECTOR_ONLY
Evaluated Cases     34 / 40
Hit Rate@K          0.9118
Average Recall@K    0.9118
MRR                 0.8333
Average Score       0.8231
```

34개 ANSWERABLE Case 기준 약 31건이 Top-K 내 정답 문서를 포함했습니다.

```text
Hit Cases ≈ 31 / 34
```

이 결과는 Graph 실험 전체의 비교 기준으로 유지합니다.

---

## 4. Common Experimental Conditions

V1과 V2에서 공통으로 유지한 조건:

```text
Project
lunchwork_seoul

Golden Dataset
rag-lunchwork_seoul-golden-v1.json

Total Cases
40

Evaluated Cases
34

Embedding Model
nomic-embed-text

Top-K
5

Graph Source
EPUB Spine

Graph Relations
PREVIOUS
NEXT

Graph Hop
1

Graph Score
1.0

Graph Weight
0.10
```

V2에서는 Graph 문서 필터링 정책만 변경합니다.

---

# VECTOR_GRAPH_V1

## 5. V1 Definition

```text
Experiment ID       VECTOR_GRAPH_V1
Retrieval Type      VECTOR_GRAPH
Version             V1
Graph Source        EPUB Spine
Relations           PREVIOUS / NEXT
Hop                 1
Graph Score         1.0
Graph Weight        0.10
Graph Filter        quiz.xhtml only
```

V1은 EPUB Spine의 모든 linear XHTML 문서를 Graph 대상으로 사용합니다.

---

## 6. V1 Architecture

```text
Question
   ↓
Vector Retriever
   ↓
Vector Seed Documents
   ↓
DefaultEpubGraphExpansionProvider
   ↓
EPUB Spine
   ↓
PREVIOUS / NEXT
   ↓
Graph Candidates
   ↓
Vector Search
   ↓
Graph Boost
   ↓
Final Top-K
```

---

## 7. V1 Actual Graph Debug

실제 EPUB 구조:

```text
manifestItems=29
spineItems=21
spineSourceCount=20
```

실제 Vector Seed:

```text
rawSeeds=[
  OEBPS/Text/chapter00_1.xhtml,
  OEBPS/Text/author.xhtml,
  OEBPS/Text/chapter10_2.xhtml
]
```

정규화 결과:

```text
normalizedSeeds=[
  OEBPS/Text/chapter00_1.xhtml,
  OEBPS/Text/author.xhtml,
  OEBPS/Text/chapter10_2.xhtml
]
```

모든 Seed는 Spine에서 정상적으로 발견되었습니다.

---

## 8. V1 Actual Expansion

### chapter00_1.xhtml

```text
PREVIOUS → author.xhtml
NEXT     → chapter10_1.xhtml
```

결과:

```text
author.xhtml
→ already seed

chapter10_1.xhtml
→ candidate added
```

### author.xhtml

```text
PREVIOUS → nav.xhtml
NEXT     → chapter00_1.xhtml
```

결과:

```text
nav.xhtml
→ candidate added

chapter00_1.xhtml
→ already seed
```

### chapter10_2.xhtml

```text
PREVIOUS → chapter10_1.xhtml
NEXT     → chapter10_3.xhtml
```

결과:

```text
chapter10_1.xhtml
→ candidate added

chapter10_3.xhtml
→ candidate added
```

최종:

```text
expandedSources={
  OEBPS/Text/chapter10_1.xhtml=1.0,
  OEBPS/Text/nav.xhtml=1.0,
  OEBPS/Text/chapter10_3.xhtml=1.0
}
```

Summary:

```text
seeds=3
expanded=3
```

Graph 구현 자체는 정상적으로 동작했습니다.

---

## 9. V1 Result

```text
Evaluated Cases     34 / 40
Hit Rate@K          0.8235
Average Recall@K    0.8235
MRR                 0.5515
Average Score       0.7756
```

34개 Case 기준 약 28건 Hit:

```text
Hit Cases ≈ 28 / 34
```

---

## 10. V1 vs Baseline

| Metric | VECTOR_ONLY_V1 | VECTOR_GRAPH_V1 | Difference |
|---|---:|---:|---:|
| Evaluated Cases | 34 | 34 | 0 |
| Hit Rate@K | **0.9118** | 0.8235 | **-0.0883** |
| Average Recall@K | **0.9118** | 0.8235 | **-0.0883** |
| MRR | **0.8333** | 0.5515 | **-0.2818** |
| Average Score | **0.8231** | 0.7756 | **-0.0475** |

---

## 11. V1 Main Finding

핵심 문제:

```text
Spine adjacency
≠
Semantic relevance
```

대표적인 Noise:

```text
author.xhtml
    ↓ PREVIOUS
nav.xhtml
```

EPUB Reading Order에서는 정상 관계이지만 검색 의미상 관련성이 낮습니다.

또한 다음과 같은 구조적 관계가 Graph Candidate에 유입될 수 있습니다.

```text
cover.xhtml
→ nav.xhtml

nav.xhtml
→ author.xhtml

author.xhtml
→ chapter00_1.xhtml

epilogue.xhtml
→ copyright.xhtml
```

즉 V1은 Graph 자체는 정상 동작했지만, Graph Edge의 의미적 품질이 낮아 Retrieval 성능을 악화시켰습니다.

---

# VECTOR_GRAPH_V2

## 12. V2 Goal

V2에서는 V1의 다른 조건을 유지하면서 **Graph Document Filtering만 추가**합니다.

```text
V1
Naive Spine adjacency

        ↓

V2
Spine adjacency
+
Structure Document Filtering
```

목적:

```text
Graph Noise 감소
```

---

## 13. V2 Document Policy

RAG 전체 제외 정책과 Graph 전용 정책을 분리합니다.

### Global RAG Exclusion

```text
RagUtil.isExcludedDocument()
```

현재:

```text
quiz.xhtml
```

### Graph-only Exclusion

```text
EpubGraphDocumentPolicy
DefaultEpubGraphDocumentPolicy
```

Graph에서 제외하는 구조 문서:

```text
cover.xhtml
nav.xhtml
author.xhtml
copyright.xhtml
part*.xhtml
```

이 문서들은 RAG 자체에서는 사용할 수 있지만 Graph Seed/Candidate에서는 제외합니다.

---

## 14. Important V2 Design Rule

구조 문서를 Spine 목록 자체에서 제거하지 않습니다.

예:

```text
chapter10_14.xhtml
part02.xhtml
chapter20_1.xhtml
```

`part02.xhtml`을 Spine 목록에서 삭제해버리면:

```text
chapter10_14.xhtml
↔
chapter20_1.xhtml
```

이 잘못된 신규 인접 관계로 만들어질 수 있습니다.

따라서 V2 정책은:

```text
Spine Order
유지

Seed
Graph Policy 적용

Candidate
Graph Policy 적용
```

입니다.

---

## 15. V2 Expected Filtering

V1 Seed:

```text
rawSeeds=[
  chapter00_1.xhtml,
  author.xhtml,
  chapter10_2.xhtml
]
```

V2에서는:

```text
author.xhtml
→ Graph Seed 제외
```

기대:

```text
normalizedSeeds=[
  chapter00_1.xhtml,
  chapter10_2.xhtml
]
```

또한:

```text
chapter00_1.xhtml
    ↓ PREVIOUS
author.xhtml
```

은 Candidate 단계에서 차단됩니다.

반면:

```text
chapter10_2.xhtml
    ↑ PREVIOUS
chapter10_1.xhtml

chapter10_2.xhtml
    ↓ NEXT
chapter10_3.xhtml
```

은 유지됩니다.

결과적으로 V1의:

```text
expandedSources={
  chapter10_1.xhtml,
  nav.xhtml,
  chapter10_3.xhtml
}
```

에서 구조 Noise인:

```text
nav.xhtml
```

이 제거되는 방향입니다.

---

## 16. V2 Result

전체 Golden Dataset 평가 결과:

```text
Evaluated Cases     34 / 40
Hit Rate@K          0.8529
Average Recall@K    0.8529
MRR                 0.5574
```

현재 제공된 결과에는 V2의 Average Score 값이 포함되어 있지 않아 본 문서에서는 기록하지 않습니다.

34개 Case 기준:

```text
Hit Cases ≈ 29 / 34
```

---

## 17. V1 vs V2

| Metric | VECTOR_GRAPH_V1 | VECTOR_GRAPH_V2 | Difference |
|---|---:|---:|---:|
| Evaluated Cases | 34 | 34 | 0 |
| Hit Rate@K | 0.8235 | **0.8529** | **+0.0294** |
| Average Recall@K | 0.8235 | **0.8529** | **+0.0294** |
| MRR | 0.5515 | **0.5574** | **+0.0059** |
| Approx. Hit Cases | 28 | **29** | **+1** |

V2는 V1보다 개선되었습니다.

따라서:

```text
Structure Document Filtering
→ Graph Noise 감소 효과 있음
```

을 확인할 수 있습니다.

---

## 18. Baseline vs V1 vs V2

| Metric | VECTOR_ONLY_V1 | VECTOR_GRAPH_V1 | VECTOR_GRAPH_V2 |
|---|---:|---:|---:|
| Evaluated Cases | 34 | 34 | 34 |
| Hit Rate@K | **0.9118** | 0.8235 | 0.8529 |
| Average Recall@K | **0.9118** | 0.8235 | 0.8529 |
| MRR | **0.8333** | 0.5515 | 0.5574 |
| Approx. Hit Cases | **31** | 28 | 29 |

---

## 19. Interpretation

V2는 V1보다 좋아졌지만 Vector Only Baseline에는 아직 미치지 못합니다.

관계:

```text
VECTOR_GRAPH_V1
    <
VECTOR_GRAPH_V2
    <
VECTOR_ONLY_V1
```

Hit Rate:

```text
0.8235
→
0.8529
→
0.9118
```

MRR:

```text
0.5515
→
0.5574
→
0.8333
```

V2에서 Hit Rate는 개선됐지만 MRR 개선 폭은 작습니다.

이는 구조 문서 Filtering만으로 일부 Noise는 제거했지만, **Graph Expansion 또는 Graph Boost가 기존 Vector Ranking을 여전히 교란하고 있을 가능성**을 보여줍니다.

---

## 20. Current Experimental Conclusion

V1 결과:

> EPUB Spine의 단순 PREVIOUS/NEXT 관계를 그대로 Graph로 사용하면 구조 문서와 의미적으로 무관한 인접 문서가 Candidate에 포함되어 Vector Only보다 Retrieval 성능이 저하되었다.

V2 결과:

> Graph 전용 구조 문서 Filtering을 적용하면 Hit Rate@K가 0.8235에서 0.8529로 개선되어 Graph Noise 감소 효과가 확인되었다. 그러나 Vector Only의 0.9118에는 미치지 못했고 MRR 개선도 제한적이었다.

---

## 21. What V2 Proved

V2를 통해 확인된 사항:

```text
1. Graph Document Policy 분리는 유효하다.

2. RAG 전체 제외 정책과 Graph 전용 제외 정책은 분리하는 것이 적절하다.

3. 구조 문서 Filtering으로 적어도 1개 Case가 추가로 복구되었다.

4. 단순 구조 문서 Filtering만으로는 Vector Only Baseline을 넘지 못했다.

5. 남은 문제는 Graph Seed Selection 또는 Graph Boost에 있을 가능성이 높다.
```

---

## 22. Remaining Problem

V2도 Vector Only 대비:

```text
Hit Cases
31 → 29
```

로 약 2건 부족합니다.

특히 MRR:

```text
VECTOR_ONLY_V1
0.8333

VECTOR_GRAPH_V2
0.5574
```

차이가 큽니다.

따라서 다음 분석의 핵심은:

```text
Vector Only HIT
→
Graph V2 MISS
```

로 바뀐 Case를 찾는 것입니다.

---

## 23. Recommended Case Delta Analysis

다음 세 그룹을 비교해야 합니다.

```text
A. Vector Only HIT → Graph V2 MISS
   Graph가 악화시킨 Case

B. Vector Only MISS → Graph V2 HIT
   Graph가 복구한 Case

C. Vector Only HIT → Graph V2 HIT
   정답 Rank 변화 확인
```

특히 A 그룹이 다음 Graph 실험 설계를 결정합니다.

---

## 24. Candidate Cause 1 — Too Many Graph Seeds

현재 Vector Top 결과 여러 개를 모두 Graph Seed로 사용하면:

```text
Vector Top-N
    ↓
각 Seed에 PREVIOUS/NEXT
    ↓
Graph Candidate 증가
    ↓
Query Drift
```

가 발생할 수 있습니다.

예:

```text
Top-3 Seeds
×
PREVIOUS/NEXT
→
최대 6개 주변 Candidate
```

낮은 Vector Rank의 Seed까지 Graph로 확장되면 Noise가 증폭될 수 있습니다.

---

## 25. Candidate Cause 2 — Graph Weight

현재:

```text
Graph Weight = 0.10
```

입니다.

관련성이 약한 Graph Candidate도 일정 Boost를 받기 때문에 기존 Vector Rank를 교란할 가능성이 있습니다.

향후 실험 후보:

```text
0.10
0.05
0.02
```

단, Seed 수와 Weight를 동시에 변경하면 원인 분석이 어려우므로 한 실험에서는 한 변수만 변경합니다.

---

## 26. Proposed Next Experiment

다음 후보:

```text
VECTOR_GRAPH_V3
```

권장 첫 변경:

```text
Graph Seed Top-N Restriction
```

예:

```text
Graph Seed
Vector Top-1 only
```

유지 조건:

```text
Graph Source   EPUB Spine
Relations      PREVIOUS/NEXT
Hop            1
Graph Weight   0.10
Document Policy V2 유지
Top-K          5
Dataset        동일
```

이렇게 하면 V2 대비 변경 변수는 Seed Selection 하나뿐입니다.

---

## 27. Experiment Roadmap

```text
VECTOR_ONLY_V1
Baseline
Hit Rate@K = 0.9118
MRR        = 0.8333

        ↓

VECTOR_GRAPH_V1
Naive Spine adjacency
Hit Rate@K = 0.8235
MRR        = 0.5515

        ↓

VECTOR_GRAPH_V2
Structure Document Filtering
Hit Rate@K = 0.8529
MRR        = 0.5574

        ↓

Case Delta Analysis

        ↓

VECTOR_GRAPH_V3
Seed Top-N Restriction

        ↓

Graph Weight Experiment

        ↓

HYBRID_V1
Independent Vector + Graph Fusion
```

---

## 28. Report Separation

각 실험 Report는 Profile과 Version에 따라 별도 보관합니다.

```text
eval/
└─ reports/
   ├─ vector-only-v1/
   │  └─ rag-lunchwork_seoul-vector-only-v1-report.json
   │
   ├─ vector-graph-v1/
   │  └─ rag-lunchwork_seoul-vector-graph-v1-report.json
   │
   └─ vector-graph-v2/
      └─ rag-lunchwork_seoul-vector-graph-v2-report.json
```

따라서 실험 결과를 덮어쓰지 않고 재현 가능한 형태로 보존합니다.

---

## 29. Final Summary

### VECTOR_GRAPH_V1

```text
Status              COMPLETED
Evaluated Cases     34 / 40
Hit Rate@K          0.8235
Average Recall@K    0.8235
MRR                 0.5515
Average Score       0.7756
```

판정:

```text
Naive EPUB Spine adjacency degraded retrieval quality.
```

### VECTOR_GRAPH_V2

```text
Status              COMPLETED
Evaluated Cases     34 / 40
Hit Rate@K          0.8529
Average Recall@K    0.8529
MRR                 0.5574
```

판정:

```text
Structure document filtering reduced Graph noise,
but did not yet outperform the Vector Only baseline.
```

### Overall

```text
VECTOR_ONLY_V1
Hit Rate@K = 0.9118
MRR        = 0.8333

VECTOR_GRAPH_V1
Hit Rate@K = 0.8235
MRR        = 0.5515

VECTOR_GRAPH_V2
Hit Rate@K = 0.8529
MRR        = 0.5574
```

핵심 실험 결과:

> Graph Retrieval의 성능은 Graph 적용 여부 자체보다 Edge 정의, Seed 선택, Candidate 정책의 품질에 크게 좌우된다. V2의 구조 문서 Filtering은 V1 대비 성능을 개선했지만, 다음 단계에서는 Case별 Delta 분석을 통해 Graph Seed Selection과 Boost 정책을 추가로 검증해야 한다.
