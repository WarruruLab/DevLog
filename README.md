# 🗒 개발로그(DevLog) — 개발 대화 로그를 블로그 초안으로

> **"사고과정과 해결과정을 기록했으니, 흐름에 맞게 글을 써야한다."**
>
> 개발로그(DevLog)는 개발톡(DevTalk)에서 생성된 문제 해결 로그를 가져와
> MCP Server가 분류한 블록을 선택하고, AI가 블로그 초안을 자동으로 생성하는 서비스입니다.

<br/>

## 📍 WarruruLab 전체 파이프라인

> 개발로그(DevLog)는 **WarruruLab**의 세 번째 서비스입니다.

| | 개발톡(DevTalk) | MCP Server | 개발로그(DevLog) |
|:---:|:---:|:---:|:---:|
| **역할** | AI 대화로 문제 해결 로그 생성 | 메시지를 의미 단위 블록으로 분류 | 블록 선택 → 블로그 초안 생성 |
| **기술** | Spring Boot · Java 21 | FastAPI · Python · Ollama | Spring Boot · Java 21 |
| **출력** | Session · Message 저장 | session_block (tags + code_snippets) | 블로그 글 초안 |

서비스 간 호출은 개발로그(DevLog)가 주도합니다.

```
[Step 1] 메시지 동기화                           구현 완료
DevLog ──── REST ────▶ DevTalk
            내부 API로 메시지 cursor 기반 페이징 수집

[Step 2] 블록 분류                               구현 중
DevLog ──── REST ────▶ MCP Server
            메시지 전달 → session_block 반환

[Step 3] 초안 생성                               구현 중
DevLog ──── REST ────▶ Gemini API
            저장된 블록 기반으로 블로그 초안 생성
```

연관 레포: [개발톡(DevTalk)](https://github.com/WarruruLab/DevTalk) · [MCP Server](https://github.com/WarruruLab/MCP)

---

<br/>

## 📌 개발로그(DevLog) 한눈에 보기

```
개발톡(DevTalk)에서 세션 ID 전달
         ↓
DevTalk 내부 API 호출 → 메시지 동기화 (synced_message)
         ↓
MCP Server 호출 → session_block 생성 및 저장 (구현 예정)
         ↓
사용자가 블록 선택
         ↓
Gemini API 호출 → 블로그 초안 생성 및 저장 (draft)
```

<br/>

## 🎯 왜 이것을 만들었나

문제를 해결한 뒤 "블로그로 정리해야지"라고 생각하지만,
막상 빈 화면 앞에 앉으면 어디서부터 시작해야 할지 막막합니다.

| 기존 방식 | 개발로그(DevLog) |
|:---|:---|
| 로그를 보며 글을 처음부터 직접 작성 | 대화 로그가 자동으로 동기화됨 |
| 전체 로그를 AI에 넣고 요약 요청 | 의미 단위 블록으로 나눠 원하는 것만 선택 |
| 글의 구성을 처음부터 설계 | AI가 선택된 블록 기반으로 초안 생성 |

<br/>

## ⚙️ 기술 스택

| 영역 | 기술 | 선택 이유 |
|:---|:---|:---|
| Backend | Spring Boot 4.0.2 · Java 21 | 개발톡(DevTalk)과 동일한 스택으로 일관성 유지 |
| Web | Spring MVC | REST API 위주, 스트리밍 불필요 |
| Database | MySQL 8 · **Spring JDBC** (진행 중) | 아래 DB 전환 전략 참고 |
| AI | Google Gemini API | 개발톡(DevTalk)과 동일한 LlmClient 구조 재사용 |
| 외부 연동 | Spring RestClient | 개발톡(DevTalk) 내부 API 호출 |
| Frontend | Vite · TypeScript | 초기 구성 단계 |
| 문서화 | ADR 5개 | 설계 결정의 맥락과 이유 기록 |

### DB 전환 전략

개발톡(DevTalk)과 동일한 단계적 전환 방식을 적용합니다.

```
[1단계] InMemory     →  [2단계] JDBC (현재)  →  [3단계] JPA
  빠른 흐름 검증             SQL 직접 작성              ORM 적용
  구조 확인용                성능 측정 · 최적화           JDBC와 비교
```

<br/>

## 🏗 아키텍처

### 레이어 구조

```
           ┌─────────────────────────────┐
           │       Frontend (예정)        │
           └────────────┬────────────────┘
                        │ 요청 (REST)
                        ▼
           ┌─────────────────────────────┐
           │      Spring Boot API        │
           │  ├── SyncController         │
           │  ├── AnalysisController     │
           │  └── DraftController        │
           └────────────┬────────────────┘
                        │ 조회 / 저장
                        ▼
           ┌─────────────────────────────┐
           │    Repository Interface     │
           │  (JdbcSyncedMessage         │
           │   JdbcSessionBlock          │
           │   JdbcDraft                 │
           │   JdbcLogicalSession)       │
           └──────┬──────────────┬───────┘
                  │              │
                  ▼              ▼
            MySQL 8.0     외부 서비스 호출
                  │    (DevTalk · MCP · Gemini)
                  │              │
                  └──────┬───────┘
                         │ 응답 반환
                         ▼
           ┌─────────────────────────────┐
           │      Spring Boot API        │
           └────────────┬────────────────┘
                        │ 응답 (JSON)
                        ▼
           ┌─────────────────────────────┐
           │       Frontend (예정)        │
           └─────────────────────────────┘
```

### 핵심 도메인 설계

**LogicalSession** — 세션 기준점

| 필드 | 타입 | 설명 |
|:---|:---|:---|
| sessionId | String | 개발톡(DevTalk)의 sessionId를 그대로 사용 |

**SyncedMessage** — 개발톡(DevTalk)에서 동기화한 원본 메시지

| 필드 | 타입 | 설명 |
|:---|:---|:---|
| messageId | String | 개발톡(DevTalk) messageId |
| sessionId | String | 소속 세션 |
| content | String | 메시지 내용 |
| createdAt | LocalDateTime | cursor 기반 동기화 기준 |

**SessionBlock** — MCP Server가 분류한 블록

| 필드 | 타입 | 설명 |
|:---|:---|:---|
| blockId | Long (Auto Increment) | 내부 생성 ID |
| sessionId | String | 소속 세션 |
| title | String | 블록 제목 |
| contentJson | JSON | tags · code_snippets 등 MCP 분석 결과 |

**Draft** — 생성된 블로그 초안

| 필드 | 타입 | 설명 |
|:---|:---|:---|
| id | Long (Auto Increment) | 내부 생성 ID |
| sessionId | String | 소속 세션 |
| content | String | Gemini가 생성한 Markdown 초안 |

> 개발톡(DevTalk)은 InMemory 단계부터 시작해서 DB 없이 객체에 ID를 담아야 했기 때문에 `UUID.randomUUID()`가 자연스러운 선택이었습니다.
> 반면 개발로그(DevLog)는 처음부터 JDBC로 시작했기 때문에 INSERT 후 `GeneratedKeyHolder`로 ID를 바로 받을 수 있는 Auto Increment가 더 자연스러웠습니다.
> JPA 도입 시점에 UUID와 비교 분석해 변경 여부를 결정할 예정입니다.

<br/>

## 💡 주목할 설계 결정들

### 1. Pull Model — DevLog가 주도하는 데이터 수집

개발톡(DevTalk)이 DevLog에 데이터를 밀어넣는 Push 방식 대신,
DevLog가 필요한 시점에 개발톡(DevTalk)을 호출해서 가져오는 Pull 방식을 택했습니다.

- 개발톡(DevTalk)은 채팅 서비스 본연에만 집중, DevLog 존재를 알 필요 없음
- cursor(`createdAt`) 기반 페이징으로 중복 없이 동기화
- 개발톡(DevTalk) 장애가 DevLog 전체에 영향을 주지 않음

```java
// cursor 기반 동기화
String cursor = messageRepository.findLastCreatedAtBySessionId(sessionId)
    .map(LocalDateTime::toString)
    .orElse(null);  // 처음이면 전체 수집

InternalMessagePageResponse response = devTalkClient.fetchMessages(sessionId, cursor);
```

### 2. LLM 추상화 재사용 — 개발톡(DevTalk)과 동일한 구조

개발톡(DevTalk)에서 만든 `LlmClient` 인터페이스 구조를 그대로 가져와서 사용합니다.
Gemini SDK 없이 RestClient로 직접 호출하고, 실패는 예외가 아닌 값으로 처리합니다.

```java
public interface LlmClient {
    LlmResult generate(LlmRequest request);  // Success | Failure
}
```

### 3. 세션 중심 DB 설계 — session_id를 공통 기준으로

`synced_message`, `session_block`, `draft` 모두 `session_id`를 공통 분모로 가집니다.
테이블 간 물리적 FK 제약을 걸지 않고, 애플리케이션 레벨에서 검증합니다.

```
logical_session (session_id)
      ├── synced_message  (session_id)   ← DevTalk 원본
      ├── session_block   (session_id)   ← MCP 분석 결과
      └── draft           (session_id)   ← 블로그 초안
```

<br/>

## 📁 프로젝트 구조

```
devlog/
├── backend/
│   └── src/main/java/com/devlog/devlog/
│       ├── api/
│       │   ├── controller/
│       │   │   ├── sync/        # SyncController
│       │   │   ├── analysis/    # AnalysisController
│       │   │   └── draft/       # DraftController
│       │   └── dto/             # Request · Response DTO
│       ├── domain/
│       │   ├── session/         # LogicalSession · Repository (인터페이스)
│       │   ├── sync/            # SyncedMessage · Repository (인터페이스)
│       │   ├── analysis/        # SessionBlock · Repository (인터페이스)
│       │   ├── draft/           # Draft · Repository (인터페이스)
│       │   └── llm/             # LlmClient · LlmResult · LlmRequest
│       ├── service/
│       │   ├── sync/            # SyncService
│       │   ├── analysis/        # AnalysisService
│       │   ├── draft/           # DraftService
│       │   └── llm/             # AiService
│       ├── infra/
│       │   ├── client/          # DevTalkClient
│       │   ├── llm/             # GeminiHttpClient · MockLlmClient
│       │   └── persistence/     # JdbcSyncedMessage · JdbcSessionBlock · JdbcDraft · JdbcLogicalSession
│       └── config/              # LlmConfig · RestClientConfig
│
├── frontend/                    # Vite · TypeScript (초기 구성 단계)
│
└── docs/
    ├── adr/                     # ADR-001 ~ ADR-005
    └── erd/                     # devlog-erd.md
```

<br/>

## 📝 ADR (Architecture Decision Record)

| ADR | 결정 내용 |
|:---|:---|
| ADR-001 | Pull Model 기반 단방향 데이터 파이프라인 및 3단계 처리 흐름 |
| ADR-002 | 블록 구조 및 MCP 도입 결정 |
| ADR-003 | session_id 중심 DB 설계 및 하이브리드 ID 전략 |
| ADR-004 | LLM 구조 설계 — RestClient + 인터페이스 기반 추상화 |
| ADR-005 | Python 기반 MCP 서버 분리 및 서비스 계층 역할 정의 |

<br/>

## 🚀 로컬 실행

### 사전 준비

- Java 21+, MySQL 8.0+, optional Gemini API Key
- 개발톡(DevTalk) 서버 실행 중 (기본 포트 8080)

### 백엔드

```bash
cd backend
./gradlew bootRun
# → http://localhost:8081
```

### 환경변수

| 변수명 | 설명 | 기본값 |
|:---|:---|:---|
| `SERVER_PORT` | 백엔드 HTTP 포트 | `8081` |
| `SPRING_SQL_INIT_MODE` | `schema.sql` 실행 여부 | `always` |
| `MYSQL_URL` | DB 연결 URL | `jdbc:mysql://localhost:3306/devlog?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` |
| `MYSQL_USERNAME` | DB 사용자명 | `root` |
| `MYSQL_PASSWORD` | DB 비밀번호 | 빈 값 |
| `LLM_MODE` | `mock` 또는 `gemini` | `mock` |
| `LLM_GEMINI_API_KEY` | Gemini API 키 | 빈 값 |
| `LLM_GEMINI_MODEL` | Gemini 모델명 | `gemini-2.5-flash` |
| `LLM_GEMINI_BASE_URL` | Gemini API base URL | `https://generativelanguage.googleapis.com` |
| `LLM_GEMINI_CONNECT_TIMEOUT_MS` | Gemini connect timeout | `3000` |
| `LLM_GEMINI_READ_TIMEOUT_MS` | Gemini read timeout | `30000` |
| `DEVTALK_BASE_URL` | DevTalk 서비스 루트 URL. `/api`를 붙이지 않는다 | `http://localhost:8080` |
| `DEVTALK_CONNECT_TIMEOUT_MS` | DevTalk client connect timeout | `3000` |
| `DEVTALK_READ_TIMEOUT_MS` | DevTalk client read timeout | `10000` |
| `CORS_ALLOWED_ORIGINS` | 브라우저 허용 origin 목록, 쉼표로 구분 | `http://localhost:5173,http://127.0.0.1:5173` |

프론트는 `frontend/.env.example`의 `VITE_API_BASE_URL`을 사용한다. 로컬에서는 `http://localhost:8081`, 프록시 뒤에서는 `/api`처럼 DevLog API 기준 URL을 넣으면 된다.
