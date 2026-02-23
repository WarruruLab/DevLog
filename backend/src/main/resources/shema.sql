-- 1. 기존 테이블 삭제
DROP TABLE IF EXISTS draft;
DROP TABLE IF EXISTS session_block;
DROP TABLE IF EXISTS synced_message;
DROP TABLE IF EXISTS logical_session;

-- 2. logical_session (세션 관리)
CREATE TABLE logical_session (
                                 session_id VARCHAR(255) PRIMARY KEY,
                                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. synced_message (도메인의 SyncedMessage와 일치)
CREATE TABLE synced_message (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                message_id VARCHAR(255) NOT NULL,
                                session_id VARCHAR(255) NOT NULL,
                                content TEXT NOT NULL,
                                created_at DATETIME NOT NULL,
                                INDEX idx_session (session_id)
);

-- 4. session_block (도메인의 SessionBlock 및 data.sql과 일치)
CREATE TABLE session_block (
                               block_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               session_id VARCHAR(255) NOT NULL,
                               title VARCHAR(255) NOT NULL,       -- data.sql에서 찾는 컬럼
                               content_json JSON NOT NULL,        -- data.sql에서 찾는 컬럼
                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                               INDEX idx_session (session_id)
);

-- 5. draft (도메인의 Draft와 일치)
CREATE TABLE draft (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       session_id VARCHAR(255) NOT NULL,
                       content TEXT NOT NULL,
                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                       UNIQUE INDEX uidx_session (session_id) -- ON DUPLICATE KEY UPDATE를 위해 필요
);
