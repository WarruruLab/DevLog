# DevLog ERD (Entity Relationship Diagram)

## 1. 개요
DevLog 프로젝트의 데이터베이스 설계도입니다.
- **작성일:** 2026-02-11
- **도구:** Mermaid

## 2. ERD 다이어그램

```mermaid
erDiagram
    erDiagram
    %% [가상의 논리 그룹] 실제 테이블은 아니지만 기준점이 되는 세션
    LOGICAL_SESSION {
        string session_id PK "모든 데이터의 기준점"
    }

    %% 1. 원본 메시지들 (N개)
    synced_message {
        string message_id PK
        string session_id FK
        string content
        datetime created_at
    }

    %% 2. 분석된 블록들 (N개)
    session_block {
        bigint block_id PK
        string session_id FK
        string title
        json content_json
    }

    %% 3. 최종 초안 (N개)
    draft {
        bigint id PK
        string session_id FK
        string content
    }

    %% 관계 정의: 세션 하나가 모든 것을 거느린다
    LOGICAL_SESSION ||--|{ synced_message : "1.메시지_수집"
    LOGICAL_SESSION ||--|{ session_block : "2.데이터_분석"
    LOGICAL_SESSION ||--|{ draft : "3.초안_생성"
