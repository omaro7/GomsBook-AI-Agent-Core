# GomsBook AI Agent Core

`GomsBook-AI-Agent-Core`는 GomsBook AI Agent의 공통 실행 엔진을 제공하는
순수 Java 기반 Maven 프로젝트입니다.

EPUB Editor, Shorts Editor 등 특정 애플리케이션이나 UI 프레임워크에
종속되지 않는 AI Agent 핵심 기능을 제공하는 것을 목표로 합니다.

------------------------------------------------------------------------

## 1. 프로젝트 목적

기존 `GomsBook-AI-Agent`는 GomsBookEditor와 연동되는 Eclipse Plugin/PDE
기반 프로젝트입니다.

AI Agent 기능을 향후 EPUB Editor뿐만 아니라 Shorts Editor 및 다른
애플리케이션에서도 재사용하기 위해 공통 Agent 기능을
`GomsBook-AI-Agent-Core`로 분리합니다.

주요 설계 원칙은 다음과 같습니다.

-   순수 Java 기반
-   Maven JAR 프로젝트
-   Eclipse/PDE 비종속
-   Spring Boot 비종속
-   특정 Editor 비종속
-   HTTP/SSE 비종속
-   AI Agent 핵심 기능 재사용
-   API 및 Editor Adapter에서 공통 사용 가능

------------------------------------------------------------------------

## 2. 전체 아키텍처

현재 전환 단계에서는 기존 Agent와 Core를 병행하여 개발합니다.

``` text
기존 EPUB Editor 경로

GomsBookEditor
      │
      ▼
GomsBook-AI-Agent
(Eclipse Plugin / PDE)
      │
      ├─ Agent
      ├─ LLM
      ├─ RAG
      ├─ EPUB Tools
      └─ Accessibility


신규 API 검증 경로

GomsBook-AI-API
(Spring Boot / HTTP / SSE : 5001)
      │
      ▼
GomsBook-AI-Agent-Core
(Pure Java / Maven)
      │
      ├─ Agent
      ├─ LLM
      ├─ Tool
      ├─ RAG
      └─ Domain Services
```

Core 안정화 이후에는 다음 구조를 목표로 합니다.

``` text
                  ┌───────────────────┐
                  │ GomsBookEditor    │
                  │ EPUB Editor       │
                  └─────────┬─────────┘
                            │
                            ▼
                  GomsBook-AI-Agent
                   Eclipse Adapter
                            │
                            ▼
                 ┌────────────────────┐
                 │                    │
                 │ GomsBook-AI-Agent  │
                 │       Core         │
                 │                    │
                 └────────────────────┘
                            ▲
                            │
                  GomsBook-AI-API
                  HTTP / SSE Adapter
                            ▲
                            │
                  ┌─────────┴─────────┐
                  │                   │
                  │   Shorts Editor   │
                  │                   │
                  └───────────────────┘
```

------------------------------------------------------------------------

## 3. 프로젝트 역할

### GomsBook-AI-Agent-Core

공통 AI Agent 실행 엔진입니다.

``` text
Agent Execution
Tool Framework
LLM Integration
RAG
Planning
Evaluation
Common Domain Services
```

특정 UI 또는 서버 기술에 의존하지 않습니다.

### GomsBook-AI-Agent

GomsBookEditor와 Core를 연결하는 Eclipse Adapter 역할을 담당합니다.

현재 전환 기간에는 기존 Agent 구현을 그대로 유지합니다.

향후 Core가 안정화되면 중복된 공통 기능을 제거하고 Eclipse/RCP 연동 기능
중심으로 축소합니다.

### GomsBook-AI-API

외부 애플리케이션과 Core를 연결하는 HTTP/SSE Adapter입니다.

``` text
Client
  │
  │ HTTP / SSE
  ▼
GomsBook-AI-API : 5001
  │
  │ Java
  ▼
GomsBook-AI-Agent-Core
```

별도의 Agent HTTP 서버는 두지 않습니다.

Core는 API 프로세스 내부에서 Java dependency로 실행합니다.

------------------------------------------------------------------------

## 4. Core 설계 원칙

Core는 특정 실행 환경을 알지 못하도록 설계합니다.

Core에서 다음 기술에 직접 의존하지 않는 것을 원칙으로 합니다.

``` text
Eclipse e4
Eclipse PDE
SWT
JFace
OSGi
Spring Boot
HTTP
SSE
Editor UI
```

특히 다음과 같은 Eclipse 관련 타입을 Core에 직접 사용하지 않습니다.

``` text
IProject
IFile
Bundle
Platform
Display
Shell
IEclipseContext
EPartService
```

필요한 외부 기능은 Java interface를 통해 추상화하고 Adapter 계층에서
구현합니다.

------------------------------------------------------------------------

## 5. 기본 패키지

기본 Java 패키지는 다음을 사용합니다.

``` text
kr.co.goms.gomsbook.ai
```

초기 패키지 구조는 다음을 기준으로 합니다.

``` text
kr.co.goms.gomsbook.ai
│
├─ agent
│  ├─ AgentExecutor
│  ├─ AgentRequest
│  ├─ AgentResponse
│  └─ AgentContext
│
├─ llm
│  ├─ LlmClient
│  ├─ LlmRequest
│  └─ LlmResponse
│
├─ tool
│  ├─ AgentTool
│  ├─ ToolRegistry
│  ├─ ToolExecutor
│  ├─ ToolRequest
│  └─ ToolResult
│
├─ rag
├─ plan
├─ evaluation
├─ accessibility
└─ epub
```

향후 Shorts Editor 개발 시 필요한 기능은 독립적인 도메인 패키지 또는
모듈로 확장합니다.

------------------------------------------------------------------------

## 6. Maven

프로젝트는 Maven 기반 JAR 프로젝트입니다.

``` text
Group ID
kr.co.goms.gomsbook.ai

Artifact ID
GomsBook-AI-Agent-Core

Packaging
jar
```

다른 프로젝트에서는 Maven dependency로 Core를 사용합니다.

``` xml
<dependency>
    <groupId>kr.co.goms.gomsbook.ai</groupId>
    <artifactId>GomsBook-AI-Agent-Core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

------------------------------------------------------------------------

## 7. 개발 전환 전략

기존 `GomsBook-AI-Agent`의 정상 동작을 보호하기 위해 Core 전환은
단계적으로 수행합니다.

### Phase 1 - 병행 개발

``` text
GomsBookEditor
      │
      ▼
GomsBook-AI-Agent
```

기존 경로는 그대로 유지합니다.

동시에:

``` text
GomsBook-AI-API
      │
      ▼
GomsBook-AI-Agent-Core
```

경로를 구축합니다.

### Phase 2 - Core 동기화

기존 Agent에서 검증된 순수 Java 기능을 Core로 복사합니다.

초기 Source of Truth는 기존 `GomsBook-AI-Agent`입니다.

``` text
GomsBook-AI-Agent
      │
      │ 검증된 코드
      ▼
GomsBook-AI-Agent-Core
```

기능 이동이 아니라 복사 방식으로 진행하여 기존 Editor 동작에 영향을 주지
않습니다.

### Phase 3 - API/Core 검증

다음 실행 경로를 검증합니다.

``` text
HTTP / SSE
    │
    ▼
GomsBook-AI-API
    │
    ▼
AgentEngineBridge
    │
    ▼
AgentExecutor
    │
    ▼
AgentRequest
    │
    ▼
AgentResponse
```

### Phase 4 - Core 단일화

Core가 충분히 안정화되면 Core를 Source of Truth로 변경합니다.

``` text
GomsBook-AI-Agent-Core
        │
        ├─ GomsBook-AI-Agent
        │      └─ Eclipse Adapter
        │
        └─ GomsBook-AI-API
               └─ HTTP/SSE Adapter
```

기존 Agent에 존재하는 중복 공통 구현은 단계적으로 제거합니다.

------------------------------------------------------------------------

## 8. 초기 개발 순서

Core 이식은 다음 순서로 진행합니다.

``` text
1. Agent 기본 모델
   AgentRequest
   AgentResponse
   AgentContext

2. Agent 실행 계층
   AgentExecutor

3. LLM 계층
   LlmClient
   LlmRequest
   LlmResponse

4. Tool Framework
   AgentTool
   ToolRegistry
   ToolExecutor
   ToolRequest
   ToolResult

5. API ↔ Core 실행 테스트

6. RAG

7. Planning

8. Evaluation

9. EPUB / Accessibility

10. 기존 GomsBook-AI-Agent의 Core 연동
```

한 번에 전체 코드를 이동하지 않고 각 단계에서 Maven compile 및 테스트를
수행합니다.

------------------------------------------------------------------------

## 9. 테스트 전략

두 실행 경로를 독립적으로 테스트합니다.

### API / Core 테스트

``` text
GomsBook-AI-API
      │
      ▼
GomsBook-AI-Agent-Core
```

검증 대상:

-   Maven dependency
-   Agent 초기화
-   AgentRequest
-   AgentExecutor
-   AgentResponse
-   Tool Calling
-   HTTP
-   SSE

### EPUB Editor 회귀 테스트

``` text
GomsBookEditor
      │
      ▼
GomsBook-AI-Agent
```

검증 대상:

-   Chat
-   Tool Calling
-   RAG
-   EPUB 생성
-   EPUB 검증
-   Accessibility
-   Image ALT

Core 개발 기간에도 기존 EPUB Editor의 정상 동작을 유지합니다.

------------------------------------------------------------------------

## 10. 향후 확장

Core는 EPUB 전용 Agent가 아닌 범용 콘텐츠 제작 AI Agent 엔진을 목표로
합니다.

``` text
                    GomsBook-AI-Agent-Core
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
             LLM             RAG             Agent
                                              │
                                        Tool Framework
                                              │
                           ┌──────────────────┴──────────────┐
                           │                                 │
                           ▼                                 ▼
                       EPUB Tools                       Shorts Tools
```

도메인 기능이 충분히 커지면 다음과 같은 별도 모듈 분리도 검토합니다.

``` text
GomsBook-AI-Agent-Core
GomsBook-AI-Agent-EPUB
GomsBook-AI-Agent-Shorts
```

현재 단계에서는 불필요한 모듈 분리를 피하고 Core에서 기능 경계를
명확하게 유지합니다.

------------------------------------------------------------------------

## 11. Repository

Repository:

`GomsBook-AI-Agent-Core`

주요 프로젝트:

``` text
GomsBookEditor
GomsBook-AI-Agent
GomsBook-AI-Agent-Core
GomsBook-AI-API
GomsBook-AI-MCP
```

------------------------------------------------------------------------

## 12. Current Status

현재 개발 단계:

``` text
[완료] Maven Core 프로젝트 생성
[완료] Git Repository 연결
[진행] Agent 기본 계층 Core 이식
[예정] Maven Compile 검증
[예정] GomsBook-AI-API ↔ Core 연계
[예정] HTTP/SSE 실행 테스트
[예정] RAG / Tool / EPUB 기능 단계적 이식
[예정] GomsBook-AI-Agent → Core 최종 연동
```

------------------------------------------------------------------------

## License

License policy will be defined according to the GomsBook project
distribution strategy.
