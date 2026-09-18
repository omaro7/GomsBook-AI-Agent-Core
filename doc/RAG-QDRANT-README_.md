# GomsBook AI - Qdrant Vector Store

## 1. 개요

GomsBook AI의 RAG 검색 시스템에서 기존 `InMemoryVectorStore`를 운영 가능한 Vector Database인 **Qdrant**로 확장하였다.

기존 InMemory 기반 Vector Store는 Golden Dataset을 이용한 RAG Retrieval 알고리즘 검증에는 적합하지만 다음과 같은 한계가 있다.

- 애플리케이션 재시작 시 Vector 데이터 소멸
- 전체 Vector를 JVM Memory에 보관
- 프로젝트 수 증가에 따른 Memory 사용량 증가
- Vector Index 관리 기능 부재
- Payload 기반 Filtering 기능 부족
- 대규모 Vector 검색 확장성 부족

이를 개선하기 위해 GomsBook AI에 Qdrant 기반 Vector Store를 추가하였다.

Qdrant 전환 후에는 기존 Golden Dataset을 다시 평가하여 **Vector Store 교체에 따른 Retrieval 품질 Regression 여부를 검증**하였다.

현재 단계의 핵심 목표는 Qdrant가 InMemory보다 빠르다는 것을 증명하는 것이 아니라,

> 기존 Retrieval 품질을 유지하면서  
> Persistence, Project Isolation, Payload Filtering, HNSW Search를 지원하는  
> 운영 가능한 Vector DB 구조로 확장하는 것

이다.

---

## 2. 실험 범위

현재 Qdrant 구현 및 검증 범위는 다음과 같다.

```text
GomsBook AI
    │
    ├─ EPUB / Document
    ├─ DocumentLoader
    ├─ DocumentIndexer
    ├─ EmbeddingClient
    │      └─ Ollama
    │          └─ nomic-embed-text
    ├─ RagIndexer
    ├─ QdrantVectorStore
    │      └─ Qdrant
    │          └─ gomsbook_rag
    ├─ DefaultRetriever
    ├─ VectorGraphRetriever
    ├─ HybridRetriever
    └─ Golden Dataset Evaluation
```

현재 완료된 검증:

```text
Qdrant Docker 실행                         PASS
Qdrant Collection 생성                    PASS
Vector 저장                               PASS
Payload 저장                              PASS
projectId Filter 검색                     PASS
sourcePath Filter 검색                    PASS
DefaultRetriever → QdrantVectorStore      PASS
HYBRID_V2 단일 Golden Case 검증           PASS
HYBRID_V2 전체 Golden Regression Test     PASS
```

추가 Benchmark 예정:

```text
VECTOR_MEMORY_V1 vs VECTOR_QDRANT_V1

- Average Query Latency
- P50
- P95
- P99
- Indexing Time
- Vector Count
- Persistence after restart
- Multi-project Isolation
```

---

## 3. 기존 Vector Store 구조

기존 GomsBook AI의 Vector Store는 `InMemoryVectorStore`를 사용하였다.

```text
Document
   ↓
Chunk
   ↓
Embedding
   ↓
VectorRecord
   ↓
InMemoryVectorStore
   ↓
Exact Cosine Search
```

InMemory 방식은 RAG 알고리즘의 Baseline 검증에는 유용하다.

특히 작은 Dataset에서는 전체 Vector를 Memory에서 직접 비교하기 때문에 검색 Latency도 매우 낮을 수 있다.

그러나 운영 환경에서는 애플리케이션 재시작과 동시에 Vector가 사라지고, 프로젝트 수와 문서 수가 증가할수록 JVM Memory에 대한 의존도가 증가한다.

따라서 InMemory Vector Store는 제거하지 않고 **Baseline / Experiment Reference 구현체**로 유지한다.

---

## 4. Qdrant 도입 구조

Qdrant 도입 후 Vector 검색 구조는 다음과 같다.

```text
Document
   ↓
DocumentIndexer
   ↓
DocumentChunk
   ↓
EmbeddingClient
   ↓
nomic-embed-text
   ↓
VectorRecord
   ↓
QdrantVectorStore
   ↓
Qdrant Collection
   ↓
HNSW + Payload Filter
   ↓
VectorSearchResult
   ↓
Retriever
```

GomsBook AI의 Domain Interface인 `VectorStore`는 유지한다.

```text
VectorStore
    ├─ InMemoryVectorStore
    └─ QdrantVectorStore
```

따라서 상위 RAG 계층은 특정 Vector DB 구현에 직접 의존하지 않는다.

```text
Retriever
    ↓
VectorStore
```

구현체 교체는 Spring Bean Configuration에서 결정한다.

---

## 5. 기술 구성

현재 Qdrant 실험 환경은 다음과 같다.

| Component | Configuration |
|---|---|
| Vector DB | Qdrant |
| Qdrant Docker Image | `qdrant/qdrant:v1.19.1` |
| Qdrant Java Client | `io.qdrant:client:1.19.0` |
| REST Port | `6333` |
| gRPC Port | `6334` |
| Collection | `gomsbook_rag` |
| Dense Vector Name | `dense` |
| Embedding Model | `nomic-embed-text` |
| Retrieval Mode | `HYBRID` |
| Evaluation Version | `V2` |
| Golden Dataset | `rag-golden-v1` |

현재 단계에서는 Dense Vector를 우선 적용하였다.

Sparse Vector 및 Dense + Sparse Hybrid 검색은 후속 확장 대상으로 둔다.

---

## 6. Docker 구성

예시 `docker-compose.yml`:

```yaml
services:
  qdrant:
    image: qdrant/qdrant:v1.19.1
    container_name: gomsbook-qdrant
    restart: unless-stopped
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant-data:/qdrant/storage

volumes:
  qdrant-data:
```

실행:

```bash
docker compose up -d qdrant
```

상태 확인:

```bash
docker ps
```

Qdrant REST API:

```text
http://localhost:6333
```

Qdrant Dashboard:

```text
http://localhost:6333/dashboard
```

Collection Dashboard:

```text
http://localhost:6333/dashboard#/collections/gomsbook_rag
```

---

## 7. GomsBook AI 환경설정

로컬 Windows에서 GomsBook AI API를 실행하고 Qdrant만 Docker에서 실행하는 경우:

```properties
# ============================================================
# Qdrant
# ============================================================

gomsbook.ai.qdrant.host=localhost
gomsbook.ai.qdrant.grpc-port=6334
gomsbook.ai.qdrant.collection-name=gomsbook_rag
gomsbook.ai.qdrant.tls=false
```

API까지 Docker Compose 내부에서 실행한다면:

```properties
gomsbook.ai.qdrant.host=qdrant
```

를 사용한다.

---

## 8. RAG Evaluation 환경설정

현재 Golden Regression Test는 `HYBRID_V2`로 수행하였다.

```properties
# ============================================================
# RAG Evaluation
# ============================================================

gomsbook.ai.rag.evaluation.retrieval-mode=HYBRID
gomsbook.ai.rag.evaluation.version=V2
```

현재 실험 식별자는 다음과 같이 관리한다.

```text
HYBRID_V2_QDRANT_V1
```

의미:

```text
HYBRID
    Hybrid Retrieval

V2
    기존 GomsBook HYBRID_V2 Retrieval 정책

QDRANT
    Vector Backend = QdrantVectorStore

V1
    최초 Qdrant Regression Experiment
```

---

## 9. Collection 설계

현재 모든 GomsBook 프로젝트는 하나의 Qdrant Collection을 공유한다.

```text
Collection
└─ gomsbook_rag
```

프로젝트마다 Collection을 생성하지 않는다.

대신 Payload의 `projectId`를 이용하여 프로젝트를 격리한다.

```text
gomsbook_rag

├─ projectId = lunchwork_seoul
│   ├─ point
│   ├─ point
│   └─ point
│
├─ projectId = epub-ai-agent
│   ├─ point
│   └─ point
│
└─ projectId = ...
```

이 구조를 통해 Collection 수의 불필요한 증가를 방지하면서 프로젝트 단위 Filter 검색이 가능하다.

---

## 10. projectId 정책

`projectId`는 GomsBook 프로젝트를 식별하는 논리적인 ID이다.

예:

```text
Project Root
C:\1004.GomsBook\03.Project\lunchwork_seoul
```

projectId:

```text
lunchwork_seoul
```

중요한 정책:

```text
projectId != DB UUID
projectId != Qdrant Point UUID
projectId != Project Path SHA-256
```

프로젝트 폴더명 기반의 안정적인 Logical ID를 사용한다.

따라서 다음 계층에서 동일한 `projectId`가 전달되어야 한다.

```text
RagRuntime
   ↓
RagIndexer
   ↓
VectorRecord
   ↓
Qdrant Payload

RagRuntime
   ↓
RetrievalRequest
   ↓
VectorSearchRequest
   ↓
Qdrant Filter
```

---

## 11. Qdrant Payload

현재 Qdrant Point에는 검색 Vector와 함께 RAG 검색 및 운영에 필요한 Metadata를 Payload로 저장한다.

대표적인 Payload:

```json
{
  "projectId": "lunchwork_seoul",
  "recordId": "...",
  "model": "nomic-embed-text",
  "sourcePath": "OEBPS/Text/chapter10_4.xhtml",
  "title": "...",
  "chunkType": "...",
  "content": "...",
  "sequence": 5,
  "elementId": "p_05",
  "epubType": "...",
  "language": "ko",
  "sourceType": "XHTML",
  "contentHash": "...",
  "sourceHash": "...",
  "normalized": true,
  "indexedAt": "...",
  "version": "...",
  "metadata": {}
}
```

Payload는 단순 표시용 Metadata가 아니다.

```text
projectId
    → 프로젝트 격리

sourcePath
    → 특정 문서 검색 / 삭제

model
    → Embedding Model 버전 관리

recordId
    → GomsBook 내부 VectorRecord 식별

content
    → Retrieval 결과 Context 복원

elementId
    → XHTML Element 추적

sequence
    → 문서 내 Chunk 순서 복원
```

---

## 12. Qdrant Point ID

GomsBook 내부의 `VectorRecord.id`를 Qdrant Point ID로 직접 사용하지 않는다.

Qdrant Point에는 deterministic UUID를 사용한다.

개념적으로 다음 값을 기반으로 생성한다.

```text
projectId
+
recordId
+
embeddingModel
```

원래 GomsBook Record ID는 Payload의 `recordId`에 별도로 유지한다.

이를 통해 동일한 Project / Record / Model 조합은 항상 동일한 Qdrant Point를 대상으로 처리할 수 있다.

---

## 13. VectorStore API

Qdrant 적용 이후 VectorStore는 Project Scope를 지원한다.

대표 API:

```java
findById(String projectId, String id, String model)
findById(String projectId, String id)
findByIds(String projectId, List<String> ids)
findByProjectAndModel(String projectId, String model)
findByProjectAndSourcePath(String projectId, String sourcePath)
contains(String projectId, String id, String model)
delete(String projectId, String id, String model)
deleteByProjectAndSourcePath(String projectId, String sourcePath, String model)
deleteAll(String projectId, List<String> ids, String model)
```

기존 Global API도 Admin / Test 용도로 유지할 수 있다.

```text
findByModel
findBySourcePath
deleteBySourcePath
deleteByModel
clear
```

그러나 일반 프로젝트 실행 흐름에서는 Global API를 사용하지 않는다.

특히 Qdrant가 여러 프로젝트를 하나의 Collection에서 관리하기 때문에 `vectorStore.clear()`를 프로젝트 종료 시 호출하면 안 된다.

`clear()`는 Collection 전체 데이터를 삭제할 수 있는 Global/Admin Operation으로 취급한다.

---

## 14. RagIndexer 정책

`projectId`는 `DefaultRagIndexer`의 Constructor State가 아니다.

`DefaultRagIndexer`는 여러 프로젝트에서 재사용되는 Component이므로 Project ID는 실행 시점에 전달한다.

```java
ragIndexer.index(projectId, source);
ragIndexer.index(projectId, source, request);
ragIndexer.remove(projectId, sourcePath);
ragIndexer.removeCurrentModel(projectId);
```

Constructor에는 안정적인 Dependency만 둔다.

```text
DocumentIndexer
EmbeddingClient
EmbeddingModelProvider
VectorStore
HashService
```

이 구조를 통해 프로젝트 전환 시 Indexer Bean을 새로 생성할 필요가 없다.

---

## 15. RagRuntime 정책

`RagRuntime`은 현재 열린 프로젝트의 실행 Context를 관리한다.

```text
projectRoot
projectId
```

프로젝트 Open 시:

```text
Project Root
C:\1004.GomsBook\03.Project\lunchwork_seoul

↓

projectId
lunchwork_seoul
```

프로젝트 Close 시 기본 정책은 Qdrant Index를 삭제하지 않는 것이다.

```text
openProject()
    → 기존 Vector 유지

closeProject()
    → 기존 Vector 유지
```

Qdrant의 목적 중 하나가 Persistent Vector Store이므로 프로젝트를 닫는 행위와 Index 삭제를 동일하게 취급하지 않는다.

명시적인 Index 삭제가 요청될 때만 `projectId + current embedding model` 범위를 삭제한다.

---

## 16. Spring Bean 구성

InMemory와 Qdrant를 동시에 유지하기 때문에 Bean 주입 대상을 명확하게 지정해야 한다.

```java
@Bean("inMemoryVectorStore")
public VectorStore inMemoryVectorStore() {
    return new InMemoryVectorStore();
}
```

```java
@Bean("qdrantVectorStore")
@Primary
public VectorStore qdrantVectorStore(QdrantClient qdrantClient, QdrantConfiguration configuration) {
    return new QdrantVectorStore(qdrantClient, configuration);
}
```

Retriever는 Qdrant를 명시적으로 사용한다.

```java
@Bean("vectorOnlyRetriever")
@Primary
public Retriever vectorOnlyRetriever(
        EmbeddingClient embeddingClient,
        EmbeddingModelProvider embeddingModelProvider,
        @Qualifier("qdrantVectorStore") VectorStore vectorStore) {
    return new DefaultRetriever(embeddingClient, embeddingModelProvider, vectorStore);
}
```

Indexer와 Project Indexer도 동일한 `qdrantVectorStore`를 주입한다.

---

## 17. 실제 VectorStore 연결 검증

Qdrant Regression Test 전에 실제 Retriever가 QdrantVectorStore를 사용하고 있는지 확인하였다.

실행 로그:

```text
[RAG][VECTOR-STORE] kr.co.goms.gomsbook.ai.rag.vector.qdrant.QdrantVectorStore
==================================================
[RAG][VERIFY] VectorStore = kr.co.goms.gomsbook.ai.rag.vector.qdrant.QdrantVectorStore
[RAG][VERIFY] Retriever   = kr.co.goms.gomsbook.ai.rag.retrieval.DefaultRetriever
==================================================
```

실제 검색 경로:

```text
vectorOnlyRetriever
        ↓
DefaultRetriever
        ↓
QdrantVectorStore
```

HYBRID_V2:

```text
ragEvaluationRetriever
        ↓
HybridRetriever
        │
        ├─ Vector Branch
        │      ↓
        │  vectorOnlyRetriever
        │      ↓
        │  DefaultRetriever
        │      ↓
        │  QdrantVectorStore
        │
        └─ VectorGraph Branch
               ↓
           VectorGraphRetriever
               ↓
           vectorOnlyRetriever
               ↓
           QdrantVectorStore
```

따라서 Golden Regression Test가 실제 Qdrant를 대상으로 실행되었음을 확인하였다.

---

## 18. Qdrant 기능 테스트

### 18.1 Collection 확인

```text
http://localhost:6333/dashboard#/collections/gomsbook_rag
```

결과:

```text
PASS
```

### 18.2 Project Filter 테스트

실제 Payload에서 다음 값이 확인되었다.

```text
projectId = lunchwork_seoul
```

결과:

```text
PASS
```

---

## 19. sourcePath Filter 테스트

특정 EPUB XHTML 문서만 확인하기 위해 `projectId + sourcePath` Filter를 사용하였다.

확인 대상:

```text
OEBPS/Text/chapter10_4.xhtml
```

Qdrant에서 해당 문서의 여러 Chunk Point가 정상 조회되었다.

대표 Payload:

```text
projectId  = lunchwork_seoul
sourcePath = OEBPS/Text/chapter10_4.xhtml
model      = nomic-embed-text
```

결과:

```text
PASS
```

---

## 20. Windows PowerShell 5.1 UTF-8 문제

Qdrant Dashboard에서는 한글 Payload가 정상적으로 표시되었으나 Windows PowerShell 5.1의 `Invoke-RestMethod` 출력에서는 한글이 깨지는 현상이 발생하였다.

확인한 PowerShell Version:

```text
Major  Minor  Build
5      1      26100
```

Qdrant Dashboard에서 원본 Payload가 정상적으로 표시되었기 때문에 Qdrant 저장 데이터 손상이 아니라 PowerShell 5.1의 Response Encoding 문제로 판단하였다.

개발 환경에서는 PowerShell 7 사용을 권장한다.

---

## 21. Golden Dataset Regression Test

Qdrant 전환 후 기존 RAG Retrieval 품질에 Regression이 발생하는지 확인하기 위해 기존 Golden Dataset을 동일 조건으로 다시 평가하였다.

```text
Dataset           : rag-golden-v1
Dataset Total     : 40
Evaluated Cases   : 34
Retrieval Mode    : HYBRID
Version           : V2
Experiment        : HYBRID_V2_QDRANT_V1
Vector Store      : QdrantVectorStore
Embedding Model   : nomic-embed-text
```

---

## 22. 핵심 Golden Case 검증

### RAG-GOLD-008

```text
Hit Rate@K        = 0.0000
Average Recall@K  = 0.0000
MRR               = 0.0000
Result            = MISS
```

`RAG-GOLD-008`은 기존 HYBRID_V2에서도 남아 있던 Stable Miss이다.

### RAG-GOLD-015

```text
Hit Rate@K        = 1.0000
Average Recall@K  = 1.0000
MRR               = 1.0000
Relevant Rank     = 1
Result            = HIT
```

### RAG-GOLD-028

```text
Hit Rate@K        = 1.0000
Average Recall@K  = 1.0000
MRR               = 0.5000
Relevant Rank     = 2
Result            = HIT
```

### RAG-GOLD-010

```text
Hit Rate@K        = 1.0000
Average Recall@K  = 1.0000
MRR               = 1.0000
Relevant Rank     = 1
Result            = HIT
```

---

## 23. 전체 Golden Dataset 평가 결과

| Metric | Result |
|---|---:|
| Dataset Total | 40 |
| Evaluated Cases | 34 |
| Hit | 33 |
| Miss | 1 |
| Hit Rate@K | **0.9706** |
| Average Recall@K | **0.9706** |
| MRR | **0.9265** |

남은 Stable Miss:

```text
RAG-GOLD-008
```

---

## 24. 기존 InMemory HYBRID_V2 비교

| Metric | HYBRID_V2 InMemory | HYBRID_V2 Qdrant | Difference |
|---|---:|---:|---:|
| Evaluated Cases | 34 | 34 | 0 |
| Hit Rate@K | 0.9706 | **0.9706** | 0 |
| Average Recall@K | 0.9706 | **0.9706** | 0 |
| MRR | 0.9216 | **0.9265** | +0.0049 |
| Hit | 33 | **33** | 0 |
| Miss | 1 | **1** | 0 |

검색 성공률과 Recall은 동일하게 유지되었고 MRR은 소폭 증가하였다.

현재 결과에서는 Retrieval 품질 저하가 관찰되지 않았다.

---

## 25. Regression Test 결론

```text
Hit Rate@K
0.9706 → 0.9706

Average Recall@K
0.9706 → 0.9706

MRR
0.9216 → 0.9265

Hit
33 → 33

Miss
1 → 1
```

따라서 현재 Dataset과 실험 조건에서는

> InMemoryVectorStore에서 QdrantVectorStore로 전환한 이후 기존 HYBRID_V2 Retrieval 품질에 유의미한 Regression이 발생하지 않았다.

고 판단한다.

---

## 26. Qdrant 도입의 의미

이번 실험의 목적은 단순히 Qdrant가 InMemory보다 검색 속도가 빠르다는 것을 증명하는 것이 아니다.

작은 Dataset에서는 InMemory Exact Search가 Qdrant보다 빠르게 측정될 가능성도 충분하다.

Qdrant 도입의 핵심 목적은 다음과 같다.

```text
Persistent Vector Storage
        +
Project Isolation
        +
Payload Filtering
        +
HNSW Vector Index
        +
Vector Lifecycle Management
        +
Large-scale Vector Search
        +
Production-oriented Architecture
```

즉 GomsBook AI의 RAG 시스템을 실험용 InMemory RAG에서 운영 가능한 Persistent Vector RAG 구조로 확장하는 것이 핵심이다.

---

## 27. 현재 아키텍처

```text
                         ┌─────────────────────┐
                         │    GomsBook AI      │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │     RagRuntime      │
                         │ projectId / project │
                         └──────────┬──────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  │                                   │
                  ▼                                   ▼
        ┌───────────────────┐              ┌───────────────────┐
        │    RagIndexer     │              │     Retriever     │
        └─────────┬─────────┘              └─────────┬─────────┘
                  │                                   │
                  ▼                                   ▼
        ┌───────────────────┐              ┌───────────────────┐
        │ Embedding Client  │              │ Embedding Client  │
        │ nomic-embed-text  │              │ nomic-embed-text  │
        └─────────┬─────────┘              └─────────┬─────────┘
                  │                                   │
                  └─────────────────┬─────────────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │     VectorStore     │
                         └──────────┬──────────┘
                                    │
                     ┌──────────────┴──────────────┐
                     │                             │
                     ▼                             ▼
          ┌────────────────────┐       ┌────────────────────┐
          │ InMemoryVectorStore│       │ QdrantVectorStore  │
          │ Baseline / Test    │       │ Runtime / Qdrant   │
          └────────────────────┘       └─────────┬──────────┘
                                                │
                                                ▼
                                      ┌────────────────────┐
                                      │       Qdrant       │
                                      │   gomsbook_rag     │
                                      └────────────────────┘
```

---

## 28. VECTOR_MEMORY_V1 vs VECTOR_QDRANT_V1 Benchmark 결과

`InMemoryVectorStore`와 `QdrantVectorStore`의 **순수 Vector Search Latency**를 동일한 Query Vector 조건에서 비교하였다.

```text
VECTOR_MEMORY_V1
        vs
VECTOR_QDRANT_V1
```

공통 조건:

```text
Same Project
Same Dataset
Same Chunks
Same Embedding Model
Same Query Vector
Same topK
Same minimumScore
Embedding Latency Excluded
```

실측 결과:

| Metric | VECTOR_MEMORY_V1 | VECTOR_QDRANT_V1 | Delta | Qdrant / Memory |
|---|---:|---:|---:|---:|
| Average | **2.58 ms** | 12.44 ms | +9.85 ms | 약 **4.8×** |
| P50 | **2.60 ms** | 10.27 ms | +7.67 ms | 약 **3.9×** |
| P95 | **3.05 ms** | 24.74 ms | +21.69 ms | 약 **8.1×** |
| P99 | **6.43 ms** | 48.95 ms | +42.51 ms | 약 **7.6×** |

현재 소규모 Dataset에서는 Qdrant의 Average Latency가 InMemory 대비 약 4.8배 높게 측정되었다. P95/P99 Tail Latency에서도 약 7.6~8.1배 수준의 차이가 확인되었다.

이 결과는 Qdrant의 실패를 의미하지 않는다. InMemory는 JVM 내부에서 직접 Vector를 순회하는 반면, Qdrant는 gRPC 직렬화, 프로세스 경계, Payload Filter, HNSW 검색 및 응답 역직렬화 비용을 포함한다.

따라서 Qdrant 도입의 평가는 단순 Latency뿐 아니라 Retrieval Quality, Persistence, Project Isolation, Payload Filtering, HNSW 기반 운영 구조를 함께 고려한다.

---


## 29. Benchmark 원칙

Latency 실험은 단일 Query의 실행 시간만 비교하지 않는다.

다수 Query를 반복 실행하여 Latency Distribution을 측정한다.

```text
latencies
    ↓
sort
    ↓
Average
P50
P95
P99
```

특히 Vector DB Benchmark에서는 Average만으로는 Tail Latency를 확인하기 어렵기 때문에 P95와 P99를 함께 기록한다.

---

## 30. Benchmark 해석 기준

Qdrant가 더 빠른 경우:

```text
Quality 유지
+
Latency 개선
+
Persistence 확보
```

Qdrant가 더 느린 경우에도 바로 실패로 판단하지 않는다.

```text
Persistent Storage
Project Isolation
Payload Filter
HNSW Index
Scalability
```

를 확보하기 위한 운영 비용으로 해석할 수 있다.

최종 판단은 Retrieval Quality, Latency, Persistence, Scalability, Operational Capability를 함께 평가한다.

---

## 31. Persistence Test

상태:

```text
PENDING CONFIRMATION
```

검증 절차:

```text
1. 프로젝트 Indexing
2. Qdrant Point Count 확인
3. GomsBook API 종료
4. Qdrant Container 재시작
5. GomsBook API를 재기동하기 전에 Point Count 재확인
6. 재색인 없이 Retrieval 실행
7. 기존 Point 및 검색 결과 유지 확인
```

현재 대화에서 **Qdrant 재시작 전/후 실제 Point Count 결과가 아직 제공되지 않았으므로 PASS로 확정하지 않는다.**

PASS 기준:

```text
BEFORE lunchwork_seoul = 169
BEFORE epub-ai-agent   = 132

Qdrant Restart

AFTER lunchwork_seoul  = 169
AFTER epub-ai-agent    = 132
```

위 값이 유지되고 재색인 없이 Retrieval이 정상 동작하면 다음과 같이 확정한다.

```text
Storage Persistence              PASS
Retrieval after Qdrant restart   PASS
```

---


## 32. Multi-project Isolation Test

동일한 `gomsbook_rag` Collection에 두 프로젝트를 동시에 저장하여 Project Scope 격리를 검증하였다.

```text
Project A = lunchwork_seoul
Project B = epub-ai-agent
Collection = gomsbook_rag
```

실측 Point Count:

| Project | Point Count | Result |
|---|---:|---|
| `lunchwork_seoul` | **169** | PASS |
| `epub-ai-agent` | **132** | PASS |

### 32.1 Multi-project Coexistence

두 프로젝트가 동일 Collection에 동시에 저장되는 것을 확인하였다.

```text
lunchwork_seoul = 169
epub-ai-agent   = 132
```

결과:

```text
Multi-project Coexistence = PASS
```

### 32.2 projectId Filter Isolation

각 프로젝트에 대해 Qdrant `projectId` Filter를 적용하고 `Sort-Object -Unique`로 반환 Payload의 `projectId`를 확인하였다.

결과:

```text
lunchwork_seoul query
→ lunchwork_seoul 만 반환

epub-ai-agent query
→ epub-ai-agent 만 반환
```

판정:

```text
projectId Filter Isolation = PASS
```

### 32.3 Project-scoped Delete

`epub-ai-agent`의 현재 Embedding Model Vector Index 삭제 후 Count를 확인하였다.

```text
Before
epub-ai-agent = 132

After
epub-ai-agent = 0
```

판정:

```text
Target Project Delete = PASS
```

단, 삭제 후 `lunchwork_seoul = 169`가 그대로 유지되었다는 실제 Count 값은 현재 대화에서 아직 제공되지 않았으므로 **다른 프로젝트 보존 여부는 최종 확인이 필요하다.**

### 32.4 Retrieval Isolation

실제 Retriever Top-K가 다른 프로젝트의 문서를 반환하지 않는지에 대한 실행 결과는 현재 대화에서 아직 제공되지 않았다.

상태:

```text
Multi-project Coexistence       PASS
projectId Filter Isolation      PASS
Target Project Delete           PASS
Other Project Preservation      PENDING CONFIRMATION
Retrieval Isolation             PENDING CONFIRMATION
```

---


## 33. Sparse / Hybrid Vector 확장

현재 Qdrant 구현은 Dense Vector 중심이다.

```text
dense
    ↓
nomic-embed-text
```

향후 확장 후보:

```text
Dense Vector
        +
Sparse Vector
        ↓
Hybrid Search
```

현재 GomsBook의 `HYBRID_V2`는 Vector Retrieval과 Graph Retrieval 결과를 결합하는 Hybrid 방식이다.

향후 Qdrant Sparse Vector까지 적용할 경우 Dense Retrieval + Sparse Retrieval + Graph Retrieval 구조로 확장할 수 있다.

---

## 34. RAG-GOLD-008 Failure

Qdrant 전환 후에도 `RAG-GOLD-008`은 Stable Miss로 유지되었다.

이 Case는 Vector DB 자체의 문제보다는 Chunk Boundary와 Query Context의 관계가 중요한 Case이다.

향후 다음 실험 대상으로 관리한다.

```text
Chunk Context Expansion
Adjacent Chunk Retrieval
Parent / Child Retrieval
Context Window Expansion
Graph Context
Query Expansion
Reranking
```

Qdrant 도입 자체의 성공 여부를 `RAG-GOLD-008` 하나의 HIT 여부로 판단하지 않는다.

---

## 35. 실험 상태

```text
[PASS] Qdrant Docker
[PASS] Collection 생성
[PASS] Dense Vector 저장
[PASS] Payload 저장
[PASS] projectId Filter
[PASS] sourcePath Filter
[PASS] QdrantVectorStore 연결
[PASS] DefaultRetriever 연결
[PASS] HYBRID_V2 단일 Case Regression Test
[PASS] HYBRID_V2 전체 Golden Regression Test

[PASS] VECTOR_MEMORY_V1 Benchmark
[PASS] VECTOR_QDRANT_V1 Benchmark
[PASS] Average Latency   2.58 ms vs 12.44 ms
[PASS] P50               2.60 ms vs 10.27 ms
[PASS] P95               3.05 ms vs 24.74 ms
[PASS] P99               6.43 ms vs 48.95 ms

[PASS] Multi-project Coexistence
       lunchwork_seoul = 169
       epub-ai-agent   = 132

[PASS] projectId Filter Isolation
[PASS] Target Project Delete
       epub-ai-agent 132 → 0

[PENDING] Other Project Preservation after Delete
[PENDING] Retrieval Isolation
[PENDING] Persistence Test after Qdrant Restart

[TODO] Indexing Time 비교
```

---


## 36. 현재 결론

GomsBook AI의 RAG Vector Store를 기존 `InMemoryVectorStore`에서 `QdrantVectorStore`로 확장하였다.

Qdrant 적용 후 기존 HYBRID_V2 Golden Dataset을 재평가한 결과:

```text
Evaluated Cases       34
Hit                   33
Miss                  1
Hit Rate@K            0.9706
Average Recall@K      0.9706
MRR                   0.9265
```

기존 InMemory 기반 결과:

```text
Hit Rate@K            0.9706
Average Recall@K      0.9706
MRR                   0.9216
```

와 비교할 때 Hit Rate와 Recall은 동일하게 유지되었으며, MRR도 기존 수준을 유지하면서 소폭 상승하였다.

Latency Benchmark에서는 다음 결과를 얻었다.

```text
VECTOR_MEMORY_V1
Average = 2.58 ms
P50     = 2.60 ms
P95     = 3.05 ms
P99     = 6.43 ms

VECTOR_QDRANT_V1
Average = 12.44 ms
P50     = 10.27 ms
P95     = 24.74 ms
P99     = 48.95 ms
```

현재 소규모 Dataset에서는 Qdrant의 Average Latency가 InMemory 대비 약 4.8배 높았으며, P95/P99 Tail Latency에서도 약 7.6~8.1배의 차이가 확인되었다.

그러나 Qdrant 전환 후 Hit Rate@K와 Average Recall@K는 기존 `0.9706`을 그대로 유지했으며, 동일 Collection에서 `lunchwork_seoul=169`, `epub-ai-agent=132` Point의 공존 및 `projectId` Filter Isolation도 확인하였다.

따라서 현재까지는 다음과 같이 정리할 수 있다.

> Qdrant Vector Store 전환으로 Retrieval 품질 Regression 없이 Persistent Vector DB 구조를 도입했으며, 소규모 Dataset에서 발생하는 Latency Overhead를 정량화하고 Multi-project 저장 및 Filter Isolation을 검증하였다.

현재 남은 검증:

```text
Persistence after Qdrant Restart
Other Project Preservation after Project-scoped Delete
Retriever-level Multi-project Isolation
```

이 세 항목이 확인되면 `QDRANT_V1` 운영 검증을 완료한다.

---


## 37. Next Step

```text
Qdrant Implementation                   DONE
        ↓
Golden Regression Test                 DONE
        ↓
Retrieval Quality Regression           PASS
        ↓
VECTOR_MEMORY_V1
        vs
VECTOR_QDRANT_V1
        ↓
Latency Benchmark                      DONE
        ↓
Average / P50 / P95 / P99             DONE
        ↓
Multi-project Coexistence              PASS
        ↓
projectId Filter Isolation             PASS
        ↓
Target Project Delete                  PASS
        ↓
Other Project Preservation             NEXT
        ↓
Persistence after Qdrant Restart
        ↓
Retriever-level Isolation
        ↓
Qdrant Experiment Complete
```
