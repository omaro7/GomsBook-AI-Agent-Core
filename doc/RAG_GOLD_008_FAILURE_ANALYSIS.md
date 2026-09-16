# RAG-GOLD-008 Failure Analysis

## 1. 대상 Case

```text
Case ID
RAG-GOLD-008

Question
서울에 처음 올라왔을 때 저자는 시간이 날 때마다 무엇을 찾아다녔나요?
```

`RAG-GOLD-008`은 `HYBRID_V2` 전체 Retrieval 평가에서 유일하게 남은 Stable Miss Case다.

```text
Evaluated Retrieval Cases = 34
Hit                       = 33
Miss                      = 1

Stable Miss
└─ RAG-GOLD-008
```

따라서 이 Case는 단순히 전체 성능을 더 올리기 위한 튜닝 대상이라기보다, 현재 Retrieval Architecture의 한계를 분석하기 위한 대표 Failure Case로 관리한다.

---

## 2. 관찰된 현상

이 Case는 이전 실험에서도 지속적으로 Miss였다.

```text
VECTOR_ONLY_V1
→ MISS

VECTOR_GRAPH_V6
→ MISS

HYBRID_V1
→ MISS

HYBRID_V2
→ MISS
```

HYBRID_V2에서는 각 Branch Candidate Pool을 Top-5에서 Top-10으로 확대했지만 최종 Top-5에 기대 문서가 진입하지 못했다.

이는 단순히 Fusion 단계의 Ranking 문제만으로 설명하기 어렵다.

---

## 3. 현재까지 확인 가능한 사실

확인된 사실은 다음과 같다.

```text
1. Vector-only에서도 기대 문서가 Top-5에 들어오지 않았다.

2. VECTOR_GRAPH_V6에서도 기대 문서를 복구하지 못했다.

3. HYBRID_V1의 Top-5 + Top-5 Fusion에서도 Miss였다.

4. HYBRID_V2의 Top-10 + Top-10 Candidate Pool Expansion에서도 Miss가 유지되었다.

5. 다른 Stable Miss 2건인 RAG-GOLD-015, RAG-GOLD-028은
   HYBRID_V2에서 MISS_TO_HIT으로 복구되었다.

6. RAG-GOLD-008만 최종 Stable Miss로 남았다.
```

따라서 `RAG-GOLD-008`은 단순 Candidate Pool 부족과 다른 유형의 Failure일 가능성이 높다.

---

## 4. Failure 가설

아래 항목은 현재 결과를 기반으로 한 분석 가설이며, 최종 원인은 Retrieval Trace를 통해 확인해야 한다.

### 4.1 Semantic Gap 가설

질문의 표현과 기대 문서 본문의 표현 사이에 의미적 거리가 클 가능성이 있다.

질문:

```text
서울에 처음 올라왔을 때
저자는 시간이 날 때마다
무엇을 찾아다녔나요?
```

이 질문은 다음과 같은 개념을 함께 포함한다.

```text
서울에 처음 올라옴
+
시간이 날 때마다
+
찾아다닌 대상
```

반면 실제 본문에는 동일한 의미가 다음처럼 분산되어 있을 수 있다.

```text
젊은 시절
무료 공연
무료 전시
무료 시사회
정동극장
서울시립미술관
문화 공간
```

즉 질문과 본문 사이에 직접적인 Lexical Overlap이 적고, 여러 문장을 결합해야 답을 찾을 수 있는 형태일 가능성이 있다.

---

### 4.2 Multi-sentence Evidence 가설

정답이 하나의 Chunk 안에 완결된 형태로 존재하지 않고 여러 Chunk에 분산되어 있을 수 있다.

예:

```text
Chunk A
서울에 처음 올라왔을 당시의 상황

Chunk B
시간이 날 때마다 무료 공연을 찾아다님

Chunk C
무료 전시와 시사회에 대한 구체적 사례
```

현재 Retrieval은 Chunk 단위로 Rank를 평가하기 때문에, 정답을 구성하는 핵심 정보가 여러 Chunk에 분산되어 있으면 개별 Chunk Score가 충분히 높아지지 않을 수 있다.

---

### 4.3 Chunk Boundary 가설

정답 문장이 Chunk 경계에 걸려 있을 가능성이 있다.

예:

```text
Chunk N
서울에 처음 올라왔을 때는 낮에는 공부를 하고...

Chunk N+1
시간이 날 때마다 무료 공연과 전시를 찾아다녔다.
```

이 경우 각각의 Chunk만 보면 질문 전체와의 Semantic Similarity가 약해질 수 있다.

따라서 `RAG-GOLD-008`은 Chunking Policy의 한계를 보여주는 Case일 수도 있다.

---

### 4.4 Document-level Recall 부족 가설

HYBRID_V2에서 Branch Top-K를 10까지 확대했음에도 Miss가 유지되었다.

이는 기대 문서가:

```text
Rank 11+
```

에 존재하거나, 아예 Retrieval Candidate Pool에 들어오지 못했을 가능성을 의미한다.

이 경우 단순히:

```text
Top-10 → Top-20
```

으로 확대하는 것은 가능하지만, 바로 적용해서는 안 된다.

먼저 실제 기대 문서의 Branch Rank를 확인해야 한다.

---

### 4.5 Query 표현 불일치 가설

질문에서 핵심 표현은:

```text
무엇을 찾아다녔나요?
```

이지만 본문에서는:

```text
공연을 보러 다녔다
전시를 찾아갔다
무료 시사회를 보았다
문화 공간을 찾았다
```

등으로 표현되어 있을 수 있다.

즉 Retrieval Model이 질문의 추상적 표현인:

```text
무엇을 찾아다녔나
```

와 본문의 구체적 대상:

```text
무료 공연 / 전시 / 시사회
```

를 충분히 연결하지 못했을 수 있다.

---

## 5. 왜 HYBRID_V2에서도 복구되지 않았는가

HYBRID_V2의 핵심 변경은 Candidate Pool 확대였다.

```text
HYBRID_V1
Top-5 + Top-5

HYBRID_V2
Top-10 + Top-10
```

RAG-GOLD-015와 RAG-GOLD-028은 이 변화로 복구되었다.

하지만 RAG-GOLD-008은 복구되지 않았다.

따라서 이 Case는 다음 중 하나일 가능성이 높다.

```text
A. 기대 문서가 Branch Rank 11 이하에도 존재하지 않는다.

B. 기대 문서는 존재하지만 다른 Chunk들이 훨씬 높은 Similarity를 가진다.

C. 정답 Evidence가 여러 Chunk에 분산되어 단일 Chunk Score가 약하다.

D. 질문과 본문의 Lexical/Semantic 표현 차이가 크다.

E. Golden Dataset의 expectedDocuments가 실제 Evidence 위치와 맞는지 재검증이 필요하다.
```

마지막 항목도 반드시 확인해야 한다.

실험에서 Miss가 발생했다고 해서 항상 Retriever가 틀렸다고 단정하면 안 된다.

Golden Dataset의 `expectedDocuments`가 실제 정답 Evidence를 포함하는 문서와 정확히 일치하는지도 검증해야 한다.

---

## 6. 다음 Trace 분석 절차

`RAG-GOLD-008`에 대해서만 Retrieval Trace를 별도로 추출한다.

### Step 1. Golden Expected Document 확인

확인할 항목:

```text
caseId
question
referenceAnswer
expectedDocuments
```

목적:

```text
Golden Dataset의 expectedDocuments가
실제 정답 Evidence를 포함하는 문서와 정확히 일치하는지 검증
```

---

### Step 2. VECTOR_ONLY Branch Top-20 확인

현재 HYBRID_V2에서는 Top-10까지만 Fusion에 사용한다.

Failure Analysis에서는 평가 설정을 변경하지 않고 디버그 목적으로만 Top-20 또는 그 이상을 조회한다.

확인:

```text
expectedDocumentRank
sourcePath
chunkId
chunkType
rawVectorScore
rerankedVectorScore
```

판정:

```text
Expected Rank 1~10
→ Fusion 문제 가능성

Expected Rank 11~20
→ Candidate Pool Recall 문제

Expected Rank 없음
→ Semantic Retrieval 또는 Chunking 문제
```

---

### Step 3. VECTOR_GRAPH_V6 Branch Top-20 확인

확인:

```text
vectorGraphRank
retrievalSource
graphScore
graphWeight
finalScore
sourcePath
chunkId
```

특히 기대 문서가 Graph Candidate 자체로 생성되었는지 확인한다.

```text
expected document가 Graph Expansion 후보인가?
YES / NO
```

---

### Step 4. Top-ranked False Positive 분석

최종 Top-5 또는 Branch Top-10에서 기대 문서보다 앞선 문서를 분석한다.

확인:

```text
Rank
sourcePath
chunkId
chunkType

rawVectorScore
headingBoost
contentBoost
relationBoost
patternBoost

vectorRank
vectorGraphRank
hybridScore
```

목적:

```text
왜 잘못된 문서가 높은 순위를 받았는가?
```

---

### Step 5. Expected Document Chunk 분석

기대 문서 내부에서 실제 정답 Evidence Chunk를 확인한다.

확인:

```text
정답 문장 위치
Chunk ID
Chunk Type
Heading
Content
앞 Chunk
뒤 Chunk
```

특히 다음을 확인한다.

```text
정답 Evidence가 하나의 Chunk에 존재하는가?

또는

여러 Chunk에 분산되어 있는가?
```

---

## 7. Failure 유형 판정 기준

Trace 분석 후 다음 중 하나로 분류한다.

### Type A — Candidate Recall Failure

```text
Expected Document Rank > Branch Top-K
```

대응 후보:

```text
Candidate Pool Expansion
Dynamic Candidate K
Query-adaptive Top-K
```

---

### Type B — Semantic Retrieval Failure

```text
Top-20 이상에서도 Expected Document가 검색되지 않음
```

대응 후보:

```text
Query Expansion
Sparse Retrieval
BM25
Keyword Retrieval
Hybrid Dense + Sparse
```

---

### Type C — Chunking Failure

```text
정답 Evidence가 여러 Chunk에 분산
```

대응 후보:

```text
Chunk Overlap
Parent-Child Retrieval
Document-level Retrieval
Neighbor Chunk Expansion
```

---

### Type D — Ranking Failure

```text
Expected Document는 Candidate Pool에 존재하지만
False Positive가 더 높은 Rank를 차지
```

대응 후보:

```text
Reranker
Cross Encoder
Query-aware lexical scoring
RRF Weight 조정
```

---

### Type E — Golden Dataset Issue

```text
expectedDocuments와 실제 Evidence 문서가 불일치
```

대응:

```text
Golden Dataset 수정
```

이 경우 Retrieval 알고리즘을 변경하면 안 된다.

---

## 8. 현재 단계에서 하지 않을 것

`RAG-GOLD-008` 하나를 맞히기 위해 즉시 다음을 수행하지 않는다.

```text
HYBRID_V3 생성
Branch Top-K 20으로 확대
RRF Weight 재조정
Graph Weight 재조정
특정 문서 Hard Coding
질문별 Rule 추가
```

이러한 변경은 1개의 Failure Case에 과적합될 위험이 있다.

현재 HYBRID_V2는:

```text
33 / 34 Hit
Hit Rate@5 = 0.9706
MRR        = 0.9216
HIT_TO_MISS = 0
Rank Regression = 0
```

으로 이미 안정적인 성능을 보이고 있다.

따라서 먼저 Failure 원인을 분류한 뒤 일반화 가능한 개선일 때만 후속 실험을 설계한다.

---

## 9. 포트폴리오 관점의 의미

`RAG-GOLD-008`을 실패 사례로 그대로 남겨 두는 것은 오히려 실험 기록의 신뢰도를 높인다.

핵심 메시지는 다음과 같다.

> 최종 모델이 100% 성능을 달성하도록 개별 Case에 과적합하지 않고, 유일하게 남은 Stable Miss를 별도의 Failure Analysis 대상으로 분리하였다. 이후 Retrieval Trace를 통해 Candidate Recall, Semantic Gap, Chunk Boundary, Ranking, Golden Dataset 오류 여부를 순차적으로 검증하도록 설계하였다.

이는 단순한 성능 수치보다 실제 RAG 개발 과정에서 중요한 다음 역량을 보여준다.

```text
Failure를 숨기지 않음
→ 원인 가설 수립
→ Trace 기반 검증
→ Failure Type 분류
→ 일반화 가능한 개선만 후속 실험
```

---

## 10. 현재 판정

현재 판정은 다음과 같다.

```text
RAG-GOLD-008

Status
STABLE_MISS

Observed In
VECTOR_ONLY_V1
VECTOR_GRAPH_V6
HYBRID_V1
HYBRID_V2

Current Root Cause
미확정

Primary Hypotheses
1. Semantic Gap
2. Multi-sentence Evidence
3. Chunk Boundary
4. Candidate Recall 부족
5. Query 표현 불일치
6. Golden expectedDocuments 검증 필요
```

---

## 11. 다음 실행

다음 단계에서는 `RAG-GOLD-008` 하나만 대상으로 **Retrieval Diagnostic Trace**를 생성한다.

최소 확인 값:

```text
expectedDocuments

VECTOR_ONLY
├─ expectedDocumentRank
├─ Top-20 sourcePath
└─ Top-20 score

VECTOR_GRAPH_V6
├─ expectedDocumentRank
├─ Graph Candidate 여부
└─ Top-20 score

HYBRID_V2
├─ vectorRank
├─ vectorGraphRank
├─ hybridScore
└─ Final Rank

Expected Document
├─ Evidence Chunk
├─ Chunk Type
├─ Heading
├─ Content
└─ Neighbor Chunks
```

이 분석 결과를 바탕으로 후속 실험 필요 여부를 결정한다.

---

## Failure Analysis Summary

```text
Final Retrieval Candidate
HYBRID_V2

Overall
33 / 34 Hit
Hit Rate@5 = 0.9706
MRR        = 0.9216

Remaining Failure
RAG-GOLD-008

Decision
HYBRID_V3로 즉시 이동하지 않음
↓
Trace 기반 Root Cause Analysis 수행
↓
일반화 가능한 원인일 때만 개선 실험 설계
```
