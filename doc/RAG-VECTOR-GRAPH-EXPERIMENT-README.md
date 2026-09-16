# GomsBook AI RAG Vector + Graph Experiment

## 1. Overview

이 문서는 GomsBook AI의 RAG 검색 성능 검증을 위해 수행한 `VECTOR_GRAPH_V1` 실험의 구조, 실행 과정, 실제 결과, 원인 분석 및 후속 개선 방향을 기록합니다.

비교 기준은 앞서 확정한 `VECTOR_ONLY_V1`입니다.

```text
VECTOR_ONLY_V1
    ↓
Baseline

VECTOR_GRAPH_V1
    ↓
EPUB Spine Graph 적용

VECTOR_GRAPH_V2
    ↓
Graph Document Filtering
```

## 2. Experiment Goal

`VECTOR_GRAPH_V1`의 목적은 Vector Retrieval 결과를 Seed로 사용하고 EPUB Spine의 인접 문서 관계를 Graph로 확장했을 때 Retrieval 성능이 개선되는지 검증하는 것입니다.

검증 질문은 다음과 같습니다.

- Vector 검색 결과 주변의 EPUB 구조 정보를 추가하면 정답 문서 적중률이 개선되는가?
- Vector Only에서 놓친 문서를 Spine 인접 관계로 복구할 수 있는가?
- Graph가 정답 문서의 Rank를 높이는가?
- EPUB의 물리적 읽기 순서가 의미적 Retrieval Graph로도 유효한가?

## 3. Experiment Profile

```text
Experiment ID   : VECTOR_GRAPH_V1
Retrieval Type  : VECTOR_GRAPH
Version         : V1
Project         : lunchwork_seoul
Top-K           : 5
Embedding Model : nomic-embed-text
```

설정:

```properties
gomsbook.ai.rag.evaluation.retrieval-mode=VECTOR_GRAPH
gomsbook.ai.rag.evaluation.version=V1
```

## 4. Baseline

`VECTOR_ONLY_V1` 결과:

```text
Total Cases         40
Evaluated Cases     34
Hit Rate@K          0.9118
Average Recall@K    0.9118
MRR                 0.8333
Average Score       0.8231
```

이 값을 Graph 실험의 고정 Baseline으로 사용합니다.

## 5. Golden Dataset

```text
C:\1004.GomsBook\02.Publish
└─ lunchwork_seoul
   └─ eval
      └─ dataset
         └─ rag-lunchwork_seoul-golden-v1.json
```

평가 구성:

```text
Total Cases      : 40
ANSWERABLE Cases : 34
Evaluated Cases  : 34
```

## 6. Architecture

```text
Question
   ↓
Vector Retriever
   ↓
Top-K Seed Documents
   ↓
DefaultEpubGraphExpansionProvider
   ↓
EPUB Spine PREVIOUS / NEXT
   ↓
Graph Candidate Documents
   ↓
Filtered Vector Search
   ↓
Graph Boost
   ↓
Final Top-K
```

기존 Vector Only 구현은 그대로 유지합니다.

```text
DefaultRetriever
    = VECTOR_ONLY

VectorGraphRetriever
    = VECTOR + GRAPH
```

## 7. Graph Components

### GraphExpansionProvider

Vector 결과의 source path를 Seed로 받아 관련 Graph 문서와 graph score를 반환합니다.

```text
sourcePath → graphScore
```

### DefaultEpubGraphExpansionProvider

별도 Graph DB를 사용하지 않고 EPUB `content.opf`의 Manifest와 Spine을 이용해 런타임 Graph를 구성합니다.

## 8. EPUB Graph Model V1

```text
Graph Source:
EPUB Spine

Relations:
PREVIOUS
NEXT

Hop:
1-Hop

Graph Score:
1.0

Graph Weight:
0.10
```

예:

```text
chapter10_1.xhtml
        ↓
chapter10_2.xhtml
        ↓
chapter10_3.xhtml
```

Seed가 `chapter10_2.xhtml`이면:

```text
PREVIOUS → chapter10_1.xhtml
NEXT     → chapter10_3.xhtml
```

을 Candidate로 추가합니다.

## 9. Path Resolution

Manifest href:

```text
Text/chapter10_2.xhtml
```

은 최종적으로:

```text
OEBPS/Text/chapter10_2.xhtml
```

로 변환됩니다.

Golden Dataset과 Vector metadata에서도 동일한 source path 형식을 사용합니다.

## 10. Excluded Document Policy

현재 RAG 전체 제외 문서는:

```text
quiz.xhtml
```

입니다.

실제 로그:

```text
[RAG][EPUB-GRAPH] href=Text/quiz.xhtml -> source=OEBPS/Text/quiz.xhtml
[RAG][EPUB-GRAPH] source skipped - excluded: OEBPS/Text/quiz.xhtml
```

즉 `quiz.xhtml`은 Graph Candidate에서도 정상적으로 제외되었습니다.

## 11. Graph Debug Result

실제 EPUB 구조:

```text
manifestItems=29
spineItems=21
spineSourceCount=20
```

Graph에서 처리된 실제 Seed:

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

## 12. Actual Graph Expansion

### chapter00_1.xhtml

```text
position=3

PREVIOUS → author.xhtml
NEXT     → chapter10_1.xhtml
```

결과:

```text
PREVIOUS → already seed
NEXT     → chapter10_1.xhtml 추가
```

### author.xhtml

```text
position=2

PREVIOUS → nav.xhtml
NEXT     → chapter00_1.xhtml
```

결과:

```text
PREVIOUS → nav.xhtml 추가
NEXT     → already seed
```

### chapter10_2.xhtml

```text
position=5

PREVIOUS → chapter10_1.xhtml
NEXT     → chapter10_3.xhtml
```

결과:

```text
chapter10_1.xhtml 추가
chapter10_3.xhtml 추가
```

최종 Graph Candidate:

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

따라서 Graph 구현 자체는 정상적으로 동작했습니다.

## 13. Experiment Result

`VECTOR_GRAPH_V1` 전체 Golden Dataset 결과:

| Metric | Result |
|---|---:|
| Total Cases | 40 |
| Evaluated Cases | 34 |
| Hit Rate@K | **0.8235** |
| Average Recall@K | **0.8235** |
| MRR | **0.5515** |
| Average Score | **0.7756** |

## 14. Baseline Comparison

| Metric | VECTOR_ONLY_V1 | VECTOR_GRAPH_V1 | Difference |
|---|---:|---:|---:|
| Evaluated Cases | 34 | 34 | 0 |
| Hit Rate@K | **0.9118** | **0.8235** | **-0.0883** |
| Average Recall@K | **0.9118** | **0.8235** | **-0.0883** |
| MRR | **0.8333** | **0.5515** | **-0.2818** |
| Average Score | **0.8231** | **0.7756** | **-0.0475** |

34건 기준으로 해석하면:

```text
VECTOR_ONLY_V1
약 31 Hit

VECTOR_GRAPH_V1
약 28 Hit
```

즉 Graph 적용 후 약 3건의 추가 Retrieval Miss가 발생했습니다.

## 15. Main Finding

핵심 발견은 다음과 같습니다.

```text
Spine adjacency
≠
Semantic relevance
```

EPUB 구조상 인접한 문서가 RAG 질문에 의미적으로 관련된 문서라는 보장은 없습니다.

대표 사례:

```text
author.xhtml
    ↓ PREVIOUS
nav.xhtml
```

실제 로그에서도:

```text
candidate added - PREVIOUS:
OEBPS/Text/nav.xhtml
```

가 확인되었습니다.

이 관계는 EPUB Reading Order에는 맞지만 의미적 Retrieval Graph로는 Noise입니다.

## 16. Why Performance Dropped

MRR:

```text
0.8333
→
0.5515
```

로 크게 하락했습니다.

가능한 흐름:

```text
Vector Result
        ↓
Graph Candidate 추가
        ↓
구조적 Noise 유입
        ↓
Graph Boost
        ↓
정답 Rank 하락 또는 Top-K 이탈
```

즉 Graph Candidate 품질이 Vector ranking을 오히려 악화시켰습니다.

## 17. Noise Candidates

다음과 같은 관계는 구조적으로는 유효하지만 Retrieval에서는 Noise가 될 수 있습니다.

```text
cover.xhtml
→ nav.xhtml

nav.xhtml
→ author.xhtml

author.xhtml
→ chapter00_1.xhtml

chapter00_1.xhtml
→ chapter10_1.xhtml

chapter10_14.xhtml
→ epilogue.xhtml

epilogue.xhtml
→ copyright.xhtml
```

## 18. Experiment Conclusion

`VECTOR_GRAPH_V1`은 성능 측면에서는 Vector Only보다 낮았습니다.

그러나 실험은 성공입니다.

확인된 사실:

```text
1. EPUB Spine 기반 Graph 생성 정상
2. Vector Seed → Graph Expansion 정상
3. Graph Candidate가 Retrieval에 실제 참여
4. 단순 PREVIOUS/NEXT Graph는 품질 개선 실패
5. 구조 문서가 Graph Noise를 유발
6. Graph Edge Definition이 Retrieval 품질에 직접 영향
```

핵심 결론:

> EPUB Spine의 단순 PREVIOUS/NEXT 관계를 Vector Retrieval에 직접 적용하면 구조 문서와 의미적으로 무관한 인접 문서가 검색 후보로 유입되어 Vector Only보다 Retrieval 성능이 저하되었다.

## 19. VECTOR_GRAPH_V1 Definition

이 실험은 다음 조건으로 고정합니다.

```text
Base Retriever
Vector Only

Graph Source
EPUB Spine

Relations
PREVIOUS / NEXT

Hop
1

Graph Score
1.0

Graph Weight
0.10

Graph Document Filter
quiz.xhtml only

Top-K
5

Evaluated Cases
34
```

이 조건은 변경하지 않고 실험 이력으로 보존합니다.

## 20. Next Experiment — VECTOR_GRAPH_V2

V2에서는 다른 조건을 유지하고 **Graph Document Filtering만 변경**합니다.

```text
VECTOR_GRAPH_V1
Naive Spine adjacency
        ↓
VECTOR_GRAPH_V2
Structure document filtering
```

Graph에서 제외할 후보:

```text
cover.xhtml
nav.xhtml
author.xhtml
copyright.xhtml
part*.xhtml
```

이 문서들은 RAG 자체에서는 유효할 수 있으므로 `RagUtil.isExcludedDocument()`에 넣지 않습니다.

대신 Graph 전용 정책으로 분리합니다.

```text
EpubGraphDocumentPolicy
    ↓
DefaultEpubGraphDocumentPolicy
```

구조:

```text
DefaultEpubGraphExpansionProvider
        │
        ├─ Manifest
        ├─ Spine
        └─ EpubGraphDocumentPolicy
                 ↓
          Graph Eligible?
            ├─ YES → Graph Edge
            └─ NO  → DROP
```

## 21. Experiment Roadmap

```text
VECTOR_ONLY_V1
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

        ↓

VECTOR_GRAPH_V3
Part Boundary Filtering

        ↓

VECTOR_GRAPH_V4
Seed Top-N Restriction

        ↓

Graph Weight Tuning

        ↓

HYBRID_V1
Independent Vector + Graph Fusion
```

## 22. Summary

```text
Status              COMPLETED
Experiment          VECTOR_GRAPH_V1
Project             lunchwork_seoul
Total Cases         40
Evaluated Cases     34
Hit Rate@K          0.8235
Average Recall@K    0.8235
MRR                 0.5515
Average Score       0.7756
```

비교:

```text
VECTOR_ONLY_V1
Hit Rate@K = 0.9118
MRR        = 0.8333

VECTOR_GRAPH_V1
Hit Rate@K = 0.8235
MRR        = 0.5515
```

최종 판정:

```text
Naive EPUB Spine adjacency degraded retrieval quality.
```

다음 단계는 `EpubGraphDocumentPolicy`를 도입하여 구조 문서를 Graph 대상에서 제거한 `VECTOR_GRAPH_V2`를 구현하고 동일 Golden Dataset으로 재평가하는 것입니다.
