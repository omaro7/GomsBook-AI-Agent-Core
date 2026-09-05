# GomsBook AI Conversation ID 설계 가이드

## 1. 개요

GomsBook AI의 Chat은 단순한 단일 질의/응답을 넘어 사용자가 AI와 대화하면서 EPUB 프로젝트를 작성하고 수정하는 방향으로 확장한다.

예를 들어 다음과 같은 연속적인 대화가 가능해야 한다.

```text
USER
전자책 AI Agent를 주제로 목차를 만들어줘.

ASSISTANT
1부 AI Agent의 이해
  1장 AI Agent란 무엇인가
  2장 Tool Calling
...

USER
2부에는 EPUB Tool 설계 내용을 추가해줘.

ASSISTANT
수정된 목차는 다음과 같습니다.
...

USER
좋아. 해당 내용으로 목차 파일을 만들어줘.
```

마지막 요청의 `"해당 내용"`을 이해하려면 현재 User Message만 LLM에 전달해서는 안 된다.

이전 User/Assistant Message를 동일한 대화 단위로 저장하고, 다음 Agent 실행 시 Conversation History를 LLM Context에 다시 제공해야 한다.

이를 위해 GomsBook AI에 `conversationId`를 도입한다.

---

# 2. 설계 목표

Conversation ID 도입의 주요 목표는 다음과 같다.

1. 하나의 Chat 대화를 고유하게 식별한다.
2. User/Assistant Message를 Conversation 단위로 저장한다.
3. 다음 질문에서 이전 대화를 LLM Context로 제공한다.
4. 하나의 Conversation에서 여러 Agent Run을 연결한다.
5. Tool Calling과 Approval을 Agent Run에 연결한다.
6. PostgreSQL에서 Agent 실행 이력을 추적할 수 있도록 한다.
7. Logback 로그와 PostgreSQL 실행 이력을 동일한 ID로 추적할 수 있도록 한다.
8. 향후 사용자와 AI가 대화하면서 Chapter를 공동 집필할 수 있는 기반을 제공한다.

---

# 3. 핵심 식별자

GomsBook AI에서는 다음 식별자를 구분한다.

```text
projectId
    │
    └── conversationId
            │
            ├── messageId
            │
            └── runId
                  │
                  ├── toolCallId
                  └── approvalId
```

## projectId

현재 작업 중인 EPUB 프로젝트를 식별한다.

예:

```text
epub-ai-agent
lunchwork_seoul
third_jeju
```

하나의 Project에는 여러 Conversation이 존재할 수 있다.

---

## conversationId

하나의 Chat 대화를 식별한다.

예:

```text
7b381f7a-1af5-4be7-87b4-...
```

사용자가 새로운 Chat을 시작하면 새로운 `conversationId`를 발급한다.

동일한 Chat에서 질문을 계속하는 동안에는 동일한 `conversationId`를 사용한다.

---

## messageId

Conversation 안의 개별 메시지를 식별한다.

```text
Conversation
 ├─ USER Message
 ├─ ASSISTANT Message
 ├─ USER Message
 └─ ASSISTANT Message
```

각 메시지는 고유한 `messageId`를 가진다.

---

## runId

사용자의 한 번의 질문에 대한 Agent 실행을 식별한다.

같은 Conversation에서도 User가 새로운 질문을 하면 새로운 `runId`가 생성된다.

```text
conversationId=C001

 ├─ runId=R001
 │    "목차를 만들어줘"
 │
 ├─ runId=R002
 │    "2부를 수정해줘"
 │
 └─ runId=R003
      "해당 내용으로 목차 파일을 만들어줘"
```

따라서 `conversationId`와 `runId`는 서로 다른 책임을 가진다.

---

# 4. Project와 Conversation 관계

Conversation은 하나의 EPUB Project에 귀속시키는 것을 기본 원칙으로 한다.

```text
epub-ai-agent
 ├─ Conversation A
 ├─ Conversation B
 └─ Conversation C

lunchwork_seoul
 ├─ Conversation D
 └─ Conversation E
```

현재 Chat에서 Project를 변경하는 경우 기존 Conversation을 다른 Project의 Conversation으로 재사용하지 않는다.

이를 통해 다음 정보의 Scope를 명확하게 유지한다.

- Conversation History
- Agent Run
- Tool Calling
- Approval
- EPUB Project Context
- 향후 Project RAG Context

---

# 5. Conversation Message

Conversation에서 발생하는 실제 메시지를 저장한다.

기본 Role은 다음과 같다.

```java
public enum ConversationMessageRole {

    USER,

    ASSISTANT,

    TOOL
}
```

Conversation History 예:

```text
USER
목차를 만들어줘.

ASSISTANT
1부 AI Agent의 이해
1장 AI Agent란 무엇인가
2장 Tool Calling
...

USER
2부에는 EPUB Tool 설계를 추가해줘.

ASSISTANT
수정된 목차입니다.
...

USER
해당 내용으로 목차 파일을 만들어줘.
```

마지막 User Message를 처리할 때 이전 메시지들을 함께 LLM에 전달한다.

---

# 6. Conversation Context 처리

기존 방식이 다음과 같다고 가정한다.

```text
현재 User Message
       │
       ▼
      LLM
```

Conversation 도입 후에는 다음 구조가 된다.

```text
conversationId
       │
       ▼
ConversationStore
       │
       ▼
Conversation History
       │
       ├─ USER
       ├─ ASSISTANT
       ├─ USER
       └─ ASSISTANT
       │
       ▼
현재 USER Message
       │
       ▼
      LLM
```

따라서 LLM은 `"해당 내용"`, `"앞의 목차"`, `"방금 작성한 1장"` 같은 대화 의존적인 표현을 해석할 수 있다.

---

# 7. Tool과 Conversation의 책임 분리

Tool이 Conversation History를 직접 조회하거나 해석하지 않는다.

다음 구조는 사용하지 않는다.

```text
CreateEpubNavigationTool
        │
        ▼
ConversationRepository
        │
        ▼
이전 Assistant Message 검색
```

Tool은 Conversation을 이해할 책임이 없다.

올바른 구조는 다음과 같다.

```text
Conversation History
        │
        ▼
       LLM
        │
        ▼
의도 및 Tool Arguments 생성
        │
        ▼
       Tool
```

예를 들어 사용자가 다음과 같이 요청한다.

```text
해당 내용으로 목차 파일을 만들어줘.
```

LLM은 Conversation History를 이용하여 `"해당 내용"`을 해석한다.

그 후 Tool에는 완성된 구조화 데이터를 전달한다.

```json
{
  "items": [
    ...
  ]
}
```

Tool은 전달받은 arguments만 이용하여 EPUB을 변경한다.

---

# 8. AgentContext

Agent 실행 중 필요한 Trace 정보를 `AgentContext`를 통해 전달한다.

개념적인 구조는 다음과 같다.

```java
public class AgentContext {

    private final String projectId;

    private final String conversationId;

    private final String runId;
}
```

각 필드의 역할은 다음과 같다.

```text
projectId
→ 현재 EPUB Project

conversationId
→ 현재 Chat Conversation

runId
→ 현재 Agent 실행
```

Tool에서 `conversationId`가 필요한 경우 주로 Logging 및 Trace 목적으로 사용한다.

Tool이 `conversationId`를 이용하여 Conversation History를 직접 조회하지 않는다.

---

# 9. PostgreSQL 저장 구조

Conversation과 Agent Execution History는 PostgreSQL에 구조화하여 저장한다.

기본 테이블은 다음 5개로 구성한다.

```text
ai_conversation
ai_conversation_message
ai_agent_run
ai_agent_tool_call
ai_agent_approval
```

관계는 다음과 같다.

```text
ai_conversation
    │
    ├── ai_conversation_message
    │
    └── ai_agent_run
            │
            ├── ai_agent_tool_call
            │
            └── ai_agent_approval
```

---

# 10. ai_conversation

Chat Conversation 자체를 저장한다.

주요 정보:

```text
conversation_id
project_id
title
status
created_at
updated_at
```

예:

```text
conversation_id = C001
project_id      = epub-ai-agent
title           = EPUB AI Agent 목차 작성
status          = ACTIVE
```

---

# 11. ai_conversation_message

User/Assistant/Tool Message를 저장한다.

주요 정보:

```text
message_id
conversation_id
run_id
role
content
sequence_no
created_at
```

`sequence_no`를 이용하여 Conversation Message의 순서를 보장한다.

---

# 12. ai_agent_run

한 번의 Agent 실행을 저장한다.

주요 정보:

```text
run_id
conversation_id
project_id
user_message_id
assistant_message_id
status
model_name
started_at
completed_at
error_code
error_message
```

Agent Run 상태 예:

```java
public enum AgentRunStatus {

    RUNNING,

    WAITING_FOR_APPROVAL,

    COMPLETED,

    FAILED,

    CANCELLED
}
```

---

# 13. ai_agent_tool_call

Agent에서 실행한 Tool Calling 이력을 저장한다.

주요 정보:

```text
tool_call_id
run_id
conversation_id
tool_name
status
arguments
result
started_at
completed_at
error_code
error_message
```

`arguments`와 `result`는 PostgreSQL `jsonb` 사용을 기본으로 한다.

예:

```json
{
  "authorName": "곰스",
  "profile": "곰스작가입니다."
}
```

Tool Calling History를 통해 특정 Run에서 어떤 Tool이 어떤 arguments로 실행되었는지 추적할 수 있다.

---

# 14. ai_agent_approval

Tool 실행 과정에서 발생하는 Approval을 저장한다.

주요 정보:

```text
approval_id
run_id
conversation_id
tool_call_id
tool_name
action
title
message
file_name
payload
status
created_at
resolved_at
```

Approval의 구조화 데이터는 `payload jsonb`에 저장한다.

Approval 상태 예:

```java
public enum AgentApprovalStatus {

    PENDING,

    APPROVED,

    REJECTED,

    CANCELLED
}
```

---

# 15. Agent 실행 흐름

Conversation 기반 Agent 실행의 기본 흐름은 다음과 같다.

```text
React Chat
    │
    │ projectId
    │ conversationId
    │ message
    ▼
AgentRunService
    │
    ├─ Conversation 확인
    │
    ├─ USER Message 저장
    │
    ├─ AgentRun 생성
    │
    ├─ Conversation History 조회
    │
    ├─ LLM 호출
    │
    ├─ Tool Calling
    │
    ├─ Approval
    │
    ├─ ASSISTANT Message 저장
    │
    └─ AgentRun 완료
```

상세 실행 예:

```text
① USER Message 저장

② AgentRun
   RUNNING

③ Conversation History 조회

④ LLM 실행

⑤ Tool Calling 발생
   AgentToolCall 저장

⑥ Approval 필요
   AgentApproval 저장
   AgentRun = WAITING_FOR_APPROVAL

⑦ 사용자 승인

⑧ Tool 실행

⑨ ToolCall = SUCCESS

⑩ ASSISTANT Message 저장

⑪ AgentRun = COMPLETED
```

---

# 16. React의 conversationId 관리

`conversationId`는 Chat UI/API 계층에서 관리한다.

새로운 Chat을 시작할 때 Conversation을 생성한다.

```text
React
 │
 │ conversationId 없음
 ▼
POST /api/conversations
 │
 ▼
conversationId 발급
```

이후 Agent 요청에는 항상 동일한 `conversationId`를 전달한다.

예:

```json
{
  "projectId": "epub-ai-agent",
  "conversationId": "7b381f7a-...",
  "message": "목차를 만들어줘."
}
```

다음 요청:

```json
{
  "projectId": "epub-ai-agent",
  "conversationId": "7b381f7a-...",
  "message": "해당 내용으로 목차 파일을 만들어줘."
}
```

두 요청의 `runId`는 서로 다르지만 `conversationId`는 동일하다.

---

# 17. Logging

PostgreSQL Execution History와 Application Log는 역할을 분리한다.

```text
Logback / File
        │
        └─ Application / Diagnostic Log

PostgreSQL
        │
        └─ Conversation / Agent Execution History
```

PostgreSQL에 모든 `log.info()` 메시지를 저장하지 않는다.

PostgreSQL에는 다음과 같은 구조화된 실행 정보를 저장한다.

```text
Conversation 생성
Message 저장
Agent Run 시작/완료
Tool Calling
Tool Result
Approval 요청
Approval 처리
Agent 실행 오류
```

---

# 18. MDC

Application Log에서도 Conversation 단위 추적이 가능하도록 MDC를 사용한다.

기본 Trace 값:

```java
MDC.put("projectId", projectId);
MDC.put("conversationId", conversationId);
MDC.put("runId", runId);
```

Tool Calling에서는 필요에 따라:

```java
MDC.put("toolName", toolName);
```

Approval에서는:

```java
MDC.put("approvalId", approvalId);
```

로그 예:

```text
[GomsBook AI]
projectId=epub-ai-agent
conversationId=C001
runId=R003
toolName=create_epub_navigation
Tool execution started
```

PostgreSQL에도 동일한 `conversationId`와 `runId`가 존재하므로 Application Log와 Execution History를 서로 연결할 수 있다.

---

# 19. 비동기 처리와 MDC

GomsBook AI는 SSE와 Agent 비동기 실행을 사용하므로 MDC 처리 시 Thread 변경을 고려해야 한다.

MDC는 ThreadLocal 기반이므로 새로운 Executor Thread에서 자동으로 전달되지 않을 수 있다.

Agent 실행 Thread에서는 필요한 Context를 명시적으로 설정하고 종료 시 반드시 제거한다.

개념적인 형태:

```java
try {

    MDC.put("projectId", projectId);
    MDC.put("conversationId", conversationId);
    MDC.put("runId", runId);

    // Agent 실행

} finally {

    MDC.clear();
}
```

실제 Executor 구조에 따라 MDC Context 전달 방식을 별도로 적용한다.

---

# 20. Transaction 원칙

LLM 호출이나 Approval 대기 전체를 하나의 DB Transaction으로 묶지 않는다.

다음 구조는 사용하지 않는다.

```text
BEGIN

Message INSERT

LLM 호출

Tool 호출

Approval 대기

COMMIT
```

Agent 실행은 장시간 지속될 수 있으므로 각 상태 변경을 짧은 Transaction으로 저장한다.

예:

```text
TX 1
 ├─ USER Message INSERT
 └─ AgentRun RUNNING
COMMIT

LLM 실행

TX 2
 └─ ToolCall INSERT
COMMIT

Approval 발생

TX 3
 ├─ Approval INSERT
 └─ AgentRun WAITING_FOR_APPROVAL
COMMIT
```

---

# 21. Conversation History 크기

PostgreSQL에는 전체 Conversation History를 보관한다.

초기 구현에서는 전체 Message History를 LLM에 전달할 수 있다.

하지만 Conversation이 길어지면 Token 사용량과 Context Window 문제가 발생할 수 있다.

향후에는 다음 구조로 확장한다.

```text
Conversation
     │
     ├─ Conversation Summary
     │
     └─ 최근 N개 Message
              │
              ▼
             LLM
```

초기 Conversation ID 구현 단계에서는 Summary 기능을 구현하지 않는다.

Conversation 연결과 History 전달이 정상적으로 동작하는 것을 먼저 검증한다.

---

# 22. 기존 Logging Table

현재 PostgreSQL에는 임시 Logging 목적으로 생성한 다음 테이블이 존재한다.

```text
agent_execution_log
```

신규 Conversation + Agent Execution History가 정상적으로 구축되기 전까지 기존 테이블을 유지한다.

진행 순서는 다음과 같다.

```text
신규 Conversation/Execution History 구축
        ↓
AgentRunService 연동
        ↓
실제 Chat 테스트
        ↓
PostgreSQL 데이터 검증
        ↓
Logback/MDC 검증
        ↓
기존 agent_execution_log 사용 여부 확인
        ↓
최종 삭제
```

최종적으로 더 이상 사용되지 않는 것이 확인되면 다음 DDL을 수행한다.

```sql
DROP TABLE IF EXISTS agent_execution_log;
```

`flyway_schema_history`는 Flyway Migration 관리 테이블이므로 삭제하지 않는다.

---

# 23. 구현 순서

Conversation ID는 다음 순서로 구현한다.

```text
1. PostgreSQL 신규 5개 테이블 DDL 설계

2. Flyway Migration 작성

3. Conversation 모델 구현

4. ConversationMessage 모델 구현

5. ConversationRepository 구현

6. ConversationMessageRepository 구현

7. AgentRun 모델/Repository 구현

8. AgentToolCall 모델/Repository 구현

9. AgentApproval 모델/Repository 구현

10. ConversationService 구현

11. ExecutionHistoryService 구현

12. AgentRunRequest에 conversationId 추가

13. AgentContext에 conversationId 추가

14. AgentRunService에 Conversation History 연동

15. LlmClient에 Conversation History 전달

16. USER / ASSISTANT Message 저장

17. Tool Calling History 저장

18. Approval History 저장

19. MDC에 projectId / conversationId / runId 적용

20. React chatStore에 conversationId 추가

21. React → API 요청에 conversationId 전달

22. 실제 연속 대화 테스트

23. PostgreSQL 실행 이력 검증

24. 기존 agent_execution_log 제거
```

---

# 24. 검증 시나리오

Conversation 기능 구현 후 다음 시나리오로 검증한다.

### Step 1

사용자:

```text
전자책 AI Agent를 주제로 목차를 만들어줘.
```

DB:

```text
conversationId = C001
runId          = R001
```

Conversation Message:

```text
USER
전자책 AI Agent를 주제로 목차를 만들어줘.

ASSISTANT
1부 ...
2부 ...
3부 ...
```

### Step 2

사용자:

```text
2부에 EPUB Tool Calling 내용을 추가해줘.
```

DB:

```text
conversationId = C001
runId          = R002
```

LLM은 R001의 Conversation History를 전달받아 기존 목차를 수정한다.

### Step 3

사용자:

```text
좋습니다. 해당 내용을 목차 파일로 만들어줘.
```

DB:

```text
conversationId = C001
runId          = R003
```

LLM Context:

```text
USER      최초 목차 요청
ASSISTANT 최초 목차
USER      2부 수정 요청
ASSISTANT 수정된 목차
USER      해당 내용을 목차 파일로 만들어줘
```

LLM은 `"해당 내용"`을 직전 Assistant Message의 수정된 목차로 해석하고 필요한 EPUB Tool Arguments를 생성한다.

### 성공 기준

다음 조건을 모두 만족하면 Conversation ID 기능이 정상 동작한 것으로 판단한다.

```text
[ ] 동일 Chat에서 conversationId가 유지된다.

[ ] User 질문마다 새로운 runId가 생성된다.

[ ] USER Message가 PostgreSQL에 저장된다.

[ ] ASSISTANT Message가 PostgreSQL에 저장된다.

[ ] Message 순서가 유지된다.

[ ] 다음 Agent Run에서 이전 Conversation History가 LLM에 전달된다.

[ ] 이전 Assistant 응답을 참조하는 후속 질문이 정상 처리된다.

[ ] Tool Calling이 runId와 연결된다.

[ ] Approval이 runId와 연결된다.

[ ] Logback에서 conversationId와 runId를 추적할 수 있다.

[ ] PostgreSQL과 Logback의 conversationId/runId가 일치한다.
```

---

# 25. 최종 아키텍처

```text
                         React Chat
                              │
                    projectId
                    conversationId
                    message
                              │
                              ▼
                       GomsBook-AI-API
                              │
                              ▼
                        AgentRunService
                       /       │       \
                      /        │        \
                     ▼         ▼         ▼
           Conversation    AgentEngine   Execution
              Service                    History
                 │                          │
                 ▼                          ▼
       ConversationRepository       AgentRunRepository
       MessageRepository            ToolCallRepository
                 │                  ApprovalRepository
                 │                          │
                 └──────────┬───────────────┘
                            ▼
                        PostgreSQL

                            +

                      MDC / Logback
                            │
                            ▼
                     Application Log
```

---

# 26. 설계 원칙 요약

GomsBook AI의 Conversation ID 설계는 다음 원칙을 따른다.

**Conversation은 대화를 식별한다.**

```text
conversationId
```

**Run은 한 번의 Agent 실행을 식별한다.**

```text
runId
```

**Tool과 Approval은 Run에 귀속한다.**

```text
conversationId
    └─ runId
         ├─ toolCallId
         └─ approvalId
```

**Conversation History 해석은 LLM의 책임이다.**

Tool이 Conversation History를 직접 조회하거나 해석하지 않는다.

**PostgreSQL은 Conversation과 Agent Execution History의 구조화된 이력을 저장한다.**

**Logback은 Application/Diagnostic Logging을 담당한다.**

두 시스템은 동일한:

```text
projectId
conversationId
runId
```

를 사용하여 서로 추적 가능하도록 한다.

이 구조를 기반으로 GomsBook AI는 단일 명령 기반 EPUB Agent에서 사용자가 AI와 대화하면서 목차, 작가소개, 판권, Chapter 등의 콘텐츠를 함께 작성하고 최종 결과를 EPUB Tool을 통해 실제 프로젝트에 반영하는 대화형 EPUB 제작 Agent로 확장한다.