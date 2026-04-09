# ADR-006: `concept-c` 흐름을 위한 DB 구조 개편

## 상태

Accepted

## 결정 시점

2026-04-09

## 배경

DevLog는 DevTalk 세션 로그를 가져와 메시지를 블록으로 구조화하고, 사용자가 블록을 선택해 블로그 초안을 생성하는 서비스다.

초기 DB 구조는 `logical_session`, `synced_message`, `session_block`, `draft` 중심으로 잡혀 있었다. 이 구조는 기본 저장에는 문제가 없지만, 현재 목표인 `concept-c` 흐름과는 맞지 않는 부분이 분명해졌다.

현재 프론트 흐름에서 필요한 것은 아래와 같다.

1. 홈 화면에서는 세션 요약 상태만 본다.
2. 세션 상세 진입 시 먼저 동기화와 분석 상태를 확인한다.
3. 아직 블록에 들어가지 않은 메시지가 있으면 `분석 중` 상태를 명확히 보여준다.
4. 모든 메시지가 블록에 반영된 뒤에만 블록 선택이 가능해야 한다.
5. 사용자가 직접 고른 블록 기준으로 초안이 생성되어야 한다.

하지만 기존 구조로는 아래를 설명하기 어렵다.

- 아직 블록에 들어가지 않은 메시지가 몇 개인지
- 분석이 진행 중인지, 실패인지, 완료인지
- 어떤 메시지가 어떤 블록에 포함됐는지
- 사용자가 어떤 블록을 어떤 순서로 선택했는지
- 초안이 재생성 가능한 버전인지

특히 현재 [`DraftService.java`](/D:/project_univ/warruru-lab/devlog/backend/src/main/java/com/devlog/devlog/service/draft/DraftService.java)는 세션의 전체 블록을 그대로 초안 생성 입력으로 사용한다. 이 방식은 `글 생성` 전에 사용자가 블록을 선택하는 현재 흐름과 맞지 않는다.

## 결정

DevLog의 DB 구조를 `세션 존재` 중심의 단순 저장 구조에서 `상태 추적 + 메시지-블록 매핑 + 선택 기반 초안 생성` 구조로 확장한다.

핵심 엔티티는 아래 6개로 정리한다.

1. `logical_session`
2. `session_message`
3. `session_block`
4. `session_block_message`
5. `draft`
6. `draft_block`

## 상세 결정

### 1. `logical_session`을 집계와 상태의 루트 엔티티로 확장한다

기존 `logical_session`은 세션 식별자 정도만 관리했다. 개편 후에는 홈 화면과 세션 상세 상단에서 바로 읽을 수 있는 집계와 상태를 함께 가진다.

포함 대상 예시:

- `source_session_id`
- `title`
- `session_status`
- `sync_status`
- `analysis_status`
- `total_message_count`
- `structured_message_count`
- `unstructured_message_count`
- `block_count`
- `last_synced_at`
- `last_analyzed_at`

이 선택을 한 이유:

- 홈 화면이 블록 상세를 읽지 않고도 세션 카드 상태를 그릴 수 있어야 한다.
- 세션 상세 진입 직후 `분석 중` 상태를 빠르게 보여주려면 루트 엔티티에 집계가 있어야 한다.

### 2. `synced_message`를 `session_message`로 재정의한다

기존 `synced_message`는 메시지 저장소 역할에 머물렀다. 개편 후에는 메시지의 구조화 상태까지 표현할 수 있어야 한다.

주요 컬럼 예시:

- `message_id`
- `session_id`
- `role`
- `author_name`
- `content`
- `message_created_at`
- `structure_status`
- `structured_at`

이 선택을 한 이유:

- `미구조화 메시지 n개`를 계산하려면 메시지 단위 상태가 필요하다.
- 단순히 메시지를 가져왔다는 사실만으로는 `concept-c`의 분석 오버레이 상태를 설명할 수 없다.

### 3. `session_block`은 UI와 생성에 필요한 메타를 함께 가져야 한다

기존 `session_block`의 `title`, `content_json`만으로는 부족하다.

추가 대상 예시:

- `sequence_no`
- `block_type`
- `summary`
- `status`
- `source_message_count`

이 선택을 한 이유:

- 블록 선택 화면은 고정된 순서와 역할을 가진 블록 목록을 필요로 한다.
- 초안 생성도 블록의 의미 단위가 명확해야 안정적으로 동작한다.

### 4. `session_block_message`를 추가한다

이 테이블은 어떤 메시지가 어떤 블록에 포함됐는지를 저장한다.

이 선택을 한 이유:

- 모든 메시지가 블록에 들어갔는지 판별할 수 있다.
- 미구조화 메시지 수를 계산할 수 있다.
- 블록별 메시지 범위와 흐름을 추적할 수 있다.

즉, `분석 중` 상태를 화면 연출이 아니라 실제 데이터 상태로 설명할 수 있게 된다.

### 5. `draft`는 세션당 단일 결과물이 아니라 버전 가능한 엔티티로 본다

기존 `draft`는 세션당 하나의 결과물을 전제로 하기 쉬운 구조였다. 개편 후에는 `version_no`를 가지는 버전 가능한 초안 구조로 본다.

주요 컬럼 예시:

- `draft_id`
- `session_id`
- `version_no`
- `status`
- `title`
- `content_markdown`

이 선택을 한 이유:

- 사용자가 블록 조합을 바꿔 재생성할 수 있다.
- 초안 비교와 이력 관리가 가능해진다.
- `draft.session_id UNIQUE` 구조는 현재 흐름과 맞지 않는다.

### 6. `draft_block`을 추가한다

이 테이블은 어떤 블록을 어떤 순서로 선택해 초안을 만들었는지 저장한다.

이 선택을 한 이유:

- 초안의 생성 근거를 남길 수 있다.
- 사용자의 선택 흐름을 복원할 수 있다.
- `선택 -> 생성`이라는 UX 핵심 흐름이 데이터에도 남는다.

## 왜 이런 선택을 했는가

### 화면 상태와 데이터 상태를 일치시키기 위해

`concept-c`의 핵심은 분석 오버레이가 단순한 연출이 아니라 사용자가 기다려야 하는 이유를 설명하는 것이다. 따라서 DB도 `분석 중`, `구조화 완료`, `선택 가능` 상태를 직접 표현할 수 있어야 한다.

### 사용자의 선택을 초안 생성의 중심으로 두기 위해

DevLog의 초안은 전체 세션 자동 요약이 아니라, 사용자가 블록을 골라 흐름을 정한 뒤 생성되는 결과물이어야 한다. 그래서 `draft_block`이 필요하다.

### 메시지 단위 추적이 필요하기 때문에

세션 단위 집계만으로는 미구조화 메시지와 분석 진행률을 설명할 수 없다. 메시지와 블록 간 매핑인 `session_block_message`가 있어야 분석 상태를 정확히 계산할 수 있다.

### 프론트 구현을 단순하게 만들기 위해

세션 목록, 상태 오버레이, 블록 목록, 초안 생성 이력을 각각 별도 계산하지 않고 명확한 테이블 책임으로 분리하면 이후 API 설계와 프론트 연결이 단순해진다.

## 결과

- ERD가 `분석 중 -> 블록 선택 -> 글 생성` 흐름을 직접 설명할 수 있게 된다.
- 홈 화면은 `logical_session` 집계만으로 구성 가능해진다.
- 세션 상세 화면은 `session_message`, `session_block`, `session_block_message` 조합으로 분석 상태를 정확히 보여줄 수 있다.
- 초안은 `draft`와 `draft_block` 조합으로 버전과 선택 이력을 함께 관리할 수 있다.
