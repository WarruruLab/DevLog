# ADR-007: MCP 결과 수신용 internal ingest API 도입

## 상태

Accepted

## 결정 시점

2026-04-10

## 배경

DevLog의 블록 구조화 책임은 MCP 서버에 둔다. DevLog는 메시지를 직접 chunk해서 임시 블록을 만들거나 MCP 분석 알고리즘을 구현하지 않는다.

다만 MCP 서버가 DevLog DB에 직접 연결하는 것도 1차 구현 범위에서 제외한다. DevLog DB에는 `logical_session`, `session_message`, `session_block`, `session_block_message` 간 상태와 집계 갱신 규칙이 있고, `session_block.block_id`는 `BIGINT AUTO_INCREMENT`로 생성된다. 이 규칙을 MCP 서버가 직접 다루면 DB 스키마와 트랜잭션 책임이 Python MCP 서버 쪽으로 퍼진다.

따라서 1차 구현에서는 MCP 서버가 분석 결과를 생산한 뒤 DevLog의 internal API로 결과를 전송하고, DevLog가 자기 DB에 트랜잭션으로 반영하는 구조가 필요하다.

## 결정

DevLog에 MCP 결과 수신용 internal API를 추가한다.

```text
POST /internal/mcp/session-blocks
```

이 API는 MCP가 만든 세션 블록 결과를 수신한다. 요청에는 `sessionId`, `analysisVersion`, `model`, `mode`, `blocks`를 포함한다. 각 block은 MCP 내부 식별자인 `mcpBlockId`, 순서, 타입, 제목, 요약, 메시지 ID 목록, confidence, content payload를 가진다.

DevLog는 요청을 받은 뒤 하나의 트랜잭션에서 아래 작업을 수행한다.

```text
1. logical_session.session_status = ANALYZING
2. logical_session.analysis_status = RUNNING
3. mode = REPLACE이면 기존 session_block_message 삭제
4. mode = REPLACE이면 기존 session_block 삭제
5. blocks를 session_block에 insert
6. 생성된 Long block_id와 mcpBlockId를 메모리 map으로 보관
7. 각 block.messageIds를 session_block_message에 insert
8. 매핑된 message_id를 session_message.structure_status = STRUCTURED로 갱신
9. logical_session의 total/synced/structured/unstructured/block count 갱신
10. logical_session.session_status = READY
11. logical_session.analysis_status = DONE
12. logical_session.last_analyzed_at 갱신
```

응답에는 저장된 block 수, 구조화된 message 수, 미구조화 message 수, `mcpBlockId -> block_id` 매핑, 최종 상태를 포함한다.

1차 구현의 mode는 `REPLACE`를 기준으로 한다. 실시간 append, active block 재사용, `mcpBlockId` 영속 저장은 2차 구현으로 미룬다.

요청에 포함된 `messageIds`는 DevLog의 `session_message`에 이미 존재해야 한다. 1차 구현에서는 존재하지 않는 messageId가 하나라도 있으면 전체 요청을 실패시키는 정책을 사용한다. 이 정책은 100개 더미 메시지 검증에서 누락 메시지를 빠르게 드러내기 위한 것이다.

## 이유

이 결정은 책임 경계를 다음처럼 나눈다.

- MCP: 분석 결과 생산, DevLog 저장용 payload 생성, DevLog internal API 호출
- DevLog: DB 저장 트랜잭션, 상태/count 갱신, 조회 API 제공

DevLog가 DB 저장을 담당하면 기존 Java repository와 transaction 경계를 그대로 사용할 수 있다. MCP 서버는 MySQL driver와 DevLog schema 규칙을 직접 알 필요가 없다.

또한 MCP의 `mcpBlockId`는 문자열이고 DevLog의 `block_id`는 auto increment Long이므로, 1차 구현에서는 저장 시점에만 응답용 map으로 변환한다. 이 방식은 DB 스키마 변경 없이 100개 더미 메시지의 block/message mapping 저장 검증을 먼저 끝낼 수 있다.

## 결과

DevLog 백엔드에는 다음 구성요소가 추가된다.

- `InternalMcpController`
- MCP ingest request/response DTO
- `McpBlockIngestService`
- REPLACE 저장을 위한 repository 보강

기존 `AnalysisService`의 임시 chunk 기반 블록화는 1차 검증 후 제거하거나 더 이상 기본 흐름에서 사용하지 않는다. DevLog 화면은 MCP 결과가 저장된 뒤 기존 `GET /api/sessions/{sessionId}/blocks`로 block 목록을 조회한다.

실패 시에는 저장 트랜잭션을 rollback하고 `logical_session.session_status`, `logical_session.analysis_status`, `logical_session.analysis_error_message`를 실패 상태로 갱신하는 정책을 별도로 구현한다. 트랜잭션 안에서 실패 상태까지 같이 rollback되지 않도록 실패 상태 갱신은 별도 처리 방식을 검토한다.

`REPLACE` 모드에서 기존에 `STRUCTURED`였던 메시지를 다시 `PENDING`으로 내릴지는 별도 정책이 필요하다. 1차 구현에서는 MCP 요청에 포함된 메시지를 `STRUCTURED`로 갱신하고 count는 DB 기준으로 다시 계산한다. 완전한 replace semantics가 필요해지면 `markPendingBySessionId()` 같은 repository 메서드를 추가한다.
