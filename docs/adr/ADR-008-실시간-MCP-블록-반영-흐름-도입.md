# ADR-008: 실시간 MCP 블록 반영 흐름 도입

## 상태

Proposed

## 결정 시점

2026-04-11

## 배경

현재 DevLog의 MCP 연동은 세션 단위 `REPLACE` ingest 중심이다.

```text
DevTalk -> DevLog sync -> MCP 분석 -> DevLog REPLACE ingest
```

하지만 목표는 메시지 발생 직후 block 판단 결과를 DevLog에 반영하는 실시간 구조다.

## 전체 흐름

```text
1. DevTalk에서 새 메시지가 발생한다.
2. DevTalk가 새 메시지를 MCP 서버로 실시간 전달한다.
3. MCP가 기존 active block에 append 할지, 새 block을 생성할지 판단한다.
4. MCP가 판단 결과를 DevLog internal API로 전송한다.
5. DevLog가 block, block-message mapping, session status/count를 transaction으로 저장한다.
6. DevLog 화면은 저장된 block과 상태를 조회해 즉시 반영한다.
7. 필요하면 이후 세션 전체 재분석 결과를 REPLACE ingest로 반영해 정합성을 다시 맞춘다.
```

현재 구조만으로는 아래가 부족하다.

- 실시간 append 전용 internal API
- MCP가 재참조할 block 식별자
- active block 조회 경로
- 중복 event 재시도에 대한 idempotency

## 결정

DevLog는 배치 재정렬 API와 실시간 반영 API를 분리한다.

```text
배치 재정렬:
POST /internal/mcp/session-blocks

실시간 반영:
POST /internal/mcp/session-block-events

active block 조회:
GET /internal/mcp/sessions/{sessionId}/active-block
```

추가로 아래를 함께 결정한다.

1. 기존 `POST /internal/mcp/session-blocks`는 세션 전체 `REPLACE` 용도로 유지한다.
2. `session_block`에 MCP 재참조용 외부 block 식별자를 저장한다.
3. active block은 `session_block.status`로 관리한다.
4. 실시간 ingest 요청은 `eventId` 기반 idempotency를 가진다.
5. 실시간 append 결과는 우선 저장하고, 세션 전체 정합성 보정은 이후 `REPLACE`로 허용한다.

상세 API, DB, 트랜잭션 규칙, 단계별 구현 계획은 별도 설계 문서에서 관리한다.

## 이유

- 배치 `REPLACE`와 실시간 `APPEND`는 검증 규칙과 실패 처리 방식이 다르므로 API를 분리하는 편이 명확하다.
- MCP가 같은 block을 다시 찾아 append 하려면 DevLog DB에도 외부 block 식별자가 필요하다.
- DevLog가 transaction 책임을 유지해야 block, mapping, count, status 정합성을 한 곳에서 보장할 수 있다.
- 실시간 구조에서는 중복 호출이 발생하므로 `eventId` 기반 idempotency가 필요하다.

## 결과

- DevLog는 배치 재정렬과 실시간 반영을 함께 지원하는 구조로 확장된다.
- MCP는 block 판단 엔진에 집중하고, DevLog는 저장과 조회 일관성 유지에 집중한다.
- active block 개념이 DB, internal API, 화면 상태에서 공통으로 사용된다.
- 상세 구현은 별도 설계 문서 기준으로 진행한다.
